package com.alexxlpz.crm_cbelleza.forms;

/**
 * Datos personales editables por el propio usuario (nombre, teléfono y contraseña opcional). Para cambiar la
 * contraseña hay que escribir la actual ({@code currentPassword}), salvo en cuentas que aún no tienen (solo Google).
 */
public record ProfileForm(String name, String phone, String currentPassword, String newPassword, String confirmPassword) {
}
