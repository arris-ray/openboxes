package org.pih.warehouse.productfacts

import spock.lang.Specification

import org.pih.warehouse.productfacts.ProductFactsLookup.Result

class ProductFactsServiceSpec extends Specification {

    /** A provider whose answers come from a closure, and that counts its calls. */
    static class FakeProvider implements ProductFactProvider {
        String id
        boolean supported = true
        Closure<ProductFacts> answer
        int calls = 0

        String getDisplayName() {
            return "Fake ${id}"
        }

        boolean supports(ProductIdentifiers identifiers) {
            return supported
        }

        ProductFacts lookup(ProductIdentifiers identifiers, ProviderContext context) {
            calls++
            return answer.call(identifiers)
        }
    }

    ProductFactsService service = new ProductFactsService()
    long now = 1_000_000L
    ProductIdentifiers ids = ProductIdentifiers.parse("3017624010701")

    FakeProvider first = new FakeProvider(id: "first", answer: { null })
    FakeProvider second = new FakeProvider(id: "second", answer: { new ProductFacts(name: "From second") })

    void setup() {
        service.clock = { now }
        service.providersById = [first: first, second: second]
        configure([:])
    }

    void configure(Map settings) {
        service.settings = ProductFactsSettings.of([providers: ["first", "second"]] + settings)
    }

    List<String> results(ProductFactsLookup lookup) {
        return lookup.outcomes.collect { "${it.id}:${it.result}${it.cached ? ' (cached)' : ''}".toString() }
    }

    void "providers are tried in the configured order until one finds the product"() {
        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        lookup.facts.name == "From second"
        lookup.source.is(second)
        results(lookup) == ["first:NOT_FOUND", "second:FOUND"]
        lookup.factsJson().source == [id: "second", name: "Fake second", url: null]
    }

    void "the first provider to find the product wins, and later ones aren't called"() {
        given:
        first.answer = { new ProductFacts(name: "From first") }

        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        lookup.facts.name == "From first"
        second.calls == 0
        results(lookup) == ["first:FOUND"]
    }

    void "the configured order decides, and unknown or missing providers are left out"() {
        given:
        first.answer = { new ProductFacts(name: "From first") }
        configure([providers: "nonexistent, second, first"])

        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        lookup.facts.name == "From second"
        first.calls == 0
        results(lookup) == ["second:FOUND"]
    }

    void "no providers means no lookups"() {
        given:
        configure([providers: []])

        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        lookup.facts == null
        lookup.factsJson() == null
        lookup.outcomes.empty
    }

    void "a provider that doesn't support the code is skipped"() {
        given:
        first.supported = false

        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        first.calls == 0
        results(lookup) == ["first:SKIPPED", "second:FOUND"]
    }

    void "a failing provider is skipped: #error"() {
        given:
        first.answer = { throw error }

        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        lookup.facts.name == "From second"
        results(lookup) == ["first:${result}".toString(), "second:FOUND"]

        where:
        error                                         || result
        new ProductFactsException("HTTP 500")         || Result.FAILED
        new IllegalStateException("bug")              || Result.FAILED
        ProductFactsException.rateLimited("HTTP 429") || Result.RATE_LIMITED
    }

    void "answers are cached, found or not"() {
        when:
        service.lookup(ids)
        ProductFactsLookup again = service.lookup(ProductIdentifiers.parse("03017624010701"))

        then:
        first.calls == 1
        second.calls == 1
        results(again) == ["first:NOT_FOUND (cached)", "second:FOUND (cached)"]
        again.facts.name == "From second"
    }

    void "a miss is remembered for an hour and a hit for a day"() {
        given:
        service.lookup(ids)

        when: "just over an hour later"
        now += 61 * 60000L
        service.lookup(ids)

        then:
        first.calls == 2
        second.calls == 1

        when: "just over a day later"
        now += 24 * 60 * 60000L
        service.lookup(ids)

        then:
        second.calls == 2
    }

    void "failures aren't cached"() {
        given:
        int attempts = 0
        first.answer = { attempts++ == 0 ? { throw new ProductFactsException("timeout") }() : null }

        when:
        service.lookup(ids)
        service.lookup(ids)

        then:
        first.calls == 2
    }

    void "a provider isn't called more often than its limit allows"() {
        given:
        configure(["first.requestsPerMinute": 2])

        when:
        List<ProductFactsLookup> lookups = (1..3).collect {
            service.lookup(ProductIdentifiers.parse("301762401070${it}".with { it + ProductIdentifiers.gtinCheckDigit(it) }))
        }

        then:
        first.calls == 2
        results(lookups[2])[0] == "first:RATE_LIMITED"

        when: "a minute later"
        now += 60000L
        service.lookup(ProductIdentifiers.parse("4006381333931"))

        then:
        first.calls == 3
    }

    void "a provider that says it is limiting us is paused"() {
        given:
        first.answer = { throw ProductFactsException.rateLimited("HTTP 429") }

        when:
        service.lookup(ids)
        first.answer = { null }
        ProductFactsLookup paused = service.lookup(ProductIdentifiers.parse("4006381333931"))

        then:
        first.calls == 1
        results(paused)[0] == "first:RATE_LIMITED"

        when: "after the pause"
        now += 60000L
        service.lookup(ProductIdentifiers.parse("4006381333931"))

        then:
        first.calls == 2
    }

    void "no new provider is called once the time budget is used up"() {
        given:
        first.answer = { now += 7000L; null }

        when:
        ProductFactsLookup lookup = service.lookup(ids)

        then:
        second.calls == 0
        results(lookup) == ["first:NOT_FOUND", "second:SKIPPED"]
    }
}
