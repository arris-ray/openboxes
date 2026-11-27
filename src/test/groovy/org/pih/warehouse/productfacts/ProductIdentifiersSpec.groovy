package org.pih.warehouse.productfacts

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ProductIdentifiersSpec extends Specification {

    void "a drug UPC carries its NDC (#code)"() {
        when:
        ProductIdentifiers ids = ProductIdentifiers.parse(code)

        then:
        ids.barcode == code
        ids.gtin14 == "00343547422103"
        ids.gtin13 == "0343547422103"
        ids.drug
        ids.ndcCandidates*.hyphenated == ["4354-7422-10", "43547-422-10", "43547-4221-0"]
        ids.ndcDigitForms.containsAll(["4354742210", "43547042210", "043547042210"])

        where:
        // Lisinopril and hydrochlorothiazide, checked against openFDA: UPC-A, EAN-13 and GTIN-14 forms
        code << ["343547422103", "0343547422103", "00343547422103"]
    }

    void "a case-level drug GTIN-14 carries the same NDC"() {
        given:
        String body = "5034354742210"
        String gtin = body + ProductIdentifiers.gtinCheckDigit(body)

        when:
        ProductIdentifiers ids = ProductIdentifiers.parse(gtin)

        then:
        ids.gtin14 == gtin
        ids.gtin13 == null
        ids.ndcCandidates*.hyphenated.contains("43547-422-10")
    }

    void "a retail code that isn't a drug has no NDC (#code)"() {
        when:
        ProductIdentifiers ids = ProductIdentifiers.parse(code)

        then:
        ids.gtin14 == gtin14
        !ids.drug
        ids.ndcCandidates.empty

        where:
        code            || gtin14
        "012345678905"  || "00012345678905"
        "4006381333931" || "04006381333931"
        "96385074"      || "00000096385074"
        "0808829127164" || "00808829127164"
    }

    void "a barcode with a wrong check digit or unusual length is kept, but isn't a GTIN (#code)"() {
        when:
        ProductIdentifiers ids = ProductIdentifiers.parse(code)

        then:
        ids.barcode == code
        ids.gtin14 == null
        !ids.drug

        where:
        // A 3-prefixed UPC with a bad check digit isn't trusted as an NDC either
        code << ["012345678900", "343547422100", "123456", "01234567"]
    }

    void "a typed NDC '#code' is read as an NDC, not a barcode"() {
        when:
        ProductIdentifiers ids = ProductIdentifiers.parse(code)

        then:
        ids.barcode == null
        ids.gtin14 == null
        ids.drug
        ids.ndcCandidates*.hyphenated.contains("43547-422-10")

        where:
        code << ["43547-422-10", "4354742210", "43547042210", "NDC 43547-422-10"]
    }

    void "an NDC given separately comes first, and duplicates are dropped"() {
        when:
        ProductIdentifiers ids = ProductIdentifiers.parse("0343547422103", "043547042210")

        then:
        ids.ndcCandidates*.hyphenated == ["43547-422-10", "043547-0422-10", "4354-7422-10", "43547-4221-0"]
    }

    void "an NDC alone is enough"() {
        expect:
        ProductIdentifiers.parse(null, "43547-422-10").ndcCandidates*.hyphenated == ["43547-422-10"]
    }

    void "parse rejects code '#code' with NDC '#ndc'"() {
        when:
        ProductIdentifiers.parse(code, ndc)

        then:
        thrown(IllegalArgumentException)

        where:
        code            | ndc
        null            | null
        "  "            | ""
        "12345"         | null
        "ABC123456789"  | null
        "012345678905"  | "not an ndc"
    }

    void "gtinCheckDigit computes '#check' for '#body'"() {
        expect:
        ProductIdentifiers.gtinCheckDigit(body) == check

        where:
        body           || check
        "01234567890"  || "5"
        "034354742210" || "3"
        "400638133393" || "1"
        "9638507"      || "4"
    }

    void "the cache key is the same for every form of a barcode"() {
        expect:
        ProductIdentifiers.parse("343547422103").cacheKey == ProductIdentifiers.parse("00343547422103").cacheKey
    }
}
