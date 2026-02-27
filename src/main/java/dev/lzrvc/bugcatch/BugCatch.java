package dev.lzrvc.bugcatch;

import java.util.Map;

/**
 * Entry point for the BugCatch Java SDK.
 *
 * <p>Call {@link #init(BugCatchOptions)} once at application startup, then use the static
 * helpers from anywhere in your code:
 *
 * <pre>{@code
 * // Initialise once (e.g. in main() or application context)
 * BugCatch.init(new BugCatchOptions.Builder(System.getenv("BUGCATCH_DSN"))
 *     .release(System.getenv("APP_VERSION"))
 *     .environment("production")
 *     .build());
 *
 * // Capture errors
 * try {
 *     riskyOperation();
 * } catch (Exception e) {
 *     BugCatch.captureException(e);
 * }
 *
 * // Capture messages
 * BugCatch.captureMessage("Payment gateway timed out", "warning");
 *
 * // Attach user context
 * BugCatch.setUser(new UserContext.Builder().id("u123").email("alice@example.com").build());
 *
 * // Custom breadcrumbs
 * BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder()
 *     .category("db.query")
 *     .message("SELECT * FROM orders WHERE id = ?")
 *     .build());
 * }</pre>
 */
public final class BugCatch {

    private BugCatch() {}

    private static volatile BugCatchClient instance;

    /**
     * Initialise the SDK. Must be called before any other method.
     * Calling {@code init} again after a previous call replaces the existing client.
     *
     * @param options SDK configuration
     * @return the initialized {@link BugCatchClient}
     */
    public static synchronized BugCatchClient init(BugCatchOptions options) {
        if (options == null) throw new IllegalArgumentException("BugCatchOptions must not be null");
        if (instance != null) {
            instance.destroy();
        }
        instance = new BugCatchClient(options);
        return instance;
    }

    /**
     * Capture an exception and send it to BugCatch.
     *
     * @return the generated {@code event_id}, or {@code null} if not initialized / event dropped
     */
    public static String captureException(Throwable error) {
        BugCatchClient c = instance;
        return c != null ? c.captureException(error) : null;
    }

    /**
     * Capture an exception with additional arbitrary context.
     *
     * @return the generated {@code event_id}, or {@code null} if not initialized / event dropped
     */
    public static String captureException(Throwable error, Map<String, Object> extra) {
        BugCatchClient c = instance;
        return c != null ? c.captureException(error, extra) : null;
    }

    /**
     * Capture a plain message at {@code "info"} level.
     *
     * @return the generated {@code event_id}, or {@code null} if not initialized
     */
    public static String captureMessage(String message) {
        BugCatchClient c = instance;
        return c != null ? c.captureMessage(message) : null;
    }

    /**
     * Capture a plain message at the specified level.
     *
     * @param level one of {@code "fatal"}, {@code "error"}, {@code "warning"}, {@code "info"}, {@code "debug"}
     * @return the generated {@code event_id}, or {@code null} if not initialized
     */
    public static String captureMessage(String message, String level) {
        BugCatchClient c = instance;
        return c != null ? c.captureMessage(message, level) : null;
    }

    /**
     * Capture a plain message with additional context.
     *
     * @return the generated {@code event_id}, or {@code null} if not initialized
     */
    public static String captureMessage(String message, String level, Map<String, Object> extra) {
        BugCatchClient c = instance;
        return c != null ? c.captureMessage(message, level, extra) : null;
    }

    /** Attach a user to all subsequent events. */
    public static void setUser(UserContext user) {
        BugCatchClient c = instance;
        if (c != null) c.setUser(user);
    }

    /** Remove the current user context. */
    public static void clearUser() {
        BugCatchClient c = instance;
        if (c != null) c.clearUser();
    }

    /** Attach a tag key/value to all subsequent events. */
    public static void setTag(String key, String value) {
        BugCatchClient c = instance;
        if (c != null) c.setTag(key, value);
    }

    /** Add a breadcrumb to the buffer. */
    public static void addBreadcrumb(BreadcrumbEntry crumb) {
        BugCatchClient c = instance;
        if (c != null) c.addBreadcrumb(crumb);
    }

    /**
     * Destroy the current client, removing auto-installed handlers.
     * Useful in tests or when re-initialising the SDK.
     */
    public static synchronized void destroy() {
        if (instance != null) {
            instance.destroy();
            instance = null;
        }
    }

    /**
     * @return the current {@link BugCatchClient}, or {@code null} if not yet initialized.
     */
    public static BugCatchClient getClient() {
        return instance;
    }
}
