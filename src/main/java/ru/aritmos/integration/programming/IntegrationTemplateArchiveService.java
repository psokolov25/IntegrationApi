package ru.aritmos.integration.programming;

import jakarta.inject.Singleton;
import org.yaml.snakeyaml.Yaml;
import ru.aritmos.integration.domain.IntegrationTemplateExportRequest;
import ru.aritmos.integration.domain.IntegrationTemplateParameterDto;
import ru.aritmos.integration.domain.IntegrationTemplatePreviewDto;
import ru.aritmos.integration.domain.IntegrationTemplateScriptDto;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Импорт/экспорт ITS (Integration Templates) архивов для programmable Groovy-обработчиков.
 */
@Singleton
public class IntegrationTemplateArchiveService {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final GroovyScriptService groovyScriptService;
    private final AuthorizationService authorizationService;

    public IntegrationTemplateArchiveService(GroovyScriptService groovyScriptService,
                                             AuthorizationService authorizationService) {
        this.groovyScriptService = groovyScriptService;
        this.authorizationService = authorizationService;
    }

    public IntegrationTemplatePreviewDto preview(byte[] archiveBytes, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        ParsedTemplate parsed = parseArchive(archiveBytes);
        return parsed.preview();
    }

    public Map<String, Object> validateArchive(byte[] archiveBytes, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        ParsedTemplate parsed = parseArchive(archiveBytes);
        Set<String> declared = parsed.parameters().stream()
                .map(TemplateParameter::key)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> referenced = new LinkedHashSet<>();
        List<String> unknownScriptTypes = new ArrayList<>();
        for (TemplateScript script : parsed.scripts()) {
            referenced.addAll(extractParams(script.scriptBody()));
            try {
                GroovyScriptType.valueOf(script.type());
            } catch (Exception ex) {
                unknownScriptTypes.add(script.scriptId() + ":" + script.type());
            }
        }
        List<String> undeclaredParams = referenced.stream()
                .filter(key -> !declared.contains(key))
                .toList();
        List<String> unusedParams = declared.stream()
                .filter(key -> !referenced.contains(key))
                .toList();
        return Map.of(
                "ok", undeclaredParams.isEmpty() && unknownScriptTypes.isEmpty(),
                "templateId", parsed.templateId(),
                "scriptsCount", parsed.scripts().size(),
                "declaredParameters", declared,
                "referencedParameters", referenced,
                "undeclaredParameters", undeclaredParams,
                "unusedParameters", unusedParams,
                "unknownScriptTypes", unknownScriptTypes,
                "validatedAt", Instant.now().toString()
        );
    }

    public Map<String, Object> importArchive(byte[] archiveBytes,
                                             Map<String, String> parameterValues,
                                             boolean replaceExisting,
                                             SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        ParsedTemplate parsed = parseArchive(archiveBytes);
        Map<String, String> resolved = resolveParameters(parsed, parameterValues);

        List<String> importedIds = new ArrayList<>();
        for (TemplateScript script : parsed.scripts()) {
            if (!replaceExisting && groovyScriptService.exists(script.scriptId(), subject)) {
                throw new IllegalArgumentException("Скрипт уже существует, включите replaceExisting: " + script.scriptId());
            }
            String scriptBody = applyParameters(script.scriptBody(), resolved);
            groovyScriptService.save(
                    script.scriptId(),
                    GroovyScriptType.valueOf(script.type()),
                    scriptBody,
                    script.description(),
                    subject
            );
            importedIds.add(script.scriptId());
        }

        return Map.of(
                "ok", true,
                "templateId", parsed.templateId(),
                "importedScripts", importedIds,
                "parameterValues", resolved,
                "importedAt", Instant.now().toString()
        );
    }

    public byte[] exportArchive(IntegrationTemplateExportRequest request, SubjectPrincipal subject) {
        authorizationService.requirePermission(subject, "programmable-script-manage");
        if (request.scriptIds() == null || request.scriptIds().isEmpty()) {
            throw new IllegalArgumentException("Для экспорта нужно указать минимум один scriptId");
        }

        List<StoredGroovyScript> scripts = request.scriptIds().stream()
                .map(id -> {
                    StoredGroovyScript script = groovyScriptService.get(id, subject);
                    if (script == null) {
                        throw new IllegalArgumentException("Скрипт не найден: " + id);
                    }
                    return script;
                })
                .toList();

        Set<String> paramKeys = new LinkedHashSet<>();
        for (StoredGroovyScript script : scripts) {
            paramKeys.addAll(extractParams(script.scriptBody()));
        }

        List<Map<String, Object>> parameters = paramKeys.stream()
                .map(key -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("key", key);
                    p.put("label", key);
                    p.put("description", "Параметр шаблона " + key);
                    p.put("required", true);
                    p.put("defaultValue", defaultValue(request.parameterDefaults(), key));
                    return p;
                })
                .toList();

        List<Map<String, Object>> scriptDefs = new ArrayList<>();
        Map<String, String> files = new LinkedHashMap<>();
        for (StoredGroovyScript script : scripts) {
            String fileName = "scripts/" + script.scriptId() + ".groovy";
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("scriptId", script.scriptId());
            def.put("type", script.type().name());
            def.put("description", script.description());
            def.put("file", fileName);
            scriptDefs.add(def);
            files.put(fileName, script.scriptBody());
        }

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("id", safeValue(request.templateId(), "integration-template"));
        template.put("name", safeValue(request.name(), "Integration Template Export"));
        template.put("description", safeValue(request.description(), "Экспорт programmable-обработчиков Integration API"));
        template.put("version", "1.0.0");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("template", template);
        root.put("parameters", parameters);
        root.put("scripts", scriptDefs);

