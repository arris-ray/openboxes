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

/** The result of asking the providers: the details found (if any), and what each provider did. */
@CompileStatic
class ProductFactsLookup {

    enum Result {
        FOUND,
        NOT_FOUND,
        /** The provider doesn't handle this kind of code, or the time limit was reached. */
        SKIPPED,
        /** Not called, to stay within the provider's limits; or the provider said it was limiting us. */
        RATE_LIMITED,
        FAILED
    }

    @CompileStatic
    static class ProviderOutcome {
        final String id
        final String name
        final Result result
        final boolean cached

        ProviderOutcome(String id, String name, Result result, boolean cached = false) {
            this.id = id
            this.name = name
            this.result = result
            this.cached = cached
        }

        Map<String, Object> toJson() {
            return [id: id, name: name, result: result.name(), cached: cached] as Map<String, Object>
        }
    }

    /** null if no provider found the product */
    final ProductFacts facts
    /** the provider that found the product, or null */
    final ProductFactProvider source
    final List<ProviderOutcome> outcomes

    ProductFactsLookup(ProductFacts facts, ProductFactProvider source, List<ProviderOutcome> outcomes) {
        this.facts = facts
        this.source = source
        this.outcomes = Collections.unmodifiableList(new ArrayList<ProviderOutcome>(outcomes))
    }

    /** @return the details with their source, or null if none were found */
    Map<String, Object> factsJson() {
        if (!facts) {
            return null
        }
        Map<String, Object> json = facts.toJson()
        json.put("source", [id: source.id, name: source.displayName, url: facts.sourceUrl])
        return json
    }

    List<Map<String, Object>> outcomesJson() {
        return outcomes.collect { ProviderOutcome outcome -> outcome.toJson() } as List<Map<String, Object>>
    }
}
