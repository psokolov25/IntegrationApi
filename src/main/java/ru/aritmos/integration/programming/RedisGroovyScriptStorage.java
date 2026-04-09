package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.JedisPooled;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Redis-хранилище Groovy-скриптов programmable API.
 */
public class RedisGroovyScriptStorage implements GroovyScriptStorage {

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisGroovyScriptStorage(IntegrationGatewayConfiguration configuration,
                                    ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        IntegrationGatewayConfiguration.RedisScriptStorageSettings redisSettings =
                configuration.getProgrammableApi().getScriptStorage().getRedis();
        this.keyPrefix = redisSettings.getKeyPrefix();
        this.jedis = new JedisPooled(buildRedisUri(redisSettings));
    }

    @Override
    public void save(StoredGroovyScript script) {
        jedis.set(key(script.scriptId()), toJson(script));
    }

    @Override
    public StoredGroovyScript get(String scriptId) {
        String raw = jedis.get(key(scriptId));
        if (raw == null) {
            return null;
        }
        return fromJson(raw);
    }

    @Override
    public boolean delete(String scriptId) {
        return jedis.del(key(scriptId)) > 0;
    }

    @Override
    public List<StoredGroovyScript> list() {
        return jedis.keys(keyPrefix + "*").stream()
                .map(jedis::get)
                .filter(java.util.Objects::nonNull)
                .map(this::fromJson)
                .sorted(java.util.Comparator.comparing(StoredGroovyScript::scriptId))
                .toList();
    }

    private String key(String scriptId) {
        return keyPrefix + scriptId;
    }

    private String toJson(StoredGroovyScript script) {
        try {
            return objectMapper.writeValueAsString(script);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сериализовать Groovy-скрипт", ex);
        }
    }

    private StoredGroovyScript fromJson(String raw) {
        try {
            return objectMapper.readValue(raw, StoredGroovyScript.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось десериализовать Groovy-скрипт", ex);
        }
    }

    private URI buildRedisUri(IntegrationGatewayConfiguration.RedisScriptStorageSettings redisSettings) {
        String passwordPart = redisSettings.getPassword() == null || redisSettings.getPassword().isBlank()
                ? ""
                : ":" + redisSettings.getPassword() + "@";
        return URI.create("redis://" + passwordPart
                + redisSettings.getHost() + ":" + redisSettings.getPort()
                + "/" + redisSettings.getDatabase());
    }
}
