package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerOptimalServiceSelectionRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;
import ru.aritmos.integration.domain.CustomerServiceAvailabilityRequest;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ExternalRestCustomerCrmIntegrationGatewayTest {

    @Test
    void shouldBuildIdentifyClientRequestAndMaskIdentifier() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> result = gateway.identifyClient(new CustomerLookupRequest(
                "customer-crm",
                "phone",
                "+79991234567",
                "",
                Map.of("onlyActive", true),
                Map.of("X-Tenant", "demo"),
                "REST_API",
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of()
        ));

        Assertions.assertEquals("IDENTIFY_CLIENT_IN_EXTERNAL_CRM", result.get("operation"));
        Assertions.assertEquals("PHONE", result.get("identifierType"));
        Assertions.assertEquals("********4567", result.get("identifierValueMasked"));
        Assertions.assertEquals("/api/v1/customers/identify", client.lastPath);
        Assertions.assertEquals("POST", client.lastMethod);
        Assertions.assertEquals("customer-crm", client.lastServiceId);
    }

    @Test
    void shouldBuildMedicalServicesRequestWithDistinctBranches() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        gateway.fetchAvailableMedicalServices(new CustomerMedicalServicesRequest(
                "customer-crm",
                "snils",
                "123-456-789 00",
                "/api/v2/services/available",
                List.of("BR-1", "BR-1", "BR-2", " "),
                Map.of("serviceGroup", "LAB"),
                Map.of(),
                "REST_API",
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of()
        ));

        Assertions.assertEquals("/api/v2/services/available", client.lastPath);
        Assertions.assertEquals(List.of("BR-1", "BR-2"), client.lastBody.get("branchIds"));
    }

    @Test
    void shouldRejectMissingRequiredIdentifier() {
        ExternalRestCustomerCrmIntegrationGateway gateway =
                new ExternalRestCustomerCrmIntegrationGateway(
                        new CapturingExternalRestClient(),
                        new CapturingMessageBusGateway(),
                        new CapturingGroovyScriptService(),
                        new ObjectMapper()
                );
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> gateway.fetchPrebookingData(new CustomerPrebookingRequest(
                        "customer-crm",
                        "inn",
                        " ",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        "REST_API",
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of()
                )));
        Assertions.assertEquals("identifierValue обязателен", error.getMessage());
    }

    @Test
    void shouldUseMessageBrokerConnectorWhenConfigured() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        CapturingMessageBusGateway busGateway = new CapturingMessageBusGateway();
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                busGateway,
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> result = gateway.identifyClient(new CustomerLookupRequest(
                "customer-crm",
                "phone",
                "+79991234567",
                "",
                Map.of(),
                Map.of(),
                "MESSAGE_BROKER",
                "broker-1",
                "crm.lookup",
                "msg-1",
                null,
                Map.of(),
                Map.of()
        ));

        Assertions.assertEquals("MESSAGE_BROKER", result.get("connectorType"));
        Assertions.assertEquals("broker-1", busGateway.lastBrokerId);
        Assertions.assertEquals("crm.lookup", busGateway.lastTopic);
        Assertions.assertEquals("msg-1", busGateway.lastKey);
        Assertions.assertNull(client.lastServiceId);
    }

    @Test
    void shouldApplyGroovyResponseTransform() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        CapturingGroovyScriptService scriptService = new CapturingGroovyScriptService();
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                new CapturingMessageBusGateway(),
                scriptService,
                new ObjectMapper()
        );

        Map<String, Object> result = gateway.fetchPrebookingData(
                new CustomerPrebookingRequest(
                        "customer-crm",
                        "inn",
                        "7707083893",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("source", "ui"),
                        Map.of(),
                        "REST_API",
                        null,
                        null,
                        null,
                        "script-1",
                        Map.of("mode", "strict"),
                        Map.of("traceId", "trace-1")
                ),
                new SubjectPrincipal("user-1", Set.of("programmable-script-execute"))
        );

        Assertions.assertEquals("script-1", result.get("responseScriptId"));
        Assertions.assertTrue(result.containsKey("transformedResult"));
        Assertions.assertEquals("script-1", scriptService.lastScriptId);
    }

    @Test
    void shouldKeepLegacyPrebookingBehaviorByDefault() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> response = gateway.fetchPrebookingData(new CustomerPrebookingRequest(
                "customer-crm",
                "phone",
                "+79991234567",
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                "REST_API",
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of()
        ));

        Assertions.assertEquals("FETCH_PREBOOKING_DATA", response.get("operation"));
        Assertions.assertEquals("LEGACY_PREBOOKING", response.get("queryMode"));
        Assertions.assertEquals(List.of("/api/v1/pre-appointments"), client.invokedPaths);
    }

    @Test
    void shouldReturnUnifiedPrebookingWithTimeWindowsAndServicesWithPrerequisites() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        client.responseByPath = Map.of(
                "/api/v2/prebooking/windows", Map.of(
                        "timeWindows", List.of(
                                Map.of("windowId", "W-1", "start", "2026-04-12T09:00:00Z", "end", "2026-04-12T09:30:00Z", "serviceId", "THERAPIST")
                        )
                ),
                "/api/v2/services/available", Map.of(
                        "services", List.of(
                                Map.of("serviceId", "THERAPIST", "serviceName", "Терапевт", "prerequisiteServiceIds", List.of("CBC")),
                                Map.of("serviceId", "CBC", "serviceName", "Общий анализ крови")
                        )
                )
        );
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> response = gateway.fetchUnifiedPrebookingData(new CustomerPrebookingRequest(
                "customer-crm",
                "phone",
                "+79991234567",
                null,
                "/api/v2/services/available",
                "/api/v2/prebooking/windows",
                "TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES",
                "THERAPIST",
                "BR-1",
                Map.of(),
                Map.of(),
                "REST_API",
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of()
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> windows = (List<Map<String, Object>>) response.get("timeWindows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) response.get("services");

        Assertions.assertEquals(2, client.invokedPaths.size());
        Assertions.assertTrue(client.invokedPaths.contains("/api/v2/prebooking/windows"));
        Assertions.assertTrue(client.invokedPaths.contains("/api/v2/services/available"));
        Assertions.assertEquals(1, windows.size());
        Assertions.assertEquals("W-1", windows.get(0).get("windowId"));
        Assertions.assertEquals(2, services.size());
        Assertions.assertEquals(List.of("CBC"), services.get(0).get("prerequisiteServiceIds"));
    }

    @Test
    void shouldNormalizeServicesWithPrerequisitesFromConnectorResponse() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        client.nextResponse = Map.of(
                "data", Map.of(
                        "services", List.of(
                                Map.of("serviceId", "THERAPIST", "serviceName", "Терапевт", "prerequisiteServiceIds", List.of("CBC", "URINE")),
                                Map.of("serviceId", "CBC", "serviceName", "Общий анализ крови")
                        )
                )
        );
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                client,
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> response = gateway.fetchMedicalServicesWithPrerequisites(new CustomerMedicalServicesRequest(
                "customer-crm",
                "snils",
                "123456",
                null,
                List.of(),
                Map.of(),
                Map.of(),
                "REST_API",
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of()
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) response.get("services");
        Assertions.assertEquals(2, services.size());
        Assertions.assertEquals("THERAPIST", services.get(0).get("serviceId"));
        Assertions.assertEquals(List.of("CBC", "URINE"), services.get(0).get("prerequisiteServiceIds"));
    }

    @Test
    void shouldCalculateEligibleServicesByCompletedServices() {
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                new CapturingExternalRestClient(),
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> response = gateway.calculateEligibleMedicalServices(
                new CustomerServiceAvailabilityRequest(
                        null,
                        "customer-1",
                        List.of("CBC", "URINE"),
                        List.of("THERAPIST", "CBC"),
                        List.of(
                                Map.of("serviceId", "THERAPIST", "serviceName", "Терапевт", "prerequisiteServiceIds", List.of("CBC", "URINE")),
                                Map.of("serviceId", "CBC", "serviceName", "Общий анализ крови", "prerequisiteServiceIds", List.of())
                        )
                )
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eligible = (List<Map<String, Object>>) response.get("eligibleServices");
        Assertions.assertEquals(2, eligible.size());
        Assertions.assertTrue(eligible.stream().anyMatch(item -> "THERAPIST".equals(item.get("serviceId"))));
        Assertions.assertTrue(eligible.stream().allMatch(item -> Boolean.TRUE.equals(item.get("availableNow"))));
    }

    @Test
    void shouldSelectOptimalServiceWithBuiltInFormula() {
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                new CapturingExternalRestClient(),
                new CapturingMessageBusGateway(),
                new CapturingGroovyScriptService(),
                new ObjectMapper()
        );

        Map<String, Object> response = gateway.selectOptimalMedicalService(new CustomerOptimalServiceSelectionRequest(
                "customer-1",
                List.of(
                        Map.of("serviceId", "THERAPIST", "serviceName", "Терапевт", "waitingCount", 4, "standardWaitMinutes", 15),
                        Map.of("serviceId", "CBC", "serviceName", "Общий анализ крови", "waitingCount", 2, "standardWaitMinutes", 10)
                ),
                null,
                Map.of(),
                Map.of()
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> selectedService = (Map<String, Object>) response.get("selectedService");
        Assertions.assertEquals("CBC", selectedService.get("serviceId"));
        Assertions.assertEquals(false, response.get("scriptApplied"));
    }

    @Test
    void shouldSelectOptimalServiceUsingGroovyScript() {
        CapturingGroovyScriptService scriptService = new CapturingGroovyScriptService();
        scriptService.nextExecutionResult = Map.of("selectedServiceId", "THERAPIST");
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(
                new CapturingExternalRestClient(),
                new CapturingMessageBusGateway(),
                scriptService,
                new ObjectMapper()
        );

        Map<String, Object> response = gateway.selectOptimalMedicalService(
                new CustomerOptimalServiceSelectionRequest(
                        "customer-1",
                        List.of(
                                Map.of("serviceId", "THERAPIST", "serviceName", "Терапевт", "waitingCount", 4, "standardWaitMinutes", 15),
                                Map.of("serviceId", "CBC", "serviceName", "Общий анализ крови", "waitingCount", 2, "standardWaitMinutes", 10)
                        ),
                        "optimal-script",
                        Map.of("strategy", "vip"),
                        Map.of("traceId", "trace-1")
                ),
                new SubjectPrincipal("user-1", Set.of("programmable-script-execute"))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> selectedService = (Map<String, Object>) response.get("selectedService");
        Assertions.assertEquals("THERAPIST", selectedService.get("serviceId"));
        Assertions.assertEquals(true, response.get("scriptApplied"));
        Assertions.assertEquals("optimal-script", scriptService.lastScriptId);
    }

    private static final class CapturingExternalRestClient extends ExternalRestClient {
        private String lastServiceId;
        private String lastMethod;
        private String lastPath;
        private Map<String, Object> lastBody = Map.of();
        private Map<String, Object> nextResponse = Map.of("status", 200, "bodyPreview", "{\"ok\":true}");
        private Map<String, Map<String, Object>> responseByPath = Map.of();
        private List<String> invokedPaths = new java.util.ArrayList<>();

        CapturingExternalRestClient() {
            super(new IntegrationGatewayConfiguration(), new ObjectMapper());
        }

        @Override
        public Map<String, Object> invoke(String serviceId,
                                          String method,
                                          String path,
                                          Map<String, Object> body,
                                          Map<String, String> headers) {
            this.lastServiceId = serviceId;
            this.lastMethod = method;
            this.lastPath = path;
            this.lastBody = body == null ? Map.of() : body;
            this.invokedPaths.add(path);
            if (responseByPath.containsKey(path)) {
                return responseByPath.get(path);
            }
            return nextResponse;
        }
    }

    private static final class CapturingMessageBusGateway extends CustomerMessageBusGateway {
        private String lastBrokerId;
        private String lastTopic;
        private String lastKey;

        CapturingMessageBusGateway() {
            super(new IntegrationGatewayConfiguration(), List.of());
        }

        @Override
        public Map<String, Object> publish(String brokerId,
                                           String topic,
                                           String key,
                                           Map<String, Object> payload,
                                           Map<String, String> headers) {
            this.lastBrokerId = brokerId;
            this.lastTopic = topic;
            this.lastKey = key;
            return Map.of("status", "ACCEPTED");
        }
    }

    private static final class CapturingGroovyScriptService extends GroovyScriptService {
        private String lastScriptId;
        private Object nextExecutionResult;

        CapturingGroovyScriptService() {
            super(
                    new InMemoryGroovyScriptStorage(),
                    new IntegrationGatewayConfiguration(),
                    null,
                    null,
                    null,
                    null,
                    new AuthorizationService(),
                    new ObjectMapper()
            );
        }

        @Override
        public Object executeAdvanced(String scriptId,
                                      com.fasterxml.jackson.databind.JsonNode payload,
                                      Map<String, Object> parameters,
                                      Map<String, Object> context,
                                      SubjectPrincipal subject) {
            this.lastScriptId = scriptId;
            if (nextExecutionResult != null) {
                return nextExecutionResult;
            }
            return Map.of("ok", true, "scriptId", scriptId, "parameters", parameters, "context", context);
        }
    }
}
