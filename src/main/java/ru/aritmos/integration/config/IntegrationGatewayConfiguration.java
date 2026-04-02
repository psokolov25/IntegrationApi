package ru.aritmos.integration.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import ru.aritmos.integration.security.core.SecurityMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Конфигурация шлюза интеграции VisitManager.
 */
@Introspected
@ConfigurationProperties("integration")
public class IntegrationGatewayConfiguration {

    @NotEmpty
    private List<String> apiKeys = new ArrayList<>();

    private SecurityMode securityMode = SecurityMode.API_KEY;

    private String internalSigningKey = "dev-signing-key";

    private List<InternalClient> internalClients = new ArrayList<>();

    private KeycloakSettings keycloak = new KeycloakSettings();

    /**
     * Локальное дообогащение прав (для HYBRID режима): subjectId -> permissions.
     */
    private Map<String, Set<String>> localSubjectPermissions = new HashMap<>();

    private ProgrammableApiSettings programmableApi = new ProgrammableApiSettings();

    private ClientPolicySettings clientPolicy = new ClientPolicySettings();

    private EventingSettings eventing = new EventingSettings();

    private Duration queueCacheTtl = Duration.ofSeconds(30);

    private Duration branchStateCacheTtl = Duration.ofSeconds(15);
    private Duration branchStateEventRefreshDebounce = Duration.ofSeconds(2);

    private List<VisitManagerInstance> visitManagers = new ArrayList<>();

    private Map<String, String> branchRouting = new HashMap<>();

    /**
     * Резервный маршрут branchId -> backup VisitManager id.
     */
    private Map<String, String> branchFallbackRouting = new HashMap<>();

    /**
     * Временная настройка для stub-клиента: какие vm считать недоступными.
     */
    private List<String> simulatedUnavailableTargets = new ArrayList<>();

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public SecurityMode getSecurityMode() {
        return securityMode;
    }

    public void setSecurityMode(SecurityMode securityMode) {
        this.securityMode = securityMode;
    }

    public String getInternalSigningKey() {
        return internalSigningKey;
    }

    public void setInternalSigningKey(String internalSigningKey) {
        this.internalSigningKey = internalSigningKey;
    }

    public List<InternalClient> getInternalClients() {
        return internalClients;
    }

    public void setInternalClients(List<InternalClient> internalClients) {
        this.internalClients = internalClients;
    }

    public KeycloakSettings getKeycloak() {
        return keycloak;
    }

    public void setKeycloak(KeycloakSettings keycloak) {
        this.keycloak = keycloak;
    }

    public Map<String, Set<String>> getLocalSubjectPermissions() {
        return localSubjectPermissions;
    }

    public void setLocalSubjectPermissions(Map<String, Set<String>> localSubjectPermissions) {
        this.localSubjectPermissions = localSubjectPermissions;
    }

    public ProgrammableApiSettings getProgrammableApi() {
        return programmableApi;
    }

    public void setProgrammableApi(ProgrammableApiSettings programmableApi) {
        this.programmableApi = programmableApi;
    }

    public ClientPolicySettings getClientPolicy() {
        return clientPolicy;
    }

    public void setClientPolicy(ClientPolicySettings clientPolicy) {
        this.clientPolicy = clientPolicy;
    }

    public EventingSettings getEventing() {
        return eventing;
    }

    public void setEventing(EventingSettings eventing) {
        this.eventing = eventing;
    }

    public Duration getQueueCacheTtl() {
        return queueCacheTtl;
    }

    public void setQueueCacheTtl(Duration queueCacheTtl) {
        this.queueCacheTtl = queueCacheTtl;
    }

    public Duration getBranchStateCacheTtl() {
        return branchStateCacheTtl;
    }

    public void setBranchStateCacheTtl(Duration branchStateCacheTtl) {
        this.branchStateCacheTtl = branchStateCacheTtl;
    }

    public Duration getBranchStateEventRefreshDebounce() {
        return branchStateEventRefreshDebounce;
    }

    public void setBranchStateEventRefreshDebounce(Duration branchStateEventRefreshDebounce) {
        this.branchStateEventRefreshDebounce = branchStateEventRefreshDebounce;
    }

    public List<VisitManagerInstance> getVisitManagers() {
        return visitManagers;
    }

    public void setVisitManagers(List<VisitManagerInstance> visitManagers) {
        this.visitManagers = visitManagers;
    }

    public Map<String, String> getBranchRouting() {
        return branchRouting;
    }

    public void setBranchRouting(Map<String, String> branchRouting) {
        this.branchRouting = branchRouting;
    }

    public Map<String, String> getBranchFallbackRouting() {
        return branchFallbackRouting;
    }

    public void setBranchFallbackRouting(Map<String, String> branchFallbackRouting) {
        this.branchFallbackRouting = branchFallbackRouting;
    }

    public List<String> getSimulatedUnavailableTargets() {
        return simulatedUnavailableTargets;
    }

    public void setSimulatedUnavailableTargets(List<String> simulatedUnavailableTargets) {
        this.simulatedUnavailableTargets = simulatedUnavailableTargets;
    }

    @Introspected
    public static class EventingSettings {
        private boolean enabled = false;
        private int maxRetries = 2;
        private int maxPayloadFields = 100;
        private long maxFutureSkewSeconds = 300;
        private int dlqWarnThreshold = 10;
        private int duplicateWarnThreshold = 50;
        private int maxDlqEvents = 5000;
        private int maxProcessedEvents = 20000;
        private long retentionSeconds = 604800;
        private int snapshotImportMaxEvents = 50000;
        private boolean snapshotImportRequireMatchingProcessedKeys = true;
        private boolean snapshotImportRejectCrossListDuplicates = true;
        private KafkaSettings kafka = new KafkaSettings();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public KafkaSettings getKafka() {
            return kafka;
        }

