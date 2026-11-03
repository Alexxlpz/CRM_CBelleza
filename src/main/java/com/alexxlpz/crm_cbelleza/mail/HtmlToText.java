package com.alexxlpz.crm_cbelleza.mail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión en texto plano de un correo HTML, para enviarla junto al HTML: los filtros de spam desconfían de los correos
 * que solo traen HTML y así también se leen en relojes, lectores de pantalla o clientes sin HTML. Basta para las
 * plantillas de {@code templates/email/} (párrafos, negritas, enlaces); no pretende convertir cualquier HTML.
 */
final class HtmlToText {

    private static final Pattern DROP = Pattern.compile("(?is)<!--.*?-->|<(head|style|script)\\b.*?</\\1\\s*>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a\\s*>");
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|</(p|div|h[1-6]|li|tr|table|ul|ol)\\s*>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");

    private HtmlToText() {
    }

    static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = DROP.matcher(html).replaceAll("");
        text = replaceLinks(text);
        text = BREAK.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = unescape(text);
        StringBuilder out = new StringBuilder();
        int blankLines = 0;
        for (String line : text.split("\\R")) {
            String clean = line.replace('\u00A0', ' ').replaceAll("[ \\t]+", " ").strip();
            if (clean.isEmpty()) {
                blankLines++;
                continue;
            }
            if (!out.isEmpty()) {
                out.append(blankLines > 0 ? "\n\n" : "\n");
            }
            out.append(clean);
            blankLines = 0;
        }
        return out.toString();
    }

    /** {@code <a href="url">Texto</a>} pasa a «Texto: url», que es lo que se puede pulsar o copiar en texto plano. */
    private static String replaceLinks(String html) {
        Matcher m = LINK.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = unescape(m.group(1)).strip();
            String label = unescape(TAG.matcher(m.group(2)).replaceAll("")).strip();
            String replacement = label.isEmpty() || label.equals(url) ? url : label + ": " + url;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String unescape(String text) {
        Matcher m = ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = switch (name) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "laquo" -> "«";
                case "raquo" -> "»";
                default -> {
                    if (name.startsWith("#x") || name.startsWith("#X")) {
                        yield new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
                    } else if (name.startsWith("#")) {
                        yield new String(Character.toChars(Integer.parseInt(name.substring(1))));
                    }
                    yield m.group();
                }
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
