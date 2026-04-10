package ru.aritmos.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class BranchStateCacheTest {

    @Test
    void shouldIgnoreEventWithSameUpdatedAtToPreventOutOfOrderOverride() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        BranchStateCache cache = new BranchStateCache(cfg, Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC));

        boolean firstApplied = cache.putIfNewer(new BranchStateDto(
                "BR-31",
                "vm-main",
                "OPEN",
                "09:00-18:00",
                1,
                Instant.parse("2026-01-10T10:00:00Z"),
                false,
                "sync-1"
        ));
        boolean secondApplied = cache.putIfNewer(new BranchStateDto(
                "BR-31",
                "vm-main",
                "CLOSED",
                "09:00-18:00",
                0,
                Instant.parse("2026-01-10T10:00:00Z"),
                false,
                "sync-2"
        ));

        BranchStateDto actual = cache.get("vm-main", "BR-31");
        Assertions.assertTrue(firstApplied);
        Assertions.assertFalse(secondApplied);
        Assertions.assertEquals("OPEN", actual.status());
        Assertions.assertEquals("sync-1", actual.updatedBy());
    }

    @Test
    void shouldIgnoreStateWithoutUpdatedAtWhenCacheAlreadyHasTimestamp() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        BranchStateCache cache = new BranchStateCache(cfg, Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC));

        cache.putIfNewer(new BranchStateDto(
                "BR-32",
                "vm-main",
                "OPEN",
                "09:00-18:00",
                2,
                Instant.parse("2026-01-10T10:00:00Z"),
                false,
                "sync-1"
        ));

        boolean appliedWithoutTimestamp = cache.putIfNewer(new BranchStateDto(
                "BR-32",
                "vm-main",
                "PAUSED",
                "09:00-18:00",
                5,
                null,
                false,
                "sync-2"
        ));

        BranchStateDto actual = cache.get("vm-main", "BR-32");
        Assertions.assertFalse(appliedWithoutTimestamp);
        Assertions.assertEquals("OPEN", actual.status());
        Assertions.assertEquals(2, actual.queueSize());
    }
}
