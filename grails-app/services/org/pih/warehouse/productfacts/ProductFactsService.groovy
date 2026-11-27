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

import grails.core.GrailsApplication
import org.pih.warehouse.productfacts.ProductFactsLookup.ProviderOutcome
import org.pih.warehouse.productfacts.ProductFactsLookup.Result

/**
 * Finds details for a scanned code that isn't in OpenBoxes, by asking the configured providers in priority order
 * (see ProductFactsSettings). The first provider that finds the product wins; the rest aren't called.
 *
 * A provider is skipped if it doesn't handle the code, if calling it would go over its request limit, or once the
 * lookup has taken longer than the time budget. A provider that fails, times out or says it is limiting us is
 * skipped, and the next one is tried. Answers, found or not, are cached; failures aren't.
 *
 * Not transactional: the providers make HTTP requests, which shouldn't hold a database connection.
 */
class ProductFactsService {

    static transactional = false

    GrailsApplication grailsApplication

    /** The current time in milliseconds. Replaced in tests. */
    Closure<Long> clock = { System.currentTimeMillis() }

    ProductFactsHttpClient productFactsHttpClient = new ProductFactsHttpClient()

    private Map<String, ProductFactProvider> providersById
    private ProductFactsSettings settings
    private ProductFactsCache cache
    private final Map<String, RateLimiter> rateLimiters = [:]

    ProductFactsLookup lookup(ProductIdentifiers identifiers) {
        long startedAt = clock.call()
        List<ProviderOutcome> outcomes = []

        for (String providerId : getSettings().providers) {
            ProductFactProvider provider = getProvidersById()[providerId]
            if (!provider) {
                log.warn("Product facts provider '${providerId}' is configured, but there's no such provider")
                continue
            }
            if (!provider.supports(identifiers)) {
                outcomes << outcome(provider, Result.SKIPPED)
                continue
            }

            String cacheKey = "${providerId}|${identifiers.cacheKey}"
            ProductFactsCache.Entry cached = getCache().get(cacheKey)
            if (cached) {
                if (cached.facts) {
                    outcomes << outcome(provider, Result.FOUND, true)
                    return new ProductFactsLookup(cached.facts, provider, outcomes)
                }
                outcomes << outcome(provider, Result.NOT_FOUND, true)
                continue
            }

            if (clock.call() - startedAt >= getSettings().timeBudgetMs) {
                outcomes << outcome(provider, Result.SKIPPED)
                continue
            }
            RateLimiter rateLimiter = rateLimiterFor(providerId)
            if (!rateLimiter.tryAcquire()) {
                outcomes << outcome(provider, Result.RATE_LIMITED)
                continue
            }

            ProductFacts facts
            try {
                facts = provider.lookup(identifiers, new ProviderContext(providerId, getSettings(), productFactsHttpClient))
            } catch (ProductFactsException e) {
                log.warn("Product facts provider ${providerId} couldn't look up ${identifiers}: ${e.message}")
                if (e.rateLimited) {
                    rateLimiter.pause(getSettings().providerNumber(providerId, "pauseAfterRateLimitMs"))
                }
                outcomes << outcome(provider, e.rateLimited ? Result.RATE_LIMITED : Result.FAILED)
                continue
            } catch (Exception e) {
                log.warn("Product facts provider ${providerId} failed for ${identifiers}", e)
                outcomes << outcome(provider, Result.FAILED)
                continue
            }

            getCache().put(cacheKey, facts, facts ? getSettings().cacheHitTtlMs : getSettings().cacheMissTtlMs)
            if (facts) {
                outcomes << outcome(provider, Result.FOUND)
                return new ProductFactsLookup(facts, provider, outcomes)
            }
            outcomes << outcome(provider, Result.NOT_FOUND)
        }
        return new ProductFactsLookup(null, null, outcomes)
    }

    private static ProviderOutcome outcome(ProductFactProvider provider, Result result, boolean cached = false) {
        return new ProviderOutcome(provider.id, provider.displayName, result, cached)
    }

    /** Every provider bean, by id. */
    synchronized Map<String, ProductFactProvider> getProvidersById() {
        if (providersById == null) {
            providersById = grailsApplication.mainContext.getBeansOfType(ProductFactProvider).values()
                    .collectEntries { [(it.id): it] }
        }
        return providersById
    }

    synchronized void setProvidersById(Map<String, ProductFactProvider> providersById) {
        this.providersById = providersById
    }

    synchronized ProductFactsSettings getSettings() {
        if (settings == null) {
            settings = new ProductFactsSettings({ String key -> configValue(key) })
        }
        return settings
    }

    synchronized void setSettings(ProductFactsSettings settings) {
        this.settings = settings
        this.cache = null
        this.rateLimiters.clear()
    }

    private Object configValue(String key) {
        Object value = grailsApplication.config.getProperty(key, Object)
        if (value != null && !(value instanceof Map)) {
            return value
        }
        return System.getenv(key.toUpperCase().replace(".", "_"))
    }

    private synchronized ProductFactsCache getCache() {
        if (cache == null) {
            cache = new ProductFactsCache(getSettings().cacheMaxEntries, clock)
        }
        return cache
    }

    private synchronized RateLimiter rateLimiterFor(String providerId) {
        if (!rateLimiters[providerId]) {
            int perMinute = (int) getSettings().providerNumber(providerId, "requestsPerMinute")
            rateLimiters[providerId] = new RateLimiter(perMinute, clock)
        }
        return rateLimiters[providerId]
    }
}
