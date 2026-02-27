package dev.lzrvc.bugcatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe ring buffer for breadcrumb entries.
 */
final class BreadcrumbBuffer {

    private final int maxSize;
    private final ArrayDeque<BreadcrumbEntry> buffer;

    BreadcrumbBuffer(int maxSize) {
        this.maxSize = maxSize;
        this.buffer  = new ArrayDeque<>(maxSize);
    }

    synchronized void add(BreadcrumbEntry entry) {
        if (buffer.size() >= maxSize) {
            buffer.pollFirst();
        }
        buffer.addLast(entry);
    }

    synchronized List<BreadcrumbEntry> getAll() {
        return new ArrayList<>(buffer);
    }

    synchronized void clear() {
        buffer.clear();
    }
}
