package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.CenterForm;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos de los centros. */
@Service
@Transactional
public class CenterService {

    private final CenterRepository centerRepository;

    public CenterService(CenterRepository centerRepository) {
        this.centerRepository = centerRepository;
    }

    @Transactional(readOnly = true)
    public Center getCenterById(Long id) {
        return centerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El centro solicitado no existe."));
    }

    /** Actualiza solo los campos informados; un nombre vacío no borra el actual. */
    public Center updateCenter(Long centerId, CenterForm form) {
        Center center = getCenterById(centerId);
        if (!FormText.isBlank(form.name())) {
            center.setName(form.name().trim());
        }
        if (form.address() != null) center.setAddress(form.address().trim());
        if (form.phone() != null) center.setPhone(form.phone().trim());
        if (form.email() != null) center.setEmail(form.email().trim());
        if (form.latitude() != null) center.setLatitude(form.latitude());
        if (form.longitude() != null) center.setLongitude(form.longitude());
        return centerRepository.save(center);
    }
}
