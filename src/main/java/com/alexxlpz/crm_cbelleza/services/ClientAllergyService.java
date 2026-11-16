package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Lo que la ficha técnica de cada cliente dice de sus alergias, para avisar en la agenda y en «Hoy». Cada centro edita
 * su plantilla de ficha: cuenta como alergia cualquier campo cuyo nombre (o id) hable de alergias, como el
 * «Alergias o Sensibilidades» de la plantilla por defecto.
 */
@Service
public class ClientAllergyService {

    private final ClientCardRepository clientCardRepository;
    private final ClientCardTemplateService templateService;
    private final ClientCardJson json;

    public ClientAllergyService(ClientCardRepository clientCardRepository,
                                ClientCardTemplateService templateService,
                                ClientCardJson json) {
        this.clientCardRepository = clientCardRepository;
        this.templateService = templateService;
        this.json = json;
    }

    /** Alergias apuntadas de cada cliente del centro (id del cliente → texto); sin entrada si no hay nada escrito. */
    public Map<Long, String> byClient(Long centerId) {
        List<String> fieldIds = allergyFieldIds(centerId);
        if (fieldIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (ClientCard card : clientCardRepository.findByCenterId(centerId)) {
            if (card.getClient() == null) {
                continue;
            }
            String text = allergiesIn(json.readData(card.getDataJson()), fieldIds);
            if (text != null) {
                result.put(card.getClient().getId(), text);
            }
        }
        return result;
    }

    /** Alergias de un cliente en el centro, o {@code null} si su ficha no dice nada. */
    public String of(Long centerId, Long clientId) {
        if (clientId == null) {
            return null;
        }
        List<String> fieldIds = allergyFieldIds(centerId);
        if (fieldIds.isEmpty()) {
            return null;
        }
        return clientCardRepository.findByCenterIdAndClientId(centerId, clientId)
                .map(card -> allergiesIn(json.readData(card.getDataJson()), fieldIds))
                .orElse(null);
    }

    private List<String> allergyFieldIds(Long centerId) {
        return templateService.getFields(centerId).stream()
                .filter(ClientAllergyService::isAllergyField)
                .map(ClientCardFieldDTO::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    static boolean isAllergyField(ClientCardFieldDTO field) {
        return mentionsAllergy(field.getLabel()) || mentionsAllergy(field.getId());
    }

    private static boolean mentionsAllergy(String text) {
        if (text == null) {
            return false;
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return plain.contains("alergi") || plain.contains("alergen");
    }

    private static String allergiesIn(Map<String, String> data, List<String> fieldIds) {
        String text = fieldIds.stream()
                .map(data::get)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" · "));
        return text.isEmpty() ? null : text;
    }
}
