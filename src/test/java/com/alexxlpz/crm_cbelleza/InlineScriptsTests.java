package com.alexxlpz.crm_cbelleza;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La CSP solo deja ejecutar los .js de la propia web (script-src 'self', sin 'unsafe-inline'). Si una plantilla
 * vuelve a llevar un script en línea o un atributo on...=, en el navegador no funcionaría: este test lo avisa antes.
 * Los datos que necesita una página van en un bloque {@code type="application/json"} (no se ejecuta) o en data-.
 * Los correos (templates/email) no pasan por la CSP de la web.
 */
class InlineScriptsTests {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_TYPE = Pattern.compile("type=\"application/(ld\\+)?json\"");
    private static final Pattern EVENT_ATTRIBUTE = Pattern.compile("\\s(th:)?on[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    @Test
    void templatesHaveNoInlineScripts() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".html"))
                    .filter(f -> !TEMPLATES.relativize(f).startsWith("email")).toList()) {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                Matcher script = SCRIPT_TAG.matcher(html);
                while (script.find()) {
                    String attributes = script.group(1);
                    if (!attributes.contains("src=") && !DATA_TYPE.matcher(attributes).find()) {
                        problems.add(TEMPLATES.relativize(file) + ": <script" + attributes + "> en línea");
                    }
                }
                Matcher event = EVENT_ATTRIBUTE.matcher(html);
                while (event.find()) {
                    problems.add(TEMPLATES.relativize(file) + ": atributo" + event.group() + " (usa addEventListener en un .js)");
                }
                if (html.contains("javascript:")) {
                    problems.add(TEMPLATES.relativize(file) + ": enlace javascript:");
                }
            }
        }
        assertTrue(problems.isEmpty(), "La CSP bloquearía:\n" + String.join("\n", problems));
    }
}
