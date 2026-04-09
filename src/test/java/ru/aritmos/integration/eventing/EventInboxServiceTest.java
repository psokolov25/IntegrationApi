package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class EventInboxServiceTest {

    @Test
    void shouldReturnInboxSnapshotWithStatusFilterAndSorting() throws InterruptedException {
        EventInboxService service = new EventInboxService();

        service.beginProcessing("evt-1");
        service.markProcessed("evt-1");
        Thread.sleep(5);
        service.beginProcessing("evt-2");
        service.markFailed("evt-2", "boom");
        Thread.sleep(5);
        service.beginProcessing("evt-3");

        List<EventInboxService.InboxEntry> failed = service.snapshot(10, "failed");
        Assertions.assertEquals(1, failed.size());
        Assertions.assertEquals("evt-2", failed.get(0).eventId());

        List<EventInboxService.InboxEntry> all = service.snapshot(2, "");
        Assertions.assertEquals(2, all.size());
        Assertions.assertEquals("evt-3", all.get(0).eventId());
        Assertions.assertEquals("evt-2", all.get(1).eventId());

        int removedFailed = service.removeByStatus("FAILED");
        Assertions.assertEquals(1, removedFailed);
        Assertions.assertEquals(0, service.snapshot(10, "FAILED").size());
    }
}
