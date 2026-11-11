package com.alexxlpz.crm_cbelleza.productdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * {@link ProductDatabase} con la API de productos propia de Cuquora (el contenedor {@code product-api}): sabe los
 * productos que han dado de alta los centros y, si no conoce uno, lo busca ella en Open Beauty Facts y lo guarda.
 * Se usa cuando está configurada {@code crm.product-api.url} (PRODUCT_API_URL); va por la red interna de Docker.
 */
@Component
@ConditionalOnExpression("!'${crm.product-api.url:}'.isBlank()")
public class ProductApiClient implements ProductDatabase {

    private static final Logger log = LoggerFactory.getLogger(ProductApiClient.class);
    static final String KEY_HEADER = "X-Api-Key";

    private final String baseUrl;
    private final ObjectMapper json;
    private final RestClient http;

    public ProductApiClient(@Value("${crm.product-api.url}") String baseUrl,
                            @Value("${crm.product-api.key:}") String key,
                            ObjectMapper json) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Falta PRODUCT_API_KEY: es la misma clave que tiene la API de productos.");
        }
        this.baseUrl = baseUrl.strip().replaceAll("/+$", "");
        this.json = json;
        // Directo, sin proxy: la API está en la misma red de Docker
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        // Si no lo conoce, la API pregunta a Open Beauty Facts y baja la foto: puede tardar unos segundos
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(this.baseUrl)
                .defaultHeader(KEY_HEADER, key.strip())
                .build();
    }

    @Override
    public Optional<ExternalProduct> find(String barcode) {
        try {
            String body = http.get().uri("/api/products/{code}", barcode).retrieve().body(String.class);
            return body == null ? Optional.empty() : parse(json.readTree(body));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                log.warn("La API de productos ha respondido {} al buscar {}", e.getStatusCode().value(), barcode);
            }
            return Optional.empty();
        } catch (RestClientException | IOException e) {
            log.warn("No se ha podido consultar la API de productos para {}: {}", barcode, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> image(String imageUrl) {
        // Solo fotos de la propia API: la clave no sale nunca hacia otra dirección
        if (imageUrl == null || !imageUrl.startsWith(baseUrl + "/api/products/")) {
            return Optional.empty();
        }
        try {
            byte[] data = http.get().uri(URI.create(imageUrl)).retrieve().body(byte[].class);
            return data == null || data.length == 0 ? Optional.empty() : Optional.of(data);
        } catch (RestClientException e) {
            log.warn("No se ha podido traer la foto {} de la API de productos: {}", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void contribute(String barcode, ProductContribution product) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("name", product.name());
        if (product.category() != null) form.add("category", product.category());
        if (product.description() != null) form.add("description", product.description());
        if (product.photo() != null) {
            form.add("image", new ByteArrayResource(product.photo()) {
                @Override
                public String getFilename() {
                    return "foto.jpg";
                }
            });
        }
        try {
            http.post().uri("/api/products/{code}", barcode)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("No se ha podido compartir el producto {} con la API de productos: {}", barcode, e.getMessage());
        }
    }

    private Optional<ExternalProduct> parse(JsonNode p) {
        String name = text(p, "name");
        if (name == null) return Optional.empty();
        String image = text(p, "image");
        ProductOrigin origin = "CUQUORA".equals(text(p, "source")) ? ProductOrigin.CUQUORA : ProductOrigin.OPEN_BEAUTY_FACTS;
        return Optional.of(new ExternalProduct(name, text(p, "brand"), text(p, "quantity"), text(p, "description"), text(p, "category"),
                image == null || !image.startsWith("/api/products/") ? null : baseUrl + image, origin));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) return null;
        return value.asText().strip();
    }
}
