package ru.aritmos.integration.programming;

import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Генератор шаблонов Groovy REST-клиентов на основе внешнего OpenAPI (YAML/JSON).
 */
public final class OpenApiGroovyClientGenerator {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

    private OpenApiGroovyClientGenerator() {
    }

    public static Map<String, Object> generate(String openApiUrl, String serviceIdHint) {
        if (openApiUrl == null || openApiUrl.isBlank()) {
            throw new IllegalArgumentException("openApiUrl обязателен");
        }
        String content = readOpenApi(openApiUrl.trim());
        Map<String, Object> root = objectMap(new Yaml().load(content));

        Map<String, Object> info = objectMap(root.get("info"));
        String title = stringValue(info.get("title"), "external-openapi");
        String version = stringValue(info.get("version"), "");
        String serviceId = normalizeServiceId(serviceIdHint, title);
        String defaultBaseUrl = resolveDefaultBaseUrl(root);

        Map<String, Object> paths = objectMap(root.get("paths"));
        List<Map<String, Object>> methods = new ArrayList<>();
        List<Map<String, Object>> scripts = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String apiPath = pathEntry.getKey();
            Map<String, Object> pathSpec = objectMap(pathEntry.getValue());
            for (Map.Entry<String, Object> methodEntry : pathSpec.entrySet()) {
                String httpMethod = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(httpMethod)) {
                    continue;
                }
                Map<String, Object> operation = objectMap(methodEntry.getValue());
                String operationId = stringValue(operation.get("operationId"), buildOperationId(httpMethod, apiPath));
                String scriptId = "openapi-" + serviceId + "-" + sanitize(operationId);
                String summary = stringValue(operation.get("summary"),
                        stringValue(operation.get("description"), operationId));
                List<String> pathParameters = extractPathParameters(apiPath);
                Map<String, Object> methodInfo = new LinkedHashMap<>();
                methodInfo.put("operationId", operationId);
                methodInfo.put("scriptId", scriptId);
                methodInfo.put("httpMethod", httpMethod.toUpperCase(Locale.ROOT));
                methodInfo.put("path", apiPath);
                methodInfo.put("summary", summary);
                methodInfo.put("pathParameters", pathParameters);
                methods.add(Map.copyOf(methodInfo));

                Map<String, Object> scriptApiPayload = Map.of(
                        "scriptId", scriptId,
                        "type", "VISIT_MANAGER_ACTION",
                        "description", "OpenAPI client: " + operationId + " [" + httpMethod.toUpperCase(Locale.ROOT) + " " + apiPath + "]",
                        "scriptBody", buildGroovyScriptTemplate(serviceId, httpMethod, apiPath, pathParameters)
                );
                scripts.add(Map.of(
                        "scriptId", scriptId,
                        "scriptType", "VISIT_MANAGER_ACTION",
                        "operationId", operationId,
                        "scriptBody", scriptApiPayload.get("scriptBody"),
                        "saveScriptRequest", scriptApiPayload,
                        "saveScriptApi", "PUT /api/v1/program/scripts/{scriptId}",
                        "executeAdvancedExample", Map.of(
                                "api", "POST /api/v1/program/scripts/{scriptId}/execute-advanced",
                                "body", Map.of(
                                        "payload", Map.of(),
                                        "parameters", buildPathParameterExample(pathParameters, serviceId),
                                        "context", Map.of("source", "openapi-generated-client")
                                )
                        )
                ));
            }
        }
        Map<String, Object> connectorPreset = Map.of(
                "messageBrokers", List.of(),
                "externalRestServices", List.of(
                        Map.of(
                                "id", serviceId,
                                "baseUrl", defaultBaseUrl,
                                "defaultHeaders", Map.of()
                        )
                )
        );
        return Map.of(
                "sourceUrl", openApiUrl,
                "generatedAt", Instant.now().toString(),
                "serviceId", serviceId,
                "serviceTitle", title,
                "serviceVersion", version,
                "externalRestServicePreset", Map.of(
                        "id", serviceId,
                        "baseUrl", defaultBaseUrl,
                        "defaultHeaders", Map.of()
                ),
                "clients", List.copyOf(methods),
                "scripts", List.copyOf(scripts),
                "toolkit", Map.of(
                        "connectorPresetsPreviewRequest", Map.of(
                                "operation", "IMPORT_CONNECTOR_PRESETS_PREVIEW",
                                "parameters", connectorPreset
                        ),
                        "connectorPresetsApplyRequest", Map.of(
                                "operation", "IMPORT_CONNECTOR_PRESETS_APPLY",
                                "parameters", Map.of(
                                        "replaceExisting", false,
                                        "includeRollbackSnapshot", true,
                                        "messageBrokers", List.of(),
                                        "externalRestServices", connectorPreset.get("externalRestServices")
                                )
                        ),
                        "scriptsBatch", List.copyOf(scripts)
                ),
                "usage", Map.of(
                        "createScriptApi", "PUT /api/v1/program/scripts/{scriptId}",
                        "runScriptApi", "POST /api/v1/program/scripts/{scriptId}/execute-advanced",
                        "requiredBinding", "externalRestClient.invoke(serviceId, method, path, body, headers)"
                )
        );
    }

    private static Map<String, Object> buildPathParameterExample(List<String> pathParameters, String serviceId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("serviceId", serviceId);
        params.put("headers", Map.of());
        for (String pathParameter : pathParameters) {
            params.put("path_" + sanitize(pathParameter), "<" + pathParameter + ">");
        }
        return Map.copyOf(params);
    }

    private static String buildGroovyScriptTemplate(String serviceId,
                                                    String httpMethod,
                                                    String apiPath,
                                                    List<String> pathParameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("def serviceId = params.serviceId ?: '").append(serviceId).append("'\n");
        builder.append("def path = \"").append(toGroovyPathTemplate(apiPath, pathParameters)).append("\"\n");
        builder.append("def method = '").append(httpMethod.toUpperCase(Locale.ROOT)).append("'\n");
        builder.append("def body = payload instanceof Map ? payload : [:]\n");
        builder.append("def headers = params.headers instanceof Map ? params.headers : [:]\n");
        builder.append("return externalRestClient.invoke(serviceId, method, path, body, headers)\n");
        return builder.toString();
    }

    private static String toGroovyPathTemplate(String apiPath, List<String> parameters) {
        String template = apiPath;
        for (String parameter : parameters) {
            template = template.replace("{" + parameter + "}",
                    "${params.path_" + sanitize(parameter) + " ?: ''}");
        }
        return template;
    }

    private static List<String> extractPathParameters(String apiPath) {
        List<String> params = new ArrayList<>();
        int from = 0;
        while (from < apiPath.length()) {
            int start = apiPath.indexOf('{', from);
            if (start < 0) {
                break;
            }
            int end = apiPath.indexOf('}', start + 1);
            if (end < 0) {
                break;
            }
            String param = apiPath.substring(start + 1, end).trim();
            if (!param.isBlank()) {
                params.add(param);
            }
            from = end + 1;
        }
        return params;
    }

    private static String buildOperationId(String method, String apiPath) {
        return sanitize(method + "_" + apiPath);
    }

    private static String resolveDefaultBaseUrl(Map<String, Object> root) {
        Object rawServers = root.get("servers");
        if (!(rawServers instanceof List<?> servers) || servers.isEmpty()) {
            return "";
        }
        for (Object rawServer : servers) {
            Map<String, Object> server = objectMap(rawServer);
            String url = stringValue(server.get("url"), "");
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private static String normalizeServiceId(String serviceIdHint, String title) {
        if (serviceIdHint != null && !serviceIdHint.isBlank()) {
            return sanitize(serviceIdHint);
        }
        return sanitize(title);
    }

    private static String sanitize(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "openapi-service" : normalized;
    }

    private static String readOpenApi(String openApiUrl) {
        try {
            if (openApiUrl.startsWith("http://") || openApiUrl.startsWith("https://")) {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(openApiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalArgumentException("OpenAPI URL недоступен, HTTP status=" + response.statusCode());
                }
                return response.body();
            }
            Path path = openApiUrl.startsWith("file://")
                    ? Path.of(URI.create(openApiUrl))
                    : Path.of(openApiUrl);
            return Files.readString(path);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось загрузить OpenAPI из " + openApiUrl, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String stringValue(Object raw, String fallback) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        return value.isBlank() ? fallback : value;
    }
}
