/*
 * Foto de presentación del centro (fragments/photo-picker.html): enseña la foto elegida antes de subirla
 * y avisa al momento si no es JPG o PNG o pesa demasiado, en vez de esperar a que conteste el servidor.
 * Las <img data-photo-mirror> de la página (la vista previa del alta) muestran la misma foto.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    document.querySelectorAll('[data-photo-picker]').forEach(picker => {
        const input = picker.querySelector('input[type="file"]');
        const img = picker.querySelector('[data-photo-img]');
        const initials = picker.querySelector('[data-photo-initials]');
        const error = picker.querySelector('[data-photo-error]');
        const name = picker.querySelector('[data-photo-name]');
        const maxBytes = (Number(picker.dataset.maxMb) || 8) * 1024 * 1024;
        const original = img.getAttribute('src');
        let objectUrl = null;

        function show(src) {
            const targets = [img, ...document.querySelectorAll('[data-photo-mirror]')];
            targets.forEach(target => {
                if (src) target.src = src; else target.removeAttribute('src');
                target.hidden = !src;
            });
            initials.hidden = !!src;
            document.querySelectorAll('[data-photo-mirror-initials]').forEach(el => { el.hidden = !!src; });
        }

        function fail(message) {
            error.textContent = message;
            error.hidden = false;
            input.value = '';
            name.hidden = true;
            show(original);
        }

        input.addEventListener('change', () => {
            if (objectUrl) URL.revokeObjectURL(objectUrl);
            objectUrl = null;
            error.hidden = true;
            name.hidden = true;
            const file = input.files[0];
            if (!file) {
                show(original);
                return;
            }
            if (!['image/jpeg', 'image/png'].includes(file.type)) {
                fail('Elige una foto en JPG o PNG.');
                return;
            }
            if (file.size > maxBytes) {
                fail(`La foto pesa más de ${picker.dataset.maxMb} MB. Elige una más ligera.`);
                return;
            }
            objectUrl = URL.createObjectURL(file);
            show(objectUrl);
            name.textContent = file.name;
            name.hidden = false;
        });
    });
});
