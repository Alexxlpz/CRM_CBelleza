package com.alexxlpz.crm_cbelleza.forms;

/**
 * Datos del formulario de alta de clientes (/register). El teléfono es opcional; phoneCountry es el prefijo elegido
 * en la lista (sin «+»), como en el alta de centros.
 */
public record RegistrationForm(String name, String email, String phoneCountry, String phone, String password,
                               String confirmPassword) {

    public String normalizedEmail() {
        return Emails.normalize(email);
    }

    /** Teléfono con su prefijo y en grupos («+34 612 345 678»), o {@code null} si no se ha puesto. */
    public String normalizedPhone() {
        return FormText.isBlank(phone) ? null : PhoneNumbers.normalize(phoneCountry, phone);
    }

    /** Prefijo que sale elegido en la lista: el enviado o, si no hay, España. */
    public String phoneCountryOrDefault() {
        return FormText.isBlank(phoneCountry) ? PhoneNumbers.DEFAULT_COUNTRY : phoneCountry.trim();
    }
}
