package ru.aritmos.integration.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;

class RuntimeSafetyLimitServiceTest {

    @Test
    void shouldClampLimitsForLowHardwareProfile() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.setAggregateMaxBranches(500);
        configuration.setAggregateRequestTimeoutMillis(15_000);
        configuration.getEventing().setOutboxAutoFlushBatchSize(300);
        configuration.getEventing().setMaxPayloadFields(500);

        RuntimeSafetyLimitService service = new RuntimeSafetyLimitService(configuration, new StubHardwareProbe(2, 1_500_000_000L));
        service.applyLimits();

        Assertions.assertEquals(50, configuration.getAggregateMaxBranches());
        Assertions.assertEquals(2_500L, configuration.getAggregateRequestTimeoutMillis());
        Assertions.assertEquals(20, configuration.getEventing().getOutboxAutoFlushBatchSize());
        Assertions.assertEquals(60, configuration.getEventing().getMaxPayloadFields());
        Assertions.assertEquals("DEGRADED", service.readinessStatus());
        Assertions.assertEquals("LOW", service.profile());
        Assertions.assertTrue(service.limited());
    }

    @Test
    void shouldKeepConfiguredLimitsWhenWithinHardwareCaps() {
        IntegrationGatewayConfiguration configuration = new IntegrationGatewayConfiguration();
        configuration.setAggregateMaxBranches(80);
        configuration.setAggregateRequestTimeoutMillis(2500);
        configuration.getEventing().setOutboxAutoFlushBatchSize(20);
        configuration.getEventing().setMaxPayloadFields(80);

        RuntimeSafetyLimitService service = new RuntimeSafetyLimitService(configuration, new StubHardwareProbe(8, 8_000_000_000L));
        service.applyLimits();

        Assertions.assertEquals(80, configuration.getAggregateMaxBranches());
        Assertions.assertEquals(2500L, configuration.getAggregateRequestTimeoutMillis());
        Assertions.assertEquals(20, configuration.getEventing().getOutboxAutoFlushBatchSize());
        Assertions.assertEquals(80, configuration.getEventing().getMaxPayloadFields());
        Assertions.assertEquals("UP", service.readinessStatus());
        Assertions.assertEquals("HIGH", service.profile());
        Assertions.assertFalse(service.limited());
    }

    private static final class StubHardwareProbe extends RuntimeHardwareProbe {
        private final int cpu;
        private final long memory;

        private StubHardwareProbe(int cpu, long memory) {
            this.cpu = cpu;
            this.memory = memory;
        }

        @Override
        public int availableProcessors() {
            return cpu;
        }

        @Override
        public long maxMemoryBytes() {
            return memory;
        }
    }
}
