package com.stocksense.soap;

import com.stocksense.service.SystemStatusService;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Set;

import static com.stocksense.soap.SoapXml.add;
import static com.stocksense.soap.SoapXml.element;
import static com.stocksense.soap.SoapXml.group;
import static com.stocksense.soap.SoapXml.list;

/** getSystemStatus: database, market-data providers and ML service health. */
@Component
public class SystemEndpoint implements WsOperationHandler {

    private final SystemStatusService systemStatus;

    public SystemEndpoint(SystemStatusService systemStatus) {
        this.systemStatus = systemStatus;
    }

    @Override
    public Set<String> operations() {
        return Set.of("getSystemStatus");
    }

    @Override
    public Element handle(String operation, Element request, Document out) {
        SystemStatusService.Status s = systemStatus.status();
        Element response = element(out, "getSystemStatusResponse");
        add(response, "status", s.status());
        add(response, "serverTime", s.serverTime());
        add(response, "database", s.database());
        list(response, "providers", "provider", s.providers(), (e, p) -> {
            add(e, "name", p.name());
            add(e, "available", p.available());
        });
        Element ml = group(response, "mlService");
        add(ml, "status", s.mlStatus());
        add(ml, "detail", s.mlDetail());
        add(response, "version", s.version());
        return response;
    }
}
