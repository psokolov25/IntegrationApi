package ru.aritmos.integration.api;

import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.audit.AuditService;
import ru.aritmos.integration.client.StubVisitManagerClient;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.RequestSecurityContext;
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

class GatewayControllerTest {

    @Test
    void shouldRejectEmptyBranchIdsAfterNormalization() {
        GatewayController controller = controllerWithSingleVm(200);
        HttpRequest<?> request = HttpRequest.GET("/api/queues/aggregate");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("queue-aggregate")));

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.aggregateQueues(request, " ,  , "));
        Assertions.assertEquals("Необходимо передать хотя бы один branchId в параметре branchIds", ex.getMessage());
    }

    @Test
    void shouldAggregateQueuesForNormalizedBranchIds() {
        GatewayController controller = controllerWithSingleVm(200);
        HttpRequest<?> request = HttpRequest.GET("/api/queues/aggregate");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("queue-aggregate")));

        var response = controller.aggregateQueues(request, " BR-1 , BR-1 , BR-2 ");
        Assertions.assertFalse(response.partial());
        Assertions.assertEquals(List.of("BR-1", "BR-2"),
                response.successful().stream().map(item -> item.branchId()).toList());
    }


    @Test
    void shouldRejectWhenBranchIdsCountExceedsConfiguredLimit() {
        GatewayController controller = controllerWithSingleVm(2);
        HttpRequest<?> request = HttpRequest.GET("/api/queues/aggregate");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("queue-aggregate")));

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.aggregateQueues(request, "BR-1,BR-2,BR-3"));
        Assertions.assertEquals("Количество branchIds превышает лимит 2", ex.getMessage());
    }

    @Test
    void shouldApplyLimitAfterDeduplication() {
        GatewayController controller = controllerWithSingleVm(2);
        HttpRequest<?> request = HttpRequest.GET("/api/queues/aggregate");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("queue-aggregate")));

        var response = controller.aggregateQueues(request, "BR-1, BR-1, BR-2, BR-2");
        Assertions.assertFalse(response.partial());
        Assertions.assertEquals(List.of("BR-1", "BR-2"),
                response.successful().stream().map(item -> item.branchId()).toList());
    }

    private GatewayController controllerWithSingleVm(int aggregateMaxBranches) {
        IntegrationGatewayConfiguration cfg = new IntegrationGatewayConfiguration();
        IntegrationGatewayConfiguration.VisitManagerInstance vm = new IntegrationGatewayConfiguration.VisitManagerInstance();
        vm.setId("vm-main");
        vm.setBaseUrl("http://localhost");
        vm.setActive(true);
        cfg.setVisitManagers(List.of(vm));
        cfg.setBranchRouting(Map.of("BR-1", "vm-main", "BR-2", "vm-main", "BR-3", "vm-main"));
        cfg.setAggregateMaxBranches(aggregateMaxBranches);

        GatewayService gatewayService = new GatewayService(
                new RoutingService(cfg),
                new StubVisitManagerClient(cfg),
                new QueueCache(cfg),
                new BranchStateCache(cfg),
                new AuditService(),
                new VisitManagerMetricsService()
        );

        return new GatewayController(gatewayService, new VisitManagerMetricsService(), new AuthorizationService(), cfg);
    }
}
