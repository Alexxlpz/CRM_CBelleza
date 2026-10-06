package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.TreatmentForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Carta de tratamientos de cada centro. */
@Service
@Transactional
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final CenterRepository centerRepository;

    public TreatmentService(TreatmentRepository treatmentRepository, CenterRepository centerRepository) {
        this.treatmentRepository = treatmentRepository;
        this.centerRepository = centerRepository;
    }

    @Transactional(readOnly = true)
    public List<Treatment> getTreatmentsByCenter(Long centerId) {
        return treatmentRepository.findByCenterId(centerId);
    }

    @Transactional(readOnly = true)
    public long countByCenter(Long centerId) {
        return treatmentRepository.countByCenterId(centerId);
    }

    public void addTreatment(Long centerId, TreatmentForm form) {
        if (FormText.isBlank(form.name()) || form.price() == null || form.duration() == null || form.duration() <= 0) {
            throw new BusinessRuleException("Completa nombre, precio y duración del tratamiento.");
        }
        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        treatmentRepository.save(Treatment.builder()
                .name(form.name().trim())
                .description(form.description())
                .price(form.price())
                .duration(form.duration())
                .type(parseType(form.type()))
                .center(center)
                .build());
    }

    private static TreatmentType parseType(String type) {
        try {
            return TreatmentType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            return TreatmentType.OTRO;
        }
    }
}
