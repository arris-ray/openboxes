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

/** A provider couldn't answer: it is limiting requests, or the request failed. */
@CompileStatic
class ProductFactsException extends RuntimeException {

    final boolean rateLimited

    ProductFactsException(String message, boolean rateLimited = false, Throwable cause = null) {
        super(message, cause)
        this.rateLimited = rateLimited
    }

    static ProductFactsException rateLimited(String message) {
        return new ProductFactsException(message, true)
    }

    /** @return an exception for an HTTP status the provider didn't expect (429 and 503 count as rate limiting) */
    static ProductFactsException forStatus(int status) {
        return new ProductFactsException("HTTP ${status}", status == 429 || status == 503)
    }
}
