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
    private AnonymousAccessSettings anonymousAccess = new AnonymousAccessSettings();

    /**
     * Локальное дообогащение прав (для HYBRID режима): subjectId -> permissions.
     */
    private Map<String, Set<String>> localSubjectPermissions = new HashMap<>();

    private ProgrammableApiSettings programmableApi = new ProgrammableApiSettings();

    private ClientPolicySettings clientPolicy = new ClientPolicySettings();

    private EventingSettings eventing = new EventingSettings();

    private Duration queueCacheTtl = Duration.ofSeconds(30);
    private int aggregateMaxBranches = 200;
    private long aggregateRequestTimeoutMillis = 3000;

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

    public AnonymousAccessSettings getAnonymousAccess() {
        return anonymousAccess;
    }

    public void setAnonymousAccess(AnonymousAccessSettings anonymousAccess) {
        this.anonymousAccess = anonymousAccess;
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

    public int getAggregateMaxBranches() {
        return aggregateMaxBranches;
    }

    public void setAggregateMaxBranches(int aggregateMaxBranches) {
        this.aggregateMaxBranches = aggregateMaxBranches;
    }

    public long getAggregateRequestTimeoutMillis() {
        return aggregateRequestTimeoutMillis;
    }

    public void setAggregateRequestTimeoutMillis(long aggregateRequestTimeoutMillis) {
        this.aggregateRequestTimeoutMillis = aggregateRequestTimeoutMillis;
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
        private boolean statePersistenceEnabled = true;
        private String statePersistencePath = "cache/eventing-state/snapshot.json";
        private int outboxBackoffSeconds = 5;
        private int outboxMaxAttempts = 20;
        private int inboxProcessingTimeoutSeconds = 120;
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
        private EntityChangedBranchMappingSettings entityChangedBranchMapping = new EntityChangedBranchMappingSettings();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isStatePersistenceEnabled() {
            return statePersistenceEnabled;
        }

        public void setStatePersistenceEnabled(boolean statePersistenceEnabled) {
            this.statePersistenceEnabled = statePersistenceEnabled;
        }

        public String getStatePersistencePath() {
            return statePersistencePath;
        }

        public void setStatePersistencePath(String statePersistencePath) {
            this.statePersistencePath = statePersistencePath;
        }

        public int getOutboxBackoffSeconds() {
            return outboxBackoffSeconds;
        }

        public void setOutboxBackoffSeconds(int outboxBackoffSeconds) {
            this.outboxBackoffSeconds = outboxBackoffSeconds;
        }

        public int getOutboxMaxAttempts() {
            return outboxMaxAttempts;
        }

        public void setOutboxMaxAttempts(int outboxMaxAttempts) {
            this.outboxMaxAttempts = outboxMaxAttempts;
        }

        public int getInboxProcessingTimeoutSeconds() {
            return inboxProcessingTimeoutSeconds;
        }

        public void setInboxProcessingTimeoutSeconds(int inboxProcessingTimeoutSeconds) {
            this.inboxProcessingTimeoutSeconds = inboxProcessingTimeoutSeconds;
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

        public EntityChangedBranchMappingSettings getEntityChangedBranchMapping() {
            return entityChangedBranchMapping;
        }

        public void setEntityChangedBranchMapping(EntityChangedBranchMappingSettings entityChangedBranchMapping) {
            this.entityChangedBranchMapping = entityChangedBranchMapping;
        }
    }

    @Introspected
    public static class AnonymousAccessSettings {
        private boolean enabled = false;
        private String subjectId = "anonymous-debug";
        private Set<String> permissions = Set.of(
                "queue-view", "queue-call", "queue-aggregate", "metrics-view",
                "event-process", "branch-state-view", "branch-state-manage",
                "programmable-script-manage", "programmable-script-execute"
        );

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(String subjectId) {
            this.subjectId = subjectId;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }
    }

    @Introspected
    public static class EntityChangedBranchMappingSettings {
        private boolean enabled = true;
        private String eventType = "ENTITY_CHANGED";
        private List<String> classNamePaths = List.of(
                "className",
                "class",
                "entityClass",
                "data.class",
                "data.entityClass",
                "data.entity.class",
                "meta.class",
                "meta.entityClass"
        );
        private List<String> acceptedClassNames = List.of("Branch");
        private List<String> branchIdPaths = List.of(
                "newValue.id",
                "oldValue.id",
                "data.branch.id",
                "data.branchId",
                "data.branch_id",
                "data.entity.id",
                "data.entity.branchId",
                "branch.id",
                "branchId",
                "branch_id"
        );
        private List<String> statusPaths = List.of(
                "newValue.status",
                "oldValue.status",
                "data.state.status",
                "data.state.code",
                "data.status",
                "data.entity.state.status",
                "data.entity.status",
                "status",
                "state"
        );
        private List<String> activeWindowPaths = List.of(
                "newValue.activeWindow",
                "newValue.resetTime",
                "oldValue.activeWindow",
                "oldValue.resetTime",
                "data.state.activeWindow",
                "data.state.active_window",
                "data.activeWindow",
                "data.entity.state.activeWindow",
                "data.entity.activeWindow",
                "activeWindow",
                "active_window"
        );
        private List<String> queueSizePaths = List.of(
                "newValue.queueSize",
                "oldValue.queueSize",
                "data.state.queueSize",
                "data.queueSize",
                "data.entity.state.queueSize",
                "data.entity.queueSize",
                "queueSize",
                "queue_size"
        );
        private List<String> updatedAtPaths = List.of(
                "data.state.updatedAt",
                "data.updatedAt",
                "data.entity.state.updatedAt",
                "data.entity.updatedAt",
                "updatedAt",
                "updated_at"
        );
        private List<String> updatedByPaths = List.of(
                "data.state.updatedBy",
                "data.updatedBy",
                "data.entity.state.updatedBy",
                "data.entity.updatedBy",
                "updatedBy",
                "updated_by"
        );
        private List<String> visitManagerIdPaths = List.of(
                "meta.visitManagerId",
                "metadata.visitManagerId",
                "data.visitManagerId",
                "data.targetVisitManagerId",
                "visitManagerId",
                "targetVisitManagerId",
                "target_visit_manager_id"
        );
        /**
         * Ключи «оберток», через которые разрешено рекурсивно продолжать path-резолвинг.
         */
        private List<String> wrapperKeys = List.of(
                "data", "payload", "entity", "entities", "event", "message", "content", "body",
                "detail", "item", "items", "result", "snapshot", "newValue", "oldValue", "branch"
        );
        /**
         * Корневые пути branch-snapshot для fallback-расчета queueSize.
         */
        private List<String> queueSnapshotRoots = List.of(
                "newValue", "data.entity", "data.branch", "data", "oldValue"
        );
        /**
         * Варианты ключей контейнера service points.
         */
        private List<String> servicePointsKeys = List.of(
                "servicePoints", "service_points", "windows", "serviceWindows"
        );
        /**
         * Варианты ключей контейнера очередей.
         */
        private List<String> queuesKeys = List.of(
                "queues", "queueMap", "queue_map"
        );
        /**
         * Варианты ключей коллекций визитов.
         */
        private List<String> visitsKeys = List.of(
                "visits", "visitList", "visit_list"
        );

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public List<String> getClassNamePaths() {
            return classNamePaths;
        }

        public void setClassNamePaths(List<String> classNamePaths) {
            this.classNamePaths = classNamePaths;
        }

        public List<String> getAcceptedClassNames() {
            return acceptedClassNames;
        }

        public void setAcceptedClassNames(List<String> acceptedClassNames) {
            this.acceptedClassNames = acceptedClassNames;
        }

        public List<String> getBranchIdPaths() {
            return branchIdPaths;
        }

        public void setBranchIdPaths(List<String> branchIdPaths) {
            this.branchIdPaths = branchIdPaths;
        }

        public List<String> getStatusPaths() {
            return statusPaths;
        }

        public void setStatusPaths(List<String> statusPaths) {
            this.statusPaths = statusPaths;
        }

        public List<String> getActiveWindowPaths() {
            return activeWindowPaths;
        }

        public void setActiveWindowPaths(List<String> activeWindowPaths) {
            this.activeWindowPaths = activeWindowPaths;
        }

        public List<String> getQueueSizePaths() {
            return queueSizePaths;
        }

        public void setQueueSizePaths(List<String> queueSizePaths) {
            this.queueSizePaths = queueSizePaths;
        }

        public List<String> getUpdatedAtPaths() {
            return updatedAtPaths;
        }

        public void setUpdatedAtPaths(List<String> updatedAtPaths) {
            this.updatedAtPaths = updatedAtPaths;
        }

        public List<String> getUpdatedByPaths() {
            return updatedByPaths;
        }

        public void setUpdatedByPaths(List<String> updatedByPaths) {
            this.updatedByPaths = updatedByPaths;
        }

        public List<String> getVisitManagerIdPaths() {
            return visitManagerIdPaths;
        }

        public void setVisitManagerIdPaths(List<String> visitManagerIdPaths) {
            this.visitManagerIdPaths = visitManagerIdPaths;
        }

        public List<String> getWrapperKeys() {
            return wrapperKeys;
        }

        public void setWrapperKeys(List<String> wrapperKeys) {
            this.wrapperKeys = wrapperKeys;
        }

        public List<String> getQueueSnapshotRoots() {
            return queueSnapshotRoots;
        }

        public void setQueueSnapshotRoots(List<String> queueSnapshotRoots) {
            this.queueSnapshotRoots = queueSnapshotRoots;
        }

        public List<String> getServicePointsKeys() {
            return servicePointsKeys;
        }

        public void setServicePointsKeys(List<String> servicePointsKeys) {
            this.servicePointsKeys = servicePointsKeys;
        }

        public List<String> getQueuesKeys() {
            return queuesKeys;
        }

        public void setQueuesKeys(List<String> queuesKeys) {
            this.queuesKeys = queuesKeys;
        }

        public List<String> getVisitsKeys() {
            return visitsKeys;
        }

        public void setVisitsKeys(List<String> visitsKeys) {
            this.visitsKeys = visitsKeys;
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
        private ScriptStorageSettings scriptStorage = new ScriptStorageSettings();
        private List<ExternalRestServiceSettings> externalRestServices = new ArrayList<>();
        private List<MessageBrokerSettings> messageBrokers = new ArrayList<>();
        private List<MessageReactionRouteSettings> messageReactions = new ArrayList<>();

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

        public ScriptStorageSettings getScriptStorage() {
            return scriptStorage;
        }

        public void setScriptStorage(ScriptStorageSettings scriptStorage) {
            this.scriptStorage = scriptStorage;
        }

        public List<ExternalRestServiceSettings> getExternalRestServices() {
            return externalRestServices;
        }

        public void setExternalRestServices(List<ExternalRestServiceSettings> externalRestServices) {
            this.externalRestServices = externalRestServices;
        }

        public List<MessageBrokerSettings> getMessageBrokers() {
            return messageBrokers;
        }

        public void setMessageBrokers(List<MessageBrokerSettings> messageBrokers) {
            this.messageBrokers = messageBrokers;
        }

        public List<MessageReactionRouteSettings> getMessageReactions() {
            return messageReactions;
        }

        public void setMessageReactions(List<MessageReactionRouteSettings> messageReactions) {
            this.messageReactions = messageReactions;
        }
    }

    @Introspected
    public static class ScriptStorageSettings {
        private RedisScriptStorageSettings redis = new RedisScriptStorageSettings();
        private FileScriptStorageSettings file = new FileScriptStorageSettings();

        public RedisScriptStorageSettings getRedis() {
            return redis;
        }

        public void setRedis(RedisScriptStorageSettings redis) {
            this.redis = redis;
        }

        public FileScriptStorageSettings getFile() {
            return file;
        }

        public void setFile(FileScriptStorageSettings file) {
            this.file = file;
        }
    }

    @Introspected
    public static class FileScriptStorageSettings {
        private boolean enabled = true;
        private String path = "cache/program-scripts";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    @Introspected
    public static class RedisScriptStorageSettings {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 6379;
        private int database = 0;
        private String password = "";
        private String keyPrefix = "integration:groovy:script:";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    @Introspected
    public static class ExternalRestServiceSettings {
        @NotBlank
        private String id;
        @NotBlank
        private String baseUrl;
        private Map<String, String> defaultHeaders = new HashMap<>();

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

        public Map<String, String> getDefaultHeaders() {
            return defaultHeaders;
        }

        public void setDefaultHeaders(Map<String, String> defaultHeaders) {
            this.defaultHeaders = defaultHeaders;
        }
    }

    @Introspected
    public static class MessageBrokerSettings {
        @NotBlank
        private String id;
        @NotBlank
        private String type = "LOGGING";
        private boolean enabled = true;
        private Map<String, String> properties = new HashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }

    @Introspected
    public static class MessageReactionRouteSettings {
        @NotBlank
        private String brokerId;
        @NotBlank
        private String topic = "*";
        @NotBlank
        private String scriptId;
        private boolean enabled = true;

        public String getBrokerId() {
            return brokerId;
        }

        public void setBrokerId(String brokerId) {
            this.brokerId = brokerId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getScriptId() {
            return scriptId;
        }

        public void setScriptId(String scriptId) {
            this.scriptId = scriptId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
