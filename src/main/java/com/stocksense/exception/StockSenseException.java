package com.stocksense.exception;

/** Base domain error. The code is returned to SOAP clients inside the fault detail. */
public class StockSenseException extends RuntimeException {

    private final String code;
    private final boolean clientFault;

    public StockSenseException(String code, String message, boolean clientFault) {
        super(message);
        this.code = code;
        this.clientFault = clientFault;
    }

    public String getCode() {
        return code;
    }

    public boolean isClientFault() {
        return clientFault;
    }

    public static class InvalidRequest extends StockSenseException {
        public InvalidRequest(String message) {
            super("INVALID_REQUEST", message, true);
        }
    }

    public static class SymbolNotFound extends StockSenseException {
        public SymbolNotFound(String symbol) {
            super("SYMBOL_NOT_FOUND", "No market data found for symbol: " + symbol, true);
        }
    }

    public static class InsufficientData extends StockSenseException {
        public InsufficientData(String message) {
            super("INSUFFICIENT_DATA", message, true);
        }
    }

    public static class ProviderUnavailable extends StockSenseException {
        public ProviderUnavailable(String message) {
            super("PROVIDER_UNAVAILABLE", message, false);
        }
    }

    public static class MlService extends StockSenseException {
        public MlService(String message) {
            super("ML_SERVICE_ERROR", message, false);
        }
    }

    /** Internal: one provider failed (network, rate limit, bad payload). The router tries the next one. */
    public static class Provider extends RuntimeException {
        public Provider(String message) {
            super(message);
        }
    }
}
