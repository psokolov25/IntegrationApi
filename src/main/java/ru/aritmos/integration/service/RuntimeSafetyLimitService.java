package ru.aritmos.integration.service;

import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

/**
 * Защитный сервис, который на старте оценивает аппаратные ресурсы и ограничивает
 * наиболее рискованные параметры runtime (fan-out/flush/payload), чтобы снизить
 * вероятность подвисания текущего и соседних сервисов.
 */
@Singleton
public class RuntimeSafetyLimitService {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeSafetyLimitService.class);
    private static final long ONE_GB = 1024L * 1024L * 1024L;

    private final IntegrationGatewayConfiguration configuration;
    private final RuntimeHardwareProbe hardwareProbe;

    private volatile String profile = "UNKNOWN";
    private volatile boolean limited;

    public RuntimeSafetyLimitService(IntegrationGatewayConfiguration configuration,
                                     RuntimeHardwareProbe hardwareProbe) {
        this.configuration = configuration;
        this.hardwareProbe = hardwareProbe;
    }

    @EventListener
    void onStartup(StartupEvent ignored) {
        applyLimits();
    }

    void applyLimits() {
        int cpu = Math.max(1, hardwareProbe.availableProcessors());
        long memory = Math.max(0, hardwareProbe.maxMemoryBytes());
        HardwareProfile hardwareProfile = detectProfile(cpu, memory);
        this.profile = hardwareProfile.name();

        int aggregateCap = switch (hardwareProfile) {
            case LOW -> 50;
            case MEDIUM -> 120;
            case HIGH -> 250;
        };
        int outboxBatchCap = switch (hardwareProfile) {
            case LOW -> 20;
            case MEDIUM -> 50;
            case HIGH -> 100;
        };
        int payloadCap = switch (hardwareProfile) {
            case LOW -> 60;
            case MEDIUM -> 120;
            case HIGH -> 200;
        };
        long aggregateTimeoutCap = switch (hardwareProfile) {
            case LOW -> 2_500L;
            case MEDIUM -> 4_000L;
            case HIGH -> 10_000L;
        };

        int originalAggregateMaxBranches = configuration.getAggregateMaxBranches();
        int originalOutboxBatch = configuration.getEventing().getOutboxAutoFlushBatchSize();
        int originalPayload = configuration.getEventing().getMaxPayloadFields();
        long originalAggregateTimeout = configuration.getAggregateRequestTimeoutMillis();

        int effectiveAggregateMaxBranches = clampPositive(originalAggregateMaxBranches, aggregateCap);
        int effectiveOutboxBatch = clampPositive(originalOutboxBatch, outboxBatchCap);
        int effectivePayload = clampPositive(originalPayload, payloadCap);
        long effectiveAggregateTimeout = clampPositive(originalAggregateTimeout, aggregateTimeoutCap);

        configuration.setAggregateMaxBranches(effectiveAggregateMaxBranches);
        configuration.getEventing().setOutboxAutoFlushBatchSize(effectiveOutboxBatch);
        configuration.getEventing().setMaxPayloadFields(effectivePayload);
        configuration.setAggregateRequestTimeoutMillis(effectiveAggregateTimeout);

        this.limited = originalAggregateMaxBranches != effectiveAggregateMaxBranches
                || originalOutboxBatch != effectiveOutboxBatch
                || originalPayload != effectivePayload
                || originalAggregateTimeout != effectiveAggregateTimeout;

        if (limited) {
            LOG.warn("RUNTIME_SAFETY_LIMITS_APPLIED profile={} cpu={} maxMemoryMb={} aggregateMaxBranches={}=>{} outboxBatch={}=>{} maxPayloadFields={}=>{} aggregateTimeout={}=>{}",
                    hardwareProfile.name(),
                    cpu,
                    memory / (1024 * 1024),
                    originalAggregateMaxBranches,
                    effectiveAggregateMaxBranches,
                    originalOutboxBatch,
                    effectiveOutboxBatch,
                    originalPayload,
                    effectivePayload,
                    originalAggregateTimeout,
                    effectiveAggregateTimeout);
        } else {
            LOG.info("RUNTIME_SAFETY_LIMITS_OK profile={} cpu={} maxMemoryMb={} (no clamps)",
                    hardwareProfile.name(), cpu, memory / (1024 * 1024));
        }
    }

    public String readinessStatus() {
        return limited ? "DEGRADED" : "UP";
    }

    public String profile() {
        return profile;
    }

    public boolean limited() {
        return limited;
    }

    private HardwareProfile detectProfile(int cpu, long maxMemoryBytes) {
        if (cpu <= 2 || maxMemoryBytes <= 2 * ONE_GB) {
            return HardwareProfile.LOW;
        }
        if (cpu <= 4 || maxMemoryBytes <= 4 * ONE_GB) {
            return HardwareProfile.MEDIUM;
        }
        return HardwareProfile.HIGH;
    }

    private int clampPositive(int configured, int cap) {
        int normalized = configured <= 0 ? 1 : configured;
        return Math.min(normalized, Math.max(1, cap));
    }

    private long clampPositive(long configured, long cap) {
        long normalized = configured <= 0 ? 500 : configured;
        return Math.min(normalized, Math.max(500L, cap));
    }

    private enum HardwareProfile {
        LOW, MEDIUM, HIGH
    }
}
