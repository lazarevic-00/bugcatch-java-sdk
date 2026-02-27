package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;
import dev.lzrvc.bugcatch.internal.JsonUtil.JsonSerializable;

import java.time.Instant;
import java.util.Map;

/**
 * A single breadcrumb that records an event leading up to an error.
 *
 * <pre>{@code
 * BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder()
 *     .category("user.action")
 *     .message("Clicked submit button")
 *     .build());
 * }</pre>
 */
public final class BreadcrumbEntry implements JsonSerializable {

    private final String timestamp;
    private final String type;
    private final String category;
    private final String message;
    private final Map<String, Object> data;

    private BreadcrumbEntry(Builder builder) {
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now().toString();
        this.type      = builder.type;
        this.category  = builder.category;
        this.message   = builder.message;
        this.data      = builder.data;
    }

    public String getTimestamp() { return timestamp; }
    public String getType()      { return type; }
    public String getCategory()  { return category; }
    public String getMessage()   { return message; }
    public Map<String, Object> getData() { return data; }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"timestamp\":").append(JsonUtil.escape(timestamp));
        if (type != null)     sb.append(",\"type\":").append(JsonUtil.escape(type));
        if (category != null) sb.append(",\"category\":").append(JsonUtil.escape(category));
        if (message != null)  sb.append(",\"message\":").append(JsonUtil.escape(message));
        if (data != null && !data.isEmpty()) {
            String dataJson = JsonUtil.objectMapToJson(data);
            if (dataJson != null) sb.append(",\"data\":").append(dataJson);
        }
        return sb.append("}").toString();
    }

    public static final class Builder {
        private String timestamp;
        private String type;
        private String category;
        private String message;
        private Map<String, Object> data;

        public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
        public Builder type(String type)           { this.type = type;           return this; }
        public Builder category(String category)   { this.category = category;   return this; }
        public Builder message(String message)     { this.message = message;     return this; }
        public Builder data(Map<String, Object> data) { this.data = data;        return this; }

        public BreadcrumbEntry build() { return new BreadcrumbEntry(this); }
    }
}
