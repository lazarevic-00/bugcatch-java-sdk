package dev.lzrvc.bugcatch.internal;

import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON serializer for BugCatch SDK payloads.
 */
public final class JsonUtil {

    private JsonUtil() {}

    /** Escape a string value and wrap it in double quotes. Returns "null" for null input. */
    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /** Serialize a string map to JSON object. Skips null values. */
    public static String stringMapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!first) sb.append(",");
            sb.append(escape(entry.getKey())).append(":").append(escape(entry.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    /** Serialize an object map to JSON. Handles String, Number, Boolean, null, List, Map, JsonSerializable. */
    @SuppressWarnings("unchecked")
    public static String objectMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append(escape(entry.getKey())).append(":").append(valueToJson(entry.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    /** Serialize a list to JSON array. */
    public static String listToJson(List<? extends JsonSerializable> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (JsonSerializable item : list) {
            if (!first) sb.append(",");
            sb.append(item != null ? item.toJson() : "null");
            first = false;
        }
        return sb.append("]").toString();
    }

    /** Serialize a raw object value to its JSON representation. */
    @SuppressWarnings("unchecked")
    public static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s)          return escape(s);
        if (value instanceof Boolean b)         return b.toString();
        if (value instanceof Number n)          return n.toString();
        if (value instanceof JsonSerializable j) return j.toJson();
        if (value instanceof Map<?,?> m)        return objectMapToJson((Map<String, Object>) m);
        if (value instanceof List<?> l)         return listToJson((List<JsonSerializable>) l);
        return escape(value.toString());
    }

    /** Implemented by all SDK types that can serialize themselves to JSON. */
    public interface JsonSerializable {
        String toJson();
    }
}
