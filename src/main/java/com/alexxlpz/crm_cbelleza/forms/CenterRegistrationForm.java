package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;

import java.util.List;

/** Solicitud pública de alta de un centro. */
public record CenterRegistrationForm(String centerName, String cif, String contactName, String phone, String email,
                                     String city, List<String> specialties, String notes) {

    public void validate() {
        if (FormText.isBlank(centerName) || FormText.isBlank(cif) || FormText.isBlank(contactName)
                || FormText.isBlank(phone) || FormText.isBlank(email)) {
            throw new BusinessRuleException("Por favor, completa todos los campos obligatorios.");
        }
        if (!email.contains("@")) {
            throw new BusinessRuleException("Introduce un correo electrónico válido.");
        }
    }

    public List<String> specialtiesOrEmpty() {
        return specialties == null ? List.of() : specialties;
    }
}
