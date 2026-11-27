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

import groovy.transform.CompileStatic

/**
 * Settings for product facts lookups, under openboxes.stockScan.productFacts. The defaults are here rather than in
 * application.yml; any of them can be set in openboxes.yml, or as an environment variable named after the key in
 * capitals with dots as underscores, e.g. OPENBOXES_STOCKSCAN_PRODUCTFACTS_OPENFDA_APIKEY.
 *
 * <pre>
 * openboxes:
 *   stockScan:
 *     productFacts:
 *       providers: [openFda, openFoodFacts]   # tried in this order; leave one out to turn it off
 *       userAgent: OpenBoxes-StockScan/1.0
 *       timeBudgetMs: 6000                    # no new provider calls after this long
 *       cache: { maxEntries: 2000, hitTtlMinutes: 1440, missTtlMinutes: 60 }
 *       openFda: { apiKey: "", connectTimeoutMs: 2000, readTimeoutMs: 3000, requestsPerMinute: 200 }
 *       openFoodFacts: { connectTimeoutMs: 2000, readTimeoutMs: 3000, requestsPerMinute: 10 }
 * </pre>
 *
 * Every provider also takes baseUrl (to use a mirror or proxy) and pauseAfterRateLimitMs.
 */
@CompileStatic
class ProductFactsSettings {

    static final String PREFIX = "openboxes.stockScan.productFacts."

    static final Map<String, Object> DEFAULTS = [
            "providers"                      : ["openFda", "openFoodFacts"],
            // TODO: Open Food Facts asks for "AppName/Version (ContactEmail)". Decide whether to add a contact email.
            "userAgent"                      : "OpenBoxes-StockScan/1.0",
            "timeBudgetMs"                   : 6000,
            "cache.maxEntries"               : 2000,
            "cache.hitTtlMinutes"            : 24 * 60,
            "cache.missTtlMinutes"           : 60,
            // openFDA allows 240 a minute (and 1,000 a day without an API key)
            "openFda.requestsPerMinute"      : 200,
            // Open Food Facts allows 15 product reads a minute, and may ban an IP that goes over
            "openFoodFacts.requestsPerMinute": 10,
    ] as Map<String, Object>

    static final Map<String, Object> PROVIDER_DEFAULTS = [
            "connectTimeoutMs"     : 2000,
            "readTimeoutMs"        : 3000,
            "requestsPerMinute"    : 60,
            "pauseAfterRateLimitMs": 60000,
    ] as Map<String, Object>

    /** Returns the configured value for a full key, or null. */
    private final Closure<Object> source

    /**
     * @param source returns the configured value for a full key (e.g. "openboxes.stockScan.productFacts.userAgent"),
     *     or null if it isn't set
     */
    ProductFactsSettings(Closure<Object> source) {
        this.source = source
    }

    /** Settings from a map of keys without the prefix, for tests. */
    static ProductFactsSettings of(Map<String, Object> values) {
        return new ProductFactsSettings({ String key -> values[key - PREFIX] })
    }

    /** @return the provider ids in priority order */
    List<String> getProviders() {
        Object value = raw("providers")
        List<Object> items = value instanceof String ? (((String) value).split(",") as List<Object>) :
                (value as List<Object>) ?: []
        List<String> ids = []
        for (Object item : items) {
            String id = item?.toString()?.trim()
            if (id) {
                ids.add(id)
            }
        }
        return ids
    }

    String getUserAgent() {
        return string("userAgent")
    }

    long getTimeBudgetMs() {
        return number("timeBudgetMs")
    }

    int getCacheMaxEntries() {
        return (int) number("cache.maxEntries")
    }

    long getCacheHitTtlMs() {
        return number("cache.hitTtlMinutes") * 60000L
    }

    long getCacheMissTtlMs() {
        return number("cache.missTtlMinutes") * 60000L
    }

    String providerString(String providerId, String name) {
        return string("${providerId}.${name}".toString())
    }

    long providerNumber(String providerId, String name) {
        String key = "${providerId}.${name}".toString()
        return raw(key) != null || DEFAULTS.containsKey(key) ? number(key) : (PROVIDER_DEFAULTS[name] as Long)
    }

    private Object raw(String key) {
        Object value = source.call(PREFIX + key)
        return value != null ? value : DEFAULTS[key]
    }

    private String string(String key) {
        Object value = raw(key)
        return value?.toString()?.trim() ?: null
    }

    private long number(String key) {
        Object value = raw(key)
        if (value instanceof Number) {
            return ((Number) value).longValue()
        }
        try {
            return Long.parseLong(value.toString().trim())
        } catch (NumberFormatException | NullPointerException ignored) {
            throw new IllegalStateException("${PREFIX}${key} must be a whole number")
        }
    }
}
