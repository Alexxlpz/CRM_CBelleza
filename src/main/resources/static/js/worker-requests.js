/**
 * «Por aprobar» del panel del trabajador (worker/dashboard.html): aceptar o rechazar una solicitud sin ir a la agenda.
 * Usa los mismos endpoints que la agenda (/worker/appointments/{id}/approve y /reject). Al aceptar sin profesional,
 * la cita queda a nombre de quien la acepta (BookingService.approve). Al terminar, la fila desaparece y bajan los
 * contadores de solicitudes; si no queda ninguna, se quita el bloque.
 */
(function () {
    'use strict';

    const panel = document.getElementById('porAprobar');
    if (!panel) return;

    const countBadge = panel.querySelector('[data-requests-count]');

    async function send(id, action) {
        try {
            const response = await fetch(`/worker/appointments/${encodeURIComponent(id)}/${action}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams()
            });
            if (response.ok) return { ok: true };
            return { ok: false, message: await Cuquora.errorMessage(response, 'No se ha podido actualizar la solicitud.') };
        } catch (err) {
            console.error(err);
            return { ok: false, message: 'Error de conexión. Inténtalo de nuevo.' };
        }
    }

    /** Baja en uno los contadores de solicitudes del panel (cabecera, tarjeta de la agenda y este bloque). */
    function decreaseCounters() {
        const left = panel.querySelectorAll('.request-item:not(.is-leaving)').length;
        if (countBadge) countBadge.textContent = String(left);
        const counter = document.querySelector('.counter-card--link[href="/worker/calendar"]');
        const value = counter?.querySelector('.counter-value');
        if (value) {
            const n = Math.max(0, (parseInt(value.textContent, 10) || 0) - 1);
            value.textContent = String(n);
            counter.classList.toggle('counter-card--alert', n > 0);
        }
        const badge = document.querySelector('.action-card[href="/worker/calendar"] .action-card-badge.badge-warning');
        if (badge) {
            const n = Math.max(0, (parseInt(badge.textContent, 10) || 0) - 1);
            if (n === 0) badge.remove();
            else badge.textContent = n === 1 ? '1 solicitud pendiente' : `${n} solicitudes pendientes`;
        }
        return left;
    }

    function removeRow(row) {
        row.classList.add('is-leaving');
        const left = decreaseCounters();
        const done = () => {
            row.remove();
            if (left === 0) panel.remove();
        };
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) done();
        else setTimeout(done, 160);
    }

    panel.addEventListener('click', async event => {
        const button = event.target.closest('[data-request-action]');
        if (!button) return;
        const row = button.closest('.request-item');
        const action = button.dataset.requestAction;
        if (action === 'reject') {
            const ok = await Cuquora.confirm(`Avisaremos al cliente de que no podéis atenderle (${button.dataset.context}).`, {
                title: '¿Rechazar la solicitud?',
                confirmText: 'Rechazar'
            });
            if (!ok) return;
        }
        const buttons = row.querySelectorAll('[data-request-action]');
        buttons.forEach(b => { b.disabled = true; });
        const result = await send(button.dataset.id, action);
        if (!result.ok) {
            await Cuquora.alert(result.message, 'No se ha podido actualizar la solicitud');
            buttons.forEach(b => { b.disabled = false; });
            return;
        }
        removeRow(row);
    });
})();
