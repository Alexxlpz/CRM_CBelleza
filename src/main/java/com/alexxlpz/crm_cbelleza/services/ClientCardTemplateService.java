package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.entities.ClientCardTemplate;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Estructura (campos) de la ficha técnica de clientes de cada centro. */
@Service
@Transactional
public class ClientCardTemplateService {

    static final String DEFAULT_FIELDS_JSON = """
        [
          {"id":"tipo_piel_cabello","label":"Tipo de Piel / Cabello","type":"text","placeholder":"Ej. Piel mixta / Cabello fino teñido","required":false},
          {"id":"alergias_sensibilidades","label":"Alergias o Sensibilidades","type":"text","placeholder":"Ej. Alergia al amoníaco, látex, fragancias","required":false},
          {"id":"tratamientos_habituales","label":"Coloración / Tratamientos habituales","type":"text","placeholder":"Ej. Tinte 6.34, Mechas balayage, etc.","required":false},
          {"id":"observaciones_preferencias","label":"Observaciones y Preferencias Técnicas","type":"textarea","placeholder":"Preferencias de temperatura de lavado, notas del especialista, etc.","required":false}
        ]
        """;

    private final ClientCardTemplateRepository templateRepository;
    private final CenterRepository centerRepository;
    private final ClientCardJson json;

    public ClientCardTemplateService(ClientCardTemplateRepository templateRepository,
                                     CenterRepository centerRepository,
                                     ClientCardJson json) {
        this.templateRepository = templateRepository;
        this.centerRepository = centerRepository;
        this.json = json;
    }

    public List<ClientCardFieldDTO> getFields(Long centerId) {
        List<ClientCardFieldDTO> fields = json.readFields(getOrCreate(centerId).getFieldsJson());
        return fields.isEmpty() ? json.readFields(DEFAULT_FIELDS_JSON) : fields;
    }

    /**
     * Guarda los campos enviados por el editor de plantilla. Las listas llegan en paralelo
     * (un elemento por campo); las etiquetas vacías se descartan.
     */
    public void saveFields(Long centerId, List<String> ids, List<String> labels,
                           List<String> types, List<String> placeholders) {
        List<ClientCardFieldDTO> fields = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i).trim();
            if (label.isEmpty()) continue;
            String rawId = valueAt(ids, i, "");
            fields.add(ClientCardFieldDTO.builder()
                    .id(slug(rawId.isBlank() ? label : rawId))
                    .label(label)
                    .type(valueAt(types, i, "text"))
                    .placeholder(valueAt(placeholders, i, ""))
                    .required(false)
                    .build());
        }
        if (fields.isEmpty()) {
            throw new BusinessRuleException("La plantilla debe contener al menos un campo.");
        }
        ClientCardTemplate template = getOrCreate(centerId);
        template.setFieldsJson(json.write(fields));
        templateRepository.save(template);
    }

    private ClientCardTemplate getOrCreate(Long centerId) {
        return templateRepository.findByCenterId(centerId).orElseGet(() -> templateRepository.save(
                ClientCardTemplate.builder()
                        .center(centerRepository.findById(centerId)
                                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado.")))
                        .fieldsJson(DEFAULT_FIELDS_JSON)
                        .build()));
    }

    private static String valueAt(List<String> values, int index, String fallback) {
        return values != null && index < values.size() && values.get(index) != null ? values.get(index).trim() : fallback;
    }

    private static String slug(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}
