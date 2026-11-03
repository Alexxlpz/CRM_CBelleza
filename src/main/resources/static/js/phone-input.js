/*
 * Teléfono con prefijo aparte (fragments/phone-input.html): agrupa las cifras mientras se escriben
 * («612 345 678») y, si se escribe o se pega el número con «+» o «00» delante, elige ese prefijo en la lista.
 * Las reglas son las de PhoneNumbers.java: si cambia una allí, hay que cambiarla aquí.
 * Añade Cuquora.validators.phoneNumber() para form-validation.js.
 */
(function () {
    'use strict';

    // Agrupación cuando el número tiene la longitud habitual del país
    const GROUPS = { 34: [3, 3, 3], 351: [3, 3, 3], 33: [1, 2, 2, 2, 2], 1: [3, 3, 4], 56: [1, 4, 4], 44: [4, 6] };
    // Países en los que el 0 inicial de uso nacional no se marca con el prefijo
    const TRUNK_ZERO = new Set(['33', '44', '49', '31', '32', '41', '43', '353', '54', '40', '212']);
    const MAX_DIGITS = 15;

    function defaultGroups(n) {
        if (n <= 4) return [n];
        if (n === 5) return [2, 3];
        const tail = n % 3 === 1 ? 4 : n % 3 === 2 ? 8 : 0;
        const sizes = [];
        let rest = n;
        while (rest > tail) { sizes.push(3); rest -= 3; }
        if (tail === 4) sizes.push(4);
        if (tail === 8) sizes.push(4, 4);
        return sizes;
    }

    /** Cifras en grupos; con {@code partial}, mientras se escribe (aún no se sabe la longitud final). */
    function group(code, digits, partial) {
        let sizes = GROUPS[code];
        const total = sizes ? sizes.reduce((a, b) => a + b, 0) : 0;
        // A medias (o aún corto para el país), como irá quedando; si no, como lo guardará el servidor
        if (!sizes || total !== digits.length) {
            sizes = partial || digits.length < total ? (sizes || []).concat(Array(8).fill(3)) : defaultGroups(digits.length);
        }
        const parts = [];
        let at = 0;
        for (const size of sizes) {
            if (at >= digits.length) break;
            parts.push(digits.slice(at, at + size));
            at += size;
        }
        return parts.join(' ');
    }

    function problem(code, digits) {
        switch (code) {
            case '34': return /^[6789]\d{8}$/.test(digits) ? ''
                : 'Un teléfono de España tiene 9 cifras y empieza por 6, 7, 8 o 9 (ej.: 612 345 678).';
            case '351': return /^[29]\d{8}$/.test(digits) ? ''
                : 'Un teléfono de Portugal tiene 9 cifras y empieza por 2 o 9 (ej.: 912 345 678).';
            case '33': return /^[1-9]\d{8}$/.test(digits) ? ''
                : 'Un teléfono de Francia tiene 9 cifras sin el 0 inicial (ej.: 6 12 34 56 78).';
            case '1': return /^[2-9]\d{9}$/.test(digits) ? ''
                : 'Un teléfono de EE. UU., Canadá o el Caribe tiene 10 cifras (ej.: 212 555 0123).';
            default: {
                const max = MAX_DIGITS - code.length;
                return digits.length >= 4 && digits.length <= max ? ''
                    : `Revisa el teléfono: sin el prefijo tiene que tener entre 4 y ${max} cifras.`;
            }
        }
    }

    function codesOf(root) {
        return new Set((root.dataset.callingCodes || '').split(' ').filter(Boolean));
    }

    function callingCodeOf(codes, digits) {
        for (let length = 1; length <= 3 && length <= digits.length; length++) {
            if (codes.has(digits.slice(0, length))) return digits.slice(0, length);
        }
        return '';
    }

    function selectCountry(select, code) {
        if (![...select.options].some(o => o.value === code)) {
            const option = new Option(`+${code} (otro país)`, code);
            option.dataset.long = option.text;
            option.dataset.short = `+${code}`;
            select.add(option);
        }
        select.value = code;
    }

    /** Cifras que admite el número sin el prefijo: las del país (más el 0 inicial donde se usa) o hasta 15 en total. */
    function maxDigits(code, value) {
        const sizes = GROUPS[code];
        const extraZero = TRUNK_ZERO.has(code) && value.replace(/\D/g, '').startsWith('0') ? 1 : 0;
        return (sizes ? sizes.reduce((a, b) => a + b, 0) : MAX_DIGITS - code.length) + extraZero;
    }

    /** Cifras nacionales tal como las entenderá el servidor (sin el prefijo pegado ni el 0 inicial). */
    function nationalDigits(code, value) {
        let digits = value.replace(/\D/g, '');
        const sizes = GROUPS[code];
        if (sizes && digits.startsWith(code) && digits.length === code.length + sizes.reduce((a, b) => a + b, 0)) {
            digits = digits.slice(code.length);
        }
        if (TRUNK_ZERO.has(code) && digits.startsWith('0')) digits = digits.slice(1);
        return digits;
    }

    /** Mensaje de error del teléfono, o '' si vale (o está vacío: eso lo dice la regla «required»). */
    function phoneProblem(input) {
        const root = input.closest('[data-phone-input]');
        const value = input.value.trim();
        if (!value || !root) return '';
        if (!/^[+0-9 ()./-]+$/.test(value) || value.lastIndexOf('+') > 0) {
            return 'El teléfono solo puede llevar números y espacios (y el prefijo con «+» delante).';
        }
        const select = root.querySelector('[data-phone-country]');
        return problem(select.value, nationalDigits(select.value, value));
    }

    function setup(root) {
        const select = root.querySelector('[data-phone-country]');
        const input = root.querySelector('[data-phone-number]');
        const codes = codesOf(root);

        // «+44 7700…» o «0044 7700…»: el prefijo pasa a la lista y en el campo queda el resto
        function takeCountry() {
            const value = input.value.trim();
            if (!value.startsWith('+') && !value.startsWith('00')) return;
            const digits = value.replace(/\D/g, '').slice(value.startsWith('00') ? 2 : 0);
            const code = callingCodeOf(codes, digits);
            if (!code) return;
            selectCountry(select, code);
            input.value = digits.slice(code.length);
            select.dispatchEvent(new Event('change'));
        }

        function format(partial) {
            if (!/^[\d\s().-]*$/.test(input.value)) return; // con otros caracteres se deja tal cual para que se vea el error
            const code = select.value;
            const digits = partial ? input.value.replace(/\D/g, '') : nationalDigits(code, input.value);
            input.value = group(code, digits, partial);
        }

        // Cerrada, la lista solo enseña el prefijo («+34»); al abrirla, también el país («+34 (España)»)
        const showLong = () => [...select.options].forEach(o => { if (o.dataset.long) o.text = o.dataset.long; });
        const showShort = () => {
            showLong();
            const chosen = select.selectedOptions[0];
            if (chosen?.dataset.short) chosen.text = chosen.dataset.short;
        };
        select.addEventListener('mousedown', showLong);
        select.addEventListener('touchstart', showLong, { passive: true });
        select.addEventListener('keydown', e => { if (e.key !== 'Tab') showLong(); });
        select.addEventListener('change', showShort);
        select.addEventListener('blur', showShort);

        // No deja pasar de las cifras del país (9 en España). Con «+» o «00» delante se deja: trae su propio prefijo
        const tooLong = value => !/^\s*(\+|00)/.test(value) && value.replace(/\D/g, '').length > maxDigits(select.value, value);
        input.addEventListener('beforeinput', e => {
            // Solo números: el prefijo va en la lista y los espacios los pone el formato
            if (e.inputType === 'insertText' && /[^\d ]/.test(e.data || '')) {
                e.preventDefault();
                return;
            }
            if (!e.inputType.startsWith('insert') || e.inputType === 'insertFromPaste') return;
            const next = input.value.slice(0, input.selectionStart) + (e.data || '') + input.value.slice(input.selectionEnd);
            if (tooLong(next)) e.preventDefault();
        });

        input.addEventListener('input', e => {
            takeCountry();
            // Lo pegado o autocompletado con otros caracteres («(91) 234-56-78») se queda en cifras
            if (/[^\d ]/.test(input.value)) input.value = input.value.replace(/[^\d ]/g, '');
            // Lo pegado (o lo que se cuele) se recorta a las cifras del país
            if (tooLong(input.value)) {
                let keep = maxDigits(select.value, input.value);
                input.value = [...input.value].filter(c => !/\d/.test(c) || keep-- > 0).join('').trimEnd();
            }
            // Solo al escribir al final: en medio movería el cursor
            const atEnd = input.selectionStart === input.value.length;
            if (atEnd && e.inputType !== 'deleteContentBackward') format(true);
        });
        input.addEventListener('blur', () => format(false));
        select.addEventListener('change', () => {
            format(false);
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        takeCountry();
        format(false);
        showShort();
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('[data-phone-input]').forEach(setup);
    });

    window.Cuquora = window.Cuquora || {};
    if (window.Cuquora.validators) {
        window.Cuquora.validators.phoneNumber = () => (value, el) => phoneProblem(el);
    }
    window.Cuquora.phoneProblem = phoneProblem;
})();
