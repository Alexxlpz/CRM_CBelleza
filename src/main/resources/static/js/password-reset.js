/* «¿Olvidaste tu contraseña?»: validación del correo y de la contraseña nueva (el servidor la vuelve a comprobar). */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const V = Cuquora.validators;

    const forgot = document.getElementById('forgotForm');
    if (forgot) {
        Cuquora.validateForm(forgot, {
            email: [V.required('Escribe tu correo electrónico.'), V.email('Revisa el correo: tiene que ser como usuario@gmail.com.')]
        });
    }

    const reset = document.getElementById('resetForm');
    if (reset) {
        const min = Number(document.getElementById('submitBtn').dataset.minLength) || 8;
        Cuquora.validateForm(reset, {
            password: [V.required('Escribe una contraseña.'), V.minLength(min, `La contraseña debe tener al menos ${min} caracteres.`)],
            confirmPassword: [V.required('Repite la contraseña.'), V.sameAs('password', 'Las contraseñas no coinciden.')]
        });
    }
});
