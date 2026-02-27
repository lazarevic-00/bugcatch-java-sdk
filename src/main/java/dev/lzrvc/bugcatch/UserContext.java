package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;
import dev.lzrvc.bugcatch.internal.JsonUtil.JsonSerializable;

/**
 * User identity to attach to error events.
 *
 * <pre>{@code
 * BugCatch.setUser(new UserContext.Builder().id("u123").email("alice@example.com").build());
 * }</pre>
 */
public final class UserContext implements JsonSerializable {

    private final String id;
    private final String email;
    private final String username;

    private UserContext(Builder builder) {
        this.id = builder.id;
        this.email = builder.email;
        this.username = builder.username;
    }

    public String getId()       { return id; }
    public String getEmail()    { return email; }
    public String getUsername() { return username; }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (id != null)       { sb.append("\"id\":").append(JsonUtil.escape(id));             first = false; }
        if (email != null)    { if (!first) sb.append(","); sb.append("\"email\":").append(JsonUtil.escape(email));       first = false; }
        if (username != null) { if (!first) sb.append(","); sb.append("\"username\":").append(JsonUtil.escape(username)); }
        return sb.append("}").toString();
    }

    public static final class Builder {
        private String id;
        private String email;
        private String username;

        public Builder id(String id)             { this.id = id;             return this; }
        public Builder email(String email)       { this.email = email;       return this; }
        public Builder username(String username) { this.username = username; return this; }

        public UserContext build() { return new UserContext(this); }
    }
}
