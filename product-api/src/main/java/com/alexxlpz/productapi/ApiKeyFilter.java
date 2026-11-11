package com.alexxlpz.productapi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Todo lo que cuelga de /api pide la cabecera {@code X-Api-Key} con la clave compartida con Cuquora. Sin una clave
 * larga configurada la API no arranca: así nunca queda abierta por olvido.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";
    static final int MIN_KEY_LENGTH = 32;

    private final byte[] key;

    public ApiKeyFilter(@Value("${product-api.key:}") String key) {
        if (key == null || key.strip().length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("Falta PRODUCT_API_KEY (al menos " + MIN_KEY_LENGTH
                    + " caracteres; por ejemplo, la salida de «openssl rand -hex 32»).");
        }
        this.key = key.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"Falta la clave de la API o no es correcta.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
