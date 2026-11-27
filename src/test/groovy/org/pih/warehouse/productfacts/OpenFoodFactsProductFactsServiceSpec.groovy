package org.pih.warehouse.productfacts

import spock.lang.Specification
import spock.lang.Unroll

import static org.pih.warehouse.productfacts.FakeHttpClient.fixture
import static org.pih.warehouse.productfacts.FakeHttpClient.response

/** Uses responses recorded from Open Food Facts on 2026-11-26; never calls it. */
@Unroll
class OpenFoodFactsProductFactsServiceSpec extends Specification {

    OpenFoodFactsProductFactsService provider = new OpenFoodFactsProductFactsService()

    ProviderContext context(FakeHttpClient http) {
        return new ProviderContext("openFoodFacts", ProductFactsSettings.of([:]), http)
    }

    void "a food barcode is looked up across all product types"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(200, fixture("openfoodfacts-found.json")) })

        when:
        ProductFacts facts = provider.lookup(ProductIdentifiers.parse("3017624010701"), context(http))

        then:
        http.urls == ["https://world.openfoodfacts.org/api/v3/product/3017624010701?product_type=all&fields=" +
                              OpenFoodFactsProductFactsService.FIELDS]
        http.headers[0]["User-Agent"] == "OpenBoxes-StockScan/1.0"

        and:
        facts.name == "Nutella 400 g"
        facts.brandName == "Ferrero"
        facts.genericName == null
        facts.packageDescription == "400 g"
        facts.kind == ProductKind.FOOD
        facts.upc == "3017624010701"
        facts.imageUrl.startsWith("https://images.openfoodfacts.org/")
        facts.sourceUrl == "https://world.openfoodfacts.org/product/3017624010701"
    }

    void "404 means not found"() {
        given:
        FakeHttpClient http = new FakeHttpClient({ response(404, fixture("openfoodfacts-not-found.json")) })

        expect:
        provider.lookup(ProductIdentifiers.parse("0071924683518"), context(http)) == null
    }

    void "a product without a name or brand counts as not found"() {
        given:
        FakeHttpClient http = new FakeHttpClient({
            response(200, '{"status":"success","product":{"code":"0071924683518","product_name":"","brands":""}}')
        })

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
    }

    void "supports retail barcodes, but not '#code'"() {
        expect:
        provider.supports(ProductIdentifiers.parse("3017624010701"))
        !provider.supports(ProductIdentifiers.parse(code))

        where:
        // A drug UPC, a typed NDC, and a code that isn't a GTIN
        code << ["0343547422103", "43547-422-10", "123456"]
    }

    void "quantity '#quantity' gives '#expected'"() {
        expect:
        OpenFoodFactsProductFactsService.quantity(quantity) == expected

        where:
        quantity  || expected
        "400.0 g" || "400 g"
        "1.5 l"   || "1.5 l"
        "1"       || null
        null      || null
    }
}
