/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.inventory

import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.Transactional
import grails.validation.ValidationException

import org.pih.warehouse.api.AvailableItem
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Category
import org.pih.warehouse.product.Product
import org.pih.warehouse.product.ProductService
import org.pih.warehouse.product.ProductType
import org.pih.warehouse.productfacts.ProductIdentifiers

/**
 * Adds and uses stock from barcode scans.
 *
 * ADD mirrors the manual inventory adjustment (InventoryController.saveAdjustmentTransaction): one "Adjustment"
 * (credit) transaction with an INVENTORY_ADJUSTMENT source. USE mirrors manual consumption: one "Consumption"
 * (debit) transaction. Either way only the scanned lot and bin change, unlike record stock, which sets every row
 * it isn't given to zero.
 *
 * Transactions are saved with disableRefresh, so the caller must refresh product availability after the commit.
 */
@Transactional
class StockScanService {

    static final int MAX_QUANTITY = 10000
    static final int MAX_NAME_LENGTH = 255
    static final int MAX_SAME_SECOND_ATTEMPTS = 3
    static final String DEFAULT_ADD_COMMENT = "Added by barcode scan"
    static final String DEFAULT_USE_COMMENT = "Used (barcode scan)"

    /** Locks that serialize scans of the same product at the same location (see perform). */
    private static final int LOCK_STRIPES = 64
    private static final Object[] LOCKS = (0..<LOCK_STRIPES).collect { new Object() } as Object[]

    InventoryService inventoryService
    ProductService productService
    AdjustInventoryService adjustInventoryService

    /** Waits the given number of milliseconds. Replaced in tests. */
    Closure<Void> sleeper = { long millis -> Thread.sleep(millis) }

    /**
     * @return the barcode's digits, ignoring spaces and dashes
     * @throws StockScanException unless it's 6 to 14 digits (the GTIN family: UPC-E through GTIN-14)
     */
    static String normalizeBarcode(String barcode) {
        String digits = barcode?.replaceAll(/[\s-]/, "")
        if (!digits) {
            throw StockScanException.badRequest("Scan or enter a barcode")
        }
        if (!(digits ==~ /\d{6,14}/)) {
            throw StockScanException.badRequest("Barcode ${barcode} isn't a retail barcode (6 to 14 digits)")
        }
        return digits
    }

    /**
     * The same item can be read as UPC-A (12 digits), EAN-13 (a leading 0) or GTIN-14 (two leading 0s). Without
     * leading zeros they're all equal.
     */
    static String gtinKey(String digits) {
        return digits.replaceFirst(/^0+/, "") ?: "0"
    }

    /**
     * @throws StockScanException if the code is neither a barcode nor an NDC
     */
    static ProductIdentifiers parseIdentifiers(String barcode, String ndc) {
        try {
            return ProductIdentifiers.parse(barcode, ndc)
        } catch (IllegalArgumentException e) {
            throw StockScanException.badRequest(e.message)
        }
    }

    /**
     * Active products whose UPC is the barcode (ignoring leading zeros), or whose NDC is one of the candidate NDCs in
     * any of its 10, 11 or 12-digit forms (ignoring hyphens and spaces).
     */
    @Transactional(readOnly = true)
    List<Product> findProducts(ProductIdentifiers identifiers) {
        List<String> conditions = []
        Map params = [:]
        if (identifiers.barcode) {
            conditions << "(p.upc is not null and trim(leading '0' from trim(p.upc)) = :key)"
            params.key = gtinKey(identifiers.barcode)
        }
        if (identifiers.ndcCandidates) {
            conditions << "(p.ndc is not null and replace(replace(trim(p.ndc), '-', ''), ' ', '') in (:ndcForms))"
            params.ndcForms = identifiers.ndcDigitForms as List
        }
        // A plain String: GORM would turn the parts of a GString into query parameters
        String query = "select p from Product p where p.active = true and (" + conditions.join(" or ") +
                ") order by p.name"
        return Product.executeQuery(query, params)
    }

    /**
     * @return the product's stock at the location, one row per lot and bin with a non-zero quantity on hand, the
     *     row that expires soonest first
     */
    @Transactional(readOnly = true)
    List<AvailableItem> getStockRows(Location location, Product product) {
        List<AvailableItem> rows = inventoryService.getAvailableBinLocations(location, [product])
                .findAll { it.quantityOnHand }
        return sortStockRows(rows)
    }

    static List<AvailableItem> sortStockRows(List<AvailableItem> rows) {
        return rows.sort(false) { a, b ->
            Date expiryA = a.inventoryItem?.expirationDate
            Date expiryB = b.inventoryItem?.expirationDate
            if (expiryA != expiryB) {
                // Rows without an expiration date go last
                return expiryA == null ? 1 : expiryB == null ? -1 : expiryA <=> expiryB
            }
            return (a.inventoryItem?.lotNumber ?: "") <=> (b.inventoryItem?.lotNumber ?: "") ?:
                    (a.binLocation?.name ?: "") <=> (b.binLocation?.name ?: "")
        }
    }

