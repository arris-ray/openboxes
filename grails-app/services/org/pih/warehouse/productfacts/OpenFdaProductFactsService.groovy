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
 * Drug details from the openFDA NDC Directory (https://open.fda.gov/apis/drug/ndc/), which lists finished drugs
 * marketed in the US, as submitted by their labelers. FDA doesn't verify the data.
 *
 * Looks up the NDC first (every way the scanned digits can be hyphenated, in one request), then the barcode as a
 * 13-digit UPC, which also finds OTC products whose barcode doesn't contain the NDC. Many records share placeholder
 * UPCs, so a UPC that matches more than one product is treated as not found.
 *
 * Sends only the NDC or barcode, and the API key if one is configured.
 */
class OpenFdaProductFactsService implements ProductFactProvider {

    static transactional = false

    static final String BASE_URL = "https://api.fda.gov/drug/ndc.json"
    static final String DAILYMED_URL = "https://dailymed.nlm.nih.gov/dailymed/lookup.cfm?setid="

    @Override
    String getId() {
        return "openFda"
    }

    @Override
    String getDisplayName() {
        return "openFDA"
    }

    @Override
    boolean supports(ProductIdentifiers identifiers) {
        return identifiers.ndcCandidates || identifiers.gtin13
    }

    @Override
    ProductFacts lookup(ProductIdentifiers identifiers, ProviderContext context) {
        if (identifiers.ndcCandidates) {
            String terms = identifiers.ndcCandidates.collect { "\"${it.hyphenated}\"" }.join(" ")
            List<Map> results = search(context, "packaging.package_ndc:(${terms})", 5)
            for (Ndc ndc : identifiers.ndcCandidates) {
                for (Map result : results) {
                    Map packaging = (result.packaging as List<Map>)?.find { it.package_ndc == ndc.hyphenated }
                    if (packaging) {
                        return toFacts(result, packaging, identifiers)
                    }
                }
            }
        }
        if (identifiers.gtin13) {
            List<Map> results = search(context, "openfda.upc:\"${identifiers.gtin13}\"", 2)
            if (results.size() == 1) {
                List<Map> packages = results[0].packaging as List<Map>
                return toFacts(results[0], packages?.size() == 1 ? packages[0] : null, identifiers)
            }
        }
        return null
    }

    /** @return the matching records; empty if there are none */
    private static List<Map> search(ProviderContext context, String query, int limit) {
        String url = "${context.baseUrl(BASE_URL)}?search=${ProviderContext.encode(query)}&limit=${limit}"
        String apiKey = context.setting("apiKey")
        if (apiKey) {
            url += "&api_key=${ProviderContext.encode(apiKey)}"
        }

        ProductFactsHttpClient.Response response = context.get(url)
        if (response.status == 404) {
            return []
        }
        if (response.status != 200) {
            throw ProductFactsException.forStatus(response.status)
        }
        Object json = ProviderContext.parseJson(response.body)
        return (json instanceof Map ? json.results : null) as List<Map> ?: []
    }

    static ProductFacts toFacts(Map result, Map packaging, ProductIdentifiers identifiers) {
        String brandName = ProductFacts.tidy(result.brand_name as String)
        String genericName = ProductFacts.tidy(result.generic_name as String)
        String strength = strength(result.active_ingredients as List<Map>)
        String dosageForm = ProductFacts.tidy(result.dosage_form as String)
        List<String> setIds = (result.openfda as Map)?.spl_set_id as List<String>

        return new ProductFacts(
                name: [brandName ?: genericName, strength, dosageForm].findAll().join(" ") ?: null,
                brandName: brandName,
                genericName: genericName,
                strength: strength,
                dosageForm: dosageForm,
                route: (result.route as List<String>)?.collect { ProductFacts.tidy(it) }?.join(", ") ?: null,
                manufacturer: (result.labeler_name as String)?.trim() ?: null,
                ndc: packaging?.package_ndc as String,
                upc: identifiers.barcode,
                packageDescription: packageDescription(packaging?.description as String),
                kind: kind(result.product_type as String),
                deaSchedule: (result.dea_schedule as String)?.trim() ?: null,
                sourceUrl: setIds ? DAILYMED_URL + ProviderContext.encode(setIds[0]) : null,
        )
    }

    /** "20 mg/1" and "25 mg/1" give "20 mg/25 mg"; "/1" means per unit (tablet, capsule) and is dropped. */
    static String strength(List<Map> ingredients) {
        List<String> strengths = ingredients?.collect { (it.strength as String)?.trim()?.replaceFirst(/\/1$/, "") }
                ?.findAll() as List<String>
        return strengths ? strengths.join("/") : null
    }

    /** "500 TABLET in 1 BOTTLE (43547-422-50)" gives "500 Tablet in 1 Bottle". */
    static String packageDescription(String description) {
        String text = description?.replaceAll(/\s*\(\d+-\d+-\d+\)/, "")?.replaceAll(/\s+/, " ")?.trim()
        if (!text) {
            return null
        }
        // Capitalize the capitalized words only, keeping "in" and units as they are
        return text.replaceAll(/\b[A-Z][A-Z,]+\b/) { String word -> word[0] + word.substring(1).toLowerCase() }
    }

    static ProductKind kind(String productType) {
        String type = productType?.toUpperCase() ?: ""
        if (type.contains("PRESCRIPTION")) {
            return ProductKind.PRESCRIPTION_DRUG
        }
        if (type.contains("OTC")) {
            return ProductKind.OTC_DRUG
        }
        return productType ? ProductKind.OTHER : null
    }
}
