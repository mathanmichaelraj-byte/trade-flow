package com.stocksense.soap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Set;

/** Implements a group of SOAP operations. */
public interface WsOperationHandler {

    /** Local names of the operations (the request wrapper element names) this handler serves. */
    Set<String> operations();

    /** Builds the response wrapper element (e.g. getStockQuoteResponse) inside {@code out}. */
    Element handle(String operation, Element request, Document out);
}
