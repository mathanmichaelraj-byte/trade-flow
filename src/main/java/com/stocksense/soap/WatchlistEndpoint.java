package com.stocksense.soap;

import com.stocksense.service.WatchlistService;
import com.stocksense.util.Symbols;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Set;

import static com.stocksense.soap.SoapXml.add;
import static com.stocksense.soap.SoapXml.element;
import static com.stocksense.soap.SoapXml.list;
import static com.stocksense.soap.SoapXml.text;

/** Watchlist Management: getWatchlist, addToWatchlist, removeFromWatchlist. */
@Component
public class WatchlistEndpoint implements WsOperationHandler {

    private final WatchlistService watchlist;

    public WatchlistEndpoint(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    @Override
    public Set<String> operations() {
        return Set.of("getWatchlist", "addToWatchlist", "removeFromWatchlist");
    }

    @Override
    public Element handle(String operation, Element request, Document out) {
        Element response = element(out, operation + "Response");
        String owner = text(request, "owner");
        switch (operation) {
            case "getWatchlist" -> {
                List<WatchlistService.Entry> items = watchlist.list(owner);
                add(response, "count", items.size());
                list(response, "items", "item", items, (e, i) -> {
                    add(e, "symbol", i.symbol());
                    add(e, "name", i.name());
                    add(e, "price", i.price());
                    add(e, "change", i.change());
                    add(e, "changePercent", i.changePercent());
                    add(e, "currency", i.currency());
                    add(e, "source", i.source());
                    add(e, "addedAt", i.addedAt());
                });
            }
            case "addToWatchlist" -> {
                String symbol = Symbols.normalize(text(request, "symbol"));
                boolean added = watchlist.add(owner, symbol);
                add(response, "symbol", symbol);
                add(response, "added", added);
                add(response, "count", watchlist.count(owner));
            }
            case "removeFromWatchlist" -> {
                String symbol = Symbols.normalize(text(request, "symbol"));
                boolean removed = watchlist.remove(owner, symbol);
                add(response, "symbol", symbol);
                add(response, "removed", removed);
                add(response, "count", watchlist.count(owner));
            }
            default -> throw new IllegalStateException("Unhandled operation " + operation);
        }
        return response;
    }
}
