package com.stocksense.soap;

import com.stocksense.exception.StockSenseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

/** DOM helpers for the document/literal SOAP 1.1 binding. */
public final class SoapXml {

    public static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String TF_NS = "urn:stocksense:ws";

    private SoapXml() {
    }

    // ------------------------------------------------------------------ parsing

    private static DocumentBuilderFactory factory() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    public static Document parse(byte[] xml) throws ParserConfigurationException, SAXException, IOException {
        return factory().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    public static Document newDocument() {
        try {
            return factory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** First element inside soapenv:Body, or null. */
    public static Element operationElement(Document doc) {
        Element root = doc.getDocumentElement();
        if (root == null || !SOAP_NS.equals(root.getNamespaceURI()) || !"Envelope".equals(root.getLocalName())) {
            return null;
        }
        Element body = child(root, SOAP_NS, "Body");
        return body == null ? null : firstElement(body);
    }

    public static Element firstElement(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element child(Element parent, String ns, String local) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && local.equals(n.getLocalName()) && ns.equals(n.getNamespaceURI())) {
                return (Element) n;
            }
        }
        return null;
    }

    /** Trimmed text of a child element in the StockSense namespace, or null when absent/blank. */
    public static String text(Element parent, String local) {
        Element e = child(parent, TF_NS, local);
        if (e == null) {
            return null;
        }
        String t = e.getTextContent();
        return t == null || t.isBlank() ? null : t.trim();
    }

    public static Integer intValue(Element parent, String local) {
        String t = text(parent, local);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            throw new StockSenseException.InvalidRequest(local + " must be an integer");
        }
    }

    public static boolean boolValue(Element parent, String local) {
        String t = text(parent, local);
        return "true".equalsIgnoreCase(t) || "1".equals(t);
    }

    /** Texts of &lt;wrapper&gt;&lt;item&gt;..&lt;/item&gt;...&lt;/wrapper&gt;. */
    public static List<String> textList(Element parent, String wrapper, String item) {
        List<String> out = new ArrayList<>();
        Element w = child(parent, TF_NS, wrapper);
        if (w == null) {
            return out;
        }
        NodeList nodes = w.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && item.equals(n.getLocalName())) {
                out.add(n.getTextContent().trim());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ building

    public static Element element(Document doc, String local) {
        return doc.createElementNS(TF_NS, "tf:" + local);
    }

    /** Appends {@code <tf:name>value</tf:name>}; null and non-finite values are omitted. */
    public static Element add(Element parent, String name, Object value) {
        if (value == null || (value instanceof Double d && !Double.isFinite(d))
                || (value instanceof Float f && !Float.isFinite(f))) {
            return null;
        }
        Element e = element(parent.getOwnerDocument(), name);
        e.setTextContent(format(value));
        parent.appendChild(e);
        return e;
    }

    public static Element group(Element parent, String name) {
        Element e = element(parent.getOwnerDocument(), name);
        parent.appendChild(e);
        return e;
    }

    public static <T> void list(Element parent, String wrapper, String item, Collection<T> items,
                                BiConsumer<Element, T> fill) {
        Element w = group(parent, wrapper);
        for (T t : items) {
            fill.accept(group(w, item), t);
        }
    }

    static String format(Object v) {
        if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) {
            BigDecimal bd = v instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) v).doubleValue());
            return bd.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        if (v instanceof Instant || v instanceof LocalDate || v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        return String.valueOf(v);
    }

    // ------------------------------------------------------------------ envelopes

    public static String envelope(Element responseElement) {
        Document doc = responseElement.getOwnerDocument();
        Element envelope = doc.createElementNS(SOAP_NS, "soapenv:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:soapenv", SOAP_NS);
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:tf", TF_NS);
        Element body = doc.createElementNS(SOAP_NS, "soapenv:Body");
        body.appendChild(responseElement);
        envelope.appendChild(body);
        return serialize(envelope);
    }

    /** SOAP 1.1 fault. The StockSense error code travels in the detail element. */
    public static String fault(String code, String message, boolean clientFault) {
        Document doc = newDocument();
        Element envelope = doc.createElementNS(SOAP_NS, "soapenv:Envelope");
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:soapenv", SOAP_NS);
        envelope.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:tf", TF_NS);
        Element body = doc.createElementNS(SOAP_NS, "soapenv:Body");
        Element fault = doc.createElementNS(SOAP_NS, "soapenv:Fault");
        fault.appendChild(plain(doc, "faultcode", clientFault ? "soapenv:Client" : "soapenv:Server"));
        fault.appendChild(plain(doc, "faultstring", message));
        Element detail = doc.createElement("detail");
        Element error = element(doc, "error");
        add(error, "code", code);
        add(error, "message", message);
        detail.appendChild(error);
        fault.appendChild(detail);
        body.appendChild(fault);
        envelope.appendChild(body);
        return serialize(envelope);
    }

    private static Element plain(Document doc, String name, String text) {
        Element e = doc.createElement(name);
        e.setTextContent(text);
        return e;
    }

    private static String serialize(Element element) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(element), new StreamResult(w));
            return w.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize SOAP message", e);
        }
    }
}
