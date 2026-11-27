/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.productfacts

/**
 * Details from Open Food Facts (https://world.openfoodfacts.org), a crowd-sourced database of food, beauty, pet food
 * and other retail products. Its data is under the Open Database License, and must be credited where shown.
 *
 * It has no drug data, so drug codes are skipped. It allows 15 product reads a minute per IP and may ban an IP that
 * goes over, so ProductFactsService keeps under a lower limit (see ProductFactsSettings).
 *
 * Sends only the barcode, and the User-Agent naming the application.
 */
class OpenFoodFactsProductFactsService implements ProductFactProvider {

    static transactional = false

    static final String BASE_URL = "https://world.openfoodfacts.org"
    static final String FIELDS = "code,product_name,brands,generic_name,quantity,product_type,image_front_small_url"

    @Override
    String getId() {
        return "openFoodFacts"
    }

    @Override
    String getDisplayName() {
        return "Open Food Facts"
    }

    @Override
    boolean supports(ProductIdentifiers identifiers) {
        return identifiers.gtin14 && !identifiers.drug
    }

    @Override
    ProductFacts lookup(ProductIdentifiers identifiers, ProviderContext context) {
        String baseUrl = context.baseUrl(BASE_URL)
        String code = identifiers.barcode
        String url = "${baseUrl}/api/v3/product/${ProviderContext.encode(code)}?product_type=all&fields=${FIELDS}"

        ProductFactsHttpClient.Response response = context.get(url)
        if (response.status == 404) {
            return null
        }
        if (response.status != 200) {
            throw ProductFactsException.forStatus(response.status)
        }
        Object json = ProviderContext.parseJson(response.body)
        Map product = json instanceof Map && json.status == "success" ? json.product as Map : null
        if (!product) {
            return null
        }
        return toFacts(product, identifiers, "${baseUrl}/product/${ProviderContext.encode(code)}")
    }

    static ProductFacts toFacts(Map product, ProductIdentifiers identifiers, String sourceUrl) {
        String productName = text(product.product_name)
        String brandName = text(product.brands)?.split(",")?.first()?.trim() ?: null
        if (!productName && !brandName) {
            return null
        }
        String quantity = quantity(text(product.quantity))
        String name = [productName ?: brandName, quantity].findAll().join(" ")

        return new ProductFacts(
                name: name,
                brandName: brandName,
                genericName: text(product.generic_name),
                upc: identifiers.barcode,
                packageDescription: quantity,
                kind: text(product.product_type) in ["food", "petfood"] ? ProductKind.FOOD : ProductKind.OTHER,
                imageUrl: text(product.image_front_small_url),
                sourceUrl: sourceUrl,
        )
    }

    /** "400.0 g" gives "400 g"; a bare number such as "1" says nothing, so it's dropped. */
    static String quantity(String quantity) {
        String value = quantity?.replaceAll(/(\d)\.0\b/, '$1')
        return value && value =~ /[A-Za-z]/ ? value : null
    }

    private static String text(Object value) {
        return value instanceof String ? (value as String).trim() ?: null : null
    }
}
