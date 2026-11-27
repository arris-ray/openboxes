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
import groovy.transform.EqualsAndHashCode

/**
 * A US National Drug Code: labeler, product and package segments.
 *
 * Today's NDCs have 10 digits split 4-4-2, 5-3-2 or 5-4-1, and the digits alone don't say which. Billing systems
 * pad them to 11 digits (5-4-2). From 2033-03-07 FDA assigns 12-digit 6-4-2 NDCs, and existing ones convert by
 * padding each segment with leading zeros. An Ndc holds the segments as written; the digit forms are derived.
 */
@CompileStatic
@EqualsAndHashCode
final class Ndc {

    /** The segment lengths of 10-digit NDCs. */
    private static final List<List<Integer>> LEGACY_LAYOUTS = [[4, 4, 2], [5, 3, 2], [5, 4, 1]]
    private static final List<List<Integer>> PADDED_LAYOUTS = [[5, 4, 2], [6, 4, 2]]

    final String labeler
    final String product
    final String packageCode

    Ndc(String labeler, String product, String packageCode) {
        this.labeler = labeler
        this.product = product
        this.packageCode = packageCode
    }

    /** @return the NDC with hyphens, e.g. 43547-422-10 */
    String getHyphenated() {
        return "${labeler}-${product}-${packageCode}"
    }

    /** @return the 10 digits, or null if this isn't a 10-digit NDC */
    String getDigits10() {
        return isLegacy() ? "${labeler}${product}${packageCode}" : null
    }

    /** @return the 11-digit (5-4-2) billing form, or null if the labeler has 6 significant digits */
    String getDigits11() {
        String digits12 = getDigits12()
        return digits12.startsWith("0") ? digits12.substring(1) : null
    }

    /** @return the 12-digit (6-4-2) form */
    String getDigits12() {
        return "${labeler.padLeft(6, '0')}${product.padLeft(4, '0')}${packageCode.padLeft(2, '0')}"
    }

    /** @return every digit form of this NDC (10, 11 and 12 digits, where they exist) */
    Set<String> getDigitForms() {
        return ([getDigits10(), getDigits11(), getDigits12()] - [null]) as Set<String>
    }

    boolean isLegacy() {
        return [labeler.length(), product.length(), packageCode.length()] in LEGACY_LAYOUTS
    }

    @Override
    String toString() {
        return getHyphenated()
    }

    /** @return the three NDCs that 10 digits can stand for */
    static List<Ndc> fromDigits10(String digits) {
        requireDigits(digits, 10)
        return LEGACY_LAYOUTS.collect { split(digits, it) }
    }

    /**
     * @param digits an 11-digit (5-4-2) or 12-digit (6-4-2) NDC
     * @return the 10-digit NDCs it could be padded from, followed by the padded form itself, which is the only
     *     candidate when no 10-digit form fits (e.g. a 6-digit labeler)
     */
    static List<Ndc> fromPaddedDigits(String digits) {
        if (!(digits ==~ /\d{11}|\d{12}/)) {
            throw new IllegalArgumentException("Expected 11 or 12 digits")
        }
        Ndc padded = split(digits, digits.length() == 11 ? [5, 4, 2] : [6, 4, 2])
        List<Ndc> candidates = LEGACY_LAYOUTS.findResults { List<Integer> layout -> unpad(padded, layout) } as List<Ndc>
        return candidates + [padded]
    }

    /**
     * Reads an NDC as written: with hyphens (any of 4-4-2, 5-3-2, 5-4-1, 5-4-2, 6-4-2) or as 10, 11 or 12 digits.
     * Spaces and a leading "NDC" are ignored.
     *
     * @return the NDCs the text can stand for, most likely first; empty if it isn't an NDC
     */
    static List<Ndc> parse(String text) {
        String value = text?.trim()?.replaceFirst(/(?i)^ndc[\s:#]*/, "")?.replaceAll(/\s/, "")
        if (!value) {
            return []
        }
        if (value ==~ /\d+-\d+-\d+/) {
            List<String> parts = value.split("-") as List<String>
            List<Integer> layout = parts*.length()
            if (layout in LEGACY_LAYOUTS) {
                return [new Ndc(parts[0], parts[1], parts[2])]
            }
            if (layout in PADDED_LAYOUTS) {
                // Padding hides which 10-digit layout it came from, as with the digits alone
                return fromPaddedDigits(parts.join(""))
            }
            return []
        }
        switch (value.length()) {
            case 10:
                return value ==~ /\d+/ ? fromDigits10(value) : Collections.<Ndc> emptyList()
            case 11:
            case 12:
                return value ==~ /\d+/ ? fromPaddedDigits(value) : Collections.<Ndc> emptyList()
            default:
                return []
        }
    }

    private static Ndc split(String digits, List<Integer> layout) {
        int a = layout[0]
        int b = a + layout[1]
        return new Ndc(digits.substring(0, a), digits.substring(a, b), digits.substring(b))
    }

    /** @return the 10-digit NDC with this layout that pads to the given NDC, or null if there isn't one */
    private static Ndc unpad(Ndc padded, List<Integer> layout) {
        String labeler = stripPadding(padded.labeler, layout[0])
        String product = stripPadding(padded.product, layout[1])
        String packageCode = stripPadding(padded.packageCode, layout[2])
        return labeler != null && product != null && packageCode != null ? new Ndc(labeler, product, packageCode) : null
    }

    /** @return the segment shortened to the length, if only zeros are removed; otherwise null */
    private static String stripPadding(String segment, int length) {
        int extra = segment.length() - length
        if (extra < 0 || segment.substring(0, extra).replace("0", "")) {
            return null
        }
        return segment.substring(extra)
    }

    private static void requireDigits(String digits, int length) {
        if (!(digits ==~ /\d{${length}}/)) {
            throw new IllegalArgumentException("Expected ${length} digits")
        }
    }
}
