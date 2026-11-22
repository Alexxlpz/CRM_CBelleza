/*
 * Cambiar la contraseña desde «Mi perfil» (cliente) y «Mi cuenta» (trabajador): si se envía con una contraseña nueva
 * de menos de 8 caracteres o distinta de la repetida, el campo se pone en rojo (form-validation.js). Vacía no se
 * cambia, así que vacía vale. El servidor lo vuelve a comprobar.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const input = document.querySelector('input[name="newPassword"][data-password-rules]');
    if (!input || !window.Cuquora?.validateForm) return;
    const V = Cuquora.validators;
    const min = 8; // PasswordPolicy.MIN_LENGTH
    Cuquora.validateForm(input.form, {
        newPassword: [V.minLength(min, `La contraseña debe tener al menos ${min} caracteres.`)],
        confirmPassword: [V.sameAs('newPassword', 'Las contraseñas no coinciden.')]
    });
});
