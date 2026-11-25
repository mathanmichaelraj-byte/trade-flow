package com.stocksense;

import com.stocksense.soap.SoapEndpointController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Full Spring context (H2 in memory, simulated provider) driven through the SOAP controller. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocksense-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "stocksense.market.providers=simulated",
        "stocksense.ml.base-url=http://localhost:9"
})
class SoapEndpointIntegrationTest {

    private static final String OWNER = "test-owner-0001";

    @Autowired
    SoapEndpointController controller;

    private ResponseEntity<String> soap(String body) {
        String xml = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tf=\"urn:stocksense:ws\">"
                + "<soapenv:Body>" + body + "</soapenv:Body></soapenv:Envelope>";
        return controller.handle(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void searchFindsCatalogEntries() {
        ResponseEntity<String> r = soap("<tf:searchStocks><tf:query>apple</tf:query></tf:searchStocks>");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(r.getBody().contains("<tf:symbol>AAPL</tf:symbol>"), r.getBody());
    }

    @Test
    void quoteHistoryTechnicalAndCompareWork() {
        ResponseEntity<String> q = soap("<tf:getStockQuote><tf:symbol>aapl</tf:symbol></tf:getStockQuote>");
        assertEquals(200, q.getStatusCode().value(), q.getBody());
        assertTrue(q.getBody().contains("<tf:source>simulated</tf:source>"));

        ResponseEntity<String> h = soap("<tf:getHistoricalData><tf:symbol>AAPL</tf:symbol><tf:range>6M</tf:range><tf:interval>WEEKLY</tf:interval></tf:getHistoricalData>");
        assertEquals(200, h.getStatusCode().value(), h.getBody());
        assertTrue(h.getBody().contains("<tf:bar>") && h.getBody().contains("<tf:maxDrawdownPercent>"));

        ResponseEntity<String> t = soap("<tf:getTechnicalAnalysis><tf:symbol>AAPL</tf:symbol><tf:range>3M</tf:range></tf:getTechnicalAnalysis>");
        assertEquals(200, t.getStatusCode().value(), t.getBody());
        assertTrue(t.getBody().contains("<tf:indicator>RSI(14)</tf:indicator>") && t.getBody().contains("<tf:overall>"));

        ResponseEntity<String> c = soap("<tf:compareStocks><tf:symbols><tf:symbol>AAPL</tf:symbol><tf:symbol>MSFT</tf:symbol></tf:symbols><tf:range>1Y</tf:range></tf:compareStocks>");
        assertEquals(200, c.getStatusCode().value(), c.getBody());
        assertTrue(c.getBody().contains("<tf:symbol>MSFT</tf:symbol>"));

        ResponseEntity<String> info = soap("<tf:getCompanyInfo><tf:symbol>TCS.NS</tf:symbol></tf:getCompanyInfo>");
        assertEquals(200, info.getStatusCode().value(), info.getBody());
        assertTrue(info.getBody().contains("<tf:currency>INR</tf:currency>"));
        assertTrue(info.getBody().contains("<tf:roe>") && info.getBody().contains("<tf:roce>"), info.getBody());
    }

    @Test
    void watchlistRoundTrip() {
        ResponseEntity<String> add = soap("<tf:addToWatchlist><tf:owner>" + OWNER + "</tf:owner><tf:symbol>NVDA</tf:symbol></tf:addToWatchlist>");
        assertTrue(add.getBody().contains("<tf:added>true</tf:added>"), add.getBody());
        ResponseEntity<String> list = soap("<tf:getWatchlist><tf:owner>" + OWNER + "</tf:owner></tf:getWatchlist>");
        assertTrue(list.getBody().contains("<tf:symbol>NVDA</tf:symbol>"), list.getBody());
        ResponseEntity<String> rm = soap("<tf:removeFromWatchlist><tf:owner>" + OWNER + "</tf:owner><tf:symbol>NVDA</tf:symbol></tf:removeFromWatchlist>");
        assertTrue(rm.getBody().contains("<tf:removed>true</tf:removed>"), rm.getBody());
    }

    @Test
    void unknownSymbolIsAClientFault() {
        ResponseEntity<String> r = soap("<tf:getStockQuote><tf:symbol>ZZZZ</tf:symbol></tf:getStockQuote>");
        assertEquals(500, r.getStatusCode().value());
        assertTrue(r.getBody().contains("SYMBOL_NOT_FOUND") && r.getBody().contains("soapenv:Client"), r.getBody());
    }

    @Test
    void schemaViolationsAreRejected() {
        ResponseEntity<String> badRange = soap("<tf:getHistoricalData><tf:symbol>AAPL</tf:symbol><tf:range>9Y</tf:range></tf:getHistoricalData>");
        assertTrue(badRange.getBody().contains("INVALID_REQUEST"), badRange.getBody());
        ResponseEntity<String> missing = soap("<tf:getStockQuote/>");
        assertTrue(missing.getBody().contains("INVALID_REQUEST"), missing.getBody());
        ResponseEntity<String> oneSymbol = soap("<tf:compareStocks><tf:symbols><tf:symbol>AAPL</tf:symbol></tf:symbols></tf:compareStocks>");
        assertTrue(oneSymbol.getBody().contains("INVALID_REQUEST"), oneSymbol.getBody());
        ResponseEntity<String> unknownOp = soap("<tf:dropTables/>");
        assertTrue(unknownOp.getBody().contains("Unknown operation"), unknownOp.getBody());
    }

    @Test
    void xxeAndMalformedXmlAreRejected() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>&e;</soapenv:Body></soapenv:Envelope>";
        ResponseEntity<String> r = controller.handle(xxe.getBytes(StandardCharsets.UTF_8));
        assertEquals(500, r.getStatusCode().value());
        assertTrue(r.getBody().contains("INVALID_XML"), r.getBody());
        assertTrue(!r.getBody().contains("root:"));
    }

    @Test
    void predictionReportsMlServiceOutageAsServerFault() {
        ResponseEntity<String> r = soap("<tf:predictStock><tf:symbol>AAPL</tf:symbol><tf:horizonDays>5</tf:horizonDays></tf:predictStock>");
        assertEquals(500, r.getStatusCode().value());
        assertTrue(r.getBody().contains("ML_SERVICE_ERROR") && r.getBody().contains("soapenv:Server"), r.getBody());
    }

    @Test
    void systemStatusReportsDegradedWhenMlIsDown() {
        ResponseEntity<String> r = soap("<tf:getSystemStatus/>");
        assertEquals(200, r.getStatusCode().value(), r.getBody());
        assertTrue(r.getBody().contains("<tf:status>DEGRADED</tf:status>"), r.getBody());
    }
}
