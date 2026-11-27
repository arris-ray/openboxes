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
 * Remembers provider answers, found or not found, for a while, so repeat scans don't call out again. Least recently
 * used entries are dropped when it's full. In memory, so it empties when the application restarts.
 */
@CompileStatic
class ProductFactsCache {

    @CompileStatic
    static class Entry {
        /** null when the provider didn't know the product */
        final ProductFacts facts
        final long expiresAt

        Entry(ProductFacts facts, long expiresAt) {
            this.facts = facts
            this.expiresAt = expiresAt
        }
    }

    private final int maxEntries
    private final Closure<Long> clock
    private final LinkedHashMap<String, Entry> entries

    ProductFactsCache(int maxEntries, Closure<Long> clock) {
        this.maxEntries = maxEntries
        this.clock = clock
        // Access order, so the eldest entry is the least recently used
        this.entries = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > ProductFactsCache.this.maxEntries
            }
        }
    }

    /** @return the entry, or null if there's none or it has expired */
    synchronized Entry get(String key) {
        Entry entry = entries.get(key)
        if (entry != null && entry.expiresAt <= clock.call()) {
            entries.remove(key)
            return null
        }
        return entry
    }

    synchronized void put(String key, ProductFacts facts, long ttlMs) {
        if (maxEntries > 0 && ttlMs > 0) {
            entries.put(key, new Entry(facts, clock.call() + ttlMs))
        }
    }

    synchronized int size() {
        return entries.size()
    }
}
