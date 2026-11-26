/*
 * «Ajustes → Centro»: avisos en rojo debajo de cada campo de «Datos del centro» (form-validation.js) antes de
 * enviar: nombre, dirección, teléfono con su prefijo (phone-input.js), correo y cabinas. Las mismas reglas que
 * CenterService.updateCenter, que lo vuelve a comprobar.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('centerDataForm');
    if (!form || !window.Cuquora?.validateForm) return;
    const V = Cuquora.validators;
    const rules = {
        centerName: [V.required('Escribe el nombre del centro.'), V.minLength(2, 'El nombre tiene que tener al menos 2 letras.')],
        centerAddress: [V.required('Escribe la dirección: calle, número, código postal y ciudad.'),
            V.minLength(5, 'Escribe la dirección completa: calle, número, código postal y ciudad.')],
        centerEmail: [V.required('Escribe el correo del centro: ahí te llegan los avisos de las reservas.'),
            V.email('Revisa el correo: tiene que ser como info@tucentro.com.')],
        cabins: [value => /^\d+$/.test(value.trim()) && +value >= 1 && +value <= 50
            ? '' : 'El número de cabinas tiene que estar entre 1 y 50.']
    };
    if (V.phoneNumber) rules.centerPhone = [V.phoneNumber()];
    Cuquora.validateForm(form, rules);
});
