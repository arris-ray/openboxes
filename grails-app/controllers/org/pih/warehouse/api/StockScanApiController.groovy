/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.api

import grails.converters.JSON

import org.pih.warehouse.core.Location
import org.pih.warehouse.core.UserService
import org.pih.warehouse.inventory.ProductAvailabilityService
import org.pih.warehouse.inventory.StockScanCommand
import org.pih.warehouse.inventory.StockScanException
import org.pih.warehouse.inventory.StockScanService
import org.pih.warehouse.inventory.Transaction
import org.pih.warehouse.product.Product
import org.pih.warehouse.productfacts.ProductFactsLookup
import org.pih.warehouse.productfacts.ProductFactsService
import org.pih.warehouse.productfacts.ProductIdentifiers

/**
 * Barcode scanning for the stock scan page. Routed by the generic "/api/${resource}s" mapping:
 *
 *   GET  /api/stockScans?barcode=…&ndc=…   products with that barcode (UPC) or NDC, with their stock at the current
 *                                          location. The barcode may itself be a typed NDC; ndc is optional. If
 *                                          no product matches, "facts" holds details from the product facts
 *                                          providers (or null), "providers" what each provider did, and
 *                                          "newProduct" a suggested new product to create from them.
 *   POST /api/stockScans             add or use stock (see StockScanCommand)
 *
 * Not @Transactional: StockScanService commits the transaction, then product availability is refreshed here, so the
 * response shows the new quantities.
 */
class StockScanApiController {

    StockScanService stockScanService
    ProductFactsService productFactsService
    ProductAvailabilityService productAvailabilityService
    UserService userService

    def list() {
        handleErrors {
            Location location = currentLocation()
            ProductIdentifiers identifiers = StockScanService.parseIdentifiers(params.barcode as String,
                    params.ndc as String)
            List<Product> products = stockScanService.findProducts(identifiers)
            Map response = [data: products.collect { stockScanService.toProductStockJson(location, it) }]
            if (!products) {
                // Not in OpenBoxes: ask the product facts providers, to help create the product
                ProductFactsLookup lookup = productFactsService.lookup(identifiers)
                response.facts = lookup.factsJson()
                response.providers = lookup.outcomesJson()
                response.newProduct = StockScanService.newProductDraft(identifiers, lookup.facts)
            }
            render(response as JSON)
        }
    }

    def create() {
        handleErrors {
            Location location = currentLocation()
            if (!userService.isUserManager(session.user)) {
                throw new StockScanException(403, "Changing stock needs at least the assistant role")
            }

            StockScanCommand command = StockScanCommand.fromJson(request.JSON)
            Transaction transaction = stockScanService.perform(location, command)
            Product product = transaction.transactionEntries.first().product

            productAvailabilityService.refreshProductsAvailability(location.id, [product.id], false)

            Map stock = stockScanService.toProductStockJson(location, product)
            render([data: stock + [
                    action     : command.action.name(),
                    quantity   : command.quantity,
                    transaction: [
                            id               : transaction.id,
                            transactionNumber: transaction.transactionNumber,
                            transactionType  : transaction.transactionType?.name,
                    ],
            ]] as JSON)
        }
    }

    private Location currentLocation() {
        Location location = session?.warehouse?.id ? Location.get(session.warehouse.id) : null
        if (!location) {
            throw StockScanException.badRequest("Choose a location first")
        }
        return location
    }

    /** Responds with the status and message of a StockScanException; anything else goes to the usual handlers. */
    private void handleErrors(Closure action) {
        try {
            action()
        } catch (StockScanException e) {
            response.status = e.status
            render([errorCode: e.status, errorMessage: e.message] as JSON)
        }
    }
}
