package com.alexxlpz.crm_cbelleza.config;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Lee el {@code .env} en UTF-8 ({@code spring.config.import=optional:file:.env[.env]} en dev). Con la pista
 * {@code [.properties]} Spring lo leía en ISO-8859-1 y las tildes salían rotas («LÃ³pez» en vez de «López»).
 */
public class DotEnvPropertySourceLoader implements PropertySourceLoader {

    @Override
    public String[] getFileExtensions() {
        return new String[]{"env"};
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (properties.isEmpty()) {
            return List.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));
        return List.of(new MapPropertySource(name, values));
    }
}
