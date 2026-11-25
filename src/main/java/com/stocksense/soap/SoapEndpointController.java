package com.stocksense.soap;

import com.stocksense.exception.StockSenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The single SOAP endpoint: {@code POST /ws/stocksense}. The first element of the SOAP Body selects the
 * operation. Requests are validated against stocksense.xsd; every failure is returned as a SOAP fault.
 */
@RestController
public class SoapEndpointController {

    private static final Logger log = LoggerFactory.getLogger(SoapEndpointController.class);
    private static final MediaType XML_UTF8 = new MediaType("text", "xml", StandardCharsets.UTF_8);
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final Map<String, WsOperationHandler> handlers = new HashMap<>();
    private final Schema schema;

    public SoapEndpointController(List<WsOperationHandler> endpoints,
                                  @Value("${stocksense.ws.validate-requests:true}") boolean validate) {
        for (WsOperationHandler h : endpoints) {
            for (String op : h.operations()) {
                handlers.put(op, h);
            }
        }
        this.schema = validate ? loadSchema() : null;
        log.info("SOAP operations: {}", new TreeSet<>(handlers.keySet()));
    }

    @GetMapping(path = "/ws/stocksense", produces = MediaType.TEXT_PLAIN_VALUE)
    public String info() {
        return "StockSense SOAP endpoint. POST SOAP 1.1 requests here.\nWSDL: /ws/stocksense.wsdl\nXSD: /ws/stocksense.xsd\n"
                + "Operations: " + new TreeSet<>(handlers.keySet()) + "\n";
    }

    @PostMapping(path = "/ws/stocksense")
    public ResponseEntity<String> handle(@RequestBody(required = false) byte[] body) {
        try {
            if (body == null || body.length == 0) {
                throw new StockSenseException.InvalidRequest("Empty SOAP request");
            }
            if (body.length > MAX_REQUEST_BYTES) {
                throw new StockSenseException.InvalidRequest("SOAP request is too large");
            }
            Document doc = SoapXml.parse(body);
            Element op = SoapXml.operationElement(doc);
            if (op == null) {
                throw new StockSenseException.InvalidRequest("Not a SOAP 1.1 envelope with a Body operation");
            }
            String name = op.getLocalName();
            WsOperationHandler handler = handlers.get(name);
            if (handler == null || !SoapXml.TF_NS.equals(op.getNamespaceURI())) {
                throw new StockSenseException.InvalidRequest("Unknown operation: " + name);
            }
            validate(op);
            Element response = handler.handle(name, op, SoapXml.newDocument());
            return ResponseEntity.ok().contentType(XML_UTF8).body(SoapXml.envelope(response));
        } catch (StockSenseException e) {
            log.debug("SOAP fault {}: {}", e.getCode(), e.getMessage());
            return fault(e.getCode(), e.getMessage(), e.isClientFault());
        } catch (SAXException e) {
            return fault("INVALID_XML", "Malformed XML in SOAP envelope", true);
        } catch (Exception e) {
            log.error("Unexpected SOAP error", e);
            return fault("INTERNAL_ERROR", "Internal server error", false);
        }
    }

    private void validate(Element op) throws SAXException, IOException {
        if (schema == null) {
            return;
        }
        Validator v = schema.newValidator();
        try {
            v.validate(new DOMSource(op));
        } catch (SAXException e) {
            String msg = e.getMessage() == null ? "schema validation failed" : e.getMessage().replaceAll("\\{urn:stocksense:ws\\}", "");
            throw new StockSenseException.InvalidRequest("Request does not match stocksense.xsd: " + msg);
        }
    }

    private static ResponseEntity<String> fault(String code, String message, boolean client) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(XML_UTF8)
                .body(SoapXml.fault(code, message, client));
    }

    private static Schema loadSchema() {
        try (InputStream in = SoapEndpointController.class.getResourceAsStream("/static/ws/stocksense.xsd")) {
            if (in == null) {
                throw new IllegalStateException("stocksense.xsd not found on classpath");
            }
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return sf.newSchema(new javax.xml.transform.stream.StreamSource(in));
        } catch (IOException | SAXException e) {
            throw new IllegalStateException("Cannot load stocksense.xsd", e);
        }
    }
}
