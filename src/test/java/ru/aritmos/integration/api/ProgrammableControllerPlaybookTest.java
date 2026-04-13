package ru.aritmos.integration.api;

import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.eventing.EventInboxService;
import ru.aritmos.integration.eventing.EventOutboxService;
import ru.aritmos.integration.programming.CustomerMessageBusAdapter;
import ru.aritmos.integration.programming.InMemoryGroovyScriptStorage;
import ru.aritmos.integration.programming.ScriptDebugHistoryService;
import ru.aritmos.integration.programming.StudioWorkspaceService;
import ru.aritmos.integration.security.RequestSecurityContext;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ProgrammableControllerPlaybookTest {

    @Test
    void shouldReturnPlaybookFromControllerWithDescendingSort() {
        ProgrammableController controller = controller();
        HttpRequest<?> request = HttpRequest.GET("/api/v1/program/studio/playbook");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("programmable-script-execute")));

        List<Map<String, Object>> result = controller.studioPlaybook(request, "order", "desc", "", "", "", 50, 0);
        Assertions.assertEquals(15, result.size());
        Assertions.assertEquals("gui-ops", result.get(0).get("group"));
        Assertions.assertEquals("connectors-health", result.get(result.size() - 1).get("group"));
    }

    @Test
    void shouldApplyQueryFilterFromController() {
        ProgrammableController controller = controller();
        HttpRequest<?> request = HttpRequest.GET("/api/v1/program/studio/playbook");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("programmable-script-execute")));

        List<Map<String, Object>> result = controller.studioPlaybook(request, "importance", "asc", "", "", "runtime", 50, 0);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.stream().allMatch(item ->
                String.valueOf(item.get("group")).contains("runtime")
                        || String.valueOf(item.get("title")).toLowerCase().contains("runtime")
                        || String.valueOf(item.get("check")).toLowerCase().contains("runtime")
                        || String.valueOf(item.get("api")).toLowerCase().contains("runtime")));
    }

    @Test
    void shouldReturnPlaybookOptionsFromController() {
        ProgrammableController controller = controller();
        HttpRequest<?> request = HttpRequest.GET("/api/v1/program/studio/playbook/options");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("programmable-script-execute")));

        Map<String, Object> result = controller.studioPlaybookOptions(request);
        Assertions.assertEquals(List.of("importance", "order", "group"), result.get("sortBy"));
        Assertions.assertEquals(List.of("asc", "desc"), result.get("sortOrder"));
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) result.get("groups");
        Assertions.assertTrue(groups.contains("branch-state-sync"));
    }

    @Test
    void shouldApplyLimitFromController() {
        ProgrammableController controller = controller();
        HttpRequest<?> request = HttpRequest.GET("/api/v1/program/studio/playbook");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("programmable-script-execute")));

        List<Map<String, Object>> result = controller.studioPlaybook(request, "order", "asc", "", "", "", 3, 0);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("connectors-health", result.get(0).get("group"));
    }

    @Test
    void shouldApplyOffsetFromController() {
        ProgrammableController controller = controller();
        HttpRequest<?> request = HttpRequest.GET("/api/v1/program/studio/playbook");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("programmable-script-execute")));

        List<Map<String, Object>> result = controller.studioPlaybook(request, "order", "asc", "", "", "", 3, 2);
        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("queue-smoke", result.get(0).get("group"));
    }

    @Test
    void shouldReturnPlaybookPageWithPaginationMetadata() {
        ProgrammableController controller = controller();
        HttpRequest<?> request = HttpRequest.GET("/api/v1/program/studio/playbook/page");
        RequestSecurityContext.attach(request, new SubjectPrincipal("ext-user", Set.of("programmable-script-execute")));

        Map<String, Object> page = controller.studioPlaybookPage(request, "order", "asc", "", "", "", 3, 2);
        Assertions.assertEquals(15, page.get("total"));
        Assertions.assertEquals(3, page.get("limit"));
        Assertions.assertEquals(2, page.get("offset"));
        Assertions.assertEquals(true, page.get("hasMore"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        Assertions.assertEquals(3, items.size());
        Assertions.assertEquals("queue-smoke", items.get(0).get("group"));
    }

    private ProgrammableController controller() {
        StudioWorkspaceService studioWorkspaceService = new StudioWorkspaceService(
                new IntegrationGatewayConfiguration(),
                new EventInboxService(),
                new EventOutboxService(),
                new InMemoryGroovyScriptStorage(),
                new ScriptDebugHistoryService(),
                List.of()
        );
        return new ProgrammableController(
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.<CustomerMessageBusAdapter>emptyList(),
                new IntegrationGatewayConfiguration(),
                null,
                new AuthorizationService(),
                new ScriptDebugHistoryService(),
                studioWorkspaceService,
                null,
                null,
                null
        );
    }
}
