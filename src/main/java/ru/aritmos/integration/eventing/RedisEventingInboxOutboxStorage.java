package ru.aritmos.integration.eventing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.JedisPooled;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Redis-хранилище inbox/outbox для восстановления состояния после рестарта.
 */
public class RedisEventingInboxOutboxStorage implements EventingInboxOutboxStorage {

    private static final TypeReference<Map<String, EventInboxService.InboxEntry>> INBOX_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, EventOutboxMessage>> OUTBOX_TYPE = new TypeReference<>() {
    };

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;
    private final String inboxKey;
    private final String outboxKey;
    private final String runtimeSettingsKey;

    public RedisEventingInboxOutboxStorage(IntegrationGatewayConfiguration.EventingStorageRedisSettings redisSettings,
                                           ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jedis = new JedisPooled(buildRedisUri(redisSettings));
        this.inboxKey = redisSettings.getKeyPrefix() + "inbox";
        this.outboxKey = redisSettings.getKeyPrefix() + "outbox";
        this.runtimeSettingsKey = redisSettings.getKeyPrefix() + "runtime-settings";
    }

    @Override
    public synchronized Map<String, EventInboxService.InboxEntry> loadInbox() {
        return fromJson(jedis.get(inboxKey), INBOX_TYPE);
    }

    @Override
    public synchronized void saveInbox(Map<String, EventInboxService.InboxEntry> snapshot) {
        jedis.set(inboxKey, toJson(snapshot));
    }

    @Override
    public synchronized Map<String, EventOutboxMessage> loadOutbox() {
        return fromJson(jedis.get(outboxKey), OUTBOX_TYPE);
    }

    @Override
    public synchronized void saveOutbox(Map<String, EventOutboxMessage> snapshot) {
        jedis.set(outboxKey, toJson(snapshot));
    }

    @Override
    public synchronized Map<String, Object> loadRuntimeSettings() {
        return fromJson(jedis.get(runtimeSettingsKey), new TypeReference<Map<String, Object>>() {
        });
    }

    @Override
    public synchronized void saveRuntimeSettings(Map<String, Object> snapshot) {
        jedis.set(runtimeSettingsKey, toJson(snapshot));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сериализовать inbox/outbox snapshot для Redis", ex);
        }
    }

    private <T> Map<String, T> fromJson(String raw, TypeReference<Map<String, T>> type) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось десериализовать inbox/outbox snapshot из Redis", ex);
        }
    }

    private URI buildRedisUri(IntegrationGatewayConfiguration.EventingStorageRedisSettings redisSettings) {
        String passwordPart = redisSettings.getPassword() == null || redisSettings.getPassword().isBlank()
                ? ""
                : ":" + redisSettings.getPassword() + "@";
        return URI.create("redis://" + passwordPart
                + redisSettings.getHost() + ":" + redisSettings.getPort()
                + "/" + redisSettings.getDatabase());
    }
}
