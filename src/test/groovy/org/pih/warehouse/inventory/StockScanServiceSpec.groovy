package org.pih.warehouse.inventory

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.grails.web.json.JSONObject
import spock.lang.Specification
import spock.lang.Unroll

import org.pih.warehouse.api.AvailableItem
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Product
import org.pih.warehouse.productfacts.ProductFacts
import org.pih.warehouse.productfacts.ProductIdentifiers
import org.pih.warehouse.productfacts.ProductKind

@Unroll
class StockScanServiceSpec extends Specification implements ServiceUnitTest<StockScanService>, DataTest {

    TransactionType adjustmentType
    TransactionType consumptionType

    void setupSpec() {
        mockDomains(Transaction, TransactionEntry, TransactionType)
    }

    void setup() {
        adjustmentType = new TransactionType(name: "Adjustment")
        adjustmentType.id = Constants.ADJUSTMENT_CREDIT_TRANSACTION_TYPE_ID
        adjustmentType.save(validate: false)

        consumptionType = new TransactionType(name: "Consumption")
        consumptionType.id = Constants.CONSUMPTION_TRANSACTION_TYPE_ID
        consumptionType.save(validate: false)
    }

    void "normalizeBarcode returns '#expected' for '#barcode'"() {
        expect:
        StockScanService.normalizeBarcode(barcode) == expected

        where:
        barcode             || expected
        "012345678905"      || "012345678905"
        " 0123 4567 8905 "  || "012345678905"
        "0-12345-67890-5"   || "012345678905"
        "01234565"          || "01234565"
        "00012345678905"    || "00012345678905"
    }

    void "normalizeBarcode rejects '#barcode'"() {
        when:
        StockScanService.normalizeBarcode(barcode)

        then:
        StockScanException e = thrown()
        e.status == 400

        where:
        barcode << [null, "", "   ", "12345", "123456789012345", "ABC123456789", "0123456789O5"]
    }

    void "gtinKey treats UPC-A, EAN-13 and GTIN-14 forms of a code as the same"() {
        expect:
        StockScanService.gtinKey("012345678905") == "12345678905"
        StockScanService.gtinKey("0012345678905") == "12345678905"
        StockScanService.gtinKey("00012345678905") == "12345678905"
        StockScanService.gtinKey("4006381333931") == "4006381333931"
        StockScanService.gtinKey("000000") == "0"
    }

    void "validateQuantity accepts #quantity"() {
        when:
        StockScanService.validateQuantity(quantity)

        then:
        noExceptionThrown()

        where:
        quantity << [1, 2, StockScanService.MAX_QUANTITY]
    }

    void "validateQuantity rejects #quantity"() {
        when:
        StockScanService.validateQuantity(quantity)

        then:
        StockScanException e = thrown()
        e.status == 400

        where:
        quantity << [null, 0, -1, StockScanService.MAX_QUANTITY + 1]
    }

    void "validateLotAndExpiry accepts lot '#lot', expiry #expiry with lot control #lotControl"() {
        given:
        Product product = new Product(name: "Beans", lotAndExpiryControl: lotControl)

        when:
        StockScanService.validateLotAndExpiry(product, lot, expiry)

        then:
        noExceptionThrown()

        where:
        lot   | expiry     | lotControl
        null  | null       | false
        "L1"  | null       | false
        "L1"  | new Date() | false
        "L1"  | new Date() | true
    }

    void "validateLotAndExpiry rejects lot '#lot', expiry #expiry with lot control #lotControl"() {
        given:
        Product product = new Product(name: "Beans", lotAndExpiryControl: lotControl)

        when:
        StockScanService.validateLotAndExpiry(product, lot, expiry)

        then:
        StockScanException e = thrown()
        e.status == 400

        where:
        lot   | expiry     | lotControl
        null  | new Date() | false
        null  | null       | true
        "L1"  | null       | true
    }

    void "requireActivity rejects a location that doesn't support the activity"() {
        given:
        Location location = Stub(Location) {
            supports(ActivityCode.CONSUME_STOCK) >> false
            getName() >> "Main Warehouse"
        }

        when:
        StockScanService.requireActivity(location, ActivityCode.CONSUME_STOCK, "Consume stock")

        then:
        StockScanException e = thrown()
        e.status == 400
        e.message.contains("Consume stock")
    }

