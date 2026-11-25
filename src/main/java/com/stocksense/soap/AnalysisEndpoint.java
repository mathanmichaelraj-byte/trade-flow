package com.stocksense.soap;

import com.stocksense.service.ComparisonService;
import com.stocksense.service.HistoryAnalysisService;
import com.stocksense.service.TechnicalAnalysisService;
import com.stocksense.util.Symbols;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Set;

import static com.stocksense.soap.SoapXml.add;
import static com.stocksense.soap.SoapXml.element;
import static com.stocksense.soap.SoapXml.group;
import static com.stocksense.soap.SoapXml.list;
import static com.stocksense.soap.SoapXml.text;
import static com.stocksense.soap.SoapXml.textList;

/** Historical Analysis, Technical Analysis and comparison operations. */
@Component
public class AnalysisEndpoint implements WsOperationHandler {

    private final HistoryAnalysisService history;
    private final TechnicalAnalysisService technical;
    private final ComparisonService comparison;

    public AnalysisEndpoint(HistoryAnalysisService history, TechnicalAnalysisService technical,
                            ComparisonService comparison) {
        this.history = history;
        this.technical = technical;
        this.comparison = comparison;
    }

    @Override
    public Set<String> operations() {
        return Set.of("getHistoricalData", "getTechnicalAnalysis", "compareStocks");
    }

    @Override
    public Element handle(String operation, Element request, Document out) {
        Element response = element(out, operation + "Response");
        switch (operation) {
            case "getHistoricalData" -> {
                HistoryAnalysisService.Result r = history.analyze(Symbols.normalize(text(request, "symbol")),
                        text(request, "range"), text(request, "interval"));
                add(response, "symbol", r.symbol());
                add(response, "range", r.range());
                add(response, "interval", r.interval());
                add(response, "source", r.source());
                add(response, "count", r.bars().size());
                list(response, "bars", "bar", r.bars(), (e, b) -> {
                    add(e, "date", b.date());
                    add(e, "open", b.open());
                    add(e, "high", b.high());
                    add(e, "low", b.low());
                    add(e, "close", b.close());
                    add(e, "volume", b.volume());
                });
                HistoryAnalysisService.Summary s = r.summary();
                Element se = group(response, "summary");
                add(se, "startDate", s.startDate());
                add(se, "endDate", s.endDate());
                add(se, "startPrice", s.startPrice());
                add(se, "endPrice", s.endPrice());
                add(se, "changePercent", s.changePercent());
                add(se, "high", s.high());
                add(se, "low", s.low());
                add(se, "averageVolume", s.averageVolume());
                add(se, "annualizedVolatility", s.annualizedVolatility());
                add(se, "maxDrawdownPercent", s.maxDrawdownPercent());
                add(se, "bestDayPercent", s.bestDayPercent());
                add(se, "worstDayPercent", s.worstDayPercent());
                add(se, "upDays", s.upDays());
                add(se, "downDays", s.downDays());
            }
            case "getTechnicalAnalysis" -> {
                String range = text(request, "range");
                TechnicalAnalysisService.Result r = technical.analyze(Symbols.normalize(text(request, "symbol")),
                        HistoryAnalysisService.rangeDays(range));
                add(response, "symbol", r.symbol());
                add(response, "asOf", r.asOf());
                add(response, "price", r.price());
                add(response, "overall", r.overall());
                add(response, "buySignals", r.buy());
                add(response, "sellSignals", r.sell());
                add(response, "neutralSignals", r.neutral());
                list(response, "signals", "signal", r.signals(), (e, s) -> {
                    add(e, "indicator", s.indicator());
                    add(e, "value", s.value());
                    add(e, "signal", s.signal());
                    add(e, "note", s.note());
                });
                list(response, "series", "point", r.series(), (e, p) -> {
                    add(e, "date", p.date());
                    add(e, "close", p.close());
                    add(e, "sma20", p.sma20());
                    add(e, "sma50", p.sma50());
                    add(e, "ema20", p.ema20());
                    add(e, "rsi14", p.rsi14());
                    add(e, "macd", p.macd());
                    add(e, "macdSignal", p.macdSignal());
                    add(e, "macdHistogram", p.macdHistogram());
                    add(e, "bbUpper", p.bbUpper());
                    add(e, "bbMiddle", p.bbMiddle());
                    add(e, "bbLower", p.bbLower());
                    add(e, "atr14", p.atr14());
                });
            }
            case "compareStocks" -> {
                ComparisonService.Result r = comparison.compare(textList(request, "symbols", "symbol"), text(request, "range"));
                add(response, "range", r.range());
                list(response, "items", "item", r.items(), (e, i) -> {
                    add(e, "symbol", i.symbol());
                    add(e, "name", i.name());
                    add(e, "price", i.price());
                    add(e, "periodReturnPercent", i.periodReturnPercent());
                    add(e, "annualizedVolatility", i.annualizedVolatility());
                    add(e, "maxDrawdownPercent", i.maxDrawdownPercent());
                    add(e, "rsi14", i.rsi14());
                    add(e, "overall", i.overall());
                    list(e, "series", "point", i.series(), (pe, p) -> {
                        add(pe, "date", p.date());
                        add(pe, "value", p.value());
                    });
                });
            }
            default -> throw new IllegalStateException("Unhandled operation " + operation);
        }
        return response;
    }
}
