package com.stocksense.util;

import java.util.List;
import java.util.Map;

/** Null-safe navigation over the Map/List trees produced by JSON message converters. */
public final class Json {

    private Json() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    /** Walks a path of String keys / Integer indexes; returns null when any step is missing. */
    public static Object at(Object root, Object... path) {
        Object cur = root;
        for (Object key : path) {
            if (cur == null) {
                return null;
            }
            if (key instanceof Integer i) {
                List<Object> l = list(cur);
                cur = i < l.size() ? l.get(i) : null;
            } else {
                cur = obj(cur).get(String.valueOf(key));
            }
        }
        return cur;
    }

    /** Number, numeric string ("1,234.5", "1.2%"), or {raw: n} wrapper -> Double; null otherwise. */
    public static Double dbl(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map<?, ?> m) {
            return dbl(m.get("raw"));
        }
        if (o instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        if (o instanceof String s) {
            String t = s.trim().replace(",", "").replace("%", "");
            if (t.isEmpty()) {
                return null;
            }
            try {
                double d = Double.parseDouble(t);
                return Double.isFinite(d) ? d : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Long lng(Object o) {
        Double d = dbl(o);
        return d == null ? null : d.longValue();
    }

    /** Trimmed string or null when absent/blank. */
    public static String str(Object o) {
        if (o == null || o instanceof Map || o instanceof List) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
