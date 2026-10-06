package com.alexxlpz.crm_cbelleza.forms;

/** Datos personales editables por el propio usuario (nombre, teléfono y contraseña opcional). */
public record ProfileForm(String name, String phone, String newPassword, String confirmPassword) {
}
