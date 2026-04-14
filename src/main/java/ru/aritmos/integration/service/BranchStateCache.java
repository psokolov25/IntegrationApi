package ru.aritmos.integration.service;

import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.BranchStateDto;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Кэш состояния отделений для быстрых запросов внешних пультов.
 */
@Singleton
public class BranchStateCache {

    private final IntegrationGatewayConfiguration configuration;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BranchStateCache(IntegrationGatewayConfiguration configuration) {
        this(configuration, Clock.systemUTC());
    }

    BranchStateCache(IntegrationGatewayConfiguration configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    public BranchStateDto get(String sourceVisitManagerId, String branchId) {
        CacheEntry entry = cache.get(key(sourceVisitManagerId, branchId));
        if (entry == null) {
            return null;
        }
        if (Instant.now(clock).isAfter(entry.expiresAt())) {
            cache.remove(key(sourceVisitManagerId, branchId));
            return null;
        }
        BranchStateDto value = entry.value();
        return new BranchStateDto(
                value.branchId(),
                value.sourceVisitManagerId(),
                value.status(),
                value.activeWindow(),
                value.queueSize(),
                value.updatedAt(),
                true,
                value.updatedBy()
        );
    }

    public void put(BranchStateDto state) {
        Instant expiresAt = Instant.now(clock).plus(configuration.getBranchStateCacheTtl());
        cache.put(key(state.sourceVisitManagerId(), state.branchId()), new CacheEntry(normalized(state), expiresAt));
    }

    /**
     * Обновляет запись только если событие не устарело по updatedAt.
     *
     * @return true, если запись применена в кэш.
     */
    public boolean putIfNewer(BranchStateDto state) {
        String cacheKey = key(state.sourceVisitManagerId(), state.branchId());
        Instant expiresAt = Instant.now(clock).plus(configuration.getBranchStateCacheTtl());
        BranchStateDto normalized = normalized(state);
        AtomicBoolean applied = new AtomicBoolean(false);
        cache.compute(cacheKey, (ignored, current) -> {
            if (current == null || isStrictlyNewer(normalized, current.value())) {
                applied.set(true);
                return new CacheEntry(normalized, expiresAt);
            }
            return current;
        });
        return applied.get();
    }

    public List<BranchStateDto> snapshot() {
        Instant now = Instant.now(clock);
        return cache.entrySet().stream()
                .filter(entry -> now.isBefore(entry.getValue().expiresAt()))
                .map(Map.Entry::getValue)
                .map(CacheEntry::value)
                .sorted(Comparator.comparing(BranchStateDto::branchId))
                .toList();
    }

    private record CacheEntry(BranchStateDto value, Instant expiresAt) {
    }

    private BranchStateDto normalized(BranchStateDto state) {
        return new BranchStateDto(
                state.branchId(),
                state.sourceVisitManagerId(),
                state.status(),
                state.activeWindow(),
                state.queueSize(),
                state.updatedAt(),
                false,
                state.updatedBy()
        );
    }

    private String key(String sourceVisitManagerId, String branchId) {
        return sourceVisitManagerId + ":" + branchId;
    }

    private boolean isStrictlyNewer(BranchStateDto incoming, BranchStateDto current) {
        if (current.updatedAt() == null) {
            return true;
        }
        if (incoming.updatedAt() == null) {
            return false;
        }
        if (incoming.updatedAt().isAfter(current.updatedAt())) {
            return true;
        }
        if (incoming.updatedAt().equals(current.updatedAt())) {
            return hasStateDelta(incoming, current);
        }
        return false;
    }

    private boolean hasStateDelta(BranchStateDto incoming, BranchStateDto current) {
        if (!safeEquals(incoming.status(), current.status())) {
            return true;
        }
        if (!safeEquals(incoming.activeWindow(), current.activeWindow())) {
            return true;
        }
        if (incoming.queueSize() != current.queueSize()) {
            return true;
        }
        return !safeEquals(incoming.updatedBy(), current.updatedBy());
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
