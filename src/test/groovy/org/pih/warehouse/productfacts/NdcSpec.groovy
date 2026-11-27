package org.pih.warehouse.productfacts

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class NdcSpec extends Specification {

    void "10 digits stand for three NDCs"() {
        expect:
        Ndc.fromDigits10("4354742210")*.hyphenated == ["4354-7422-10", "43547-422-10", "43547-4221-0"]
    }

    void "#hyphenated has digit forms #digits10, #digits11 and #digits12"() {
        given:
        Ndc ndc = Ndc.parse(hyphenated).first()

        expect:
        ndc.digits10 == digits10
        ndc.digits11 == digits11
        ndc.digits12 == digits12
        ndc.legacy == (digits10 != null)

        where:
        hyphenated       || digits10     | digits11      | digits12
        "0002-3227-30"   || "0002322730" | "00002322730" | "000002322730"
        "43547-422-10"   || "4354742210" | "43547042210" | "043547042210"
        "50090-1234-5"   || "5009012345" | "50090123405" | "050090123405"
        "123456-1234-12" || null         | null          | "123456123412"
    }

    void "parse reads '#text' as #expected"() {
        expect:
        Ndc.parse(text)*.hyphenated == expected

        where:
        text               || expected
        "43547-422-10"     || ["43547-422-10"]
        " NDC 43547-422-10" || ["43547-422-10"]
        "NDC: 0002-3227-30" || ["0002-3227-30"]
        "4354742210"       || ["4354-7422-10", "43547-422-10", "43547-4221-0"]
        // 11-digit billing form: only one padding fits, followed by the padded form itself
        "43547042210"      || ["43547-422-10", "43547-0422-10"]
        "43547-0422-10"    || ["43547-422-10", "43547-0422-10"]
        // Padding that fits all three layouts
        "00123045601"      || ["0123-0456-01", "00123-456-01", "00123-0456-1", "00123-0456-01"]
        // 12-digit 6-4-2 (from 2033): converted NDCs unpad; a new 6-digit labeler doesn't
        "043547042210"     || ["43547-422-10", "043547-0422-10"]
        "123456-1234-12"   || ["123456-1234-12"]
    }

    void "parse returns nothing for '#text'"() {
        expect:
        Ndc.parse(text) == []

        where:
        text << [null, "", "  ", "123", "4354-742-210", "43547-42X-10", "012345678905x", "1234567890123"]
    }

    void "NDCs with the same segments are equal"() {
        expect:
        Ndc.parse("43547-422-10").first() == new Ndc("43547", "422", "10")
        Ndc.parse("43547-422-10").first() != new Ndc("4354", "7422", "10")
    }
}
