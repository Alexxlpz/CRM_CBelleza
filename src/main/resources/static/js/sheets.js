/*
 * Paneles deslizantes (side sheet en escritorio / bottom sheet en móvil) y pestañas accesibles.
 *
 * Paneles: <div class="sheet-overlay" id="x" hidden> ... </div>   (también .info-modal-overlay)
 *   - Se abren con [data-sheet-open="x"] y se cierran con [data-sheet-close], Escape o clic en el fondo.
 *   - En móvil se pueden cerrar arrastrando el asa (.bottom-sheet-handle) hacia abajo.
 *   - El foco queda atrapado dentro mientras están abiertos y vuelve al botón que los abrió.
 *
 * Pestañas: <div role="tablist" data-tabs> <button role="tab" aria-controls="panelId">…</button> </div>
 *   - Flechas izquierda/derecha para moverse; emite el evento "tabchange" con detail.tab.
 */
(function () {
    'use strict';

    const OVERLAYS = '.sheet-overlay, .info-modal-overlay';
    const openStack = [];

    function open(overlay, opener) {
        if (!overlay || !overlay.hidden) return;
        overlay.hidden = false;
        overlay.classList.add('is-open');
        openStack.push({ overlay, opener: opener || document.activeElement });
        document.body.classList.add('modal-open');
        const focusTarget = overlay.querySelector('[autofocus], input:not([type="hidden"]), select, textarea, button');
        focusTarget?.focus({ preventScroll: true });
    }

    function close(overlay) {
        if (!overlay || overlay.hidden) return;
        overlay.hidden = true;
        overlay.classList.remove('is-open');
        const index = openStack.findIndex(entry => entry.overlay === overlay);
        const [entry] = index >= 0 ? openStack.splice(index, 1) : [];
        if (!openStack.length) document.body.classList.remove('modal-open');
        entry?.opener?.focus?.({ preventScroll: true });
    }

    document.addEventListener('click', e => {
        const opener = e.target.closest('[data-sheet-open]');
        if (opener) {
            e.preventDefault();
            open(document.getElementById(opener.dataset.sheetOpen), opener);
            return;
        }
        const closer = e.target.closest('[data-sheet-close]');
        if (closer) {
            close(closer.closest(OVERLAYS));
            return;
        }
        if (e.target.matches?.(OVERLAYS)) close(e.target);
    });

    document.addEventListener('keydown', e => {
        const top = openStack[openStack.length - 1]?.overlay;
        if (!top) return;
        if (e.key === 'Escape') close(top);
        else window.Cuquora?.trapFocus?.(top, e);
    });

    /* ---- Arrastrar para cerrar (móvil) ---- */
    function enableDrag(handle) {
        const card = handle.closest('.sheet-content, .info-modal-card');
        const overlay = handle.closest(OVERLAYS);
        if (!card || !overlay) return;
        let startY = 0;
        let delta = 0;
        let dragging = false;

        const point = e => (e.touches ? e.touches[0].clientY : e.clientY);
        function onMove(e) {
            if (!dragging) return;
            delta = point(e) - startY;
            if (delta > 0) {
                if (e.cancelable) e.preventDefault();
                card.style.transform = `translateY(${delta}px)`;
                overlay.style.opacity = String(Math.max(0.1, 1 - delta / 300));
            }
        }
        function onEnd() {
            if (!dragging) return;
            dragging = false;
            document.removeEventListener('touchmove', onMove);
            document.removeEventListener('touchend', onEnd);
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onEnd);
            card.style.transition = 'transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.25s ease';
            const dismiss = delta > 70;
            card.style.transform = dismiss ? 'translateY(100%)' : 'translateY(0)';
            overlay.style.opacity = dismiss ? '0' : '1';
            setTimeout(() => {
                if (dismiss) close(overlay);
                card.style.transform = '';
                card.style.transition = '';
                overlay.style.opacity = '';
                delta = 0;
            }, 230);
        }
        function onStart(e) {
            startY = point(e);
            dragging = true;
            card.style.transition = 'none';
            document.addEventListener('touchmove', onMove, { passive: false });
            document.addEventListener('touchend', onEnd);
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', onEnd);
        }
        handle.addEventListener('touchstart', onStart, { passive: true });
        handle.addEventListener('mousedown', onStart);
    }

    /* ---- Pestañas ---- */
    function initTabs(tablist) {
        const tabs = Array.from(tablist.querySelectorAll('[role="tab"]'));
        function select(tab, focus) {
            tabs.forEach(t => {
                const active = t === tab;
                t.setAttribute('aria-selected', String(active));
                t.tabIndex = active ? 0 : -1;
                t.classList.toggle('active', active);
                const panel = document.getElementById(t.getAttribute('aria-controls'));
                if (panel) {
                    panel.hidden = !active;
                    panel.classList.toggle('active', active);
                }
            });
            if (focus) tab.focus();
            tablist.dispatchEvent(new CustomEvent('tabchange', { detail: { tab } }));
        }
        tabs.forEach((tab, i) => {
            tab.addEventListener('click', () => select(tab, false));
            tab.addEventListener('keydown', e => {
                const step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
                if (!step) return;
                e.preventDefault();
                select(tabs[(i + step + tabs.length) % tabs.length], true);
            });
        });
        select(tabs.find(t => t.getAttribute('aria-selected') === 'true') || tabs[0], false);
    }

    function init() {
        document.querySelectorAll('.bottom-sheet-handle').forEach(enableDrag);
        document.querySelectorAll('[role="tablist"][data-tabs]').forEach(initTabs);
    }

    window.Cuquora = window.Cuquora || {};
    window.Cuquora.sheet = {
        open: id => open(document.getElementById(id)),
        close: id => close(document.getElementById(id))
    };

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
