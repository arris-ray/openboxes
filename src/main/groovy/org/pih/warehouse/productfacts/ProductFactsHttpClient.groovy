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

import java.nio.charset.StandardCharsets

/**
 * Makes HTTP GET requests for providers, with timeouts and a cap on the response size.
 *
 * Built on the JDK's HttpURLConnection rather than a library, so it doesn't depend on whichever HTTP client other
 * dependencies happen to bring in.
 */
@CompileStatic
class ProductFactsHttpClient {

    static final int MAX_RESPONSE_BYTES = 1024 * 1024

    @CompileStatic
    static class Response {
        final int status
        final String body

        Response(int status, String body) {
            this.status = status
            this.body = body
        }
    }

    /**
     * @throws IOException on a connection failure or timeout, or if the response is too large
     */
    Response get(String url, Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection()
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            headers.each { String name, String value -> connection.setRequestProperty(name, value) }

            int status = connection.responseCode
            InputStream stream = status >= 400 ? connection.errorStream : connection.inputStream
            return new Response(status, stream ? read(stream) : "")
        } finally {
            connection.disconnect()
        }
    }

    private static String read(InputStream stream) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        byte[] buffer = new byte[8192]
        try {
            int count
            while ((count = stream.read(buffer)) != -1) {
                if (out.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IOException("Response larger than ${MAX_RESPONSE_BYTES} bytes")
                }
                out.write(buffer, 0, count)
            }
        } finally {
            stream.close()
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8)
    }
}