        public void setKafka(KafkaSettings kafka) {
            this.kafka = kafka;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getMaxPayloadFields() {
            return maxPayloadFields;
        }

        public void setMaxPayloadFields(int maxPayloadFields) {
            this.maxPayloadFields = maxPayloadFields;
        }

        public long getMaxFutureSkewSeconds() {
            return maxFutureSkewSeconds;
        }

        public void setMaxFutureSkewSeconds(long maxFutureSkewSeconds) {
            this.maxFutureSkewSeconds = maxFutureSkewSeconds;
        }

        public int getDlqWarnThreshold() {
            return dlqWarnThreshold;
        }

        public void setDlqWarnThreshold(int dlqWarnThreshold) {
            this.dlqWarnThreshold = dlqWarnThreshold;
        }

        public int getDuplicateWarnThreshold() {
            return duplicateWarnThreshold;
        }

        public void setDuplicateWarnThreshold(int duplicateWarnThreshold) {
            this.duplicateWarnThreshold = duplicateWarnThreshold;
        }

        public int getMaxDlqEvents() {
            return maxDlqEvents;
        }

        public void setMaxDlqEvents(int maxDlqEvents) {
            this.maxDlqEvents = maxDlqEvents;
        }

        public int getMaxProcessedEvents() {
            return maxProcessedEvents;
        }

        public void setMaxProcessedEvents(int maxProcessedEvents) {
            this.maxProcessedEvents = maxProcessedEvents;
        }

        public long getRetentionSeconds() {
            return retentionSeconds;
        }

        public void setRetentionSeconds(long retentionSeconds) {
            this.retentionSeconds = retentionSeconds;
        }

        public int getSnapshotImportMaxEvents() {
            return snapshotImportMaxEvents;
        }

        public void setSnapshotImportMaxEvents(int snapshotImportMaxEvents) {
            this.snapshotImportMaxEvents = snapshotImportMaxEvents;
        }

        public boolean isSnapshotImportRequireMatchingProcessedKeys() {
            return snapshotImportRequireMatchingProcessedKeys;
        }

        public void setSnapshotImportRequireMatchingProcessedKeys(boolean snapshotImportRequireMatchingProcessedKeys) {
            this.snapshotImportRequireMatchingProcessedKeys = snapshotImportRequireMatchingProcessedKeys;
        }

        public boolean isSnapshotImportRejectCrossListDuplicates() {
            return snapshotImportRejectCrossListDuplicates;
        }

        public void setSnapshotImportRejectCrossListDuplicates(boolean snapshotImportRejectCrossListDuplicates) {
            this.snapshotImportRejectCrossListDuplicates = snapshotImportRejectCrossListDuplicates;
        }
    }

    @Introspected
    public static class KafkaSettings {
        private boolean enabled = false;
        private String inboundTopic = "integration.inbound";
        private String outboundTopic = "integration.outbound";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInboundTopic() {
            return inboundTopic;
        }

        public void setInboundTopic(String inboundTopic) {
            this.inboundTopic = inboundTopic;
        }

        public String getOutboundTopic() {
            return outboundTopic;
        }

        public void setOutboundTopic(String outboundTopic) {
            this.outboundTopic = outboundTopic;
        }
    }

    @Introspected
    public static class ClientPolicySettings {
        private int retryAttempts = 2;
        private long timeoutMillis = 1000;
        private int circuitFailureThreshold = 3;
        private long circuitOpenSeconds = 10;

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        public int getCircuitFailureThreshold() {
            return circuitFailureThreshold;
        }

        public void setCircuitFailureThreshold(int circuitFailureThreshold) {
            this.circuitFailureThreshold = circuitFailureThreshold;
        }

        public long getCircuitOpenSeconds() {
            return circuitOpenSeconds;
        }

        public void setCircuitOpenSeconds(long circuitOpenSeconds) {
            this.circuitOpenSeconds = circuitOpenSeconds;
        }
    }

    @Introspected
    public static class ProgrammableApiSettings {
        private boolean enabled = false;
        private List<ProgrammableEndpoint> endpoints = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<ProgrammableEndpoint> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(List<ProgrammableEndpoint> endpoints) {
            this.endpoints = endpoints;
        }
    }

    @Introspected
    public static class ProgrammableEndpoint {
        @NotBlank
        private String id;
        @NotBlank
        private String operation;
        @NotBlank
        private String requiredPermission;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getRequiredPermission() {
            return requiredPermission;
        }

        public void setRequiredPermission(String requiredPermission) {
            this.requiredPermission = requiredPermission;
        }
    }

    @Introspected
    public static class KeycloakSettings {
        private String issuer = "";
        private String audience = "";
        /**
         * DEV-only shared secret для HS256 проверки.
         */
        private String sharedSecret = "dev-keycloak-secret";

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }
    }

    @Introspected
    public static class InternalClient {
        @NotBlank
        private String clientId;
        @NotBlank
        private String clientSecret;
        private Set<String> permissions = Set.of();

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }
    }

    @Introspected
    public static class VisitManagerInstance {
        @NotBlank
        private String id;
        @NotBlank
        private String baseUrl;
        private String regionId;
        private boolean active = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getRegionId() {
            return regionId;
        }

        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
