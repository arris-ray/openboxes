package org.pih.warehouse.productfacts

import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ProductFactsSupportSpec extends Specification {

    long now = 0L

    void "the cache drops the least recently used entry when full"() {
        given:
        ProductFactsCache cache = new ProductFactsCache(2, { now })
        cache.put("a", new ProductFacts(name: "A"), 1000)
        cache.put("b", null, 1000)

        when:
        cache.get("a")
        cache.put("c", new ProductFacts(name: "C"), 1000)

        then:
        cache.get("a").facts.name == "A"
        cache.get("b") == null
        cache.get("c").facts.name == "C"
    }

    void "cache entries expire"() {
        given:
        ProductFactsCache cache = new ProductFactsCache(10, { now })
        cache.put("a", null, 1000)

        expect:
        cache.get("a") != null

        when:
        now = 1000

        then:
        cache.get("a") == null
        cache.size() == 0
    }

    void "the rate limiter allows a number of requests in any rolling minute"() {
        given:
        RateLimiter limiter = new RateLimiter(2, { now })

        expect:
        limiter.tryAcquire()
        (now = 30000) && limiter.tryAcquire()
        !limiter.tryAcquire()
        (now = 60000) && limiter.tryAcquire()
        !limiter.tryAcquire()
        (now = 90000) && limiter.tryAcquire()
    }

    void "the rate limiter can be paused"() {
        given:
        RateLimiter limiter = new RateLimiter(10, { now })
        limiter.pause(5000)

        expect:
        !limiter.tryAcquire()
        (now = 5000) && limiter.tryAcquire()
    }

    void "settings have defaults, and configured values win"() {
        given:
        ProductFactsSettings defaults = ProductFactsSettings.of([:])
        ProductFactsSettings configured = ProductFactsSettings.of([
                "providers"              : "openFoodFacts , openFda",
                "openFda.readTimeoutMs"  : "1500",
                "openFda.apiKey"         : " key ",
                "openFoodFacts.requestsPerMinute": 5,
        ])

        expect:
        defaults.providers == ["openFda", "openFoodFacts"]
        defaults.userAgent == "OpenBoxes-StockScan/1.0"
        defaults.providerNumber("openFda", "readTimeoutMs") == 3000
        defaults.providerNumber("openFoodFacts", "requestsPerMinute") == 10
        defaults.providerNumber("other", "requestsPerMinute") == 60
        defaults.providerString("openFda", "apiKey") == null
        defaults.cacheHitTtlMs == 24 * 60 * 60000L

        configured.providers == ["openFoodFacts", "openFda"]
        configured.providerNumber("openFda", "readTimeoutMs") == 1500
        configured.providerString("openFda", "apiKey") == "key"
        configured.providerNumber("openFoodFacts", "requestsPerMinute") == 5
    }

    void "a setting that should be a number but isn't is reported"() {
        when:
        ProductFactsSettings.of(["openFda.readTimeoutMs": "soon"]).providerNumber("openFda", "readTimeoutMs")

        then:
        IllegalStateException e = thrown()
        e.message.contains("openboxes.stockScan.productFacts.openFda.readTimeoutMs")
    }

    void "tidy changes '#text' to '#expected'"() {
        expect:
        ProductFacts.tidy(text) == expected

        where:
        text                    || expected
        "TABLET, FILM COATED"   || "Tablet, Film Coated"
        "LUCKY SUPER SOFT"      || "Lucky Super Soft"
        "Lisinopril  Tablets "  || "Lisinopril Tablets"
        "aluminum chlorohydrate" || "aluminum chlorohydrate"
        "123"                   || "123"
        "  "                    || null
    }

    void "the HTTP client returns the status and body, and sends the headers"() {
        given:
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        Map<String, String> agents = [:]
        server.createContext("/") { exchange ->
            agents[exchange.requestURI.path] = exchange.requestHeaders.getFirst("User-Agent")
            byte[] body = '{"ok":true}'.bytes
            exchange.sendResponseHeaders(exchange.requestURI.path == "/missing" ? 404 : 200, body.length)
            exchange.responseBody.withStream { it.write(body) }
        }
        server.start()
        String base = "http://127.0.0.1:${server.address.port}"
        ProductFactsHttpClient client = new ProductFactsHttpClient()

        when:
        ProductFactsHttpClient.Response found = client.get("${base}/found", ["User-Agent": "Test/1"], 1000, 1000)
        ProductFactsHttpClient.Response missing = client.get("${base}/missing", [:], 1000, 1000)

        then:
        found.status == 200
        found.body == '{"ok":true}'
        agents["/found"] == "Test/1"
        missing.status == 404
        missing.body == '{"ok":true}'

        cleanup:
        server?.stop(0)
    }

    void "the HTTP client times out on a slow server"() {
        given:
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> sleep(2000); exchange.sendResponseHeaders(200, -1); exchange.close() }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        server.start()

        when:
        new ProductFactsHttpClient().get("http://127.0.0.1:${server.address.port}/", [:], 500, 300)

        then:
        thrown(SocketTimeoutException)

        cleanup:
        server?.stop(0)
        (server?.executor as java.util.concurrent.ExecutorService)?.shutdownNow()
    }
}
