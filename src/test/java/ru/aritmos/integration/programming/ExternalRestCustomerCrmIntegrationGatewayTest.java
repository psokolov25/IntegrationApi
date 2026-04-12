package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;

import java.util.List;
import java.util.Map;

class ExternalRestCustomerCrmIntegrationGatewayTest {

    @Test
    void shouldBuildIdentifyClientRequestAndMaskIdentifier() {
        CapturingExternalRestClient client = new CapturingExternalRestClient();
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(client);

        Map<String, Object> result = gateway.identifyClient(new CustomerLookupRequest(
                "customer-crm",
                "phone",
                "+79991234567",
                "",
                Map.of("onlyActive", true),
                Map.of("X-Tenant", "demo")
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
        ExternalRestCustomerCrmIntegrationGateway gateway = new ExternalRestCustomerCrmIntegrationGateway(client);

        gateway.fetchAvailableMedicalServices(new CustomerMedicalServicesRequest(
                "customer-crm",
                "snils",
                "123-456-789 00",
                "/api/v2/services/available",
                List.of("BR-1", "BR-1", "BR-2", " "),
                Map.of("serviceGroup", "LAB"),
                Map.of()
        ));

        Assertions.assertEquals("/api/v2/services/available", client.lastPath);
        Assertions.assertEquals(List.of("BR-1", "BR-2"), client.lastBody.get("branchIds"));
    }

    @Test
    void shouldRejectMissingRequiredIdentifier() {
        ExternalRestCustomerCrmIntegrationGateway gateway =
                new ExternalRestCustomerCrmIntegrationGateway(new CapturingExternalRestClient());
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> gateway.fetchPrebookingData(new CustomerPrebookingRequest(
                        "customer-crm",
                        "inn",
                        " ",
                        null,
                        Map.of(),
                        Map.of()
                )));
        Assertions.assertEquals("identifierValue обязателен", error.getMessage());
    }

    private static final class CapturingExternalRestClient extends ExternalRestClient {
        private String lastServiceId;
        private String lastMethod;
        private String lastPath;
        private Map<String, Object> lastBody = Map.of();

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
            return Map.of("status", 200, "bodyPreview", "{\"ok\":true}");
        }
    }
}

