package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;
import dev.lzrvc.bugcatch.internal.JsonUtil.JsonSerializable;

import java.util.List;
import java.util.Map;

/**
 * The event payload sent to the BugCatch ingest endpoint.
 *
 * <p>Instances are created by {@link BugCatchClient}. You can inspect or modify this object
 * inside a {@link BeforeSendHook}.
 */
public final class EventPayload implements JsonSerializable {

    private final String eventId;
    private final String timestamp;
    private String level;
    private String message;
    private final String platform = "java";
    private String release;
    private String environment;
    private UserContext user;
    private Map<String, String> tags;
    private Map<String, Object> extra;
    private List<ExceptionValue> exceptions;
    private List<BreadcrumbEntry> breadcrumbs;

    EventPayload(String eventId, String timestamp) {
        this.eventId   = eventId;
        this.timestamp = timestamp;
    }

    // --- Getters ---
    public String               getEventId()     { return eventId; }
    public String               getTimestamp()   { return timestamp; }
    public String               getLevel()       { return level; }
    public String               getMessage()     { return message; }
    public String               getPlatform()    { return platform; }
    public String               getRelease()     { return release; }
    public String               getEnvironment() { return environment; }
    public UserContext          getUser()        { return user; }
    public Map<String, String>  getTags()        { return tags; }
    public Map<String, Object>  getExtra()       { return extra; }
    public List<ExceptionValue> getExceptions()  { return exceptions; }
    public List<BreadcrumbEntry> getBreadcrumbs() { return breadcrumbs; }

    // --- Package-private setters (set by BugCatchClient) ---
    void setLevel(String level)                       { this.level = level; }
    void setMessage(String message)                   { this.message = message; }
    void setRelease(String release)                   { this.release = release; }
    void setEnvironment(String environment)           { this.environment = environment; }
    void setUser(UserContext user)                    { this.user = user; }
    void setTags(Map<String, String> tags)            { this.tags = tags; }
    void setExtra(Map<String, Object> extra)          { this.extra = extra; }
    void setExceptions(List<ExceptionValue> exceptions) { this.exceptions = exceptions; }
    void setBreadcrumbs(List<BreadcrumbEntry> crumbs) { this.breadcrumbs = crumbs; }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"event_id\":").append(JsonUtil.escape(eventId));
        sb.append(",\"timestamp\":").append(JsonUtil.escape(timestamp));
        sb.append(",\"platform\":").append(JsonUtil.escape(platform));
        if (level != null)       sb.append(",\"level\":").append(JsonUtil.escape(level));
        if (message != null)     sb.append(",\"message\":").append(JsonUtil.escape(message));
        if (release != null)     sb.append(",\"release\":").append(JsonUtil.escape(release));
        if (environment != null) sb.append(",\"environment\":").append(JsonUtil.escape(environment));

        if (user != null) sb.append(",\"user\":").append(user.toJson());

        if (tags != null && !tags.isEmpty()) {
            String tagsJson = JsonUtil.stringMapToJson(tags);
            if (tagsJson != null) sb.append(",\"tags\":").append(tagsJson);
        }

        if (extra != null && !extra.isEmpty()) {
            String extraJson = JsonUtil.objectMapToJson(extra);
            if (extraJson != null) sb.append(",\"extra\":").append(extraJson);
        }

        if (exceptions != null && !exceptions.isEmpty()) {
            String excJson = JsonUtil.listToJson(exceptions);
            if (excJson != null) sb.append(",\"exception\":{\"values\":").append(excJson).append("}");
        }

        if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
            String crumbJson = JsonUtil.listToJson(breadcrumbs);
            if (crumbJson != null) sb.append(",\"breadcrumbs\":").append(crumbJson);
        }

        return sb.append("}").toString();
    }
}
