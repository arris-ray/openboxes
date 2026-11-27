package org.pih.warehouse.productfacts

import spock.lang.Specification
import spock.lang.Unroll

import static org.pih.warehouse.productfacts.FakeHttpClient.decode
import static org.pih.warehouse.productfacts.FakeHttpClient.fixture
import static org.pih.warehouse.productfacts.FakeHttpClient.response

/** Uses responses recorded from openFDA on 2026-11-26; never calls it. */
@Unroll
class OpenFdaProductFactsServiceSpec extends Specification {

    OpenFdaProductFactsService provider = new OpenFdaProductFactsService()

    ProviderContext context(FakeHttpClient http, Map settings = [:]) {
        return new ProviderContext("openFda", ProductFactsSettings.of(settings), http)
    }

    void "a drug UPC is looked up by every hyphenation of its NDC in one request"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(200, fixture("openfda-ndc-lisinopril.json")) })

        when:
        ProductFacts facts = provider.lookup(ProductIdentifiers.parse("0343547422103"), context(http))

        then:
        http.urls.size() == 1
        decode(http.urls[0]) == 'https://api.fda.gov/drug/ndc.json?search=packaging.package_ndc:' +
                '("4354-7422-10" "43547-422-10" "43547-4221-0")&limit=5'
        http.headers[0]["User-Agent"] == "OpenBoxes-StockScan/1.0"

        and:
        facts.name == "Lisinopril and Hydrochlorothiazide 20 mg/25 mg Tablet"
        facts.brandName == "Lisinopril and Hydrochlorothiazide"
        facts.genericName == "Lisinopril and Hydrochlorothiazide Tablets"
        facts.strength == "20 mg/25 mg"
        facts.dosageForm == "Tablet"
        facts.route == "Oral"
        facts.manufacturer == "Solco Healthcare LLC"
        facts.ndc == "43547-422-10"
        facts.upc == "0343547422103"
        facts.packageDescription == "100 Tablet in 1 Bottle"
        facts.kind == ProductKind.PRESCRIPTION_DRUG
        facts.deaSchedule == null
        facts.sourceUrl == "https://dailymed.nlm.nih.gov/dailymed/lookup.cfm?setid=96922ddc-e87f-4670-b22e-0ffd0d8f66e1"
    }

    void "the package matching the scanned NDC is chosen"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(200, fixture("openfda-ndc-lisinopril.json")) })

        when:
        ProductFacts facts = provider.lookup(ProductIdentifiers.parse(null, "43547-422-50"), context(http))

        then:
        facts.ndc == "43547-422-50"
        facts.packageDescription == "500 Tablet in 1 Bottle"
    }

    void "an NDC search without a matching package falls back to the UPC"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ String url ->
            decode(url).contains("package_ndc") ? response(404, fixture("openfda-not-found.json")) :
                    response(200, fixture("openfda-ndc-lisinopril.json"))
        })

        when:
        ProductFacts facts = provider.lookup(ProductIdentifiers.parse("0343547422103"), context(http))

        then:
        http.urls.size() == 2
        decode(http.urls[1]).contains('search=openfda.upc:"0343547422103"&limit=2')
        facts.name.startsWith("Lisinopril")
        // Several packages, and a UPC doesn't say which
        facts.ndc == null
    }

    void "an OTC product is found by its retail UPC"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(200, fixture("openfda-upc-otc.json")) })

        when:
        ProductFacts facts = provider.lookup(ProductIdentifiers.parse("808829127164"), context(http))

        then:
        http.urls.size() == 1
        decode(http.urls[0]).contains('openfda.upc:"0808829127164"')
        facts.name == "Lucky Super Soft Antiperspirant Deodorant Cool Comfort 20 g/100g Stick"
        facts.genericName == "aluminum chlorohydrate"
        facts.kind == ProductKind.OTC_DRUG
        facts.ndc == "76176-019-01"
        facts.packageDescription == "45 g in 1 Applicator"
        facts.manufacturer == "Ningbo Liyuan Daily Chemical Products Co., Ltd."
    }

    void "a UPC shared by several products (a placeholder) counts as not found"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(200, fixture("openfda-upc-ambiguous.json")) })

        expect:
        provider.lookup(ProductIdentifiers.parse("012345678905"), context(http)) == null
    }

    void "404 means not found"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(404, fixture("openfda-not-found.json")) })

        expect:
        provider.lookup(ProductIdentifiers.parse("0071924683518"), context(http)) == null
    }

    void "HTTP #status is a failure, and rate limiting is #rateLimited"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(status, "") })

        when:
        provider.lookup(ProductIdentifiers.parse("0071924683518"), context(http))

        then:
        ProductFactsException e = thrown()
        e.rateLimited == rateLimited

        where:
        status || rateLimited
        429    || true
        503    || true
        500    || false
        403    || false
    }

    void "a response that isn't JSON is a failure"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(200, "<html>") })

        when:
        provider.lookup(ProductIdentifiers.parse("0071924683518"), context(http))

        then:
        thrown(ProductFactsException)
    }

    void "the API key and base URL come from the settings"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(404, "") })

        when:
        provider.lookup(ProductIdentifiers.parse("0071924683518"),
                context(http, ["openFda.apiKey": "k&y", "openFda.baseUrl": "http://mirror.test/ndc.json/"]))

        then:
        http.urls[0].startsWith("http://mirror.test/ndc.json?search=")
        http.urls[0].endsWith("&api_key=k%26y")
    }

    void "supports drug codes and 13-digit barcodes, but not '#code'"() {
        expect:
        !provider.supports(ProductIdentifiers.parse(code))

        where:
        // A GTIN-14 for a case of a non-drug, and a code that isn't a GTIN
        code << ["50012345678900", "123456"]
    }

    void "strength of #strengths is '#expected'"() {
        expect:
        OpenFdaProductFactsService.strength(strengths.collect { [strength: it] }) == expected

        where:
        strengths                || expected
        ["20 mg/1", "25 mg/1"]   || "20 mg/25 mg"
        ["6.25 mg/15mL"]         || "6.25 mg/15mL"
        []                       || null
    }
}
