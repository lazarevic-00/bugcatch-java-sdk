package dev.lzrvc.bugcatch;

import dev.lzrvc.bugcatch.internal.JsonUtil;
import dev.lzrvc.bugcatch.internal.JsonUtil.JsonSerializable;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a single exception (or chained cause) within an event.
 */
public final class ExceptionValue implements JsonSerializable {

    private final String type;
    private final String value;
    private final List<StackFrame> frames;

    private ExceptionValue(String type, String value, List<StackFrame> frames) {
        this.type   = type;
        this.value  = value;
        this.frames = frames;
    }

    /** Build an ExceptionValue from a {@link Throwable}. */
    public static ExceptionValue from(Throwable t) {
        List<StackFrame> frames = Arrays.stream(t.getStackTrace())
                .map(StackFrame::from)
                .toList();
        return new ExceptionValue(t.getClass().getName(), t.getMessage(), frames);
    }

    public String           getType()   { return type; }
    public String           getValue()  { return value; }
    public List<StackFrame> getFrames() { return frames; }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        if (type != null)  sb.append("\"type\":").append(JsonUtil.escape(type)).append(",");
        if (value != null) sb.append("\"value\":").append(JsonUtil.escape(value)).append(",");
        sb.append("\"stacktrace\":{\"frames\":");
        if (frames != null && !frames.isEmpty()) {
            String framesJson = JsonUtil.listToJson(frames);
            sb.append(framesJson != null ? framesJson : "[]");
        } else {
            sb.append("[]");
        }
        sb.append("}}");
        return sb.toString();
    }
}
