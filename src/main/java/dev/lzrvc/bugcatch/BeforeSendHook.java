package dev.lzrvc.bugcatch;

/**
 * Hook called just before an event is sent to the BugCatch ingest endpoint.
 *
 * <p>Return the (optionally modified) event to send it, or return {@code null} to drop the event.
 *
 * <pre>{@code
 * BugCatchOptions options = new BugCatchOptions.Builder("https://...")
 *     .beforeSend(event -> {
 *         // scrub sensitive data
 *         event.getExtra().remove("password");
 *         return event;
 *     })
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface BeforeSendHook {

    /**
     * @param event the event about to be sent
     * @return the event to send, or {@code null} to discard it
     */
    EventPayload beforeSend(EventPayload event);
}
