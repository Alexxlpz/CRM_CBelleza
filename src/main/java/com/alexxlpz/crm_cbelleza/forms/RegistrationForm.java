package com.alexxlpz.crm_cbelleza.forms;

/** Datos del formulario de alta de clientes (/register). */
public record RegistrationForm(String name, String email, String phone, String password, String confirmPassword) {

    public String normalizedEmail() {
        return email == null ? null : email.trim().toLowerCase();
    }
}
