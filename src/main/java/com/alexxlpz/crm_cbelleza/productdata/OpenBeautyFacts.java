package com.alexxlpz.crm_cbelleza.productdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * {@link ProductDatabase} con <a href="https://world.openbeautyfacts.org">Open Beauty Facts</a>: base de datos
 * abierta de cosméticos (licencia ODbL; las fotos, CC BY-SA). No pide clave, pero sí un User-Agent que diga quién
 * llama. Las fotos solo se descargan de sus propios servidores, nunca de una dirección cualquiera.
 * Solo se usa directamente cuando no está configurada la API de productos propia ({@code crm.product-api.url}).
 */
@Component
@ConditionalOnExpression("'${crm.product-api.url:}'.isBlank()")
public class OpenBeautyFacts implements ProductDatabase {

    private static final Logger log = LoggerFactory.getLogger(OpenBeautyFacts.class);
    private static final String API = "https://world.openbeautyfacts.org/api/v2/product/{code}.json"
            + "?fields=product_name,product_name_es,generic_name_es,brands,quantity,categories_tags,image_front_url,image_url";
    private static final String USER_AGENT = "Cuquora/1.0 (+https://cuquora.com)";
    /** Las fotos del envase pesan poco; más que esto no es una foto de producto. */
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final ObjectMapper json;
    private final RestClient http;

    public OpenBeautyFacts(ObjectMapper json) {
        this.json = json;
        HttpClient client = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    @Override
    public Optional<ExternalProduct> find(String barcode) {
        try {
            String body = http.get().uri(API, barcode).retrieve().body(String.class);
            return parse(body == null ? null : json.readTree(body));
        } catch (RestClientResponseException e) {
            // Un código que no conoce responde 404 con status 0: no es un fallo
            if (e.getStatusCode().value() != 404) {
                log.warn("Open Beauty Facts ha respondido {} al buscar {}", e.getStatusCode().value(), barcode);
            }
            return Optional.empty();
        } catch (RestClientException | java.io.IOException e) {
            log.warn("No se ha podido consultar Open Beauty Facts para {}: {}", barcode, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> image(String imageUrl) {
        if (!isOwnImage(imageUrl)) {
            return Optional.empty();
        }
        try {
            // Se lee con tope: una respuesta enorme no llega a ocupar memoria
            byte[] data = http.get().uri(URI.create(imageUrl)).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) return null;
                try (InputStream in = response.getBody()) {
                    return in.readNBytes(MAX_IMAGE_BYTES + 1);
                }
            });
            return data == null || data.length == 0 || data.length > MAX_IMAGE_BYTES ? Optional.empty() : Optional.of(data);
        } catch (RestClientException e) {
            log.warn("No se ha podido descargar la foto {}: {}", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /** Solo https y solo de los servidores de Open Beauty Facts (o de su hermano Open Food Facts, que comparte fotos). */
    static boolean isOwnImage(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getPort() == -1 && host != null
                    && (host.endsWith(".openbeautyfacts.org") || host.endsWith(".openfoodfacts.org"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static Optional<ExternalProduct> parse(JsonNode root) {
        if (root == null || root.path("status").asInt(0) != 1 || !root.path("product").isObject()) {
            return Optional.empty();
        }
        JsonNode p = root.path("product");
        String name = firstText(p, "product_name_es", "product_name", "generic_name_es");
        String brand = text(p, "brands");
        if (brand != null && brand.contains(",")) {
            brand = brand.substring(0, brand.indexOf(',')).trim(); // la primera es la marca; el resto, el grupo
        }
        StringBuilder tags = new StringBuilder();
        p.path("categories_tags").forEach(tag -> tags.append(tag.asText()).append(' '));
        String image = firstText(p, "image_front_url", "image_url");
        if (name == null && brand == null) {
            return Optional.empty();
        }
        return Optional.of(new ExternalProduct(name, brand, text(p, "quantity"), null, ProductCategories.fromTags(tags.toString()),
                image, ProductOrigin.OPEN_BEAUTY_FACTS));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) return null;
        return value.asText().strip();
    }
}
