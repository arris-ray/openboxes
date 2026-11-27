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
 * What a scanned or typed code identifies: a barcode (GTIN family), NDCs, or both.
 *
 * On US drug packages the barcode carries the NDC. As a GTIN-14 (zero-padded), a drug code has "0" and then "3" in
 * its 2nd and 3rd positions (GS1 prefix 03 is reserved for US drugs), and the next 10 digits are the NDC. The first
 * digit is the packaging-level indicator. Those 10 digits can be split three ways, so they give three candidates.
 */
@CompileStatic
final class ProductIdentifiers {

    /** The barcode's digits, as scanned or typed; null if there was none. */
    final String barcode

    /** The barcode as a GTIN-14, or null if it isn't a valid GTIN (wrong length or check digit). */
    final String gtin14

    /** NDCs the code may stand for, most likely first. */
    final List<Ndc> ndcCandidates

    private ProductIdentifiers(String barcode, String gtin14, List<Ndc> ndcCandidates) {
        this.barcode = barcode
        this.gtin14 = gtin14
        this.ndcCandidates = Collections.unmodifiableList(ndcCandidates.unique(false))
    }

    /**
     * @param code a scanned or typed code: a barcode (6 to 14 digits; spaces and dashes are ignored), or an NDC
     *     (10 or 11 digits, or hyphenated as on the label)
     * @param ndc an NDC from elsewhere, such as GS1 AI 715 in a DataMatrix, or typed; may be null
     * @throws IllegalArgumentException if neither is given, or either isn't a barcode or NDC
     */
    static ProductIdentifiers parse(String code, String ndc = null) {
        String barcode = null
        List<Ndc> ndcs = []

        String trimmed = code?.trim()
        if (trimmed) {
            List<Ndc> fromCode = looksLikeNdc(trimmed) ? Ndc.parse(trimmed) : []
            if (fromCode) {
                ndcs.addAll(fromCode)
            } else {
                barcode = trimmed.replaceAll(/[\s-]/, "")
                if (!(barcode ==~ /\d{6,14}/)) {
                    throw new IllegalArgumentException("${code} isn't a retail barcode (6 to 14 digits) or an NDC")
                }
            }
        }

        if (ndc?.trim()) {
            List<Ndc> parsed = Ndc.parse(ndc)
            if (!parsed) {
                throw new IllegalArgumentException("${ndc} isn't an NDC")
            }
            ndcs.addAll(0, parsed)
        }

        String gtin14 = barcode ? toGtin14(barcode) : null
        String drugNdc = gtin14 ? drugNdcDigits(gtin14) : null
        if (drugNdc) {
            ndcs.addAll(Ndc.fromDigits10(drugNdc))
        }

        if (!barcode && !ndcs) {
            throw new IllegalArgumentException("Scan or enter a barcode")
        }
        return new ProductIdentifiers(barcode, gtin14, ndcs)
    }

    /**
     * Hyphenated, or 10 or 11 digits: no GTIN has 10 or 11 digits, so a code of that length was typed from an NDC.
     */
    static boolean looksLikeNdc(String text) {
        String value = text.trim().replaceFirst(/(?i)^ndc[\s:#]*/, "")
        return value ==~ /\d{4,6}-\d{3,4}-\d{1,2}/ || value ==~ /\d{10,11}/
    }

    /** @return the GTIN-14 form of an 8, 12, 13 or 14-digit code with a valid check digit; otherwise null */
    static String toGtin14(String digits) {
        // All zeros passes the check digit test, but is only ever a placeholder (openFDA has dozens of them)
        if (!(digits ==~ /\d{8}|\d{12,14}/) || digits ==~ /0+/) {
            return null
        }
        String body = digits.substring(0, digits.length() - 1)
        if (gtinCheckDigit(body) != digits[-1]) {
            return null
        }
        return digits.padLeft(14, "0")
    }

    /** @return the check digit for a GTIN without its check digit */
    static String gtinCheckDigit(String digits) {
        int sum = 0
        int weight = 3
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - ('0' as char)) * weight
            weight = 4 - weight
        }
        return String.valueOf((10 - sum % 10) % 10)
    }

    /** @return the 10 NDC digits of a US drug GTIN-14, or null if it isn't one */
    static String drugNdcDigits(String gtin14) {
        return gtin14.substring(1, 3) == "03" ? gtin14.substring(3, 13) : null
    }

    /** True if the code is a US drug barcode (or an NDC), so drug providers apply and others can be skipped. */
    boolean isDrug() {
        return !ndcCandidates.isEmpty()
    }

    /** @return the GTIN-13 form (UPC-A with a leading 0, or EAN-13), or null if there's none */
    String getGtin13() {
        return gtin14?.startsWith("0") ? gtin14.substring(1) : null
    }

    /** @return every 10, 11 and 12-digit form of the candidate NDCs */
    Set<String> getNdcDigitForms() {
        Set<String> forms = new LinkedHashSet<>()
        ndcCandidates.each { Ndc ndc -> forms.addAll(ndc.digitForms) }
        return forms
    }

    /** @return a stable key for caching lookups of these identifiers */
    String getCacheKey() {
        return "${gtin14 ?: barcode ?: ''}|${ndcCandidates*.digits12.join(',')}"
    }

    @Override
    String toString() {
        return getCacheKey()
    }
}
