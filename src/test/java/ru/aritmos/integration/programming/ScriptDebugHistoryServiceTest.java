package ru.aritmos.integration.programming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class ScriptDebugHistoryServiceTest {

    @Test
    void shouldStoreFilterAndClearDebugHistory() {
        ScriptDebugHistoryService service = new ScriptDebugHistoryService();
        service.record(new ScriptDebugHistoryService.DebugEntry(
                "script-a",
                Instant.parse("2026-01-01T10:00:00Z"),
                12,
                true,
                Map.of("ok", true),
                null,
                Map.of("x", 1),
                Map.of("p", "1"),
                Map.of("source", "test")
        ));
        service.record(new ScriptDebugHistoryService.DebugEntry(
                "script-b",
                Instant.parse("2026-01-01T11:00:00Z"),
                20,
                false,
                null,
                "boom",
                Map.of("x", 2),
                Map.of(),
                Map.of("source", "test")
        ));

        List<ScriptDebugHistoryService.DebugEntry> filtered = service.list("script-a", 10);
        Assertions.assertEquals(1, filtered.size());
        Assertions.assertEquals("script-a", filtered.get(0).scriptId());

        List<ScriptDebugHistoryService.DebugEntry> all = service.list("", 10);
        Assertions.assertEquals(2, all.size());
        Assertions.assertEquals("script-b", all.get(0).scriptId());
        Assertions.assertEquals("script-a", service.latest("script-a").scriptId());

        int removedOne = service.clear("script-a");
        Assertions.assertEquals(1, removedOne);
        int removedAll = service.clear("");
        Assertions.assertEquals(1, removedAll);
    }
}
