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
import groovy.transform.ToString

/**
 * Details about a product from one provider, in a provider-neutral form. Every field may be null. The details are
 * suggestions for the person creating the product; providers don't verify them.
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class ProductFacts {

    /** A suggested product name, e.g. "Lisinopril and Hydrochlorothiazide 20 mg/25 mg Tablet". */
    String name
    String brandName
    String genericName
    String strength
    String dosageForm
    String route
    String manufacturer
    /** The package NDC, hyphenated. */
    String ndc
    /** The scanned barcode. */
    String upc
    String packageDescription
    ProductKind kind
    /** The DEA schedule (CI to CV) of a controlled substance. */
    String deaSchedule
    String imageUrl
    /** A page with more about the product, such as its label. */
    String sourceUrl

    Map<String, Object> toJson() {
        return [
                name              : name,
                brandName         : brandName,
                genericName       : genericName,
                strength          : strength,
                dosageForm        : dosageForm,
                route             : route,
                manufacturer      : manufacturer,
                ndc               : ndc,
                upc               : upc,
                packageDescription: packageDescription,
                kind              : kind?.name(),
                deaSchedule       : deaSchedule,
                imageUrl          : imageUrl,
                sourceUrl         : sourceUrl,
        ] as Map<String, Object>
    }

    /**
     * Provider data often comes in capitals ("TABLET, FILM COATED"). Text that is all capitals is changed to
     * capitalized words ("Tablet, Film Coated"); anything else is left as it is.
     */
    static String tidy(String text) {
        String value = text?.trim()?.replaceAll(/\s+/, " ")
        if (!value) {
            return null
        }
        if (value != value.toUpperCase() || value == value.toLowerCase()) {
            return value
        }
        return value.toLowerCase().replaceAll(/(^|[\s,\/(\-])([a-z])/) { List<String> m -> m[1] + m[2].toUpperCase() }
    }
}
