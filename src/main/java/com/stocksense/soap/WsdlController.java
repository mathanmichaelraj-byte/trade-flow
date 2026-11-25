package com.stocksense.soap;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Serves the WSDL with the service location rewritten to the host/port the client actually used. */
@RestController
public class WsdlController {

    private static final MediaType XML_UTF8 = new MediaType("text", "xml", StandardCharsets.UTF_8);

    @GetMapping("/ws/stocksense.wsdl")
    public ResponseEntity<String> wsdl(HttpServletRequest request) throws IOException {
        String template;
        try (InputStream in = WsdlController.class.getResourceAsStream("/static/ws/stocksense.wsdl")) {
            if (in == null) {
                return ResponseEntity.notFound().build();
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base = request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        return ResponseEntity.ok().contentType(XML_UTF8).body(template.replace("http://localhost:8081", base));
    }
}
