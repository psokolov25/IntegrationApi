package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import jakarta.inject.Singleton;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;
import ru.aritmos.integration.service.GatewayService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис управления Groovy-скриптами в Redis и их выполнения.
 */
@Singleton
public class GroovyScriptService {

    private final GroovyScriptStorage storage;
    private final IntegrationGatewayConfiguration configuration;
    private final GatewayService gatewayService;
    private final VisitManagerRestInvoker visitManagerRestInvoker;
    private final ExternalRestClient externalRestClient;
    private final CustomerMessageBusGateway messageBusGateway;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public GroovyScriptService(GroovyScriptStorage storage,
                               IntegrationGatewayConfiguration configuration,
                               GatewayService gatewayService,
                               VisitManagerRestInvoker visitManagerRestInvoker,
                               ExternalRestClient externalRestClient,
                               CustomerMessageBusGateway messageBusGateway,
                               AuthorizationService authorizationService,
                               ObjectMapper objectMapper) {
        this.storage = storage;
        this.configuration = configuration;
        this.gatewayService = gatewayService;
        this.visitManagerRestInvoker = visitManagerRestInvoker;
        this.externalRestClient = externalRestClient;
        this.messageBusGateway = messageBusGateway;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    public StoredGroovyScript save(String scriptId,
                                   GroovyScriptType type,
                                   String scriptBody,
                                   String description,
                                   SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        if (scriptBody == null || scriptBody.isBlank()) {
            throw new IllegalArgumentException("scriptBody обязателен");
        }
        StoredGroovyScript script = new StoredGroovyScript(
                scriptId,
                type,
                scriptBody,
                description == null ? "" : description,
                Instant.now(),
                subject.subjectId()
        );
        storage.save(script);
        return script;
    }

    public StoredGroovyScript get(String scriptId, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return storage.get(scriptId);
    }

    public boolean delete(String scriptId, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        return storage.delete(scriptId);
    }

    public Object execute(String scriptId,
                          JsonNode payload,
                          SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-execute");
        StoredGroovyScript script = storage.get(scriptId);
        if (script == null) {
            throw new IllegalArgumentException("Groovy script не найден: " + scriptId);
        }
        Binding binding = new Binding();
        Map<String, Object> input = payload == null
                ? Map.of()
                : objectMapper.convertValue(payload, Map.class);
        binding.setVariable("input", input);
        binding.setVariable("subject", subject.subjectId());
        binding.setVariable("externalRestClient", externalRestClient);
        binding.setVariable("messageBusGateway", messageBusGateway);
        switch (script.type()) {
            case BRANCH_CACHE_QUERY -> {
                binding.setVariable("branchStateSnapshot", gatewayService.branchStateSnapshot());
                binding.setVariable("getBranchState", (java.util.function.BiFunction<String, String, Object>)
                        (branchId, target) -> gatewayService.getBranchState(subject.subjectId(), branchId, target == null ? "" : target));
            }
            case VISIT_MANAGER_ACTION -> binding.setVariable("visitManagerInvoker", visitManagerRestInvoker);
            default -> throw new IllegalArgumentException("Неподдерживаемый script type");
        }

        Object result = new GroovyShell(binding).evaluate(script.scriptBody());
        if (result instanceof Map<?, ?> map) {
            return new HashMap<>(map);
        }
        return result;
    }

    /**
     * Реакция Groovy-скриптов на входящие сообщения шины/брокера заказчика.
     */
    public List<Object> reactOnIncomingMessage(String brokerId,
                                               String topic,
                                               String key,
                                               Map<String, Object> payload,
                                               Map<String, String> headers,
                                               String explicitScriptId,
                                               SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-execute");
        List<String> scriptsToRun = resolveReactionScripts(brokerId, topic, explicitScriptId);
        List<Object> results = new ArrayList<>();
        for (String scriptId : scriptsToRun) {
            StoredGroovyScript script = storage.get(scriptId);
            if (script == null) {
                continue;
            }
            if (script.type() != GroovyScriptType.MESSAGE_BUS_REACTION) {
                throw new IllegalArgumentException("Script " + scriptId + " должен иметь тип MESSAGE_BUS_REACTION");
            }
            Binding binding = new Binding();
            Map<String, Object> messageInput = new HashMap<>();
            messageInput.put("brokerId", brokerId == null ? "" : brokerId);
            messageInput.put("topic", topic == null ? "" : topic);
            messageInput.put("key", key == null ? "" : key);
            messageInput.put("payload", payload == null ? Map.of() : payload);
            messageInput.put("headers", headers == null ? Map.of() : headers);
            binding.setVariable("input", messageInput);
            binding.setVariable("subject", subject.subjectId());
            binding.setVariable("externalRestClient", externalRestClient);
            binding.setVariable("messageBusGateway", messageBusGateway);
            binding.setVariable("visitManagerInvoker", visitManagerRestInvoker);
            Object result = new GroovyShell(binding).evaluate(script.scriptBody());
            results.add(result);
        }
        return results;
    }

    private List<String> resolveReactionScripts(String brokerId, String topic, String explicitScriptId) {
        if (explicitScriptId != null && !explicitScriptId.isBlank()) {
            return List.of(explicitScriptId);
        }
        return configuration.getProgrammableApi()
                .getMessageReactions()
                .stream()
                .filter(IntegrationGatewayConfiguration.MessageReactionRouteSettings::isEnabled)
                .filter(route -> route.getBrokerId().equals(brokerId == null ? "" : brokerId))
                .filter(route -> "*".equals(route.getTopic()) || route.getTopic().equals(topic == null ? "" : topic))
                .map(IntegrationGatewayConfiguration.MessageReactionRouteSettings::getScriptId)
                .toList();
    }
}