    @Transactional(readOnly = true)
    Map toProductStockJson(Location location, Product product) {
        List<AvailableItem> rows = getStockRows(location, product)
        return [
                product       : [
                        id                 : product.id,
                        name               : product.displayNameOrDefaultName ?: product.name,
                        productCode        : product.productCode,
                        upc                : product.upc,
                        lotAndExpiryControl: product.lotAndExpiryControl ?: false,
                ],
                quantityOnHand: rows.sum { it.quantityOnHand ?: 0 } ?: 0,
                items         : rows.collect { AvailableItem row ->
                    [
                            inventoryItemId: row.inventoryItem?.id,
                            lotNumber      : row.inventoryItem?.lotNumber ?: null,
                            expirationDate : row.inventoryItem?.expirationDate?.format(StockScanCommand.DATE_FORMAT),
                            binLocation    : row.binLocation ? [id: row.binLocation.id, name: row.binLocation.name] : null,
                            quantityOnHand : row.quantityOnHand,
                    ]
                },
        ]
    }

    /**
     * Records the scan as a transaction at the location, and commits it.
     *
     * Scans of the same product at the same location run one at a time, from the same-second check until the commit.
     * Otherwise two scans arriving together both see the current second as free (see findFreeTransactionDate). This
     * only covers a single application server, which is how this page is meant to be used.
     *
     * @return the saved transaction, whose single entry holds the product
     */
    @NotTransactional
    Transaction perform(Location location, StockScanCommand command) {
        validateQuantity(command.quantity)
        String productKey = command.newProduct ? "new:${command.barcode}" : command.productId
        synchronized (lockFor("${location.id}:${productKey}")) {
            return performInTransaction(location, command)
        }
    }

    Transaction performInTransaction(Location location, StockScanCommand command) {
        return command.action == StockScanAction.USE ? use(location, command) : add(location, command)
    }

    private static Object lockFor(String key) {
        return LOCKS[Math.floorMod(key.hashCode(), LOCK_STRIPES)]
    }


    private Transaction add(Location location, StockScanCommand command) {
        requireActivity(location, ActivityCode.ADJUST_INVENTORY, "Adjust inventory")
        if (command.newProduct && command.productId) {
            throw StockScanException.badRequest("Send either an existing product or a new product, not both")
        }

        Product product = command.newProduct ? createProduct(location, command) : requireProduct(command.productId)
        String lotNumber = command.lotNumber ?: null
        validateLotAndExpiry(product, lotNumber, command.expirationDate)
        Location binLocation = findBinLocation(location, command.binLocationId)

        InventoryItem inventoryItem = inventoryService.findOrCreateInventoryItem(product, lotNumber, command.expirationDate)
        Transaction transaction = buildTransaction(StockScanAction.ADD, location, inventoryItem, binLocation,
                command.quantity, command.comment)
        transaction.transactionSource = adjustInventoryService.createAdjustInventoryTransactionSource(location)
        return save(transaction, location, product)
    }

    private Transaction use(Location location, StockScanCommand command) {
        requireActivity(location, ActivityCode.CONSUME_STOCK, "Consume stock")
        if (command.newProduct) {
            throw StockScanException.badRequest("Only products already in stock can be used")
        }

        Product product = requireProduct(command.productId)
        AvailableItem row = chooseStockRow(getStockRows(location, product), command.inventoryItemId,
                command.binLocationId)
        if (command.quantity > row.quantityOnHand) {
            throw StockScanException.badRequest("Only ${row.quantityOnHand} on hand there, so you can't use ${command.quantity}")
        }

        Transaction transaction = buildTransaction(StockScanAction.USE, location, row.inventoryItem, row.binLocation,
                command.quantity, command.comment)
        return save(transaction, location, product)
    }

