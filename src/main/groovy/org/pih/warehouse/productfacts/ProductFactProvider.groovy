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

/**
 * A source of product details for a scanned code.
 *
 * To add a provider, write a Grails service (a class in grails-app/services whose name ends in "Service") that
 * implements this interface, and add its id to openboxes.stockScan.productFacts.providers. ProductFactsService finds
 * every bean that implements this interface, and calls the configured ones in order until one finds the product.
 *
 * Providers send only the product identifier to remote services, never anything about people.
 */
interface ProductFactProvider {

    /** The id used in configuration, e.g. "openFda". */
    String getId()

    /** The name shown with the details, e.g. "openFDA". */
    String getDisplayName()

    /** @return false to skip this provider for these identifiers, e.g. a drug-only provider for a food barcode */
    boolean supports(ProductIdentifiers identifiers)

    /**
     * Called only if supports() returned true. Results are cached by ProductFactsService.
     *
     * @return the product's details, or null if the provider doesn't know the product
     * @throws ProductFactsException if the provider is rate limiting or failed; other exceptions count as failures
     */
    ProductFacts lookup(ProductIdentifiers identifiers, ProviderContext context)
}
