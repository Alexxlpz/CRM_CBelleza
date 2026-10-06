/*
 * Agenda mensual del trabajador (worker/calendar.html).
 * Pinta el mes con indicadores por día y la lista de citas del día seleccionado,
 * y permite aprobar o rechazar solicitudes (POST /worker/appointments/{id}/approve|reject).
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const esc = Cuquora.escapeHtml;
    const appointments = window.CALENDAR_APPOINTMENTS || [];
    const MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    const STATUS = {
        PENDING: { css: 'badge-pending', label: 'Solicitud' },
        CONFIRMED: { css: 'badge-confirmed', label: 'Confirmada' },
        COMPLETED: { css: 'badge-completed', label: 'Completada' },
        REJECTED: { css: 'badge-rejected', label: 'Rechazada' }
    };

    const grid = document.getElementById('calendarDays');
    const monthTitle = document.getElementById('calendarMonthYear');
    const dayTitle = document.getElementById('selectedDateTitle');
    const dayCount = document.getElementById('selectedDateCount');
    const list = document.getElementById('agendaList');

    let currentYear;
    let currentMonth;
    let selectedDayKey = null;

    const pad = n => String(n).padStart(2, '0');
    const dateKey = (day, month, year) => `${year}-${pad(month + 1)}-${pad(day)}`;
    const appointmentsOn = key => appointments
        .filter(app => app.dateTime && app.dateTime.startsWith(key))
        .sort((a, b) => a.dateTime.localeCompare(b.dateTime));
    const euros = value => Number(value).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    /** Las citas confirmadas cuya hora de fin ya pasó se muestran como completadas sin recargar. */
    function refreshFinishedAppointments() {
        const now = new Date();
        let changed = false;
        appointments.forEach(app => {
            if (app.status !== 'CONFIRMED' || !app.dateTime) return;
            const minutes = app.treatment?.duration || 60;
            if (now >= new Date(new Date(app.dateTime).getTime() + minutes * 60000)) {
                app.status = 'COMPLETED';
                changed = true;
            }
        });
        if (changed) refresh();
    }

    function renderCalendar() {
        monthTitle.textContent = `${MONTHS[currentMonth]} ${currentYear}`;
        const startDay = (new Date(currentYear, currentMonth, 1).getDay() + 6) % 7; // lunes = 0
        const totalDays = new Date(currentYear, currentMonth + 1, 0).getDate();
        const prevMonthDays = new Date(currentYear, currentMonth, 0).getDate();
        const today = new Date();
        grid.innerHTML = '';

        const filler = number => {
            const cell = document.createElement('div');
            cell.className = 'calendar-day-cell other-month';
            cell.setAttribute('aria-hidden', 'true');
            cell.textContent = number;
            return cell;
        };

        for (let i = startDay; i > 0; i--) grid.appendChild(filler(prevMonthDays - i + 1));

        for (let day = 1; day <= totalDays; day++) {
            const key = dateKey(day, currentMonth, currentYear);
            const dayApps = appointmentsOn(key);
            const hasAttended = dayApps.some(a => a.status === 'CONFIRMED' || a.status === 'COMPLETED');
            const hasPending = dayApps.some(a => a.status === 'PENDING');

            const cell = document.createElement('button');
            cell.type = 'button';
            cell.className = 'calendar-day-cell';
            cell.textContent = day;
            const label = new Date(currentYear, currentMonth, day).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
            cell.setAttribute('aria-label', label + (dayApps.length ? `, ${dayApps.length} citas` : '') + (hasPending ? ', con solicitudes pendientes' : ''));

            if (day === today.getDate() && currentMonth === today.getMonth() && currentYear === today.getFullYear()) {
                cell.classList.add('today-day');
                cell.setAttribute('aria-current', 'date');
            }
            if (hasAttended || hasPending) {
                const dots = document.createElement('div');
                dots.className = 'calendar-dots-container';
                if (hasAttended) dots.insertAdjacentHTML('beforeend', '<div class="calendar-dot gold-dot"></div>');
                if (hasPending) {
                    dots.insertAdjacentHTML('beforeend', '<div class="calendar-dot red-dot"></div>');
                    cell.classList.add('pending-day-highlight');
                }
                cell.appendChild(dots);
            }
            if (selectedDayKey === key) {
                cell.classList.add('active-day');
                cell.setAttribute('aria-pressed', 'true');
            }
            cell.addEventListener('click', () => selectDay(day, currentMonth, currentYear, true));
            grid.appendChild(cell);
        }

        const remaining = (7 - ((startDay + totalDays) % 7)) % 7;
        for (let i = 1; i <= remaining; i++) grid.appendChild(filler(i));
    }

    function appointmentCard(app) {
        const status = STATUS[app.status] || STATUS.CONFIRMED;
        const time = app.dateTime.split('T')[1].substring(0, 5);
        const client = app.client || {};
        const card = document.createElement('article');
        card.className = 'agenda-item-card' + (app.status === 'PENDING' ? ' pending-card' : '');

        const newClient = app.isNewClient
            ? '<span class="badge-new-client"><i class="lucide-sparkles" aria-hidden="true"></i> Nuevo Cliente</span>' : '';
        const clientCardLink = (app.status === 'CONFIRMED' || app.status === 'COMPLETED') && client.id ? `
            <div class="agenda-card-footer">
                <a href="/worker/clients/${encodeURIComponent(client.id)}" class="btn-template-config btn-template-config--sm">
                    <i class="lucide-file-text" aria-hidden="true"></i> Ficha del Cliente
                </a>
            </div>` : '';
        const note = app.workerMessage ? `
            <div class="agenda-worker-note">
                <i class="lucide-message-square" aria-hidden="true"></i><span>Nota: "${esc(app.workerMessage)}"</span>
            </div>` : '';
        const actions = app.status === 'PENDING' ? `
            <div class="approval-message-box">
                <label class="form-label agenda-note-label" for="message-${app.id}">Escribir nota opcional (mensaje al cliente):</label>
                <textarea id="message-${app.id}" class="approval-msg-input" rows="2"
                          placeholder="Ej. Por favor ven 5 minutos antes o motivo de cancelación..."></textarea>
                <div class="agenda-card-actions">
                    <button type="button" class="btn-agenda-action reject" data-action="reject">
                        <i class="lucide-x agenda-action-icon" aria-hidden="true"></i> Rechazar
                    </button>
                    <button type="button" class="btn-agenda-action approve" data-action="approve">
                        <i class="lucide-check agenda-action-icon" aria-hidden="true"></i> Aceptar Cita
                    </button>
                </div>
            </div>` : '';

        card.innerHTML = `
            <div class="agenda-card-top">
                <div class="agenda-item-time">
                    <i class="lucide-clock" aria-hidden="true"></i>
                    <span>${time} h</span>
                </div>
                <div class="agenda-card-badges">
                    ${newClient}
                    <span class="badge ${status.css}">${status.label}</span>
                </div>
            </div>
            <h3 class="agenda-item-treatment">${esc(app.treatment?.name)}</h3>
            <div class="agenda-item-detail">
                <i class="lucide-user" aria-hidden="true"></i>
                <strong>${esc(client.name || app.guestName || 'Cliente')}</strong>
            </div>
            <div class="agenda-item-detail">
                <i class="lucide-phone" aria-hidden="true"></i>
                <span>Teléfono: ${esc(client.phone || app.guestPhone || '—')}</span>
            </div>
            <div class="agenda-item-detail">
                <i class="lucide-sparkles" aria-hidden="true"></i>
                <span>Estilista: ${esc(app.worker ? app.worker.name : 'Pendiente de asignación')}</span>
            </div>
            <div class="agenda-item-detail agenda-detail-margin">
                <i class="lucide-badge-euro" aria-hidden="true"></i>
                <span class="agenda-price-text">${euros(app.treatment?.price ?? 0)} €</span>
                <span class="agenda-duration-text">(${esc(app.treatment?.duration)} min)</span>
            </div>
            ${clientCardLink}
            ${note}
            ${actions}`;

        card.querySelectorAll('[data-action]').forEach(button =>
            button.addEventListener('click', () => changeStatus(app, button.dataset.action, card)));
        return card;
    }

    async function changeStatus(app, action, card) {
        const message = card.querySelector('textarea')?.value || '';
        const buttons = card.querySelectorAll('[data-action]');
        buttons.forEach(b => { b.disabled = true; });
        try {
            const response = await fetch(`/worker/appointments/${encodeURIComponent(app.id)}/${action}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ message })
            });
            if (!response.ok) {
                const reason = await Cuquora.errorMessage(response, 'No se ha podido actualizar el estado de la cita.');
                await Cuquora.alert(reason, 'No se ha podido actualizar la cita');
                buttons.forEach(b => { b.disabled = false; });
                return;
            }
            app.status = action === 'approve' ? 'CONFIRMED' : 'REJECTED';
            app.workerMessage = message;
            refresh();
        } catch (err) {
            console.error(err);
            await Cuquora.alert('Error de conexión al actualizar la cita. Inténtalo de nuevo.', 'Sin conexión');
            buttons.forEach(b => { b.disabled = false; });
        }
    }

    function selectDay(day, month, year, isUserClick) {
        selectedDayKey = dateKey(day, month, year);
        renderCalendar();

        const title = new Date(year, month, day).toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
        dayTitle.textContent = title.charAt(0).toUpperCase() + title.slice(1);

        const apps = appointmentsOn(selectedDayKey);
        const count = status => apps.filter(a => a.status === status).length;
        dayCount.textContent = `${count('CONFIRMED')} confirmadas`
            + (count('COMPLETED') ? `, ${count('COMPLETED')} completadas` : '')
            + `, ${count('PENDING')} solicitudes pendientes`;

        list.innerHTML = '';
        if (apps.length === 0) {
            list.innerHTML = `
                <div class="notification-empty">
                    <i class="lucide-calendar-days" aria-hidden="true"></i>
                    <span>No hay citas programadas para esta jornada</span>
                </div>`;
        } else {
            apps.forEach(app => list.appendChild(appointmentCard(app)));
        }

        if (isUserClick && window.innerWidth <= 900) {
            dayTitle.scrollIntoView({ behavior: 'smooth', block: 'start' });
            dayTitle.focus({ preventScroll: true });
        }
    }

    function refresh() {
        renderCalendar();
        if (selectedDayKey) {
            const [y, m, d] = selectedDayKey.split('-').map(Number);
            selectDay(d, m - 1, y, false);
        }
    }

    document.getElementById('prevMonthBtn').addEventListener('click', () => {
        currentMonth--;
        if (currentMonth < 0) { currentMonth = 11; currentYear--; }
        renderCalendar();
    });
    document.getElementById('nextMonthBtn').addEventListener('click', () => {
        currentMonth++;
        if (currentMonth > 11) { currentMonth = 0; currentYear++; }
        renderCalendar();
    });

    // Día inicial: el de ?date=YYYY-MM-DD (enlaces de las notificaciones) o hoy.
    const target = new URLSearchParams(window.location.search).get('date');
    const start = /^\d{4}-\d{2}-\d{2}$/.test(target || '') ? new Date(target + 'T00:00:00') : new Date();
    currentYear = start.getFullYear();
    currentMonth = start.getMonth();
    selectDay(start.getDate(), currentMonth, currentYear, false);

    refreshFinishedAppointments();
    setInterval(refreshFinishedAppointments, 15000);
});
