/*
 * Cuquora — comportamiento común a todas las páginas.
 *  1. CSRF: añade el token a todas las peticiones fetch que modifican datos.
 *  2. Menú lateral móvil (drawer) accesible: foco atrapado, Escape y retorno del foco.
 *  3. Diálogos propios (Cuquora.confirm / Cuquora.alert) en lugar de confirm()/alert() del navegador.
 *  4. Formularios con data-confirm="Mensaje": piden confirmación antes de enviarse.
 *  5. Cuquora.escapeHtml: para insertar texto del servidor en plantillas HTML de JavaScript; Cuquora.telLink: teléfono que llama.
 *  6. Modales estáticos: <div class="ui-modal-overlay" id="x" hidden> se abren con
 *     [data-modal-open="x"] y se cierran con [data-modal-close], Escape o clic fuera.
 *  7. Selector de vista Tarjetas / Lista: [data-view-toggle="idDelListado"] con botones
 *     [data-view="grid|list"]. Añade .is-list-view al listado y recuerda la elección.
 *  8. Aviso de cookies (#cookieBanner, en fragments/footer): se muestra hasta que se pulsa «Entendido».
 *  9. «¿Qué tal tu cita?» (#reviewPrompt, en fragments/footer): sale una vez por cita, después del aviso de cookies.
 * 10. Botones [data-copy="idDelCampo"]: copian el texto del campo y lo confirman en el propio botón.
 * 11. Color al estirar: <html> lleva .is-near-end en la mitad final de la página, para que el rebote
 *     de abajo enseñe el color del pie y el de arriba el de la cabecera (common.css).
 * 12. Botón del ojo (data-toggle-password="idDelInput"): muestra u oculta lo escrito en un campo de contraseña.
 * 13. «Cómo llegar» (enlaces [data-directions] a Google Maps): en iPhone, iPad y Mac deja elegir Apple Maps o Google Maps.
 * 14. Contraseñas (input[data-password-rules] las nuevas, input[data-password-limit] la del login): contador «12/32»;
 *     en las nuevas, además, solo los caracteres que admite PasswordPolicy.
 */
