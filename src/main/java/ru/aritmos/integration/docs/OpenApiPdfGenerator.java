package ru.aritmos.integration.docs;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Утилита генерации PDF-документации из OpenAPI YAML.
 */
public final class OpenApiPdfGenerator {

    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 14f;
    private static final float MARGIN = 36f;
    private static final Path DEFAULT_FONT = Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");

    private OpenApiPdfGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Использование: OpenApiPdfGenerator <input.yml> <output.pdf>");
        }
        Path input = Path.of(args[0]).toAbsolutePath();
        Path output = Path.of(args[1]).toAbsolutePath();
        Map<String, Object> document = parse(input);
        List<String> lines = toLines(document);
        writePdf(output, lines);
        System.out.println("PDF документация сформирована: " + output);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(Path input) throws IOException {
        String content = Files.readString(input);
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(content);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Некорректный OpenAPI YAML: " + input);
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toLines(Map<String, Object> root) {
        List<String> lines = new ArrayList<>();
        lines.add("OpenAPI документация");
        lines.add("====================");
        Map<String, Object> info = asMap(root.get("info"));
        lines.add("Title: " + value(info.get("title")));
        lines.add("Version: " + value(info.get("version")));
        lines.add("Description: " + value(info.get("description")));
        lines.add("");

        Object tagsRaw = root.get("tags");
        if (tagsRaw instanceof List<?> tags) {
            lines.add("Теги");
            lines.add("----");
            for (Object tagRaw : tags) {
                Map<String, Object> tag = asMap(tagRaw);
                lines.add("* " + value(tag.get("name")) + " — " + value(tag.get("description")));
            }
            lines.add("");
        }

        Map<String, Object> paths = asMap(root.get("paths"));
        lines.add("Эндпоинты");
        lines.add("---------");
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> methods = asMap(pathEntry.getValue());
            for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                String method = methodEntry.getKey().toUpperCase();
                Map<String, Object> operation = asMap(methodEntry.getValue());
                lines.add(method + " " + path);
                lines.add("  summary: " + value(operation.get("summary")));
                lines.add("  description: " + value(operation.get("description")));
                Object tagsForOp = operation.get("tags");
                if (tagsForOp instanceof List<?> tagList && !tagList.isEmpty()) {
                    lines.add("  tags: " + tagList);
                }
                Map<String, Object> responses = asMap(operation.get("responses"));
                for (Map.Entry<String, Object> response : responses.entrySet()) {
                    Map<String, Object> responseBody = asMap(response.getValue());
                    lines.add("  response " + response.getKey() + ": " + value(responseBody.get("description")));
                }
            }
            lines.add("");
        }

        Map<String, Object> components = asMap(root.get("components"));
        Map<String, Object> schemas = asMap(components.get("schemas"));
        lines.add("Сущности (schemas)");
        lines.add("------------------");
        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            lines.add(schemaEntry.getKey());
            Map<String, Object> schema = asMap(schemaEntry.getValue());
            lines.add("  type: " + value(schema.get("type")));
            lines.add("  description: " + value(schema.get("description")));
            Map<String, Object> properties = asMap(schema.get("properties"));
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                Map<String, Object> propertySchema = asMap(property.getValue());
                lines.add("    - " + property.getKey() + " (" + value(propertySchema.get("type")) + "): "
                        + value(propertySchema.get("description")));
            }
        }
        return lines;
    }

    private static void writePdf(Path output, List<String> lines) throws IOException {
        Files.createDirectories(output.getParent());
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            content.beginText();
            content.setFont(font, FONT_SIZE);
            content.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
            float y = page.getMediaBox().getHeight() - MARGIN;
            for (String line : lines) {
                for (String wrapped : wrap(line, 120)) {
                    if (y <= MARGIN) {
                        content.endText();
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        content.beginText();
                        content.setFont(font, FONT_SIZE);
                        content.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
                        y = page.getMediaBox().getHeight() - MARGIN;
                    }
                    content.showText(wrapped);
                    content.newLineAtOffset(0, -LEADING);
                    y -= LEADING;
                }
            }
            content.endText();
            content.close();
            document.save(output.toFile());
        }
    }

    private static PDFont loadFont(PDDocument document) throws IOException {
        if (!Files.exists(DEFAULT_FONT)) {
            throw new IllegalStateException("Не найден шрифт для кириллицы: " + DEFAULT_FONT);
        }
        return PDType0Font.load(document, DEFAULT_FONT.toFile());
    }

    private static List<String> wrap(String input, int maxLength) {
        if (input == null) {
            return List.of("");
        }
        if (input.length() <= maxLength) {
            return List.of(input.replace("\t", "  "));
        }
        List<String> lines = new ArrayList<>();
        String remaining = input.replace("\t", "  ");
        while (remaining.length() > maxLength) {
            int split = remaining.lastIndexOf(' ', maxLength);
            if (split < 0) {
                split = maxLength;
            }
            lines.add(remaining.substring(0, split));
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
    }
}