    static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            throw StockScanException.badRequest("Quantity must be a whole number from 1 to ${MAX_QUANTITY}")
        }
    }

    /** The same rules as record stock. */
    static void validateLotAndExpiry(Product product, String lotNumber, Date expirationDate) {
        if (expirationDate && !lotNumber) {
            throw StockScanException.badRequest("Items with an expiration date must also have a lot number")
        }
        if (product.lotAndExpiryControl && (!lotNumber || !expirationDate)) {
            throw StockScanException.badRequest("${product.name} needs both a lot number and an expiration date")
        }
    }

    static void requireActivity(Location location, ActivityCode activity, String settingName) {
        if (!location.supports(activity)) {
            throw StockScanException.badRequest(
                    "${location.name} doesn't allow this. Turn on \"${settingName}\" in the location's settings.")
        }
    }

    /**
     * Picks the stock row to use. With no row given, the only row is used; with several, the caller must choose.
     *
     * @param inventoryItemId the lot to use, or null to use the only row
     * @param binLocationId the bin of that lot, or null for the default bin
     */
    static AvailableItem chooseStockRow(List<AvailableItem> rows, String inventoryItemId, String binLocationId) {
        List<AvailableItem> inStock = rows.findAll { it.quantityOnHand > 0 }
        if (!inStock) {
            throw StockScanException.badRequest("There's none of this product in stock here")
        }
        if (!inventoryItemId) {
            if (inStock.size() > 1) {
                throw StockScanException.badRequest("This product is in stock in more than one lot or bin. Choose one.")
            }
            return inStock.first()
        }
        AvailableItem row = inStock.find {
            it.inventoryItem?.id == inventoryItemId && it.binLocation?.id == (binLocationId ?: null)
        }
        if (!row) {
            throw StockScanException.badRequest("There's none of this product in stock in that lot and bin")
        }
        return row
    }

    Transaction buildTransaction(StockScanAction action, Location location, InventoryItem inventoryItem,
                                 Location binLocation, Integer quantity, String comment) {
        String transactionTypeId = action == StockScanAction.USE ?
                Constants.CONSUMPTION_TRANSACTION_TYPE_ID : Constants.ADJUSTMENT_CREDIT_TRANSACTION_TYPE_ID
        Transaction transaction = new Transaction(
                transactionType: TransactionType.get(transactionTypeId),
                inventory: location.inventory,
                comment: comment ?: (action == StockScanAction.USE ? DEFAULT_USE_COMMENT : DEFAULT_ADD_COMMENT),
        )
        // Product availability is refreshed synchronously by the caller after the commit. Set separately because
        // disableRefresh is transient, and map-constructor data binding skips transients.
        transaction.disableRefresh = true
        // Both credit and debit entries hold a positive quantity; the transaction type gives the direction.
        transaction.addToTransactionEntries(new TransactionEntry(
                product: inventoryItem.product,
                inventoryItem: inventoryItem,
                binLocation: binLocation,
                quantity: quantity,
        ))
        return transaction
    }

    /**
     * Reports and quantity calculations break if a product has two transactions at the same second (see
     * InventoryService.hasTransactionEntriesOnDate), and a transaction can't be dated in the future. So if the current
     * second is taken, wait for the next one.
     *
     * @return the current time, truncated to the second, at which the product has no transactions
     */
    Date findFreeTransactionDate(Location location, Product product) {
        for (int attempt = 1; attempt <= MAX_SAME_SECOND_ATTEMPTS; attempt++) {
            long now = System.currentTimeMillis()
            Date date = new Date(now - now % 1000)
            if (!inventoryService.hasTransactionEntriesOnDate(location, date, [product])) {
                return date
            }
            if (attempt < MAX_SAME_SECOND_ATTEMPTS) {
                sleeper(1000 - now % 1000 + 10)
            }
        }
        throw new StockScanException(409, "${product.name} was just changed. Try again in a moment.")
    }

    private Transaction save(Transaction transaction, Location location, Product product) {
        transaction.transactionDate = findFreeTransactionDate(location, product)
        transaction.transactionNumber = inventoryService.generateTransactionNumber(transaction)
        transaction.save(failOnError: true)
        return transaction
    }

    private Product createProduct(Location location, StockScanCommand command) {
        String name = command.newProductName?.trim()
        if (!name) {
            throw StockScanException.badRequest("Enter a name for the new product")
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw StockScanException.badRequest("Product names can be at most ${MAX_NAME_LENGTH} characters")
        }
        Category category = command.newProductCategoryId ? Category.get(command.newProductCategoryId) : null
        if (!category) {
            throw StockScanException.badRequest("Choose a category for the new product")
        }
        ProductType productType = ProductType.defaultProductType.list()?.find()
        if (!productType) {
            throw new StockScanException(500, "No default product type is configured")
        }

        Product product = new Product(
                name: name,
                category: category,
                upc: normalizeBarcode(command.barcode),
                productType: productType,
        )
        // As ProductApiController.save does
        product.validateRequiredFieldsInLocation(location)
        if (product.hasErrors() || !productService.saveProduct(product)) {
            throw new ValidationException("Invalid product", product.errors)
        }
        return product
    }

    private static Product requireProduct(String productId) {
        Product product = productId ? Product.get(productId) : null
        if (!product || !product.active) {
            throw new StockScanException(404, "Product not found")
        }
        return product
    }

    private static Location findBinLocation(Location location, String binLocationId) {
        if (!binLocationId) {
            return null
        }
        Location binLocation = Location.get(binLocationId)
        if (!binLocation || binLocation.parentLocation?.id != location.id) {
            throw StockScanException.badRequest("That bin isn't in ${location.name}")
        }
        return binLocation
    }
}
