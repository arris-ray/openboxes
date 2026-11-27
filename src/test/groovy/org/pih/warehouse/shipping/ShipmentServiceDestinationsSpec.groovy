package org.pih.warehouse.shipping

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.Location
import org.pih.warehouse.product.Product

class ShipmentServiceDestinationsSpec extends Specification implements ServiceUnitTest<ShipmentService>, DataTest {

    void setupSpec() {
        mockDomains(Location, Product, Shipment, ShipmentItem)
    }

    void "getDestinationsWithActivityCodeAndProductInShipmentWithStatus returns no locations when none support the activity"() {
        given: "no location supports the activity, so the location query returns nothing"
        GroovySpy(Location, global: true)
        Location.executeQuery(_ as String, _ as Map) >> []

        expect: "no locations, rather than a query with an empty 'in' list, which is invalid SQL"
        service.getDestinationsWithActivityCodeAndProductInShipmentWithStatus(
                new Product(), ActivityCode.MANAGE_INVENTORY,
                [ShipmentStatusCode.SHIPPED, ShipmentStatusCode.PARTIALLY_RECEIVED]) == []
    }
}
