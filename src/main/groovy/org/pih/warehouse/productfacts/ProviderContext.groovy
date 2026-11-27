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

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/** What a provider gets for one lookup: its settings, and HTTP requests made with them. */
@CompileStatic
class ProviderContext {

    final String providerId
    final ProductFactsSettings settings
    final ProductFactsHttpClient httpClient

    ProviderContext(String providerId, ProductFactsSettings settings, ProductFactsHttpClient httpClient) {
        this.providerId = providerId
        this.settings = settings
        this.httpClient = httpClient
    }

    /** @return the provider's setting, e.g. "apiKey", or null if it isn't set */
    String setting(String name) {
        return settings.providerString(providerId, name)
    }

    /** @return the configured base URL, or the provider's own */
    String baseUrl(String defaultUrl) {
        return (setting("baseUrl") ?: defaultUrl).replaceAll(/\/+$/, "")
    }

    /**
     * GETs a URL with the configured User-Agent and timeouts.
     *
     * @return the response's status and body
     * @throws ProductFactsException if the request failed or timed out
     */
    ProductFactsHttpClient.Response get(String url) {
        Map<String, String> headers = [
                "Accept"    : "application/json",
                "User-Agent": settings.userAgent ?: "OpenBoxes",
        ]
        try {
            return httpClient.get(url, headers, (int) settings.providerNumber(providerId, "connectTimeoutMs"),
                    (int) settings.providerNumber(providerId, "readTimeoutMs"))
        } catch (IOException e) {
            throw new ProductFactsException("${e.class.simpleName}: ${e.message}", false, e)
        }
    }

    /**
     * @return the parsed JSON body
     * @throws ProductFactsException if it isn't JSON
     */
    static Object parseJson(String body) {
        try {
            return new JsonSlurper().parseText(body)
        } catch (Exception e) {
            throw new ProductFactsException("The response isn't JSON", false, e)
        }
    }

    static String encode(String value) {
        return URLEncoder.encode(value, "UTF-8")
    }
}