        Yaml yaml = new Yaml();
        String yamlContent = yaml.dump(root);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            putEntry(zip, "template.yml", yamlContent);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                putEntry(zip, entry.getKey(), entry.getValue());
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сформировать ITS-архив", ex);
        }
    }

    private ParsedTemplate parseArchive(byte[] archiveBytes) {
        Map<String, byte[]> entries = unzipEntries(archiveBytes);
        String yamlPath = entries.containsKey("template.yml") ? "template.yml"
                : entries.containsKey("template.yaml") ? "template.yaml"
                : null;
        if (yamlPath == null) {
            throw new IllegalArgumentException("ITS-архив должен содержать template.yml или template.yaml");
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(new String(entries.get(yamlPath), StandardCharsets.UTF_8));
        if (root == null) {
            throw new IllegalArgumentException("template.yml пустой или некорректный");
        }

        Map<String, Object> template = asMap(root.get("template"));
        String templateId = asString(template.get("id"), "integration-template");
        String templateName = asString(template.get("name"), templateId);
        String templateDescription = asString(template.get("description"), "");
        String templateVersion = asString(template.get("version"), "1.0.0");

        List<TemplateParameter> parameters = asList(root.get("parameters")).stream()
                .map(this::asMap)
                .map(item -> new TemplateParameter(
                        asString(item.get("key"), ""),
                        asString(item.get("label"), asString(item.get("key"), "")),
                        asString(item.get("description"), ""),
                        Boolean.parseBoolean(String.valueOf(item.getOrDefault("required", true))),
                        asString(item.get("defaultValue"), "")
                ))
                .filter(p -> !p.key().isBlank())
                .toList();

        List<TemplateScript> scripts = asList(root.get("scripts")).stream()
                .map(this::asMap)
                .map(item -> {
                    String file = asString(item.get("file"), "");
                    if (file.isBlank() || !entries.containsKey(file)) {
                        throw new IllegalArgumentException("В ITS отсутствует groovy-файл: " + file);
                    }
                    return new TemplateScript(
                            asString(item.get("scriptId"), ""),
                            asString(item.get("type"), "MESSAGE_BUS_REACTION"),
                            asString(item.get("description"), ""),
                            file,
                            new String(entries.get(file), StandardCharsets.UTF_8)
                    );
                })
                .filter(script -> !script.scriptId().isBlank())
                .toList();

        if (scripts.isEmpty()) {
            throw new IllegalArgumentException("В ITS-архиве отсутствуют scripts");
        }

        return new ParsedTemplate(templateId, templateName, templateDescription, templateVersion, parameters, scripts);
    }

    private Map<String, String> resolveParameters(ParsedTemplate parsed, Map<String, String> provided) {
        Map<String, String> resolved = new LinkedHashMap<>();
        Map<String, String> safeProvided = provided == null ? Map.of() : provided;
        for (TemplateParameter parameter : parsed.parameters()) {
            String value = safeProvided.getOrDefault(parameter.key(), parameter.defaultValue());
            if (parameter.required() && (value == null || value.isBlank())) {
                throw new IllegalArgumentException("Для импорта обязателен параметр: " + parameter.key());
            }
            resolved.put(parameter.key(), value == null ? "" : value);
        }

        Set<String> discovered = new LinkedHashSet<>();
        for (TemplateScript script : parsed.scripts()) {
            discovered.addAll(extractParams(script.scriptBody()));
        }
        for (String key : discovered) {
            if (!resolved.containsKey(key)) {
                String value = safeProvided.getOrDefault(key, "");
                resolved.put(key, value);
            }
        }
        return resolved;
    }

    private List<String> extractParams(String scriptBody) {
        Matcher matcher = PARAM_PATTERN.matcher(scriptBody == null ? "" : scriptBody);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }

    private String applyParameters(String scriptBody, Map<String, String> params) {
        String result = scriptBody;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private Map<String, byte[]> unzipEntries(byte[] archiveBytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) > -1) {
                    out.write(buffer, 0, read);
                }
                entries.put(entry.getName(), out.toByteArray());
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("ITS-архив пустой");
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать ITS-архив", ex);
        }
    }

    private void putEntry(ZipOutputStream zip, String path, String body) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String defaultValue(Map<String, String> values, String key) {
        if (values == null) {
            return "";
        }
        return Objects.toString(values.getOrDefault(key, ""), "");
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String raw = String.valueOf(value);
        return raw.isBlank() ? fallback : raw;
    }

    private record TemplateParameter(String key, String label, String description, boolean required, String defaultValue) {
    }

    private record TemplateScript(String scriptId, String type, String description, String file, String scriptBody) {
    }

    private record ParsedTemplate(String templateId,
                                  String name,
                                  String description,
                                  String version,
                                  List<TemplateParameter> parameters,
                                  List<TemplateScript> scripts) {
        private IntegrationTemplatePreviewDto preview() {
            List<IntegrationTemplateParameterDto> p = parameters.stream()
                    .map(param -> new IntegrationTemplateParameterDto(
                            param.key(),
                            param.label(),
                            param.description(),
                            param.required(),
                            param.defaultValue()
                    ))
                    .toList();
            List<IntegrationTemplateScriptDto> s = scripts.stream()
                    .map(script -> new IntegrationTemplateScriptDto(
                            script.scriptId(),
                            script.type(),
                            script.description(),
                            script.file()
                    ))
                    .toList();
            return new IntegrationTemplatePreviewDto(templateId, name, description, version, p, s);
        }
    }
}
