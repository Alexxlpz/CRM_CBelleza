/* Invitación de trabajador: validación de la contraseña nueva (el servidor la vuelve a comprobar). */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('invitationForm');
    if (!form) return;
    const V = Cuquora.validators;
    const min = Number(document.getElementById('submitBtn').dataset.minLength) || 6;
    Cuquora.validateForm(form, {
        password: [V.required('Escribe una contraseña.'), V.minLength(min, `La contraseña debe tener al menos ${min} caracteres.`)],
        confirmPassword: [V.required('Repite la contraseña.'), V.sameAs('password', 'Las contraseñas no coinciden.')]
    });
});
