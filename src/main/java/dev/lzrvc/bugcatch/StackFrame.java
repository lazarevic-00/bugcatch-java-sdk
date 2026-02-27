package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;
import dev.lzrvc.bugcatch.internal.JsonUtil.JsonSerializable;

/**
 * A single frame in a Java stack trace.
 */
public final class StackFrame implements JsonSerializable {

    private final String filename;
    private final Integer lineno;
    private final String function;
    private final boolean inApp;

    private StackFrame(String filename, Integer lineno, String function, boolean inApp) {
        this.filename = filename;
        this.lineno   = lineno;
        this.function = function;
        this.inApp    = inApp;
    }

    /** Build a StackFrame from a JVM {@link StackTraceElement}. */
    public static StackFrame from(StackTraceElement element) {
        String filename = element.getFileName();
        Integer lineno  = element.getLineNumber() > 0 ? element.getLineNumber() : null;
        String function = element.getClassName() + "." + element.getMethodName();
        boolean inApp   = isInApp(element.getClassName());
        return new StackFrame(filename, lineno, function, inApp);
    }

    private static boolean isInApp(String className) {
        return !className.startsWith("java.")
            && !className.startsWith("javax.")
            && !className.startsWith("jakarta.")
            && !className.startsWith("sun.")
            && !className.startsWith("com.sun.")
            && !className.startsWith("jdk.")
            && !className.startsWith("dev.lzrvc.bugcatch.");
    }

    public String  getFilename() { return filename; }
    public Integer getLineno()   { return lineno; }
    public String  getFunction() { return function; }
    public boolean isInApp()     { return inApp; }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (filename != null) { sb.append("\"filename\":").append(JsonUtil.escape(filename)); first = false; }
        if (lineno != null)   { if (!first) sb.append(","); sb.append("\"lineno\":").append(lineno); first = false; }
        if (function != null) { if (!first) sb.append(","); sb.append("\"function\":").append(JsonUtil.escape(function)); first = false; }
        if (!first) sb.append(",");
        sb.append("\"in_app\":").append(inApp);
        return sb.append("}").toString();
    }
}
