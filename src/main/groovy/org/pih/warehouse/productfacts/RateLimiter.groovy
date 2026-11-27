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
 * Allows at most a number of requests in any rolling minute, and can pause all requests for a while (after a provider
 * says it is limiting us). Requests over the limit are refused, not delayed.
 */
@CompileStatic
class RateLimiter {

    static final long WINDOW_MS = 60000L

    private final int maxPerWindow
    private final Closure<Long> clock
    private final ArrayDeque<Long> recent = new ArrayDeque<>()
    private long pausedUntil = 0L

    RateLimiter(int maxPerWindow, Closure<Long> clock) {
        this.maxPerWindow = maxPerWindow
        this.clock = clock
    }

    /** @return true, and counts the request, if one is allowed now */
    synchronized boolean tryAcquire() {
        long now = clock.call()
        if (now < pausedUntil) {
            return false
        }
        while (!recent.isEmpty() && recent.peekFirst() <= now - WINDOW_MS) {
            recent.pollFirst()
        }
        if (recent.size() >= maxPerWindow) {
            return false
        }
        recent.addLast(now)
        return true
    }

    /** Refuses all requests for the given time. */
    synchronized void pause(long millis) {
        pausedUntil = Math.max(pausedUntil, clock.call() + millis)
    }
}