    void "requireActivity accepts a location that supports the activity"() {
        given:
        Location location = Stub(Location) {
            supports(ActivityCode.ADJUST_INVENTORY) >> true
        }

        when:
        StockScanService.requireActivity(location, ActivityCode.ADJUST_INVENTORY, "Adjust inventory")

        then:
        noExceptionThrown()
    }

    void "chooseStockRow uses the only row when none is given"() {
        given:
        AvailableItem row = row("item-1", null, 5)

        expect:
        StockScanService.chooseStockRow([row], null, null).is(row)
    }

    void "chooseStockRow ignores rows with nothing on hand"() {
        given:
        AvailableItem empty = row("item-1", null, 0)
        AvailableItem stocked = row("item-2", null, 3)

        expect:
        StockScanService.chooseStockRow([empty, stocked], null, null).is(stocked)
    }

    void "chooseStockRow asks for a choice when there are several rows"() {
        when:
        StockScanService.chooseStockRow([row("item-1", null, 1), row("item-2", "bin-1", 2)], null, null)

        then:
        StockScanException e = thrown()
        e.status == 400
        e.message.contains("Choose")
    }

    void "chooseStockRow finds the row for lot #inventoryItemId in bin #binLocationId"() {
        given:
        List<AvailableItem> rows = [row("item-1", null, 1), row("item-1", "bin-1", 2), row("item-2", "bin-1", 3)]

        expect:
        StockScanService.chooseStockRow(rows, inventoryItemId, binLocationId).quantityOnHand == expected

        where:
        inventoryItemId | binLocationId || expected
        "item-1"        | null          || 1
        "item-1"        | ""            || 1
        "item-1"        | "bin-1"       || 2
        "item-2"        | "bin-1"       || 3
    }

    void "chooseStockRow rejects a row that isn't in stock"() {
        when:
        StockScanService.chooseStockRow([row("item-1", null, 1), row("item-2", null, 1)], "item-2", "bin-9")

        then:
        thrown(StockScanException)
    }

    void "chooseStockRow rejects a product with nothing in stock"() {
        when:
        StockScanService.chooseStockRow(rows, null, null)

        then:
        StockScanException e = thrown()
        e.message.contains("none")

        where:
        rows << [[], [row("item-1", null, 0)]]
    }

    void "sortStockRows puts the row expiring soonest first and rows without expiry last"() {
        given:
        AvailableItem later = row("later", null, 1, new Date(2030 - 1900, 0, 1))
        AvailableItem sooner = row("sooner", null, 1, new Date(2027 - 1900, 0, 1))
        AvailableItem never = row("never", null, 1, null)

        expect:
        StockScanService.sortStockRows([never, later, sooner])*.inventoryItem*.id == ["sooner", "later", "never"]
    }

    void "buildTransaction for #action makes one #typeName transaction with a positive entry"() {
        given:
        Product product = new Product(name: "Beans")
        InventoryItem inventoryItem = new InventoryItem(product: product)
        Inventory inventory = new Inventory()
        Location location = Stub(Location) {
            getInventory() >> inventory
        }

        when:
        Transaction transaction = service.buildTransaction(action, location, inventoryItem, null, 2, null)

        then:
        transaction.transactionType.id == typeId
        transaction.inventory.is(inventory)
        transaction.comment == comment
        transaction.disableRefresh
        transaction.transactionEntries.size() == 1
        TransactionEntry entry = transaction.transactionEntries.first()
        entry.quantity == 2
        entry.product.is(product)
        entry.inventoryItem.is(inventoryItem)
        entry.binLocation == null

        where:
        action               || typeId                                     | typeName      | comment
        StockScanAction.ADD  || Constants.ADJUSTMENT_CREDIT_TRANSACTION_TYPE_ID | "Adjustment"  | StockScanService.DEFAULT_ADD_COMMENT
        StockScanAction.USE  || Constants.CONSUMPTION_TRANSACTION_TYPE_ID  | "Consumption" | StockScanService.DEFAULT_USE_COMMENT
    }

    void "buildTransaction keeps a comment that was given"() {
        given:
        Location location = Stub(Location)
        InventoryItem inventoryItem = new InventoryItem(product: new Product(name: "Beans"))

        expect:
        service.buildTransaction(StockScanAction.ADD, location, inventoryItem, null, 1, "From the store")
                .comment == "From the store"
    }

    void "findFreeTransactionDate returns the current second when it's free"() {
        given:
        service.inventoryService = Stub(InventoryService) {
            hasTransactionEntriesOnDate(*_) >> false
        }

        when:
        Date date = service.findFreeTransactionDate(Stub(Location), new Product(name: "Beans"))

        then:
        date.time % 1000 == 0
        date <= new Date()
    }

