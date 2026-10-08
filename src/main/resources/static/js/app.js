/*
 * Cuquora — comportamiento común a todas las páginas.
 *  1. CSRF: añade el token a todas las peticiones fetch que modifican datos.
 *  2. Menú lateral móvil (drawer) accesible: foco atrapado, Escape y retorno del foco.
 *  3. Diálogos propios (Cuquora.confirm / Cuquora.alert) en lugar de confirm()/alert() del navegador.
 *  4. Formularios con data-confirm="Mensaje": piden confirmación antes de enviarse.
 *  5. Cuquora.escapeHtml: para insertar texto del servidor en plantillas HTML de JavaScript.
 *  6. Modales estáticos: <div class="ui-modal-overlay" id="x" hidden> se abren con
 *     [data-modal-open="x"] y se cierran con [data-modal-close], Escape o clic fuera.
 *  7. Selector de vista Tarjetas / Lista: [data-view-toggle="idDelListado"] con botones
 *     [data-view="grid|list"]. Añade .is-list-view al listado y recuerda la elección.
 */
(function () {
    'use strict';

    /* ---------- 1. CSRF en fetch ---------- */
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (csrfToken && csrfHeader && window.fetch) {
        const originalFetch = window.fetch.bind(window);
        window.fetch = function (input, init = {}) {
            const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
            const url = new URL(input instanceof Request ? input.url : input, window.location.href);
            if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && url.origin === window.location.origin) {
                const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
                headers.set(csrfHeader, csrfToken);
                headers.set('X-Requested-With', 'XMLHttpRequest');
                init = Object.assign({}, init, { headers });
            }
            return originalFetch(input, init);
        };
    }

    /* ---------- Utilidades ---------- */
    const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** Mantiene el foco del teclado dentro de `container` mientras está abierto. */
    function trapFocus(container, event) {
        if (event.key !== 'Tab') return;
        const items = Array.from(container.querySelectorAll(FOCUSABLE)).filter(el => el.offsetParent !== null);
        if (!items.length) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    /* ---------- 2. Menú lateral móvil ---------- */
    function initMobileNav() {
        const drawer = document.getElementById('mobileNavDrawer');
        const backdrop = document.getElementById('mobileNavBackdrop');
        const toggles = document.querySelectorAll('.mobile-nav-toggle');
        const closeBtn = document.getElementById('mobileDrawerClose');
        if (!drawer || !backdrop) return;

        // Se mueve a <body> para que el backdrop-filter de la navbar no recorte los elementos fijos.
        document.body.appendChild(backdrop);
        document.body.appendChild(drawer);

        let lastFocused = null;

        function setOpen(open) {
            drawer.classList.toggle('is-open', open);
            backdrop.classList.toggle('is-open', open);
            drawer.setAttribute('aria-hidden', String(!open));
            drawer.inert = !open;
            document.body.classList.toggle('mobile-nav-locked', open);
            toggles.forEach(btn => {
                btn.setAttribute('aria-expanded', String(open));
                btn.classList.toggle('is-active', open);
            });
            if (open) {
                lastFocused = document.activeElement;
                (closeBtn || drawer.querySelector(FOCUSABLE))?.focus();
            } else if (lastFocused) {
                lastFocused.focus();
            }
        }

        drawer.inert = true;
        toggles.forEach(btn => btn.addEventListener('click', e => {
            e.preventDefault();
            setOpen(!drawer.classList.contains('is-open'));
        }));
        closeBtn?.addEventListener('click', () => setOpen(false));
        backdrop.addEventListener('click', () => setOpen(false));
        drawer.querySelectorAll('a').forEach(link => link.addEventListener('click', () => setOpen(false)));
        drawer.addEventListener('keydown', e => {
            if (e.key === 'Escape') setOpen(false);
            trapFocus(drawer, e);
        });
    }

    /* ---------- 3. Diálogos propios ---------- */
    function openDialog({ title, message, confirmText = 'Aceptar', cancelText = null, tone = 'default' }) {
        return new Promise(resolve => {
            const lastFocused = document.activeElement;
            const overlay = document.createElement('div');
            overlay.className = 'ui-modal-overlay is-open';
            overlay.innerHTML = `
                <div class="ui-modal" role="dialog" aria-modal="true" aria-labelledby="uiModalTitle" aria-describedby="uiModalText">
                    <h2 class="ui-modal-title" id="uiModalTitle">${escapeHtml(title)}</h2>
                    <p class="ui-modal-text" id="uiModalText">${escapeHtml(message)}</p>
                    <div class="ui-modal-actions">
                        ${cancelText ? `<button type="button" class="btn btn-secondary" data-action="cancel">${escapeHtml(cancelText)}</button>` : ''}
                        <button type="button" class="btn ${tone === 'danger' ? 'btn-danger' : 'btn-primary'}" data-action="confirm">${escapeHtml(confirmText)}</button>
                    </div>
                </div>`;

            function close(result) {
                overlay.remove();
                document.removeEventListener('keydown', onKey, true);
                lastFocused?.focus?.();
                resolve(result);
            }
            function onKey(e) {
                if (e.key === 'Escape') { e.stopPropagation(); close(false); }
                trapFocus(overlay, e);
            }

            overlay.addEventListener('click', e => {
                const action = e.target.closest('[data-action]')?.dataset.action;
                if (action) close(action === 'confirm');
                else if (e.target === overlay) close(false);
            });
            document.addEventListener('keydown', onKey, true);
            document.body.appendChild(overlay);
            overlay.querySelector('[data-action="confirm"]').focus();
        });
    }

    const Cuquora = Object.assign(window.Cuquora || {}, {
        escapeHtml,
        trapFocus,
        alert(message, title = 'Aviso') {
            return openDialog({ title, message, tone: 'danger' });
        },
        confirm(message, { title = '¿Estás seguro?', confirmText = 'Confirmar', cancelText = 'Cancelar', tone = 'danger' } = {}) {
            return openDialog({ title, message, confirmText, cancelText, tone });
        },
        /** Lee el mensaje de error que devuelve la API ({message}) o usa uno genérico. */
        async errorMessage(response, fallback) {
            try {
                const body = await response.json();
                return body.message || fallback;
            } catch (e) {
                return fallback;
            }
        }
    });
    window.Cuquora = Cuquora;

    /* ---------- 6. Modales estáticos ---------- */
    const modalState = new Map();

    function openModal(overlay) {
        if (!overlay) return;
        modalState.set(overlay, document.activeElement);
        overlay.hidden = false;
        overlay.classList.add('is-open');
        document.body.classList.add('modal-open');
        (overlay.querySelector('[autofocus]') || overlay.querySelector(FOCUSABLE))?.focus();
    }

    function closeModal(overlay) {
        if (!overlay || overlay.hidden) return;
        overlay.classList.remove('is-open');
        overlay.hidden = true;
        if (!document.querySelector('.ui-modal-overlay.is-open')) {
            document.body.classList.remove('modal-open');
        }
        modalState.get(overlay)?.focus?.();
        modalState.delete(overlay);
        overlay.dispatchEvent(new CustomEvent('modalclose'));
    }

    Cuquora.openModal = id => openModal(document.getElementById(id));
    Cuquora.closeModal = id => closeModal(document.getElementById(id));

    document.addEventListener('click', e => {
        const opener = e.target.closest('[data-modal-open]');
        if (opener) {
            e.preventDefault();
            openModal(document.getElementById(opener.dataset.modalOpen));
            return;
        }
        const closer = e.target.closest('[data-modal-close]');
        if (closer) {
            closeModal(closer.closest('.ui-modal-overlay'));
            return;
        }
        if (e.target.classList?.contains('ui-modal-overlay') && e.target.id) {
            closeModal(e.target);
        }
    });

    document.addEventListener('keydown', e => {
        const open = Array.from(document.querySelectorAll('.ui-modal-overlay.is-open[id]')).pop();
        if (!open) return;
        if (e.key === 'Escape') closeModal(open);
        else trapFocus(open, e);
    });

    /* ---------- 7. Campana de notificaciones ---------- */
    function initNotifications() {
        const toggle = document.getElementById('notificationsToggle');
        const dropdown = document.getElementById('notificationsDropdown');
        if (!toggle || !dropdown) return;
        const setOpen = open => {
            dropdown.hidden = !open;
            dropdown.classList.toggle('is-open', open);
            toggle.setAttribute('aria-expanded', String(open));
        };
        toggle.addEventListener('click', e => {
            e.stopPropagation();
            setOpen(dropdown.hidden);
        });
        document.addEventListener('click', e => {
            if (!dropdown.hidden && !e.target.closest('.notifications-container')) setOpen(false);
        });
        dropdown.addEventListener('keydown', e => {
            if (e.key === 'Escape') { setOpen(false); toggle.focus(); }
        });
    }

    /* ---------- 4. Formularios con confirmación ---------- */
    document.addEventListener('submit', async e => {
        const form = e.target;
        if (!(form instanceof HTMLFormElement) || !form.dataset.confirm || form.dataset.confirmed === 'true') return;
        e.preventDefault();
        const ok = await Cuquora.confirm(form.dataset.confirm, {
            title: form.dataset.confirmTitle || '¿Estás seguro?',
            confirmText: form.dataset.confirmButton || 'Confirmar'
        });
        if (ok) {
            form.dataset.confirmed = 'true';
            form.requestSubmit ? form.requestSubmit() : form.submit();
        }
    });

    /* ---------- 7. Vista tarjetas / lista ---------- */
    function initViewToggles() {
        document.querySelectorAll('[data-view-toggle]').forEach(toggle => {
            const list = document.getElementById(toggle.dataset.viewToggle);
            if (!list) return;
            const key = 'cuquora.view.' + toggle.dataset.viewToggle;
            const buttons = toggle.querySelectorAll('[data-view]');
            const apply = view => {
                list.classList.toggle('is-list-view', view === 'list');
                buttons.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.view === view)));
            };
            let saved = null;
            try { saved = localStorage.getItem(key); } catch (e) { /* almacenamiento no disponible */ }
            if (saved === 'grid' || saved === 'list') apply(saved);
            buttons.forEach(b => b.addEventListener('click', () => {
                apply(b.dataset.view);
                try { localStorage.setItem(key, b.dataset.view); } catch (e) { /* almacenamiento no disponible */ }
            }));
        });
    }

    function init() {
        initMobileNav();
        initNotifications();
        initViewToggles();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
