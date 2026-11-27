package org.pih.warehouse.productfacts

/** Answers requests from a closure, and records them. */
class FakeHttpClient extends ProductFactsHttpClient {

    List<String> urls = []
    List<Map<String, String>> headers = []
    Closure<ProductFactsHttpClient.Response> handler

    FakeHttpClient(Closure<ProductFactsHttpClient.Response> handler) {
        this.handler = handler
    }

    @Override
    ProductFactsHttpClient.Response get(String url, Map<String, String> headers, int connectTimeoutMs,
                                        int readTimeoutMs) {
        urls << url
        this.headers << headers
        return handler.call(url)
    }

    static ProductFactsHttpClient.Response response(int status, String body = "") {
        return new ProductFactsHttpClient.Response(status, body)
    }

    static String fixture(String name) {
        return FakeHttpClient.getResource("/productfacts/${name}").getText("UTF-8")
    }

    static String decode(String url) {
        return URLDecoder.decode(url, "UTF-8")
    }
}
