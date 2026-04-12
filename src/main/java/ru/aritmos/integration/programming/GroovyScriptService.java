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
import java.util.LinkedHashMap;
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
        validateScriptBody(type, scriptBody);
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

    public boolean exists(String scriptId, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        return storage.get(scriptId) != null;
    }

    public boolean delete(String scriptId, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        return storage.delete(scriptId);
    }

    public List<StoredGroovyScript> list(SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-execute");
        return storage.list();
    }

    public Map<String, Object> validateScript(String scriptId, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-execute");
        StoredGroovyScript script = storage.get(scriptId);
        if (script == null) {
            throw new IllegalArgumentException("Groovy script не найден: " + scriptId);
        }
        return validateScriptBody(script.type(), script.scriptBody());
    }

    public Map<String, Object> validateScriptBody(GroovyScriptType type, String scriptBody) {
        if (scriptBody == null || scriptBody.isBlank()) {
            throw new IllegalArgumentException("scriptBody обязателен");
        }
        Binding binding = new Binding();
        binding.setVariable("input", Map.of());
        binding.setVariable("params", Map.of());
        binding.setVariable("parameters", Map.of());
        binding.setVariable("context", Map.of());
        try {
            new GroovyShell(binding).parse(scriptBody);
            return Map.of("ok", true, "type", type.name(), "message", "Скрипт успешно прошел синтаксическую валидацию");
        } catch (Exception ex) {
            return Map.of("ok", false, "type", type.name(), "message", ex.getMessage());
        }
    }

    public Object execute(String scriptId,
                          JsonNode payload,
                          SubjectPrincipal subject) {
        return executeAdvanced(scriptId, payload, Map.of(), Map.of(), subject);
    }

    /**
     * Расширенный режим выполнения: отдельные параметры и контекст передаются в binding без потерь.
     */
    public Object executeAdvanced(String scriptId,
                                  JsonNode payload,
                                  Map<String, Object> parameters,
                                  Map<String, Object> context,
                                  SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-execute");
        StoredGroovyScript script = storage.get(scriptId);
        if (script == null) {
            throw new IllegalArgumentException("Groovy script не найден: " + scriptId);
        }
        Binding binding = new Binding();
        Object payloadObject = payload == null ? Map.of() : objectMapper.convertValue(payload, Object.class);
        Map<String, Object> input = payloadObject instanceof Map<?, ?> map
                ? new LinkedHashMap<>(objectMapper.convertValue(map, Map.class))
                : Map.of("value", payloadObject);
        Map<String, Object> mergedParameters = resolveExecutionParameters(input, parameters);

        binding.setVariable("payload", payloadObject);
        binding.setVariable("input", input);
        binding.setVariable("parameters", mergedParameters);
        binding.setVariable("params", mergedParameters);
        binding.setVariable("context", context == null ? Map.of() : context);
        binding.setVariable("execution", Map.of(
                "payload", payloadObject,
                "parameters", mergedParameters,
                "context", context == null ? Map.of() : context
        ));
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
            case CONNECTOR_RESPONSE_TRANSFORM -> {
                // Специальный тип для постобработки ответов коннекторов CRM/МИС/предзаписи.
            }
            case OPTIMAL_SERVICE_SELECTION -> {
                // Специальный тип для выбора оптимальной услуги из списка кандидатов.
            }
            default -> throw new IllegalArgumentException("Неподдерживаемый script type");
        }

        Object result = new GroovyShell(binding).evaluate(script.scriptBody());
        if (result instanceof Map<?, ?> map) {
            return new HashMap<>(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveExecutionParameters(Map<String, Object> input,
                                                           Map<String, Object> explicitParameters) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Object payloadParams = input.get("parameters");
        if (payloadParams instanceof Map<?, ?> map) {
            merged.putAll((Map<String, Object>) map);
        }
        if (explicitParameters != null) {
            merged.putAll(explicitParameters);
        }
        input.put("parameters", merged);
        return merged;
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
