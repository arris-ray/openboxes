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

/**
 * A stock scan request that can't be carried out. The message is shown to the user as is, and the status is the
 * HTTP status the API responds with.
 */
class StockScanException extends RuntimeException {

    final int status

    StockScanException(int status, String message) {
        super(message)
        this.status = status
    }

    static StockScanException badRequest(String message) {
        return new StockScanException(400, message)
    }
}
