package ru.suo.integration.service;

import jakarta.inject.Singleton;
import ru.suo.integration.config.IntegrationGatewayConfiguration;
import ru.suo.integration.domain.QueueItemDto;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простейший in-memory cache c TTL для снижения нагрузки в этапе 1.
 */
@Singleton
public class QueueCache {

    private final IntegrationGatewayConfiguration configuration;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public QueueCache(IntegrationGatewayConfiguration configuration) {
        this(configuration, Clock.systemUTC());
    }

    QueueCache(IntegrationGatewayConfiguration configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    public List<QueueItemDto> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now(clock).isAfter(entry.expiresAt())) {
            cache.remove(key);
            return null;
        }
        return entry.value();
    }

    public void put(String key, List<QueueItemDto> value) {
        Instant expiresAt = Instant.now(clock).plus(configuration.getQueueCacheTtl());
        cache.put(key, new CacheEntry(value, expiresAt));
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    private record CacheEntry(List<QueueItemDto> value, Instant expiresAt) {
    }
}
