package ru.aritmos.integration.programming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integration.config.IntegrationGatewayConfiguration;
import ru.aritmos.integration.domain.IntegrationTemplateExportRequest;
import ru.aritmos.integration.security.core.AuthorizationService;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class IntegrationTemplateArchiveServiceTest {

    private final SubjectPrincipal subject = new SubjectPrincipal("tester", Set.of("programmable-script-manage", "programmable-script-execute"));

    @Test
    void shouldPreviewAndImportTemplateArchiveWithParameterSubstitution() throws Exception {
        GroovyScriptService groovyScriptService = groovyScriptService();
        IntegrationTemplateArchiveService service = new IntegrationTemplateArchiveService(groovyScriptService, new AuthorizationService());

        byte[] archive = buildArchive(
                """
                        template:
                          id: vm-template
                          name: VisitManager ITS
                          description: test
                          version: 1.0.0
                        parameters:
                          - key: vmUrl
                            label: VM URL
                            description: base url
                            required: true
                            defaultValue: https://default
                        scripts:
                          - scriptId: reaction-1
                            type: MESSAGE_BUS_REACTION
                            description: test script
                            file: scripts/reaction-1.groovy
                        """,
                Map.of("scripts/reaction-1.groovy", "return [url: '{{vmUrl}}']")
        );

        var preview = service.preview(archive, subject);
        Assertions.assertEquals("vm-template", preview.templateId());
        Assertions.assertEquals(1, preview.parameters().size());
        Assertions.assertEquals("reaction-1", preview.scripts().get(0).scriptId());

        var result = service.importArchive(archive, Map.of("vmUrl", "https://custom"), true, subject);
        Assertions.assertEquals(true, result.get("ok"));

        StoredGroovyScript imported = groovyScriptService.get("reaction-1", subject);
        Assertions.assertNotNull(imported);
        Assertions.assertTrue(imported.scriptBody().contains("https://custom"));
    }

    @Test
    void shouldExportSelectedScriptsToItsArchive() throws Exception {
        GroovyScriptService groovyScriptService = groovyScriptService();
        IntegrationTemplateArchiveService service = new IntegrationTemplateArchiveService(groovyScriptService, new AuthorizationService());

        groovyScriptService.save(
                "script-a",
                GroovyScriptType.MESSAGE_BUS_REACTION,
                "return [target: '{{endpoint}}']",
                "A",
                subject
        );

        byte[] archive = service.exportArchive(new IntegrationTemplateExportRequest(
                "custom-template",
                "Custom Template",
                "desc",
                List.of("script-a"),
                Map.of("endpoint", "https://service")
        ), subject);

        Map<String, String> entries = unzipTextEntries(archive);
        Assertions.assertTrue(entries.containsKey("template.yml"));
        Assertions.assertTrue(entries.containsKey("scripts/script-a.groovy"));
        Assertions.assertTrue(entries.get("template.yml").contains("endpoint"));
    }

    @Test
    void shouldValidateTemplateArchiveAndReportUndeclaredParameters() throws Exception {
        GroovyScriptService groovyScriptService = groovyScriptService();
        IntegrationTemplateArchiveService service = new IntegrationTemplateArchiveService(groovyScriptService, new AuthorizationService());

        byte[] archive = buildArchive(
                """
                        template:
                          id: validate-template
                        parameters:
                          - key: declaredParam
                            required: true
                            defaultValue: value
                        scripts:
                          - scriptId: reaction-2
                            type: UNKNOWN_SCRIPT_TYPE
                            file: scripts/reaction-2.groovy
                        """,
                Map.of("scripts/reaction-2.groovy", "return [x: '{{undeclaredParam}}']")
        );

        Map<String, Object> validation = service.validateArchive(archive, subject);
        Assertions.assertEquals(false, validation.get("ok"));
        Assertions.assertTrue(validation.get("undeclaredParameters").toString().contains("undeclaredParam"));
        Assertions.assertTrue(validation.get("unknownScriptTypes").toString().contains("UNKNOWN_SCRIPT_TYPE"));
    }

    private GroovyScriptService groovyScriptService() {
        return new GroovyScriptService(
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

    private byte[] buildArchive(String templateYml, Map<String, String> files) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("template.yml"));
            zip.write(templateYml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        }
    }

    private Map<String, String> unzipTextEntries(byte[] bytes) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                result.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
            return result;
        }
    }
}