(function () {
    'use strict';

    /* ---------- 1. CSRF en fetch ---------- */
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (csrfToken && csrfHeader && window.fetch) {
        const originalFetch = window.fetch.bind(window);
        window.fetch = function (input, options = {}) {
            const method = (options.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
            const url = new URL(input instanceof Request ? input.url : input, window.location.href);
            if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && url.origin === window.location.origin) {
                const headers = new Headers(options.headers || (input instanceof Request ? input.headers : undefined));
                headers.set(csrfHeader, csrfToken);
                headers.set('X-Requested-With', 'XMLHttpRequest');
                options = Object.assign({}, options, { headers });
            }
            return originalFetch(input, options);
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

    /** Teléfono que se pulsa para llamar (.tel-link), como Phones.telHref en las plantillas; sin número, «—». */
    function telLink(phone) {
        const digits = String(phone ?? '').replace(/[^\d+]/g, '');
        if (!digits) return '—';
        return `<a class="tel-link" href="tel:${escapeHtml(digits)}">${escapeHtml(phone)}</a>`;
    }

    /** Hasta dos iniciales de un nombre, como Center.getInitials() (hueco de la foto de un centro sin foto). */
    function initials(name) {
        return String(name ?? '').trim().split(/[\s-]+/)
            .filter(word => /^[\p{L}\p{N}]/u.test(word))
            .slice(0, 2)
            .map(word => String.fromCodePoint(word.codePointAt(0)).toUpperCase())
            .join('');
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
        initials,
        telLink,
        /** 'smooth' salvo si el sistema pide reducir el movimiento (el CSS no frena el scroll hecho con JS). */
        scrollBehavior() {
            return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
        },
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

    /* ---------- 6b. Modales y paneles con el teclado del móvil abierto ----------
       En iPhone el teclado tapa la parte de abajo sin encoger la página, así que un modal pegado abajo quedaba
       detrás del teclado y, con la página bloqueada, no había forma de llegar a los campos. Mientras hay un modal
       abierto, --vv-top y --vv-height dicen qué parte de la pantalla se ve; el CSS encaja el fondo del modal
       ahí y el modal hace scroll por dentro. El campo con el foco se lleva a la vista. */
    const vv = window.visualViewport;
    if (vv) {
        const root = document.documentElement;
        let frame = 0;
        let lastHeight = 0;
        let reveal = false;
        // Lleva el campo al centro moviendo solo el modal, nunca la página: en iPhone mover la página hace que
        // el teclado vuelva a mover la vista y el usuario ya no podía desplazarse.
        const revealField = () => {
            const field = document.activeElement;
            if (!field?.matches?.('input, textarea, select')) return;
            const box = field.closest('.ui-modal, .sheet-content, .info-modal-card');
            if (!box || !box.closest('.is-open')) return;
            const f = field.getBoundingClientRect();
            const r = box.getBoundingClientRect();
            if (f.top >= r.top + 8 && f.bottom <= r.bottom - 8) return; // ya se ve: no se toca
            box.scrollTop += (f.top + f.height / 2) - (r.top + r.height / 2);
        };
        const fit = () => {
            frame = 0;
            if (!document.body.classList.contains('modal-open')) {
                root.style.removeProperty('--vv-top');
                root.style.removeProperty('--vv-height');
                lastHeight = 0;
                return;
            }
            root.style.setProperty('--vv-top', vv.offsetTop + 'px');
            root.style.setProperty('--vv-height', vv.height + 'px');
            // Solo al tocar un campo o cuando el teclado sale o cambia de tamaño, no mientras el usuario se desplaza
            if (reveal || Math.abs(vv.height - lastHeight) > 60) requestAnimationFrame(revealField);
            reveal = false;
            lastHeight = vv.height;
        };
        const schedule = () => { if (!frame) frame = requestAnimationFrame(fit); };
        vv.addEventListener('resize', schedule);
        vv.addEventListener('scroll', schedule);
        document.addEventListener('focusin', e => {
            if (e.target.matches?.('input, textarea, select')) reveal = true;
            schedule();
        });
        new MutationObserver(schedule).observe(document.body, { attributes: true, attributeFilter: ['class'] });
        // Si salta la barra o el panel de contraseñas de iOS, o se cierra el teclado, a veces no llega ningún
        // aviso de la vista: se vuelve a medir al recuperar la ventana y un poco después de soltar el campo.
        window.addEventListener('resize', schedule);
        window.addEventListener('focus', () => setTimeout(schedule, 300));
        document.addEventListener('focusout', () => setTimeout(schedule, 350));
    }

    /* ---------- 7. Campana de notificaciones ---------- */
    function initNotifications() {
        // Campana del trabajador o del cliente: el botón apunta a su desplegable con aria-controls
        const toggle = document.querySelector('[data-notifications-toggle]');
        const dropdown = toggle && document.getElementById(toggle.getAttribute('aria-controls'));
        if (!toggle || !dropdown) return;
        const setOpen = open => {
            dropdown.hidden = !open;
            dropdown.classList.toggle('is-open', open);
            toggle.setAttribute('aria-expanded', String(open));
        };
        toggle.addEventListener('click', e => {
            e.stopPropagation();
            const opening = dropdown.hidden;
            setOpen(opening);
            // Campana del cliente: al abrirla, los avisos dejan de contar como nuevos
            if (opening && toggle.dataset.seenUrl && Number(toggle.dataset.unseen) > 0) {
                toggle.dataset.unseen = '0';
                toggle.querySelector('.bell-badge')?.remove();
                fetch(toggle.dataset.seenUrl, { method: 'POST' }).catch(err => console.error(err));
            }
        });
        document.addEventListener('click', e => {
            if (!dropdown.hidden && !e.target.closest('.notifications-container')) setOpen(false);
        });
        dropdown.addEventListener('keydown', e => {
            if (e.key === 'Escape') { setOpen(false); toggle.focus(); }
        });
        initNotificationDismiss(toggle, dropdown);
        containScroll(dropdown, dropdown.querySelector('.notifications-body'));
    }

    /* Con el puntero (o el dedo) sobre el desplegable, la página de detrás no se mueve nunca:
       solo se desplaza la lista, y cuando llega al final o no tiene scroll el gesto se queda ahí */
    function containScroll(panel, list) {
        const canScroll = (target, deltaY) => {
            if (!list || !list.contains(target) || deltaY === 0) return false;
            if (deltaY < 0) return list.scrollTop > 0;
            return Math.ceil(list.scrollTop + list.clientHeight) < list.scrollHeight;
        };
        panel.addEventListener('wheel', e => {
            if (!canScroll(e.target, e.deltaY)) e.preventDefault();
        }, { passive: false });
        let lastY = 0;
        panel.addEventListener('touchstart', e => { lastY = e.touches[0].clientY; }, { passive: true });
        panel.addEventListener('touchmove', e => {
            const y = e.touches[0].clientY;
            const deltaY = lastY - y;
            lastY = y;
            if (e.cancelable && !canScroll(e.target, deltaY)) e.preventDefault();
        }, { passive: false });
    }

    /* «x» en cada aviso y «Limpiar todas»: el servidor recuerda los quitados y no vuelven a salir */
    function initNotificationDismiss(toggle, dropdown) {
        const url = dropdown.dataset.dismissUrl;
        if (!url) return;
        const clearAll = dropdown.querySelector('[data-notifications-clear]');
        const empty = dropdown.querySelector('.notification-empty');
        const rows = () => Array.from(dropdown.querySelectorAll('.notification-row:not(.is-leaving)'));

        // La insignia del trabajador cuenta los avisos; la del cliente, los nuevos (ya se quita al abrir)
        const refresh = () => {
            const left = rows().length;
            if (clearAll) clearAll.hidden = left === 0;
            if (empty) empty.hidden = left > 0;
            if (!toggle.dataset.seenUrl) {
                const badge = toggle.querySelector('.bell-badge');
                if (left === 0) badge?.remove();
                else if (badge) badge.textContent = String(left);
                toggle.setAttribute('aria-label', left === 0 ? 'Notificaciones' : 'Notificaciones (' + left + ' nuevas)');
            }
        };
        const remove = list => {
            list.forEach(row => {
                row.classList.add('is-leaving');
                setTimeout(() => row.remove(), Cuquora.scrollBehavior() === 'auto' ? 0 : 200);
            });
            refresh();
        };
        const send = keys => fetch(url, { method: 'POST', body: new URLSearchParams(keys.map(k => ['key', k])) })
            .then(res => { if (!res.ok) throw new Error('HTTP ' + res.status); })
            .catch(err => console.error(err));

        dropdown.addEventListener('click', e => {
            const button = e.target.closest('[data-notification-dismiss]');
            if (!button) return;
            e.preventDefault();
            const row = button.closest('.notification-row');
            // El foco pasa a la siguiente «x» (o a «Limpiar todas» / la campana) para seguir con el teclado
            const next = row.nextElementSibling?.querySelector('[data-notification-dismiss]')
                || row.previousElementSibling?.querySelector('[data-notification-dismiss]');
            send([row.dataset.notificationKey]);
            remove([row]);
            (next || toggle).focus();
        });
        clearAll?.addEventListener('click', e => {
            e.stopPropagation();
            const list = rows();
            if (list.length === 0) return;
            send(list.map(row => row.dataset.notificationKey));
            remove(list);
            toggle.focus();
        });
    }

    /* ---------- 4. Formularios con confirmación ---------- */
    document.addEventListener('submit', async e => {
        const form = e.target;
        if (!(form instanceof HTMLFormElement) || !form.dataset.confirm || form.dataset.confirmed === 'true') return;
        e.preventDefault();
        const ok = await Cuquora.confirm(form.dataset.confirm, {
            title: form.dataset.confirmTitle || '¿Estás seguro?',
            confirmText: form.dataset.confirmButton || 'Confirmar',
            // data-confirm-tone="default" para lo que no borra ni anula nada (botón principal en vez de rojo)
            tone: form.dataset.confirmTone || 'danger'
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

    /* ---------- 8. Aviso de cookies ---------- */
    // Si algún día se añaden cookies de análisis o publicidad, sube la versión para volver a mostrarlo
    // y añade los botones Aceptar / Rechazar: esas cookies sí necesitan consentimiento previo.
    const COOKIE_NOTICE_KEY = 'cuquora.cookies';
    const COOKIE_NOTICE_VERSION = '1';

    function initCookieBanner() {
        const banner = document.getElementById('cookieBanner');
        if (!banner) return;
        let seen = null;
        try { seen = localStorage.getItem(COOKIE_NOTICE_KEY); } catch (e) { /* almacenamiento no disponible */ }
        if (seen === COOKIE_NOTICE_VERSION) return;
        banner.hidden = false;
        // El pie reserva justo lo que mide el aviso en pantalla: cambia con el ancho y el salto de línea, y es 0
        // al cerrarlo o si una extensión del navegador lo esconde (sin quitarle el atributo hidden)
        if (window.ResizeObserver) {
            new ResizeObserver(() => {
                document.documentElement.style.setProperty('--cookie-banner-height', banner.offsetHeight + 'px');
            }).observe(banner);
        }
        banner.querySelector('[data-cookie-accept]')?.addEventListener('click', () => {
            try { localStorage.setItem(COOKIE_NOTICE_KEY, COOKIE_NOTICE_VERSION); } catch (e) { /* almacenamiento no disponible */ }
            banner.hidden = true;
        });
    }

    /* ---------- 9. «¿Qué tal tu cita?» ---------- */
    // Al enseñarlo se avisa al servidor, que no lo vuelve a poner para esa cita (la campana sí lo sigue recordando)
    function initReviewPrompt() {
        const prompt = document.getElementById('reviewPrompt');
        if (!prompt) return;
        const show = () => {
            prompt.hidden = false;
            fetch(prompt.dataset.seenUrl, { method: 'POST' }).catch(() => { /* sin conexión: saldrá otra vez */ });
        };
        prompt.querySelectorAll('[data-review-prompt-close]').forEach(button =>
            button.addEventListener('click', () => { prompt.hidden = true; }));
        // «No mostrar más»: no vuelve a salir para ninguna cita
        prompt.querySelector('[data-review-prompt-off]')?.addEventListener('click', e => {
            prompt.hidden = true;
            fetch(e.currentTarget.dataset.reviewPromptOff, { method: 'POST' }).catch(() => { /* sin conexión: saldrá con la próxima cita */ });
        });
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && !prompt.hidden && !document.querySelector('.ui-modal-overlay:not([hidden])')) {
                prompt.hidden = true;
            }
        });
        // No se amontona con el aviso de cookies: espera a que lo cierren
        const banner = document.getElementById('cookieBanner');
        if (banner && !banner.hidden) {
            banner.querySelector('[data-cookie-accept]')?.addEventListener('click', () => setTimeout(show, 400));
            return;
        }
        setTimeout(show, 1200);
    }

    /* ---------- 10. Copiar al portapapeles ---------- */
    function initCopyButtons() {
        document.querySelectorAll('[data-copy]').forEach(button => button.addEventListener('click', async () => {
            const field = document.getElementById(button.dataset.copy);
            if (!field) return;
            const label = button.querySelector('span');
            try {
                await navigator.clipboard.writeText(field.value);
            } catch (e) {
                // Sin permiso para el portapapeles: se deja el texto seleccionado para copiarlo a mano
                field.select();
                return;
            }
            if (label) {
                label.textContent = 'Copiado';
                setTimeout(() => { label.textContent = 'Copiar'; }, 2000);
            }
        }));
    }

    /* Botón «Compartir» de la ficha del centro: comparte el enlace corto (/c/nombre)
       con el menú del sistema en móvil o lo copia al portapapeles en escritorio. */
    function initShareButtons() {
        document.querySelectorAll('[data-share-path]').forEach(button => button.addEventListener('click', async () => {
            const url = new URL(button.dataset.sharePath, window.location.origin).href;
            const status = button.parentElement.querySelector('.hero-share-status');
            const say = text => {
                if (!status) return;
                status.textContent = text;
                setTimeout(() => { status.textContent = ''; }, 4000);
            };
            if (navigator.share) {
                try {
                    await navigator.share({ title: button.dataset.shareTitle, url });
                    return;
                } catch (e) {
                    if (e.name === 'AbortError') return; // la persona cerró el menú
                }
            }
            try {
                await navigator.clipboard.writeText(url);
                say('Enlace copiado: ' + url);
            } catch (e) {
                say('Copia este enlace: ' + url);
            }
        }));
    }

    /* ---------- 12. Ver la contraseña que se está escribiendo ---------- */
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

    /* ---------- 14. Contraseñas: contador y caracteres ---------- */
    // Lo mismo que PasswordPolicy.ALLOWED: letras (con ñ, tildes y ü), números y símbolos del teclado, sin espacios.
    // La regla de verdad está en el servidor; aquí solo se evita escribir algo que luego se rechazaría.
    const PASSWORD_CHAR = /^[!-~ñÑáéíóúÁÉÍÓÚüÜ]$/;

    function initPasswordRules() {
        document.querySelectorAll('input[data-password-rules], input[data-password-limit]').forEach(input => {
            const max = input.maxLength > 0 ? input.maxLength : 32;
            // En el login no se filtra: una contraseña antigua con espacios o «€» tiene que poder escribirse para entrar
            const filtered = input.hasAttribute('data-password-rules');
            const wrapper = input.closest('.password-wrapper');
            const counter = document.createElement('span');
            counter.className = 'password-counter';
            counter.setAttribute('aria-hidden', 'true');
            wrapper?.classList.add('password-wrapper--counted');
            wrapper?.insertBefore(counter, input.nextSibling);

            const message = document.createElement('p');
            message.className = 'field-error-msg';
            message.id = input.id + 'Rules';
            message.setAttribute('role', 'alert');
            message.hidden = true;
            (wrapper || input).after(message);
            input.setAttribute('aria-describedby', message.id);

            const allowed = c => !filtered || PASSWORD_CHAR.test(c);
            const badChar = text => [...text].find(c => !allowed(c));
            const warn = text => {
                message.textContent = text;
                message.hidden = false;
            };
            const warnChar = c => warn(/\s/.test(c)
                ? 'La contraseña no puede llevar espacios.'
                : '«' + c + '» no vale: usa letras, números y símbolos del teclado.');
            const update = () => {
                const length = [...input.value].length;
                counter.textContent = length + '/' + max;
                counter.classList.toggle('is-full', length >= max);
            };

            input.addEventListener('beforeinput', e => {
                if (e.isComposing || !e.inputType.startsWith('insert')) return;
                const text = (e.data ?? e.dataTransfer?.getData('text/plain') ?? '').normalize('NFC');
                const bad = badChar(text);
                if (bad !== undefined) {
                    e.preventDefault();
                    warnChar(bad);
                    return;
                }
                // Lo pegado no se recorta: una contraseña cortada a escondidas no coincidiría con la que se guardó
                if (e.inputType === 'insertFromPaste') {
                    const kept = input.value.length - (input.selectionEnd - input.selectionStart);
                    if (kept + text.length > max) {
                        e.preventDefault();
                        warn('La contraseña que has pegado tiene ' + text.length + ' caracteres; como mucho son ' + max + '.');
                        return;
                    }
                }
                if (text) message.hidden = true;
            });

            // Lo que no pasa por beforeinput (autocompletar, letras con acento compuestas, navegadores antiguos)
            const clean = () => {
                // En el login se deja tal cual: normalizar podría cambiar una contraseña antigua escrita con tildes
                const value = filtered ? input.value.normalize('NFC') : input.value;
                const bad = badChar(value);
                const kept = [...value].filter(allowed).join('').slice(0, max);
                if (kept !== input.value) {
                    input.value = kept;
                }
                if (bad !== undefined) warnChar(bad);
                else message.hidden = true;
                update();
            };
            input.addEventListener('input', e => {
                if (!e.isComposing) clean();
            });
            input.addEventListener('compositionend', clean);
            update();
        });
    }

    /* ---------- 11. Color al estirar más allá del principio o del final ---------- */
    function initOverscrollColor() {
        const root = document.documentElement;
        let pending = false;
        const update = () => {
            pending = false;
            const max = root.scrollHeight - window.innerHeight;
            // En una página que no llega a hacer scroll, max es 0 y el rebote de abajo da scrollY > 0
            root.classList.toggle('is-near-end', window.scrollY > max / 2);
        };
        const schedule = () => {
            if (!pending) {
                pending = true;
                requestAnimationFrame(update);
            }
        };
        window.addEventListener('scroll', schedule, { passive: true });
        window.addEventListener('resize', schedule);
        window.addEventListener('load', schedule);
        update();
    }

    /* ---------- 13. «Cómo llegar» ---------- */
    /* El enlace ya abre Google Maps en cualquier dispositivo. En iPhone, iPad y Mac (el iPad dice ser un Mac)
       sale antes una hoja para elegir Apple Maps, que es el mapa que esas personas suelen tener. */
    function initDirections() {
        const links = document.querySelectorAll('a[data-directions][data-apple-maps-url]');
        if (!links.length || !/iPhone|iPad|iPod|Macintosh/.test(navigator.userAgent)) return;

        const overlay = document.createElement('div');
        overlay.className = 'ui-modal-overlay ui-modal-overlay--sheet';
        overlay.id = 'directionsModal';
        overlay.hidden = true;
        overlay.innerHTML = `
            <div class="ui-modal ui-modal--sheet" role="dialog" aria-modal="true" aria-labelledby="directionsTitle">
                <button type="button" class="ui-modal-close" data-modal-close aria-label="Cerrar"><i class="lucide-x" aria-hidden="true"></i></button>
                <h2 class="ui-modal-title" id="directionsTitle"><i class="lucide-navigation" aria-hidden="true"></i> Cómo llegar</h2>
                <p class="ui-modal-text" data-directions-address></p>
                <div class="directions-options">
                    <a class="btn btn-secondary" data-directions-apple target="_blank" rel="noopener noreferrer">
                        <i class="lucide-map" aria-hidden="true"></i> Abrir en Apple Maps</a>
                    <a class="btn btn-secondary" data-directions-google target="_blank" rel="noopener noreferrer">
                        <i class="lucide-map-pinned" aria-hidden="true"></i> Abrir en Google Maps</a>
                </div>
            </div>`;
        document.body.appendChild(overlay);
        const address = overlay.querySelector('[data-directions-address]');
        const apple = overlay.querySelector('[data-directions-apple]');
        const google = overlay.querySelector('[data-directions-google]');
        // Al elegir un mapa se abre en otra pestaña o app: la hoja se cierra sola
        [apple, google].forEach(option => option.addEventListener('click', () => closeModal(overlay)));

        links.forEach(link => link.addEventListener('click', e => {
            const appleUrl = link.dataset.appleMapsUrl;
            if (!appleUrl) return;
            e.preventDefault();
            apple.href = appleUrl;
            google.href = link.href;
            address.textContent = link.dataset.directions || '';
            address.hidden = !link.dataset.directions;
            openModal(overlay);
        }));
    }

    /* ---------- 15. Consentimiento de la clienta en la ficha técnica ---------- */
    /* Casilla con data-consent-fields="<selector>": si algún campo de ese selector tiene algo escrito, la casilla es
       obligatoria (la ficha puede llevar datos de salud). Se usa la validación del navegador, así que ni el botón
       de guardar ni «Guardar y salir» (unsaved-changes.js comprueba la validez) envían sin ella. */
    function initConsentChecks() {
        document.querySelectorAll('input[type="checkbox"][data-consent-fields]').forEach(box => {
            const form = box.form;
            if (!form) return;
            const message = box.dataset.consentMessage || 'Marca esta casilla para guardar.';
            const update = () => {
                const filled = [...document.querySelectorAll(box.dataset.consentFields)]
                    .some(field => field !== box && (field.value || '').trim() !== '');
                box.setCustomValidity(filled && !box.checked ? message : '');
            };
            form.addEventListener('input', update);
            form.addEventListener('change', update);
            form.addEventListener('reset', () => setTimeout(update));
            update();
        });
    }

    function init() {
        initOverscrollColor();
        initConsentChecks();
        initCopyButtons();
        initShareButtons();
        initDirections();
        initPasswordRules();
        initMobileNav();
        initNotifications();
        initViewToggles();
        initCookieBanner();
        initReviewPrompt();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
