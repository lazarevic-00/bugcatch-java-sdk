package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Core BugCatch client. Obtain via {@link BugCatch#init(BugCatchOptions)}.
 */
public final class BugCatchClient {

    private final BugCatchOptions options;
    private final String ingestUrl;
    private final String metricsUrl;
    private final HttpClient httpClient;
    private final BreadcrumbBuffer breadcrumbs;

    // Mutable state — all access is synchronized or uses concurrent structures
    private volatile UserContext currentUser;
    private final ConcurrentHashMap<String, String> tags = new ConcurrentHashMap<>();

    // Auto-capture — keep reference so we can restore on destroy()
    private volatile Thread.UncaughtExceptionHandler previousHandler;

    BugCatchClient(BugCatchOptions options) {
        this.options     = options;
        this.ingestUrl   = options.getDsn();          // DSN *is* the ingest URL
        this.metricsUrl  = buildMetricsUrl(options.getDsn());
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.breadcrumbs = new BreadcrumbBuffer(options.getMaxBreadcrumbs());

        if (options.isAutoCaptureErrors()) {
            installErrorHandlers();
        }

        debug("BugCatch initialized. Ingest: " + ingestUrl);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Capture an exception and send it to BugCatch.
     *
     * @return the generated {@code event_id}
     */
    public String captureException(Throwable error) {
        return captureException(error, null);
    }

    /**
     * Capture an exception with additional arbitrary context.
     *
     * @return the generated {@code event_id}
     */
    public String captureException(Throwable error, Map<String, Object> extra) {
        if (error == null) return null;
        if (shouldIgnore(error)) {
            debug("Ignoring exception: " + error.getMessage());
            return null;
        }

        EventPayload event = buildBase("error", extra);

        // Build exception chain innermost-first (cause → outer), matching Sentry convention
        List<ExceptionValue> chain = new ArrayList<>();
        Throwable current = error;
        while (current != null) {
            chain.add(0, ExceptionValue.from(current));
            current = current.getCause();
        }
        event.setExceptions(chain);

        return send(event);
    }

    /**
     * Capture a plain message at {@code "info"} level.
     *
     * @return the generated {@code event_id}
     */
    public String captureMessage(String message) {
        return captureMessage(message, "info", null);
    }

    /**
     * Capture a plain message at the specified level.
     *
     * @param level one of {@code "fatal"}, {@code "error"}, {@code "warning"}, {@code "info"}, {@code "debug"}
     * @return the generated {@code event_id}
     */
    public String captureMessage(String message, String level) {
        return captureMessage(message, level, null);
    }

    /**
     * Capture a plain message with additional context.
     *
     * @return the generated {@code event_id}
     */
    public String captureMessage(String message, String level, Map<String, Object> extra) {
        EventPayload event = buildBase(level != null ? level : "info", extra);
        event.setMessage(message);
        return send(event);
    }

    /** Attach a user to all subsequent events. */
    public void setUser(UserContext user) {
        this.currentUser = user;
    }

    /** Remove the current user context. */
    public void clearUser() {
        this.currentUser = null;
    }

    /** Attach a tag to all subsequent events. */
    public void setTag(String key, String value) {
        if (key != null && value != null) tags.put(key, value);
    }

    /** Remove a previously set tag. */
    public void removeTag(String key) {
        if (key != null) tags.remove(key);
    }

    /** Add a breadcrumb to the internal buffer. */
    public void addBreadcrumb(BreadcrumbEntry crumb) {
        if (crumb != null) breadcrumbs.add(crumb);
    }

    /**
     * Report an API call timing metric to BugCatch.
     *
     * <p>Use this to track outbound HTTP calls or incoming request durations.
     * The route should be a template (e.g. {@code "/api/orders/:id"}), not a
     * concrete URL with real IDs — use {@link #trackRequest(String, String, long, int)}
     * if you have a raw URL and want automatic normalization.
     *
     * <p><b>Spring Boot example</b> — implement a {@code HandlerInterceptor}:
     * <pre>{@code
     * public boolean preHandle(HttpServletRequest req, ...) {
     *     req.setAttribute("_bugcatch_start", System.currentTimeMillis());
     *     return true;
     * }
     * public void afterCompletion(HttpServletRequest req, HttpServletResponse res, ...) {
     *     long duration = System.currentTimeMillis() - (long) req.getAttribute("_bugcatch_start");
     *     BugCatch.trackRequest(req.getMethod(), req.getRequestURI(), duration, res.getStatus());
     * }
     * }</pre>
     *
     * @param method     HTTP method, e.g. {@code "GET"}
     * @param route      Request path or URL (UUIDs and numeric IDs are normalized to {@code :id})
     * @param durationMs Duration in milliseconds
     * @param statusCode HTTP response status code
     */
    public void trackRequest(String method, String route, long durationMs, int statusCode) {
        if (method == null || route == null) return;
        sendMetric(method.toUpperCase(), normalizeRoute(route), durationMs, statusCode);
    }

    /**
     * Remove the auto-installed uncaught exception handler and clear internal state.
     * Call this if you need to re-initialize the SDK (e.g. in tests).
     */
    public void destroy() {
        if (previousHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
            previousHandler = null;
        }
        breadcrumbs.clear();
        tags.clear();
        currentUser = null;
        debug("BugCatch destroyed.");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private EventPayload buildBase(String level, Map<String, Object> extra) {
        EventPayload event = new EventPayload(uuid(), Instant.now().toString());
        event.setLevel(level);
        event.setRelease(options.getRelease());
        event.setEnvironment(options.getEnvironment());
        event.setUser(currentUser);

        if (!tags.isEmpty()) {
            event.setTags(new HashMap<>(tags));
        }

        if (extra != null && !extra.isEmpty()) {
            event.setExtra(extra);
        }

        List<BreadcrumbEntry> crumbs = breadcrumbs.getAll();
        if (!crumbs.isEmpty()) {
            event.setBreadcrumbs(crumbs);
        }

        return event;
    }

    private String send(EventPayload event) {
        if (options.getBeforeSend() != null) {
            event = options.getBeforeSend().beforeSend(event);
            if (event == null) {
                debug("Event dropped by beforeSend hook.");
                return null;
            }
        }

        String json = event.toJson();
        String eventId = event.getEventId();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ingestUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        debug("Event sent: " + eventId + " (HTTP " + response.statusCode() + ")");
                    } else {
                        System.err.println("[BugCatch] Failed to send event " + eventId
                                + " — HTTP " + response.statusCode() + ": " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("[BugCatch] Network error for event " + eventId + ": " + ex.getMessage());
                    return null;
                });

        return eventId;
    }

    /** Derives the metrics endpoint URL from the ingest DSN. */
    private static String buildMetricsUrl(String dsn) {
        // DSN: http://host/ingest/{projectId}?key={sdkKey}
        // → http://host/ingest/{projectId}/metrics?key={sdkKey}
        int q = dsn.indexOf('?');
        if (q < 0) return dsn + "/metrics";
        return dsn.substring(0, q) + "/metrics?" + dsn.substring(q + 1);
    }

    /**
     * Replaces UUID and purely-numeric path segments with {@code :id} so
     * metrics group by route template rather than individual resource IDs.
     */
    private static String normalizeRoute(String route) {
        String path;
        try {
            path = new URI(route).getPath();
            if (path == null || path.isEmpty()) path = route;
        } catch (Exception e) {
            // Not a full URL — treat as path; strip query string
            int q = route.indexOf('?');
            path = q >= 0 ? route.substring(0, q) : route;
        }
        return path
                .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/:id")
                .replaceAll("/\\d{1,20}(?=/|$)", "/:id");
    }

    private void sendMetric(String method, String route, long durationMs, int statusCode) {
        String json = "{" +
                "\"method\":" + JsonUtil.escape(method) + "," +
                "\"route\":" + JsonUtil.escape(route) + "," +
                "\"duration_ms\":" + durationMs + "," +
                "\"status_code\":" + statusCode +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(metricsUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    debug("Failed to send metric: " + ex.getMessage());
                    return null;
                });
    }

    private boolean shouldIgnore(Throwable error) {
        List<Pattern> patterns = options.getIgnoreErrors();
        if (patterns.isEmpty()) return false;
        String msg = error.getMessage();
        if (msg == null) msg = error.getClass().getName();
        for (Pattern p : patterns) {
            if (p.matcher(msg).find()) return true;
        }
        return false;
    }

    private void installErrorHandlers() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            captureException(throwable, Map.of("thread", thread.getName()));
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
        debug("Auto-capture: installed uncaught exception handler.");
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private void debug(String msg) {
        if (options.isDebug()) {
            System.err.println("[BugCatch] " + msg);
        }
    }
}
