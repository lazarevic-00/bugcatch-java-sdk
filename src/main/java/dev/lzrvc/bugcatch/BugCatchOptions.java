package dev.lzrvc.bugcatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration for the BugCatch SDK.
 *
 * <p>Build via {@link Builder}:
 * <pre>{@code
 * BugCatchOptions options = new BugCatchOptions.Builder("https://ingest.bugcatch.io/ingest/my-project?key=abc")
 *     .release("1.2.3")
 *     .environment("production")
 *     .debug(true)
 *     .build();
 * BugCatch.init(options);
 * }</pre>
 */
public final class BugCatchOptions {

    private final String dsn;
    private final String release;
    private final String environment;
    private final boolean debug;
    private final int maxBreadcrumbs;
    private final boolean autoCaptureErrors;
    private final List<Pattern> ignoreErrors;
    private final BeforeSendHook beforeSend;

    private BugCatchOptions(Builder builder) {
        this.dsn               = builder.dsn;
        this.release           = builder.release;
        this.environment       = builder.environment;
        this.debug             = builder.debug;
        this.maxBreadcrumbs    = builder.maxBreadcrumbs;
        this.autoCaptureErrors = builder.autoCaptureErrors;
        this.ignoreErrors      = Collections.unmodifiableList(builder.ignoreErrors);
        this.beforeSend        = builder.beforeSend;
    }

    public String          getDsn()               { return dsn; }
    public String          getRelease()           { return release; }
    public String          getEnvironment()       { return environment; }
    public boolean         isDebug()              { return debug; }
    public int             getMaxBreadcrumbs()    { return maxBreadcrumbs; }
    public boolean         isAutoCaptureErrors()  { return autoCaptureErrors; }
    public List<Pattern>   getIgnoreErrors()      { return ignoreErrors; }
    public BeforeSendHook  getBeforeSend()        { return beforeSend; }

    // -------------------------------------------------------------------------

    public static final class Builder {

        private final String dsn;
        private String release;
        private String environment;
        private boolean debug              = false;
        private int maxBreadcrumbs         = 100;
        private boolean autoCaptureErrors  = true;
        private final List<Pattern> ignoreErrors = new ArrayList<>();
        private BeforeSendHook beforeSend;

        /**
         * @param dsn required DSN, e.g. {@code https://host/ingest/{projectId}?key={sdkKey}}
         */
        public Builder(String dsn) {
            if (dsn == null || dsn.isBlank()) throw new IllegalArgumentException("BugCatch DSN must not be blank");
            this.dsn = dsn;
        }

        /** Application version, e.g. {@code "1.2.3"}. */
        public Builder release(String release)           { this.release = release;           return this; }

        /** Deployment environment, e.g. {@code "production"}, {@code "staging"}. */
        public Builder environment(String environment)   { this.environment = environment;   return this; }

        /** Print debug logs to stderr. Default: {@code false}. */
        public Builder debug(boolean debug)              { this.debug = debug;               return this; }

        /** Maximum breadcrumbs kept in memory. Default: {@code 100}. */
        public Builder maxBreadcrumbs(int max)           { this.maxBreadcrumbs = max;        return this; }

        /**
         * Automatically install a default uncaught exception handler.
         * Default: {@code true}.
         */
        public Builder autoCaptureErrors(boolean auto)   { this.autoCaptureErrors = auto;    return this; }

        /**
         * Drop events whose exception message matches the given literal string.
         * Call multiple times to add more patterns.
         */
        public Builder ignoreError(String literal) {
            this.ignoreErrors.add(Pattern.compile(Pattern.quote(literal)));
            return this;
        }

        /**
         * Drop events whose exception message matches the given regex pattern.
         * Call multiple times to add more patterns.
         */
        public Builder ignoreErrorPattern(String regex) {
            this.ignoreErrors.add(Pattern.compile(regex));
            return this;
        }

        /**
         * Hook called before each event is sent. Return the event to send it,
         * or return {@code null} to discard it.
         */
        public Builder beforeSend(BeforeSendHook hook)   { this.beforeSend = hook;           return this; }

        public BugCatchOptions build() { return new BugCatchOptions(this); }
    }
}
