package com.alexxlpz.productapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Optional;

/**
 * <a href="https://world.openbeautyfacts.org">Open Beauty Facts</a> (datos con licencia ODbL; fotos, CC BY-SA).
 * No pide clave, pero sí un User-Agent que diga quién llama. Las fotos solo se bajan de sus propios servidores.
 */
@Component
public class OpenBeautyFactsClient implements OpenBeautyFacts {

    private static final Logger log = LoggerFactory.getLogger(OpenBeautyFactsClient.class);
    private static final String API = "https://world.openbeautyfacts.org/api/v2/product/{code}.json"
            + "?fields=product_name,product_name_es,generic_name_es,brands,quantity,categories_tags,image_front_url,image_url";
    private static final String USER_AGENT = "CuquoraProductApi/1.0 (+https://cuquora.com)";

    private final RestClient http;

    public OpenBeautyFactsClient() {
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
    public Optional<ImportedProduct> find(String gtin) {
        try {
            return toProduct(http.get().uri(API, gtin).retrieve().body(Response.class));
        } catch (RestClientResponseException e) {
            // Un código que no conoce responde 404: no es un fallo
            if (e.getStatusCode().value() != 404) {
                log.warn("Open Beauty Facts ha respondido {} al buscar {}", e.getStatusCode().value(), gtin);
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("No se ha podido consultar Open Beauty Facts para {}: {}", gtin, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> image(String url) {
        if (!isOwnImage(url)) return Optional.empty();
        try {
            // Se lee con tope: una respuesta enorme no llega a ocupar memoria
            byte[] data = http.get().uri(URI.create(url)).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) return null;
                try (InputStream in = response.getBody()) {
                    return in.readNBytes(Images.MAX_BYTES + 1);
                }
            });
            return Images.contentType(data).map(type -> data);
        } catch (RestClientException e) {
            log.warn("No se ha podido descargar la foto {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /** Solo https y solo de los servidores de Open Beauty Facts (o de Open Food Facts, que comparte fotos). */
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

    static Optional<ImportedProduct> toProduct(Response response) {
        if (response == null || response.status() != 1 || response.product() == null) return Optional.empty();
        Fields p = response.product();
        String name = first(p.nameEs(), p.name(), p.genericNameEs());
        String brand = p.brands() == null ? null : clean(p.brands().split(",")[0]); // detrás va el grupo o el fabricante
        if (name == null) name = brand;
        if (name == null) return Optional.empty();
        String tags = p.categories() == null ? null : String.join(" ", p.categories());
        return Optional.of(new ImportedProduct(cut(name, 200), cut(brand, 100), cut(clean(p.quantity()), 40),
                Categories.fromTags(tags), first(p.frontImage(), p.image())));
    }

    private static String first(String... values) {
        for (String value : values) {
            String text = clean(value);
            if (text != null) return text;
        }
        return null;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max).strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(int status, Fields product) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fields(@JsonProperty("product_name") String name,
                  @JsonProperty("product_name_es") String nameEs,
                  @JsonProperty("generic_name_es") String genericNameEs,
                  @JsonProperty("brands") String brands,
                  @JsonProperty("quantity") String quantity,
                  @JsonProperty("categories_tags") List<String> categories,
                  @JsonProperty("image_front_url") String frontImage,
                  @JsonProperty("image_url") String image) {
    }
}
