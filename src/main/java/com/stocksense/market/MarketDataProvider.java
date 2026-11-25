package com.stocksense.market;

import com.stocksense.market.MarketModels.Bar;
import com.stocksense.market.MarketModels.CompanyProfile;
import com.stocksense.market.MarketModels.QuoteData;
import com.stocksense.market.MarketModels.SearchHit;

import java.util.List;

/**
 * A source of market data. Implementations throw StockSenseException.SymbolNotFound for unknown symbols and
 * StockSenseException.Provider for any other failure so the router can fall through to the next provider.
 */
public interface MarketDataProvider {

    String name();

    boolean isAvailable();

    List<SearchHit> search(String query, int limit);

    QuoteData quote(String symbol);

    /** Daily bars, oldest first, covering roughly the last {@code days} calendar days. */
    List<Bar> history(String symbol, int days);

    CompanyProfile profile(String symbol);
}
