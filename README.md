# BugCatch Java SDK

Official Java SDK for [BugCatch](https://bugcatch.io) error tracking.

- Zero runtime dependencies
- Java 21+
- Thread-safe
- Captures uncaught exceptions automatically
- Full exception cause-chain support
- Breadcrumb trail, user context, tags, and custom metadata

---

## Installation

### Maven

```xml
<dependency>
    <groupId>dev.lzrvc</groupId>
    <artifactId>bugcatch-java-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.lzrvc:bugcatch-java-sdk:0.1.0'
```

---

## Quick start

```java
import dev.lzrvc.bugcatch.BugCatch;
import dev.lzrvc.bugcatch.BugCatchOptions;

BugCatch.init(new BugCatchOptions.Builder(System.getenv("BUGCATCH_DSN"))
    .release(System.getenv("APP_VERSION"))
    .environment("production")
    .build());
```

After `init()` the SDK automatically captures all uncaught exceptions via
`Thread.setDefaultUncaughtExceptionHandler`.

---

## Configuration

All options are set via `BugCatchOptions.Builder`:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `dsn` | `String` | **required** | Project DSN — `https://host/ingest/{projectId}?key={sdkKey}` |
| `release` | `String` | `null` | App version, e.g. `"1.2.3"` |
| `environment` | `String` | `null` | Deployment env, e.g. `"production"` |
| `debug` | `boolean` | `false` | Print debug logs to stderr |
| `maxBreadcrumbs` | `int` | `100` | Max breadcrumbs kept in memory |
| `autoCaptureErrors` | `boolean` | `true` | Auto-install uncaught exception handler |
| `ignoreError(String)` | — | — | Drop errors whose message equals this literal |
| `ignoreErrorPattern(String)` | — | — | Drop errors whose message matches this regex |
| `beforeSend(BeforeSendHook)` | — | `null` | Hook to filter/modify events before sending |

---

## Manual capture

### Exceptions

```java
try {
    processOrder(orderId);
} catch (Exception e) {
    BugCatch.captureException(e);
}

// With extra context
BugCatch.captureException(e, Map.of("orderId", orderId, "userId", userId));
```

### Messages

```java
BugCatch.captureMessage("Payment gateway timed out");
BugCatch.captureMessage("Disk usage above 90%", "warning");
BugCatch.captureMessage("Low memory", "warning", Map.of("freeMb", freeMb));
```

Levels: `"fatal"`, `"error"`, `"warning"`, `"info"`, `"debug"`

---

## User context

```java
import dev.lzrvc.bugcatch.UserContext;

BugCatch.setUser(new UserContext.Builder()
    .id("u123")
    .email("alice@example.com")
    .username("alice")
    .build());

// Remove user context (e.g. on logout)
BugCatch.clearUser();
```

---

## Tags

```java
BugCatch.setTag("region",  "eu-west-1");
BugCatch.setTag("service", "order-service");
```

Tags are attached to every subsequent event.

---

## Breadcrumbs

```java
import dev.lzrvc.bugcatch.BreadcrumbEntry;

BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder()
    .category("http")
    .message("GET /api/orders → 200")
    .build());

BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder()
    .category("db.query")
    .message("SELECT * FROM orders WHERE id = ?")
    .data(Map.of("durationMs", 42))
    .build());
```

The most recent breadcrumbs (up to `maxBreadcrumbs`) are attached to every captured event.

---

## beforeSend hook

Use `beforeSend` to filter or scrub events before they leave the process:

```java
BugCatch.init(new BugCatchOptions.Builder(dsn)
    .beforeSend(event -> {
        // Drop connection errors
        var exceptions = event.getExceptions();
        if (exceptions != null && !exceptions.isEmpty()) {
            String type = exceptions.get(0).getType();
            if ("java.net.ConnectException".equals(type)) return null;
        }

        // Scrub email from user context
        // (UserContext is immutable — rebuild without the email)
        if (event.getUser() != null) {
            event.setUser(new UserContext.Builder()
                .id(event.getUser().getId())
                .build());
        }

        return event;
    })
    .build());
```

Return the event to send it, or `null` to discard it.

---

## Spring Boot

```java
@Configuration
public class BugCatchConfig {

    @Bean
    public BugCatchClient bugCatch(
            @Value("${bugcatch.dsn}") String dsn,
            @Value("${spring.application.version:unknown}") String version,
            @Value("${spring.profiles.active:default}") String env) {

        return BugCatch.init(new BugCatchOptions.Builder(dsn)
                .release(version)
                .environment(env)
                .build());
    }
}
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception e, HttpServletRequest req) {
        BugCatch.captureException(e, Map.of(
            "path",   req.getRequestURI(),
            "method", req.getMethod()
        ));
        return ResponseEntity.status(500).body("Internal server error");
    }
}
```

---

## Cleanup

```java
// Remove auto-installed handlers and clear internal state
BugCatch.destroy();
```

Useful in tests or when shutting down an embedded application context.

---

## Event payload

The JSON sent to the ingest endpoint follows the same structure as the Node SDK:

```json
{
  "event_id": "a3f2...",
  "timestamp": "2026-02-27T12:00:00Z",
  "platform": "java",
  "level": "error",
  "release": "1.2.3",
  "environment": "production",
  "user": { "id": "u123", "email": "alice@example.com" },
  "tags": { "region": "eu-west-1" },
  "extra": { "orderId": 99 },
  "exception": {
    "values": [
      {
        "type": "java.lang.IllegalStateException",
        "value": "Order already processed",
        "stacktrace": {
          "frames": [
            { "filename": "OrderService.java", "lineno": 84, "function": "com.myapp.OrderService.process", "in_app": true }
          ]
        }
      }
    ]
  },
  "breadcrumbs": [
    { "timestamp": "...", "category": "http", "message": "GET /api/orders → 200" }
  ]
}
```

---

## Building from source

```bash
mvn clean package
mvn test
```
