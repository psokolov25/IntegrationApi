package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory история debug/execute запусков Groovy-скриптов для UI-отладчика.
 */
@Singleton
public class ScriptDebugHistoryService {

    private static final int MAX_ENTRIES = 200;
    private final Map<String, Deque<DebugEntry>> entriesByScript = new ConcurrentHashMap<>();

    public void record(DebugEntry entry) {
        if (entry == null || entry.scriptId() == null || entry.scriptId().isBlank()) {
            return;
        }
        Deque<DebugEntry> deque = entriesByScript.computeIfAbsent(entry.scriptId(), key -> new ConcurrentLinkedDeque<>());
        deque.addFirst(entry);
        while (deque.size() > MAX_ENTRIES) {
            deque.pollLast();
        }
    }

    public List<DebugEntry> list(String scriptId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit должен быть > 0");
        }
        if (scriptId != null && !scriptId.isBlank()) {
            return entriesByScript.getOrDefault(scriptId, new ConcurrentLinkedDeque<>())
                    .stream()
                    .limit(limit)
                    .toList();
        }
        return entriesByScript.values().stream()
                .flatMap(Deque::stream)
                .sorted(Comparator.comparing(DebugEntry::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    public int clear(String scriptId) {
        if (scriptId != null && !scriptId.isBlank()) {
            Deque<DebugEntry> removed = entriesByScript.remove(scriptId);
            return removed == null ? 0 : removed.size();
        }
        int removed = entriesByScript.values().stream().mapToInt(Deque::size).sum();
        entriesByScript.clear();
        return removed;
    }

    public DebugEntry latest(String scriptId) {
        if (scriptId == null || scriptId.isBlank()) {
            return null;
        }
        return entriesByScript.getOrDefault(scriptId, new ConcurrentLinkedDeque<>()).peekFirst();
    }

    public record DebugEntry(
            String scriptId,
            Instant startedAt,
            long durationMs,
            boolean ok,
            Object result,
            String error,
            Map<String, Object> payload,
            Map<String, Object> parameters,
            Map<String, Object> context
    ) {
    }
}
