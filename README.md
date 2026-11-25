# StockSense

Stock market analysis and prediction platform: **Spring Boot + SOAP web services**, live market data
(**Yahoo Finance / Twelve Data**), **MySQL**, a **Python FastAPI ML service**, and a **Chart.js dashboard**.

```
Dashboard (HTML/JS) ──SOAP──▶ Spring Boot ──▶ Stock business logic ──▶ Market data providers (Yahoo / Twelve Data / simulator)
                                                      │
                                                      ├──▶ MySQL (stocks, bars, predictions, metrics, watchlists)
                                                      └──REST──▶ FastAPI ML service ─▶ preprocessing ─▶ features ─▶ model ─▶ prediction
```

## Run it

### Option A: Docker (MySQL + ML + app)
```bash
cp .env.example .env        # optional: add TWELVE_DATA_API_KEY, change passwords
docker compose up --build
```
Open http://localhost:8081

### Option B: local, no Docker (H2 file database)
```bash
# terminal 1: ML service
cd ml-service
python -m venv .venv && .venv\Scripts\activate      # Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000

# terminal 2: backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```
Open http://localhost:8081. To use MySQL locally set `DB_URL`, `DB_USER`, `DB_PASSWORD`.

The app works without the ML service (everything except *Prediction*) and without internet
(the built-in simulator serves a catalogue of demo tickers, clearly labelled `simulated`).

## SOAP API

* Endpoint: `POST /ws/stocksense` (SOAP 1.1, document/literal)
* WSDL: `/ws/stocksense.wsdl`  XSD: `/ws/stocksense.xsd`
* Every request is validated against the XSD; errors come back as SOAP faults with a `tf:error/code` detail.

| Operation | Purpose |
|---|---|
| `searchStocks` | Find tickers by name/symbol |
| `getCompanyInfo` | Profile + fundamentals (market cap, P/E, EPS, dividend yield, beta, 52w range) |
| `getStockQuote` | Latest price, change, OHLC, volume |
| `getHistoricalData` | OHLCV (daily/weekly/monthly/yearly) + return, volatility, drawdown |
| `getTechnicalAnalysis` | SMA, EMA, RSI, MACD, Bollinger Bands, ATR series + buy/sell/neutral summary |
| `predictStock` | ML up/down probability for 1, 5, 10 or 30 trading days |
| `compareStocks` | 2-5 stocks, rebased performance and risk stats |
| `getPredictionHistory` | Past predictions and what actually happened |
| `getModelPerformance` | Hold-out metrics per model + live hit rate |
| `getWatchlist` / `addToWatchlist` / `removeFromWatchlist` | Per-browser watchlists |
| `getSystemStatus` | DB, providers and ML service health |

Example:
```bash
curl -s http://localhost:8081/ws/stocksense -H "Content-Type: text/xml" -d \
'<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tf="urn:stocksense:ws">
  <soapenv:Body><tf:getStockQuote><tf:symbol>AAPL</tf:symbol></tf:getStockQuote></soapenv:Body></soapenv:Envelope>'
```

## How the ML works

* Input: daily OHLCV from the database (up to ~3 years). Target: *is the close N trading days ahead higher than today's close?*
* Features (18): multi-horizon returns, SMA/EMA ratios, MACD histogram, RSI, Bollinger %B/width, ATR%, volatility, volume ratio, range, gap. Every feature uses only past data (unit-tested for look-ahead).
* Models: Logistic Regression, Random Forest, XGBoost. Chronological 80/20 split with an N-day embargo (no shuffling, no leakage).
* Reported: accuracy, precision, recall, F1, ROC-AUC **and the majority-class baseline**. The best model (by ROC-AUC) is refit on all history.
* **Reliability** is `LOW` unless the held-out accuracy beats the baseline by at least 3 points. Daily stock direction is very noisy; expect `LOW` often. That is honest, not a bug.
* Every prediction is stored and scored automatically once its target day has passed (daily job + on demand).

## Configuration

See `src/main/resources/application.properties` and `.env.example`. Key settings:
`MARKET_PROVIDERS` (order of providers; remove `simulated` to never serve fake data), `TWELVE_DATA_API_KEY`,
`ML_SERVICE_URL`, `DB_URL`/`DB_USER`/`DB_PASSWORD`.

## Tests
```bash
./mvnw test                         # Java (indicator maths, SOAP contract, context + JPA)
cd ml-service && pip install -r requirements-dev.txt && pytest
```

## Notes
* `_legacy/` holds the previous TradeFlow implementation; delete it when you no longer need it.
* Yahoo's endpoints are unofficial and may change or rate-limit; the provider chain falls back automatically.
* Educational project. Predictions are probabilistic estimates, not financial advice.