    void "findFreeTransactionDate waits for the next second when the current one is taken"() {
        given:
        int checks = 0
        service.inventoryService = Stub(InventoryService) {
            hasTransactionEntriesOnDate(*_) >> { checks++ == 0 }
        }
        List<Long> waits = []
        service.sleeper = { long millis -> waits << millis }

        when:
        service.findFreeTransactionDate(Stub(Location), new Product(name: "Beans"))

        then:
        checks == 2
        waits.size() == 1
        waits.first() > 0 && waits.first() <= 1010
    }

    void "findFreeTransactionDate gives up with a 409 when every attempt is taken"() {
        given:
        int checks = 0
        service.inventoryService = Stub(InventoryService) {
            hasTransactionEntriesOnDate(*_) >> { checks++; true }
        }
        service.sleeper = { long millis -> }

        when:
        service.findFreeTransactionDate(Stub(Location), new Product(name: "Beans"))

        then:
        StockScanException e = thrown()
        e.status == 409
        checks == StockScanService.MAX_SAME_SECOND_ATTEMPTS
    }

    void "StockScanCommand reads an ADD with a new product"() {
        when:
        StockScanCommand command = StockScanCommand.fromJson(new JSONObject("""{
            "action": "add",
            "newProduct": {"name": " Coffee beans ", "categoryId": "cat-1", "upc": "012345678905"},
            "quantity": 2,
            "lotNumber": "",
            "expirationDate": "2027-03-31",
            "comment": null
        }"""))

        then:
        command.action == StockScanAction.ADD
        command.newProduct
        command.newProductName == "Coffee beans"
        command.newProductCategoryId == "cat-1"
        command.barcode == "012345678905"
        command.quantity == 2
        command.lotNumber == null
        command.expirationDate.format("yyyy-MM-dd") == "2027-03-31"
        command.comment == null
        command.productId == null
        command.newProductNdc == null
        command.newProductLotAndExpiryControl == null
    }

    void "StockScanCommand reads a new product's details"() {
        when:
        StockScanCommand command = StockScanCommand.fromJson(new JSONObject('''{
            "action": "ADD", "quantity": 1,
            "newProduct": {"name": "Lisinopril 20 mg Tablet", "categoryId": "cat-1", "upc": "0343547422103",
                           "ndc": "43547-422-10", "genericName": "lisinopril", "brandName": " Zestril ",
                           "manufacturer": "Solco", "description": "100 Tablet in 1 Bottle",
                           "lotAndExpiryControl": true, "controlledSubstance": "false"}
        }'''))

        then:
        command.newProductNdc == "43547-422-10"
        command.newProductGenericName == "lisinopril"
        command.newProductBrandName == "Zestril"
        command.newProductManufacturer == "Solco"
        command.newProductDescription == "100 Tablet in 1 Bottle"
        command.newProductLotAndExpiryControl == true
        command.newProductControlledSubstance == false
    }

    void "StockScanCommand reads a USE of a chosen row"() {
        when:
        StockScanCommand command = StockScanCommand.fromJson(new JSONObject(
                '{"action": "USE", "productId": "p-1", "quantity": "1", "inventoryItemId": "i-1", "binLocationId": null}'))

        then:
        command.action == StockScanAction.USE
        !command.newProduct
        command.productId == "p-1"
        command.quantity == 1
        command.inventoryItemId == "i-1"
        command.binLocationId == null
    }

    void "StockScanCommand rejects #json"() {
        when:
        StockScanCommand.fromJson(new JSONObject(json))

        then:
        StockScanException e = thrown()
        e.status == 400

        where:
        json << [
                '{"quantity": 1}',
                '{"action": "REMOVE", "quantity": 1}',
                '{"action": "ADD", "quantity": 1.5}',
                '{"action": "ADD", "quantity": "two"}',
                '{"action": "ADD", "quantity": 1, "expirationDate": "31/03/2027"}',
                '{"action": "ADD", "quantity": 1, "expirationDate": "2027-02-30"}',
                '{"action": "ADD", "quantity": 1, "newProduct": {"name": "X", "lotAndExpiryControl": "yes"}}',
        ]
    }

