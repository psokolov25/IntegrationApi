package ru.aritmos.integration.eventing;

import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventOutboxAutoFlushSchedulerTest {

    @Test
    void shouldSkipFlushWhenAutoFlushDisabled() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setOutboxAutoFlushEnabled(false);
        TrackingOutboxFlusher flusher = new TrackingOutboxFlusher();
        EventOutboxAutoFlushScheduler scheduler = new EventOutboxAutoFlushScheduler(flusher, configuration);

        scheduler.flush();

        assertEquals(0, flusher.invocations());
    }

    @Test
    void shouldFlushWithConfiguredBatchSizeWhenEnabled() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setOutboxAutoFlushEnabled(true);
        configuration.getEventing().setOutboxAutoFlushBatchSize(25);
        TrackingOutboxFlusher flusher = new TrackingOutboxFlusher();
        EventOutboxAutoFlushScheduler scheduler = new EventOutboxAutoFlushScheduler(flusher, configuration);

        scheduler.flush();

        assertEquals(1, flusher.invocations());
        assertEquals(List.of(25), flusher.requestedLimits());
    }

    @Test
    void shouldNormalizeInvalidBatchSize() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.getEventing().setOutboxAutoFlushEnabled(true);
        configuration.getEventing().setOutboxAutoFlushBatchSize(0);
        TrackingOutboxFlusher flusher = new TrackingOutboxFlusher();
        EventOutboxAutoFlushScheduler scheduler = new EventOutboxAutoFlushScheduler(flusher, configuration);

        scheduler.flush();

        assertEquals(List.of(1), flusher.requestedLimits());
    }

    private static final class TrackingOutboxFlusher implements EventOutboxFlusher {
        private final List<Integer> requestedLimits = new ArrayList<>();

        @Override
        public List<EventProcessingResult> flushOutbox(int limit) {
            requestedLimits.add(limit);
            return List.of();
        }

        int invocations() {
            return requestedLimits.size();
        }

        List<Integer> requestedLimits() {
            return List.copyOf(requestedLimits);
        }
    }
}
