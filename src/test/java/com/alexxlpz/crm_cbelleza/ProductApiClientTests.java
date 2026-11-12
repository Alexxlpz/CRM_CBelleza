package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.productdata.ExternalProduct;
import com.alexxlpz.crm_cbelleza.productdata.ProductApiClient;
import com.alexxlpz.crm_cbelleza.productdata.ProductContribution;
import com.alexxlpz.crm_cbelleza.productdata.ProductOrigin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Cuquora hablando con la API de productos propia (aquí, un servidor de pruebas que hace de ella). */
class ProductApiClientTests {

    private static final String KEY = "clave-compartida-de-pruebas-0123456789";

    private HttpServer server;
    private ProductApiClient client;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        client = new ProductApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", KEY, new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(exchange.getRequestMethod() + " " + path + " " + exchange.getRequestHeaders().getFirst("X-Api-Key"));
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
        if (!KEY.equals(exchange.getRequestHeaders().getFirst("X-Api-Key"))) {
            send(exchange, 401, "application/json", "{}".getBytes());
        } else if (path.equals("/api/products/8436595340863")) {
            send(exchange, "POST".equals(exchange.getRequestMethod()) ? 201 : 200, "application/json", """
                    {"gtin":"8436595340863","name":"Alissi Brontë Sublime","brand":null,"category":"Cabello",
                     "quantity":"100 ml","description":"Champú de brillo","source":"CUQUORA","image":"/api/products/8436595340863/image?v=1"}
                    """.getBytes(StandardCharsets.UTF_8));
        } else if (path.equals("/api/products/8436595340863/image")) {
            send(exchange, 200, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1});
        } else {
            send(exchange, 404, "application/json", "{\"message\":\"No conocemos ese producto.\"}".getBytes());
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void findsWhatAnotherCenterAddedWithItsPhoto() {
        ExternalProduct p = client.find("8436595340863").orElseThrow();
        assertEquals("Alissi Brontë Sublime", p.name());
        assertEquals("Cabello", p.category());
        assertEquals("100 ml", p.quantity());
        assertEquals("Champú de brillo", p.description());
        assertEquals(ProductOrigin.CUQUORA, p.origin());
        assertTrue(p.imageUrl().endsWith("/api/products/8436595340863/image?v=1"));
        assertEquals(4, client.image(p.imageUrl()).orElseThrow().length);
        assertTrue(requests.stream().allMatch(r -> r.endsWith(KEY)), "manda la clave en cada petición");
    }

    @Test
    void unknownOrDownMeansFillingByHand() {
        assertTrue(client.find("4006381333931").isEmpty());
        server.stop(0);
        assertTrue(client.find("8436595340863").isEmpty(), "si la API está caída, no falla: se rellena a mano");
    }

    @Test
    void theKeyNeverLeavesForAnotherAddress() {
        assertTrue(client.image("https://images.openbeautyfacts.org/x.jpg").isEmpty());
        assertTrue(client.image("http://127.0.0.1:1/api/products/1/image").isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void sharesNameCategoryDescriptionPhotoAndCenter() {
        client.contribute("8436595340863", new ProductContribution("Alissi Brontë Sublime", "Cabello", "Champú de brillo",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}, 7L));
        assertEquals("POST /api/products/8436595340863 " + KEY, requests.getFirst());
        String body = bodies.getFirst();
        assertTrue(body.contains("name=\"name\""));
        assertTrue(body.contains("name=\"category\""));
        assertTrue(body.contains("name=\"description\""));
        assertTrue(body.contains("name=\"image\"; filename=\"foto.jpg\""));
        assertTrue(body.contains("name=\"center\""));
        assertTrue(body.contains("\r\n\r\n7\r\n"), "solo el número del centro");
        assertFalse(body.contains("price"));
    }

    @Test
    void withoutTheKeyItDoesNotStart() {
        assertThrows(IllegalStateException.class, () -> new ProductApiClient("http://product-api:8081", "", new ObjectMapper()));
    }
}
