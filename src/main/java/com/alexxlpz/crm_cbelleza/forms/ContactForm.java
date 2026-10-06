package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;

/** Formulario público de contacto. */
public record ContactForm(String name, String email, String phone, String subject, String message) {

    public void validate() {
        if (FormText.isBlank(name) || FormText.isBlank(email) || FormText.isBlank(subject) || FormText.isBlank(message)) {
            throw new BusinessRuleException("Por favor, completa todos los campos obligatorios.");
        }
        if (!email.contains("@")) {
            throw new BusinessRuleException("Introduce un correo electrónico válido.");
        }
    }
}
