/*
 * Validación en vivo de formularios (login, registro, contacto y alta de centro).
 * El botón de envío queda "deshabilitado" (clase .disabled) hasta que el formulario es válido;
 * si se pulsa igualmente, se marcan los campos con error.
 *
 * Uso:
 *   Cuquora.validateForm(document.getElementById('loginForm'), {
 *       identifier: [V.required('Introduce tu correo'), V.minLength(3, 'Mínimo 3 caracteres')],
 *       password:   [V.required('Introduce tu contraseña')]
 *   }, { onSubmit: () => showSpinner() });
 * Las claves son el atributo name del campo. Cada regla devuelve '' si es válida o el mensaje de error.
 */
(function () {
    'use strict';

    const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    const validators = {
        required: message => (value, el) => {
            if (el.type === 'checkbox') return el.checked ? '' : message;
            return value.trim() ? '' : message;
        },
        email: (message = 'Formato de correo no válido. Debe incluir "@" y un dominio (ej. usuario@gmail.com).') =>
            value => !value.trim() || EMAIL.test(value.trim()) ? '' : message,
        minLength: (min, message) => value => !value || value.trim().length >= min ? '' : message,
        phone: (message = 'Introduce un número de teléfono válido (mínimo 6 dígitos).') =>
            value => !value.trim() || value.replace(/\D/g, '').length >= 6 ? '' : message,
        sameAs: (otherName, message) => (value, el) =>
            value === (el.form.elements[otherName]?.value ?? '') ? '' : message,
        /** Valida como email si contiene "@"; si no, exige longitud mínima (usuario). */
        emailOrUsername: (minUser, userMessage) => value => {
            const v = value.trim();
            if (!v) return '';
            if (v.includes('@')) return EMAIL.test(v) ? '' : 'Formato de correo no válido.';
            return v.length >= minUser ? '' : userMessage;
        }
    };

    const ERROR_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>';

    function container(el) {
        return el.closest('.form-group, .form-item-field, .form-group-item, .terms-check-row') || el.parentNode;
    }

    function clearError(el) {
        el.classList.remove('has-error', 'shake-field');
        el.removeAttribute('aria-invalid');
        container(el).querySelector('.field-error-msg')?.remove();
    }

    function showError(el, message) {
        clearError(el);
        el.classList.add('has-error');
        el.setAttribute('aria-invalid', 'true');
        void el.offsetWidth; // reinicia la animación
        el.classList.add('shake-field');
        const msg = document.createElement('div');
        msg.className = 'field-error-msg';
        msg.setAttribute('role', 'alert');
        msg.innerHTML = ERROR_ICON;
        const text = document.createElement('span');
        text.textContent = message;
        msg.appendChild(text);
        container(el).appendChild(msg);
    }

    function validateForm(form, rules, options = {}) {
        if (!form) return;
        const submitBtn = options.submitButton || form.querySelector('[type="submit"]');
        const fields = Object.entries(rules)
            .map(([name, checks]) => ({ el: form.elements[name], checks: [].concat(checks) }))
            .filter(f => f.el);

        const errorOf = f => {
            for (const check of f.checks) {
                const message = check(f.el.value || '', f.el);
                if (message) return message;
            }
            return '';
        };
        const isValid = () => fields.every(f => !errorOf(f));

        function refreshButton() {
            const valid = isValid();
            submitBtn?.classList.toggle('disabled', !valid);
            if (valid) submitBtn?.removeAttribute('aria-disabled');
            else submitBtn?.setAttribute('aria-disabled', 'true');
        }

        function highlightAll() {
            let first = null;
            fields.forEach(f => {
                const message = errorOf(f);
                if (message) {
                    showError(f.el, message);
                    first = first || f.el;
                } else {
                    clearError(f.el);
                }
            });
            if (first) {
                first.scrollIntoView({ behavior: 'smooth', block: 'center' });
                first.focus({ preventScroll: true });
            }
        }

        fields.forEach(f => {
            const onInput = () => {
                if (!errorOf(f)) clearError(f.el);
                refreshButton();
            };
            f.el.addEventListener('input', onInput);
            f.el.addEventListener('change', onInput);
            f.el.addEventListener('blur', () => {
                if (!(f.el.value || '').trim()) return;
                const message = errorOf(f);
                message ? showError(f.el, message) : clearError(f.el);
            });
        });

        form.addEventListener('submit', e => {
            if (!isValid()) {
                e.preventDefault();
                highlightAll();
                return;
            }
            options.onSubmit?.(e);
        });

        refreshButton();
        // Los gestores de contraseñas rellenan los campos después de cargar la página.
        setTimeout(refreshButton, 300);
        return { isValid, refresh: refreshButton };
    }

    /** Muestra u oculta la contraseña del campo asociado al botón (data-toggle-password="idDelInput"). */
    document.addEventListener('click', e => {
        const btn = e.target.closest('[data-toggle-password]');
        if (!btn) return;
        const input = document.getElementById(btn.dataset.togglePassword);
        if (!input) return;
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.setAttribute('aria-pressed', String(show));
        const icon = btn.querySelector('i');
        if (icon) icon.className = show ? 'lucide-eye-off' : 'lucide-eye';
    });

    window.Cuquora = window.Cuquora || {};
    window.Cuquora.validators = validators;
    window.Cuquora.validateForm = validateForm;
})();
