package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lectura/escritura del JSON en el que se guardan las plantillas y los datos de las fichas. */
@Component
public class ClientCardJson {

    private static final TypeReference<List<ClientCardFieldDTO>> FIELDS = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> DATA = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ClientCardJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ClientCardFieldDTO> readFields(String json) {
        try {
            return objectMapper.readValue(json, FIELDS);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return List.of();
        }
    }

    public Map<String, String> readData(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, DATA));
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la ficha", e);
        }
    }

    /** true si la ficha tiene al menos un valor rellenado. */
    public boolean hasAnyValue(String json) {
        Collection<String> values = readData(json).values();
        return values.stream().anyMatch(v -> v != null && !v.isBlank());
    }
}
