/*
 * Protección de cambios sin guardar para formularios largos (ficha de cliente y plantilla de ficha).
 *  - Guarda con fetch sin recargar la página (Ctrl/Cmd + Enter también guarda).
 *  - Si hay cambios sin guardar y el usuario intenta salir (enlaces, botón atrás del navegador o
 *    del móvil, cerrar la pestaña) muestra un modal: guardar y salir / salir sin guardar / seguir editando.
 *  - Al volver con el botón atrás recarga para mostrar siempre los datos reales del servidor.
 *
 * Uso:
 *   Cuquora.guardUnsavedChanges({
 *       form: document.getElementById('fichaForm'),
 *       modalId: 'unsavedChangesModal',       // .ui-modal-overlay con botones [data-unsaved="save|discard|stay"]
 *       exitUrl: '/worker/clients',
 *       saveButton: document.getElementById('btnSaveFicha'),
 *       successMessage: 'Ficha guardada.',
 *       onSaved: () => { ... },               // opcional
 *       snapshot: () => '...'                 // opcional: estado serializado del formulario
 *   });
 */
(function () {
    'use strict';

    const SPINNER = '<i class="lucide-loader-2 spin" aria-hidden="true"></i> <span>Guardando...</span>';

    function defaultSnapshot(form) {
        return JSON.stringify(Array.from(form.elements)
            .filter(el => el.name && el.type !== 'hidden' && el.type !== 'submit' && el.type !== 'button')
            .map(el => [el.name, (el.value || '').trim()]));
    }

    function guardUnsavedChanges(options) {
        const { form, modalId, exitUrl, saveButton, successMessage, onSaved } = options;
        if (!form) return;
        const modal = document.getElementById(modalId);
        const snapshot = options.snapshot || (() => defaultSnapshot(form));
        const banner = document.getElementById('saveSuccessBanner');
        const bannerText = document.getElementById('saveSuccessMessageText');

        let initial = snapshot();
        let leaving = false;
        let guardPushed = false;

        window.history.replaceState({ page: form.id }, document.title, window.location.href);

        const isDirty = () => !leaving && snapshot() !== initial;
        const modalOpen = () => modal && !modal.hidden;

        function armGuard() {
            if (isDirty() && !guardPushed) {
                window.history.pushState({ unsavedGuard: true }, document.title, window.location.href);
                guardPushed = true;
            }
        }

        function openModal() {
            Cuquora.openModal(modalId);
        }

        function exit() {
            leaving = true;
            if (modalOpen()) Cuquora.closeModal(modalId);
            const onGuardState = !!window.history.state?.unsavedGuard;
            const cameFromApp = document.referrer && document.referrer.startsWith(window.location.origin);
            if (cameFromApp && window.history.length > (onGuardState ? 2 : 1)) {
                window.history.go(onGuardState ? -2 : -1);
            } else {
                window.location.replace(exitUrl);
            }
        }

        async function save(extra) {
            const body = new URLSearchParams(new FormData(form));
            Object.entries(extra || {}).forEach(([k, v]) => body.set(k, v));
            const response = await fetch(form.action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (!response.ok) throw new Error(await Cuquora.errorMessage(response, 'No se han podido guardar los cambios.'));
            initial = snapshot();
        }

        function showSuccess(message) {
            if (banner && bannerText) {
                bannerText.textContent = message;
                banner.hidden = false;
            }
        }

        form.addEventListener('input', armGuard);
        form.addEventListener('change', armGuard);

        form.addEventListener('submit', async e => {
            e.preventDefault();
            const original = saveButton?.innerHTML;
            if (saveButton) {
                saveButton.disabled = true;
                saveButton.innerHTML = SPINNER;
            }
            try {
                await save();
                guardPushed = !!window.history.state?.unsavedGuard;
                showSuccess(successMessage);
                onSaved?.();
                if (saveButton) {
                    saveButton.innerHTML = '<i class="lucide-check-circle" aria-hidden="true"></i> <span>¡Guardado con éxito!</span>';
                    setTimeout(() => { saveButton.disabled = false; saveButton.innerHTML = original; }, 2000);
                }
            } catch (err) {
                // Sin conexión o error inesperado: envío clásico para que el servidor muestre el resultado.
                console.warn('Guardado asíncrono fallido, se usa el envío normal', err);
                leaving = true;
                form.submit();
            }
        });

        form.addEventListener('keydown', e => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                e.preventDefault();
                form.requestSubmit ? form.requestSubmit() : saveButton?.click();
            }
        });

        // Cualquier enlace interno pasa por el aviso si hay cambios.
        document.querySelectorAll('a[href]').forEach(link => {
            const href = link.getAttribute('href');
            if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;
            link.addEventListener('click', e => {
                if (link.dataset.exitLink !== undefined) {
                    e.preventDefault();
                    isDirty() ? openModal() : exit();
                } else if (isDirty()) {
                    e.preventDefault();
                    openModal();
                }
            });
        });

        // Botón atrás del navegador / gesto del móvil
        window.addEventListener('popstate', () => {
            if (leaving) return;
            if (modalOpen()) {
                Cuquora.closeModal(modalId);
                return;
            }
            guardPushed = false;
            isDirty() ? openModal() : exit();
        });

        window.addEventListener('beforeunload', e => {
            if (isDirty()) {
                e.preventDefault();
                e.returnValue = '';
            }
        });

        // Al volver con atrás/adelante se recarga para no mostrar datos antiguos de la caché del navegador.
        window.addEventListener('pageshow', event => {
            const nav = performance.getEntriesByType?.('navigation')[0];
            if (event.persisted || nav?.type === 'back_forward') {
                window.location.reload();
            }
        });

        modal?.addEventListener('modalclose', () => {
            if (!leaving) {
                guardPushed = false;
                armGuard();
            }
        });
        modal?.querySelector('[data-unsaved="discard"]')?.addEventListener('click', exit);
        modal?.querySelector('[data-unsaved="stay"]')?.addEventListener('click', () => Cuquora.closeModal(modalId));
        modal?.querySelector('[data-unsaved="save"]')?.addEventListener('click', async e => {
            const button = e.currentTarget;
            const original = button.innerHTML;
            button.disabled = true;
            button.innerHTML = SPINNER;
            try {
                await save({ redirectAfter: exitUrl });
                exit();
            } catch (err) {
                button.disabled = false;
                button.innerHTML = original;
                Cuquora.alert(err.message, 'No se han podido guardar los cambios');
            }
        });

        return { markClean: () => { initial = snapshot(); }, isDirty, armGuard };
    }

    window.Cuquora = window.Cuquora || {};
    window.Cuquora.guardUnsavedChanges = guardUnsavedChanges;
})();
