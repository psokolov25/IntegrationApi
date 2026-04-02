package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;
import ru.aritmos.integration.service.BranchStateCache;
import ru.aritmos.integration.service.GatewayService;
import ru.aritmos.integration.service.QueueCache;
import ru.aritmos.integration.service.RoutingService;
import ru.aritmos.integration.service.VisitManagerMetricsService;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ProgrammableEndpointServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldExecuteFetchQueuesOperation() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getProgrammableApi().setEnabled(true);
        IntegrationGatewayConfiguration.ProgrammableEndpoint endpoint = new IntegrationGatewayConfiguration.ProgrammableEndpoint();
        endpoint.setId("queuesByBranch");
        endpoint.setOperation("FETCH_QUEUES");
        endpoint.setRequiredPermission("queue-view");
        cfg.getProgrammableApi().setEndpoints(List.of(endpoint));

        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-1", "vm-main"));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        ProgrammableEndpointService service = new ProgrammableEndpointService(cfg, gatewayService, new AuthorizationService());
        var subject = new SubjectPrincipal("tester", Set.of("queue-view"));

        Object result = service.execute("queuesByBranch", subject, mapper.valueToTree(Map.of("branchId", "BR-1")));
        Assertions.assertNotNull(result);
    }

    @Test
    void shouldFailWhenPermissionMissing() {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        cfg.getProgrammableApi().setEnabled(true);
        IntegrationGatewayConfiguration.ProgrammableEndpoint endpoint = new IntegrationGatewayConfiguration.ProgrammableEndpoint();
        endpoint.setId("aggregateQueues");
        endpoint.setOperation("AGGREGATE_QUEUES");
        endpoint.setRequiredPermission("queue-aggregate");
        cfg.getProgrammableApi().setEndpoints(List.of(endpoint));

        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        ProgrammableEndpointService service = new ProgrammableEndpointService(cfg, gatewayService, new AuthorizationService());
        var subject = new SubjectPrincipal("tester", Set.of("queue-view"));

        Assertions.assertThrows(SecurityException.class,
                () -> service.execute("aggregateQueues", subject, mapper.valueToTree(Map.of("branchIds", "BR-1"))));
    }
}
