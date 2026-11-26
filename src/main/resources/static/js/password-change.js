/*
 * Avisos en rojo debajo de cada campo (form-validation.js) en «Mi perfil» (cliente) y «Mi cuenta» (trabajador):
 * nombre, teléfono con su prefijo (phone-input.js) y, si se cambia, la contraseña nueva (8 caracteres como mínimo e
 * igual a la repetida). Vacía no se cambia, así que vacía vale. El servidor lo vuelve a comprobar.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const input = document.querySelector('input[name="newPassword"][data-password-rules]');
    if (!input || !window.Cuquora?.validateForm) return;
    const V = Cuquora.validators;
    const min = 8; // PasswordPolicy.MIN_LENGTH
    const form = input.form;
    const rules = {
        newPassword: [V.minLength(min, `La contraseña debe tener al menos ${min} caracteres.`)],
        confirmPassword: [V.sameAs('newPassword', 'Las contraseñas no coinciden.')]
    };
    // Nombre y teléfono: «name»/«phone» en el perfil del cliente, «workerName»/«workerPhone» en «Mi cuenta»
    ['name', 'workerName'].filter(name => form.elements[name]).forEach(name => {
        rules[name] = [V.required('Escribe tu nombre.'), V.minLength(2, 'El nombre tiene que tener al menos 2 letras.')];
    });
    ['phone', 'workerPhone'].filter(name => form.elements[name] && V.phoneNumber).forEach(name => {
        rules[name] = [V.phoneNumber()];
    });
    Cuquora.validateForm(form, rules);
});
