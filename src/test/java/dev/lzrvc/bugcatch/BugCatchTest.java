package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BugCatchTest {

    // A DSN that will fail to connect — that's fine, we just want to test event building
    private static final String TEST_DSN = "http://localhost:19999/ingest/test-project?key=test-key";

    @BeforeEach
    void setUp() {
        BugCatch.destroy(); // start each test with a clean state
    }

    @AfterEach
    void tearDown() {
        BugCatch.destroy();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    @Test
    void init_returnsClient() {
        BugCatchClient client = BugCatch.init(new BugCatchOptions.Builder(TEST_DSN).build());
        assertNotNull(client);
    }

    @Test
    void init_requiresDsn() {
        assertThrows(IllegalArgumentException.class, () -> new BugCatchOptions.Builder(null));
        assertThrows(IllegalArgumentException.class, () -> new BugCatchOptions.Builder("  "));
    }

    @Test
    void init_replacesExistingClient() {
        BugCatchClient first  = BugCatch.init(new BugCatchOptions.Builder(TEST_DSN).build());
        BugCatchClient second = BugCatch.init(new BugCatchOptions.Builder(TEST_DSN).build());
        assertNotSame(first, second);
        assertSame(second, BugCatch.getClient());
    }

    @Test
    void staticMethods_silentBeforeInit() {
        // Should not throw, just return null / no-op
        assertNull(BugCatch.captureMessage("hello"));
        assertNull(BugCatch.captureException(new RuntimeException("boom")));
        assertDoesNotThrow(() -> BugCatch.setUser(new UserContext.Builder().id("u1").build()));
        assertDoesNotThrow(() -> BugCatch.clearUser());
        assertDoesNotThrow(() -> BugCatch.setTag("k", "v"));
        assertDoesNotThrow(() -> BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder().message("x").build()));
    }

    // -------------------------------------------------------------------------
    // Options builder
    // -------------------------------------------------------------------------

    @Test
    void options_defaults() {
        BugCatchOptions opts = new BugCatchOptions.Builder(TEST_DSN).build();
        assertEquals(TEST_DSN, opts.getDsn());
        assertNull(opts.getRelease());
        assertNull(opts.getEnvironment());
        assertFalse(opts.isDebug());
        assertEquals(100, opts.getMaxBreadcrumbs());
        assertTrue(opts.isAutoCaptureErrors());
        assertTrue(opts.getIgnoreErrors().isEmpty());
        assertNull(opts.getBeforeSend());
    }

    @Test
    void options_builder_fullConfig() {
        BugCatchOptions opts = new BugCatchOptions.Builder(TEST_DSN)
                .release("2.0.0")
                .environment("staging")
                .debug(true)
                .maxBreadcrumbs(50)
                .autoCaptureErrors(false)
                .ignoreError("Connection refused")
                .ignoreErrorPattern("Timeout.*")
                .build();

        assertEquals("2.0.0",   opts.getRelease());
        assertEquals("staging", opts.getEnvironment());
        assertTrue(opts.isDebug());
        assertEquals(50,        opts.getMaxBreadcrumbs());
        assertFalse(opts.isAutoCaptureErrors());
        assertEquals(2,         opts.getIgnoreErrors().size());
    }

    // -------------------------------------------------------------------------
    // beforeSend hook
    // -------------------------------------------------------------------------

    @Test
    void beforeSend_canDropEvent() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();

        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(event -> {
                    captured.set(event);
                    return null; // drop
                })
                .build());

        String id = BugCatch.captureMessage("should be dropped");
        assertNull(id, "Event should be dropped when beforeSend returns null");
        assertNotNull(captured.get(), "beforeSend should still be called");
    }

    @Test
    void beforeSend_canModifyEvent() {
        AtomicReference<EventPayload> sent = new AtomicReference<>();

        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(event -> {
                    event.setMessage("modified: " + event.getMessage());
                    sent.set(event);
                    return event;
                })
                .build());

        BugCatch.captureMessage("original");
        assertNotNull(sent.get());
        assertTrue(sent.get().getMessage().startsWith("modified:"));
    }

    // -------------------------------------------------------------------------
    // ignoreErrors
    // -------------------------------------------------------------------------

    @Test
    void captureException_ignoredByLiteral() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .ignoreError("Connection refused")
                .beforeSend(e -> { captured.set(e); return e; })
                .build());

        String id = BugCatch.captureException(new RuntimeException("Connection refused"));
        assertNull(id, "Ignored exception should not be sent");
        assertNull(captured.get(), "beforeSend should not be called for ignored events");
    }

    @Test
    void captureException_notIgnoredWhenNoMatch() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .ignoreError("Connection refused")
                .beforeSend(e -> { captured.set(e); return null; }) // drop to avoid HTTP
                .build());

        BugCatch.captureException(new RuntimeException("NullPointerException"));
        assertNotNull(captured.get(), "Non-ignored exception should reach beforeSend");
    }

    // -------------------------------------------------------------------------
    // User context
    // -------------------------------------------------------------------------

    @Test
    void setUser_attachedToEvent() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.setUser(new UserContext.Builder().id("u42").email("test@example.com").build());
        BugCatch.captureMessage("hello");

        assertNotNull(captured.get());
        assertNotNull(captured.get().getUser());
        assertEquals("u42",              captured.get().getUser().getId());
        assertEquals("test@example.com", captured.get().getUser().getEmail());
    }

    @Test
    void clearUser_removedFromEvent() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.setUser(new UserContext.Builder().id("u42").build());
        BugCatch.clearUser();
        BugCatch.captureMessage("no user");

        assertNotNull(captured.get());
        assertNull(captured.get().getUser());
    }

    // -------------------------------------------------------------------------
    // Tags
    // -------------------------------------------------------------------------

    @Test
    void setTag_attachedToEvent() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.setTag("region", "eu-west-1");
        BugCatch.captureMessage("tagged");

        assertNotNull(captured.get());
        assertNotNull(captured.get().getTags());
        assertEquals("eu-west-1", captured.get().getTags().get("region"));
    }

    // -------------------------------------------------------------------------
    // Breadcrumbs
    // -------------------------------------------------------------------------

    @Test
    void breadcrumbs_attachedToEvent() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .maxBreadcrumbs(10)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder()
                .category("http").message("GET /api/orders").build());
        BugCatch.addBreadcrumb(new BreadcrumbEntry.Builder()
                .category("db.query").message("SELECT * FROM orders").build());
        BugCatch.captureMessage("error after breadcrumbs");

        assertNotNull(captured.get());
        List<BreadcrumbEntry> crumbs = captured.get().getBreadcrumbs();
        assertNotNull(crumbs);
        assertEquals(2, crumbs.size());
        assertEquals("GET /api/orders",     crumbs.get(0).getMessage());
        assertEquals("SELECT * FROM orders", crumbs.get(1).getMessage());
    }

    @Test
    void breadcrumbBuffer_respectsMaxSize() {
        BreadcrumbBuffer buf = new BreadcrumbBuffer(3);
        for (int i = 0; i < 5; i++) {
            buf.add(new BreadcrumbEntry.Builder().message("crumb-" + i).build());
        }
        List<BreadcrumbEntry> all = buf.getAll();
        assertEquals(3, all.size());
        assertEquals("crumb-2", all.get(0).getMessage());
        assertEquals("crumb-4", all.get(2).getMessage());
    }

    // -------------------------------------------------------------------------
    // Exception capturing
    // -------------------------------------------------------------------------

    @Test
    void captureException_setsLevelToError() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.captureException(new IllegalStateException("bad state"));
        assertNotNull(captured.get());
        assertEquals("error", captured.get().getLevel());
        assertNotNull(captured.get().getExceptions());
        assertFalse(captured.get().getExceptions().isEmpty());
        assertEquals("java.lang.IllegalStateException", captured.get().getExceptions().get(0).getType());
        assertEquals("bad state", captured.get().getExceptions().get(0).getValue());
    }

    @Test
    void captureException_includeCauseChain() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        Throwable cause = new NullPointerException("null ref");
        Throwable error = new RuntimeException("outer", cause);
        BugCatch.captureException(error);

        assertNotNull(captured.get());
        assertEquals(2, captured.get().getExceptions().size());
        // cause comes first (innermost), outer last
        assertEquals("java.lang.NullPointerException", captured.get().getExceptions().get(0).getType());
        assertEquals("java.lang.RuntimeException",     captured.get().getExceptions().get(1).getType());
    }

    @Test
    void captureException_withExtra() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.captureException(new RuntimeException("boom"),
                Map.of("requestId", "abc-123", "userId", 42));
        assertNotNull(captured.get());
        assertNotNull(captured.get().getExtra());
        assertEquals("abc-123", captured.get().getExtra().get("requestId"));
    }

    // -------------------------------------------------------------------------
    // Stack frames
    // -------------------------------------------------------------------------

    @Test
    void stackFrame_inApp_flaggedCorrectly() {
        StackTraceElement appFrame  = new StackTraceElement("com.myapp.Service", "process", "Service.java", 42);
        StackTraceElement javaFrame = new StackTraceElement("java.util.ArrayList", "add", "ArrayList.java", 100);
        StackTraceElement sdkFrame  = new StackTraceElement("dev.lzrvc.bugcatch.BugCatchClient", "send", "BugCatchClient.java", 1);

        assertTrue(StackFrame.from(appFrame).isInApp());
        assertFalse(StackFrame.from(javaFrame).isInApp());
        assertFalse(StackFrame.from(sdkFrame).isInApp());
    }

    // -------------------------------------------------------------------------
    // JSON serialisation
    // -------------------------------------------------------------------------

    @Test
    void jsonUtil_escapeString() {
        assertEquals("\"hello\"",         JsonUtil.escape("hello"));
        assertEquals("\"say \\\"hi\\\"\"", JsonUtil.escape("say \"hi\""));
        assertEquals("\"line1\\nline2\"",  JsonUtil.escape("line1\nline2"));
        assertEquals("null",              JsonUtil.escape(null));
    }

    @Test
    void eventPayload_toJson_containsRequiredFields() {
        AtomicReference<EventPayload> captured = new AtomicReference<>();
        BugCatch.init(new BugCatchOptions.Builder(TEST_DSN)
                .release("1.0.0")
                .environment("test")
                .autoCaptureErrors(false)
                .beforeSend(e -> { captured.set(e); return null; })
                .build());

        BugCatch.captureMessage("test message", "info");
        assertNotNull(captured.get());

        String json = captured.get().toJson();
        assertTrue(json.contains("\"event_id\""));
        assertTrue(json.contains("\"timestamp\""));
        assertTrue(json.contains("\"platform\":\"java\""));
        assertTrue(json.contains("\"release\":\"1.0.0\""));
        assertTrue(json.contains("\"environment\":\"test\""));
        assertTrue(json.contains("\"message\":\"test message\""));
        assertTrue(json.contains("\"level\":\"info\""));
    }

    @Test
    void userContext_toJson() {
        UserContext user = new UserContext.Builder()
                .id("u1").email("a@b.com").username("alice").build();
        String json = user.toJson();
        assertTrue(json.contains("\"id\":\"u1\""));
        assertTrue(json.contains("\"email\":\"a@b.com\""));
        assertTrue(json.contains("\"username\":\"alice\""));
    }

    @Test
    void breadcrumbEntry_toJson() {
        BreadcrumbEntry crumb = new BreadcrumbEntry.Builder()
                .category("http")
                .message("GET /api")
                .data(Map.of("status", 200))
                .build();
        String json = crumb.toJson();
        assertTrue(json.contains("\"category\":\"http\""));
        assertTrue(json.contains("\"message\":\"GET /api\""));
        assertTrue(json.contains("\"data\""));
    }
}
