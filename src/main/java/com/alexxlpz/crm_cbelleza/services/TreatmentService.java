package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TreatmentService {
    private final TreatmentRepository treatmentRepository;
    private final CenterRepository centerRepository;

    public TreatmentService(TreatmentRepository treatmentRepository, CenterRepository centerRepository) {
        this.treatmentRepository = treatmentRepository;
        this.centerRepository = centerRepository;
    }

    public List<Treatment> getTreatmentsByCenter(Long centerId) {
        return treatmentRepository.findByCenterId(centerId);
    }

    public void addTreatment(Long centerId, String name, String description, Double price, Integer duration, String type) {
        Center center = centerRepository.findById(centerId).orElseThrow(() -> new IllegalArgumentException("Invalid center ID"));

        Treatment treatment = Treatment.builder()
                .name(name)
                .description(description)
                .price(price)
                .duration(duration)
                .type(TreatmentType.valueOf(type))
                .center(center)
                .build();
        treatmentRepository.save(treatment);
    }
}
