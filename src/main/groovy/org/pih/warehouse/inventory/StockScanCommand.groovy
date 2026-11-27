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

import java.text.ParseException
import java.text.SimpleDateFormat
import org.grails.web.json.JSONObject

/**
 * A request to add or use stock from a barcode scan (POST /api/stockScans).
 *
 * ADD takes either an existing product (productId) or a new one (newProductName, newProductCategoryId and barcode),
 * plus an optional lot, expiration date and bin. USE takes an existing product and, when it's in stock in more than
 * one place, the row to take it from (inventoryItemId and binLocationId, as returned by the lookup).
 */
class StockScanCommand {

    static final String DATE_FORMAT = "yyyy-MM-dd"

    StockScanAction action
    String productId
    String newProductName
    String newProductCategoryId
    String barcode
    Integer quantity
    String lotNumber
    Date expirationDate
    String binLocationId
    String inventoryItemId
    String comment

    boolean isNewProduct() {
        return newProductName != null
    }

    static StockScanCommand fromJson(JSONObject json) {
        if (json == null) {
            throw StockScanException.badRequest("Send the scan as a JSON object")
        }

        String actionName = text(json, "action")
        StockScanAction action = StockScanAction.values().find { it.name() == actionName?.toUpperCase() }
        if (!action) {
            throw StockScanException.badRequest("Action must be ADD or USE")
        }

        Map newProduct = json.containsKey("newProduct") && json.get("newProduct") instanceof JSONObject ?
                json.get("newProduct") as Map : null

        return new StockScanCommand(
                action: action,
                productId: text(json, "productId"),
                newProductName: newProduct != null ? (text(newProduct, "name") ?: "") : null,
                newProductCategoryId: newProduct != null ? text(newProduct, "categoryId") : null,
                barcode: newProduct != null ? text(newProduct, "upc") : text(json, "barcode"),
                quantity: integer(json, "quantity"),
                lotNumber: text(json, "lotNumber"),
                expirationDate: date(json, "expirationDate"),
                binLocationId: text(json, "binLocationId"),
                inventoryItemId: text(json, "inventoryItemId"),
                comment: text(json, "comment"),
        )
    }

    /** The trimmed value, or null when it's missing, JSON null or blank. */
    private static String text(Map json, String key) {
        // JSONObject.get throws for a missing key
        if (!json.containsKey(key)) {
            return null
        }
        Object value = json.get(key)
        // JSON null is parsed into a placeholder object whose equals(null) is true
        if (value == null || value.equals(null)) {
            return null
        }
        return value.toString().trim() ?: null
    }

    private static Integer integer(Map json, String key) {
        String value = text(json, key)
        if (value == null) {
            return null
        }
        if (!(value ==~ /-?\d{1,9}/)) {
            throw StockScanException.badRequest("Quantity must be a whole number")
        }
        return value as Integer
    }

    private static Date date(Map json, String key) {
        String value = text(json, key)
        if (value == null) {
            return null
        }
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT)
        format.lenient = false
        try {
            return format.parse(value)
        } catch (ParseException ignored) {
            throw StockScanException.badRequest("Expiration date must be in ${DATE_FORMAT} format")
        }
    }
}