    void "newProductFields keeps a new product's details"() {
        given:
        StockScanCommand command = new StockScanCommand(newProductName: " Lisinopril ", barcode: "0-343547-422103",
                newProductNdc: "43547-422-10", newProductGenericName: "lisinopril", newProductBrandName: null,
                newProductManufacturer: "Solco", newProductDescription: "100 Tablet in 1 Bottle",
                newProductLotAndExpiryControl: true)

        expect:
        StockScanService.newProductFields(command) == [
                name: "Lisinopril", upc: "0343547422103", ndc: "43547-422-10", genericName: "lisinopril",
                brandName: null, manufacturer: "Solco", description: "100 Tablet in 1 Bottle",
                lotAndExpiryControl: true, controlledSubstance: false,
        ]
    }

    void "newProductFields accepts an NDC without a barcode"() {
        expect:
        StockScanService.newProductFields(new StockScanCommand(newProductName: "Lisinopril", newProductNdc: "4354742210"))
                .subMap(["upc", "ndc"]) == [upc: null, ndc: "4354742210"]
    }

    void "newProductFields rejects #problem"() {
        when:
        StockScanService.newProductFields(new StockScanCommand([newProductName: "Lisinopril", barcode: "0343547422103"] + fields))

        then:
        StockScanException e = thrown()
        e.status == 400

        where:
        problem                     | fields
        "a missing name"            | [newProductName: " "]
        "no barcode or NDC"         | [barcode: null]
        "a bad barcode"             | [barcode: "12AB"]
        "a bad NDC"                 | [newProductNdc: "123-45"]
        "a long generic name"       | [newProductGenericName: "x" * 256]
        "a long description"        | [newProductDescription: "x" * 2001]
    }

    void "newProductDraft fills a drug from its details, with lot and expiry control"() {
        given:
        ProductIdentifiers ids = ProductIdentifiers.parse("0343547422103")
        ProductFacts facts = new ProductFacts(name: "Lisinopril 20 mg Tablet", genericName: "lisinopril",
                brandName: "Zestril", manufacturer: "Solco", ndc: "43547-422-10",
                packageDescription: "100 Tablet in 1 Bottle", kind: ProductKind.PRESCRIPTION_DRUG)

        expect:
        StockScanService.newProductDraft(ids, facts) == [
                name: "Lisinopril 20 mg Tablet", upc: "0343547422103", ndc: "43547-422-10", genericName: "lisinopril",
                brandName: "Zestril", manufacturer: "Solco", description: "100 Tablet in 1 Bottle",
                lotAndExpiryControl: true, controlledSubstance: false,
        ]
    }

    void "newProductDraft marks a scheduled drug as a controlled substance"() {
        expect:
        StockScanService.newProductDraft(ProductIdentifiers.parse("0343547422103"),
                new ProductFacts(kind: ProductKind.PRESCRIPTION_DRUG, deaSchedule: "CII")).controlledSubstance
    }

    void "newProductDraft without details: lot control #lotControl and NDC #ndc for '#code'"() {
        when:
        Map draft = StockScanService.newProductDraft(ProductIdentifiers.parse(code), null)

        then:
        draft.name == null
        draft.lotAndExpiryControl == lotControl
        draft.ndc == ndc
        !draft.controlledSubstance

        where:
        code             || lotControl | ndc
        // A drug barcode whose NDC hyphenation is unknown without a provider
        "0343547422103"  || true       | null
        "43547-422-10"   || true       | "43547-422-10"
        "3017624010701"  || false      | null
    }

    void "newProductDraft leaves food and OTC kinds to the details"() {
        expect:
        !StockScanService.newProductDraft(ProductIdentifiers.parse("3017624010701"),
                new ProductFacts(name: "Nutella", kind: ProductKind.FOOD)).lotAndExpiryControl
        StockScanService.newProductDraft(ProductIdentifiers.parse("808829127164"),
                new ProductFacts(name: "Deodorant", kind: ProductKind.OTC_DRUG)).lotAndExpiryControl
    }

    private static AvailableItem row(String inventoryItemId, String binLocationId, int quantityOnHand,
                                     Date expirationDate = null) {
        InventoryItem inventoryItem = new InventoryItem(expirationDate: expirationDate)
        inventoryItem.id = inventoryItemId
        Location binLocation = null
        if (binLocationId) {
            binLocation = new Location(name: binLocationId)
            binLocation.id = binLocationId
        }
        return new AvailableItem(inventoryItem: inventoryItem, binLocation: binLocation, quantityOnHand: quantityOnHand)
    }
}
