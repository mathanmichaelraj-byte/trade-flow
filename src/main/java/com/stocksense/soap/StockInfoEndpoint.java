package com.stocksense.soap;

import com.stocksense.market.MarketModels.CompanyProfile;
import com.stocksense.market.MarketModels.QuoteData;
import com.stocksense.market.MarketModels.SearchHit;
import com.stocksense.service.MarketDataService;
import com.stocksense.util.Symbols;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Set;

import static com.stocksense.soap.SoapXml.add;
import static com.stocksense.soap.SoapXml.element;
import static com.stocksense.soap.SoapXml.group;
import static com.stocksense.soap.SoapXml.intValue;
import static com.stocksense.soap.SoapXml.list;
import static com.stocksense.soap.SoapXml.text;

/** Stock Information Module: searchStocks, getCompanyInfo, getStockQuote. */
@Component
public class StockInfoEndpoint implements WsOperationHandler {

    private final MarketDataService market;

    public StockInfoEndpoint(MarketDataService market) {
        this.market = market;
    }

    @Override
    public Set<String> operations() {
        return Set.of("searchStocks", "getCompanyInfo", "getStockQuote");
    }

    @Override
    public Element handle(String operation, Element request, Document out) {
        Element response = element(out, operation + "Response");
        switch (operation) {
            case "searchStocks" -> {
                Integer limit = intValue(request, "limit");
                List<SearchHit> hits = market.search(text(request, "query"), limit == null ? 8 : limit);
                add(response, "count", hits.size());
                list(response, "stocks", "stock", hits, (e, h) -> {
                    add(e, "symbol", h.symbol());
                    add(e, "name", h.name());
                    add(e, "exchange", h.exchange());
                    add(e, "type", h.type());
                });
            }
            case "getCompanyInfo" -> {
                CompanyProfile p = market.profile(Symbols.normalize(text(request, "symbol")));
                add(response, "symbol", p.symbol());
                add(response, "name", p.name());
                add(response, "sector", p.sector());
                add(response, "industry", p.industry());
                add(response, "exchange", p.exchange());
                add(response, "currency", p.currency());
                add(response, "country", p.country());
                add(response, "website", p.website());
                add(response, "description", p.description());
                add(response, "marketCap", p.marketCap());
                add(response, "peRatio", p.peRatio());
                add(response, "eps", p.eps());
                add(response, "dividendYield", p.dividendYield());
                add(response, "beta", p.beta());
                add(response, "week52High", p.week52High());
                add(response, "week52Low", p.week52Low());
                add(response, "roe", p.roe());
                add(response, "roce", p.roce());
                add(response, "source", p.source());
            }
            case "getStockQuote" -> {
                QuoteData q = market.quote(Symbols.normalize(text(request, "symbol")));
                Element e = group(response, "quote");
                add(e, "symbol", q.symbol());
                add(e, "name", q.name());
                add(e, "price", q.price());
                add(e, "change", q.change());
                add(e, "changePercent", q.changePercent());
                add(e, "open", q.open());
                add(e, "high", q.high());
                add(e, "low", q.low());
                add(e, "previousClose", q.previousClose());
                add(e, "volume", q.volume());
                add(e, "currency", q.currency());
                add(e, "exchange", q.exchange());
                add(e, "source", q.source());
                add(e, "asOf", q.asOf());
            }
            default -> throw new IllegalStateException("Unhandled operation " + operation);
        }
        return response;
    }
}
