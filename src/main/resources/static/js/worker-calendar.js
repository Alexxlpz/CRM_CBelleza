/*
 * Agenda del trabajador (worker/calendar.html), con dos disposiciones:
 *  - Mes: calendario con indicadores por día y la lista de citas y bloqueos del día seleccionado.
 *  - Semana: rejilla horaria de lunes a domingo con cada cita como un bloque del alto de su
 *    duración (en móvil, lista por días). Al pulsar una cita se abre su ficha en un modal.
 * Acciones: aceptar, rechazar o proponer otra hora a una solicitud; cambiar de hora o anular una cita
 * confirmada; crear citas (botón «Nueva cita» o pulsando un hueco libre de la semana); arrastrar una cita
 * confirmada a otra hora; bloquear horario; escribir el resumen del tratamiento (treatment-summary.js) en
 * citas que ya han empezado. Los avisos del cliente (resúmenes marcados como aviso) salen en sus demás citas.
 * Los formularios están en worker-agenda-forms.js y, tras cada cambio, la agenda se recarga desde
 * /worker/calendar/data.
 * La disposición elegida se recuerda en el navegador.
 * «Pantalla completa» deja la semana a la vista en el ordenador del centro: el horario cabe sin scroll,
 * sigue a la semana actual y las citas se recargan cada minuto.
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const esc = Cuquora.escapeHtml;
    const forms = window.AgendaForms;
    let appointments = window.CALENDAR_APPOINTMENTS || [];
    let blocks = window.CALENDAR_BLOCKS || [];
    const hours = window.CALENDAR_HOURS || {};
    const staff = window.CALENDAR_STAFF || {};
    const OPEN_HOUR = Number.isInteger(hours.openHour) ? hours.openHour : 9;
    const CLOSE_HOUR = Number.isInteger(hours.closeHour) ? hours.closeHour : 20;
    const SLOT = Number.isInteger(hours.slotMinutes) ? hours.slotMinutes : 30;
    const WORKING_DAYS = Array.isArray(hours.workingDays) ? hours.workingDays : [1, 2, 3, 4, 5];
    // Tramos de apertura de cada día (1 = lunes) en minutos; dos tramos = jornada partida
    const DAY_PERIODS = hours.days || {};
    const WORKERS = Array.isArray(staff.workers) ? staff.workers : [];
    const ME = staff.currentWorkerId;
    const WEEKDAYS_SHORT = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
    const VIEW_KEY = 'cuquora.calendar.view';
    const MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    const STATUS = {
        PENDING: { css: 'badge-pending', label: 'Solicitud' },
        PROPOSED: { css: 'badge-proposed', label: 'Propuesta enviada' },
        CONFIRMED: { css: 'badge-confirmed', label: 'Confirmada' },
        COMPLETED: { css: 'badge-completed', label: 'Completada' },
        REJECTED: { css: 'badge-rejected', label: 'Rechazada' },
        CANCELLED: { css: 'badge-cancelled', label: 'Anulada' },
        NO_SHOW: { css: 'badge-no_show', label: 'No se presentó' }
    };

    const grid = document.getElementById('calendarDays');
    const monthTitle = document.getElementById('calendarMonthYear');
    const dayTitle = document.getElementById('selectedDateTitle');
    const dayCount = document.getElementById('selectedDateCount');
    const list = document.getElementById('agendaList');
    const monthView = document.getElementById('monthView');
    const weekView = document.getElementById('weekView');
    const weekGrid = document.getElementById('weekGrid');
    const weekList = document.getElementById('weekList');
    const weekTitle = document.getElementById('weekTitle');
    const weekSummary = document.getElementById('weekSummary');
    const viewButtons = document.querySelectorAll('[data-calendar-view]');
    const modalBody = document.getElementById('appointmentModalBody');
    const fullscreenBtn = document.getElementById('weekFullscreenBtn');
    const LIVE_RELOAD_MS = 60000;

    let currentYear;
    let currentMonth;
    let selectedDayKey = null;
    let currentView = 'month';
    let weekStart = null;      // lunes de la semana visible
    let modalApp = null;       // cita abierta en el modal de la vista semanal
    let followToday = false;   // en pantalla completa, pasar sola a la semana nueva el lunes
    let liveTimer = null;

    const pad = n => String(n).padStart(2, '0');
    const dateKey = (day, month, year) => `${year}-${pad(month + 1)}-${pad(day)}`;
    const appointmentsOn = key => appointments
        .filter(app => app.dateTime && app.dateTime.startsWith(key))
        .sort((a, b) => a.dateTime.localeCompare(b.dateTime));
    const euros = value => Number(value).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const capitalize = text => text.charAt(0).toUpperCase() + text.slice(1);
    const keyOf = date => dateKey(date.getDate(), date.getMonth(), date.getFullYear());
    const minutesOfTime = dateTime => {
        const [h, m] = dateTime.split('T')[1].split(':').map(Number);
        return h * 60 + m;
    };
    const startMinutes = app => minutesOfTime(app.dateTime);
    const durationOf = app => app.treatment?.duration || 60;
    const clock = minutes => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
    const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
    const longDate = dateTime => new Date(dateTime).toLocaleString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
    const isFuture = app => new Date(app.dateTime) > new Date();
    const shortDate = dateTime => new Date(dateTime).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
    /** Las rechazadas y anuladas no ocupan agenda: no se pintan en la semana. */
    const occupiesAgenda = app => app.status !== 'REJECTED' && app.status !== 'CANCELLED';
    const isOpenRequest = app => app.status === 'PENDING' || app.status === 'PROPOSED';

    /** Bloqueos que tocan un día (con la parte del día que ocupan, en minutos). */
    function blocksOn(key) {
        const dayStart = new Date(key + 'T00:00:00');
        const dayEnd = new Date(dayStart.getTime() + 24 * 3600000);
        return blocks
            .filter(b => new Date(b.startsAt) < dayEnd && new Date(b.endsAt) > dayStart)
            .map(b => {
                const from = Math.max(new Date(b.startsAt) - dayStart, 0) / 60000;
                const to = Math.min(new Date(b.endsAt) - dayStart, 24 * 3600000) / 60000;
                return { block: b, from, to, allDay: from === 0 && to === 1440 };
            });
    }

    const blockWho = block => block.workerName ? block.workerName : 'Centro cerrado';
    const blockSpan = part => part.allDay ? 'Todo el día' : `${clock(part.from)} – ${part.to >= 1440 ? '24:00' : clock(part.to)}`;

    /* =====================================================================
       Datos
       ===================================================================== */

    /** Vuelve a pedir citas y bloqueos al servidor y repinta (tras cada cambio). */
    async function reload({ focusDate } = {}) {
        try {
            const response = await fetch('/worker/calendar/data');
            if (response.ok) {
                const data = await response.json();
                appointments = data.appointments || [];
                blocks = data.blocks || [];
            }
        } catch (err) {
            console.error(err);
        }
        if (focusDate) {
            const [y, m, d] = focusDate.split('-').map(Number);
            currentYear = y;
            currentMonth = m - 1;
            selectedDayKey = focusDate;
            if (currentView === 'week') weekStart = mondayOf(new Date(y, m - 1, d));
        }
        if (modalApp) {
            modalApp = appointments.find(app => app.id === modalApp.id) || null;
            if (!modalApp) Cuquora.closeModal('appointmentModal');
        }
        refresh();
    }

    /** Las citas confirmadas cuya hora de fin ya pasó se muestran como completadas sin recargar. */
    function refreshFinishedAppointments() {
        const now = new Date();
        let changed = false;
        appointments.forEach(app => {
            if (app.status !== 'CONFIRMED' || !app.dateTime) return;
            if (now >= new Date(new Date(app.dateTime).getTime() + durationOf(app) * 60000)) {
                app.status = 'COMPLETED';
                changed = true;
            }
        });
        if (changed) refresh();
    }

    /* =====================================================================
       Vista mensual
       ===================================================================== */

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
            const hasPending = dayApps.some(isOpenRequest);
            const dayBlocks = blocksOn(key);

            const cell = document.createElement('button');
            cell.type = 'button';
            cell.className = 'calendar-day-cell';
            cell.textContent = day;
            const label = new Date(currentYear, currentMonth, day).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
            cell.setAttribute('aria-label', label + (dayApps.length ? `, ${dayApps.length} citas` : '')
                + (hasPending ? ', con solicitudes pendientes' : '') + (dayBlocks.length ? ', con horario bloqueado' : ''));

            if (day === today.getDate() && currentMonth === today.getMonth() && currentYear === today.getFullYear()) {
                cell.classList.add('today-day');
                cell.setAttribute('aria-current', 'date');
            }
            const isoDay = ((new Date(currentYear, currentMonth, day).getDay() + 6) % 7) + 1;
            if (!WORKING_DAYS.includes(isoDay)) cell.classList.add('closed-day');
            if (dayBlocks.some(part => part.allDay && !part.block.workerId)) cell.classList.add('blocked-day');
            if (hasAttended || hasPending || dayBlocks.length) {
                const dots = document.createElement('div');
                dots.className = 'calendar-dots-container';
                if (hasAttended) dots.insertAdjacentHTML('beforeend', '<div class="calendar-dot gold-dot"></div>');
                if (hasPending) {
                    dots.insertAdjacentHTML('beforeend', '<div class="calendar-dot red-dot"></div>');
                    cell.classList.add('pending-day-highlight');
                }
                if (dayBlocks.length) dots.insertAdjacentHTML('beforeend', '<div class="calendar-dot block-dot"></div>');
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

    function workerOptions(selectedId) {
        return WORKERS.map(w => `<option value="${esc(String(w.id))}"${w.id === selectedId ? ' selected' : ''}>${esc(w.name)}${w.id === ME ? ' (tú)' : ''}</option>`).join('');
    }

    function appointmentCard(app) {
        const status = STATUS[app.status] || STATUS.CONFIRMED;
        const time = app.dateTime.split('T')[1].substring(0, 5);
        const client = app.client || {};
        const card = document.createElement('article');
        card.className = 'agenda-item-card' + (app.status === 'PENDING' ? ' pending-card' : '')
            + (app.status === 'PROPOSED' ? ' proposed-card' : '');

        const newClient = app.isNewClient
            ? '<span class="badge-new-client"><i class="lucide-sparkles" aria-hidden="true"></i> Nuevo Cliente</span>' : '';
        const clientCardLink = (app.status === 'CONFIRMED' || app.status === 'COMPLETED' || app.status === 'NO_SHOW') && client.id ? `
            <a href="/worker/clients/${encodeURIComponent(client.id)}" class="btn-template-config btn-template-config--sm">
                <i class="lucide-file-text" aria-hidden="true"></i> Ficha del Cliente
            </a>` : '';
        const note = app.workerMessage ? `
            <div class="agenda-worker-note">
                <i class="lucide-message-square" aria-hidden="true"></i><span>Nota: "${esc(app.workerMessage)}"</span>
            </div>` : '';
        const moved = app.previousDateTime && app.status === 'CONFIRMED' ? `
            <div class="agenda-worker-note">
                <i class="lucide-history" aria-hidden="true"></i><span>Cambiada de hora (antes: ${esc(longDate(app.previousDateTime))})</span>
            </div>` : '';
        const proposals = app.status === 'PROPOSED' && app.proposals?.length ? `
            <div class="agenda-proposals">
                <p class="agenda-proposals-title"><i class="lucide-calendar-clock" aria-hidden="true"></i> Horas propuestas al cliente:</p>
                <ul>${app.proposals.map(p => `<li>${esc(capitalize(longDate(p.dateTime)))}</li>`).join('')}</ul>
                ${app.proposalExpiresAt ? `<p class="agenda-proposals-expiry">Si no responde, se anulará el ${esc(longDate(app.proposalExpiresAt))}.</p>` : ''}
            </div>` : '';

        // Avisos de otras citas del cliente (reacciones, productos que no le sientan bien...) y veces que no vino
        const noShows = app.status !== 'NO_SHOW' && app.clientNoShows > 0
            ? `<li><strong>No se ha presentado ${app.clientNoShows === 1 ? 'a 1 cita' : `a ${esc(String(app.clientNoShows))} citas`}.</strong></li>` : '';
        const alerts = occupiesAgenda(app) && (app.clientAlerts?.length || noShows) ? `
            <div class="client-alerts">
                <p class="client-alerts-title"><i class="lucide-alert-triangle" aria-hidden="true"></i> Avisos de este cliente</p>
                <ul>${noShows}${(app.clientAlerts || []).map(a => `<li><strong>${esc(shortDate(a.dateTime))} · ${esc(a.treatmentName)}:</strong> <span>${esc(a.summary)}</span></li>`).join('')}</ul>
            </div>` : '';
        const summary = app.treatmentSummary ? `
            <div class="treatment-summary${app.treatmentAlert ? ' is-alert' : ''}">
                <p class="treatment-summary-head">
                    <i class="${app.treatmentAlert ? 'lucide-alert-triangle' : 'lucide-clipboard-pen-line'}" aria-hidden="true"></i>
                    ${app.treatmentAlert ? 'Resumen del tratamiento · aviso' : 'Resumen del tratamiento'}
                </p>
                <p class="treatment-summary-text">${esc(app.treatmentSummary)}</p>
            </div>` : '';
        const summaryButton = app.summaryEditable ? `
            <button type="button" class="btn btn-secondary btn-sm summary-button" data-action="summary">
                <i class="lucide-clipboard-pen-line" aria-hidden="true"></i> ${app.treatmentSummary ? 'Editar resumen y precio' : 'Añadir resumen y precio'}
            </button>` : '';

        let actions = '';
        if (app.status === 'PENDING') {
            actions = `
            <div class="approval-message-box">
                <label class="form-label agenda-note-label" for="message-${app.id}">Nota opcional para el cliente:</label>
                <textarea id="message-${app.id}" class="approval-msg-input" rows="2"
                          placeholder="Ej. Por favor ven 5 minutos antes o motivo del rechazo..."></textarea>
                <label class="form-label agenda-note-label" for="worker-${app.id}">La atiende:${app.workerChosenByClient && app.worker ? ` <span class="agenda-worker-asked">el cliente ha pedido a ${Cuquora.escapeHtml(app.worker.name || '')}</span>` : ''}</label>
                <select id="worker-${app.id}" class="approval-msg-input agenda-worker-select">${workerOptions(app.worker?.id ?? ME)}</select>
                <div class="agenda-card-actions agenda-card-actions--three">
                    <button type="button" class="btn-agenda-action reject" data-action="reject">
                        <i class="lucide-x agenda-action-icon" aria-hidden="true"></i> Rechazar
                    </button>
                    <button type="button" class="btn-agenda-action propose" data-action="propose">
                        <i class="lucide-calendar-clock agenda-action-icon" aria-hidden="true"></i> Otra hora
                    </button>
                    <button type="button" class="btn-agenda-action approve" data-action="approve">
                        <i class="lucide-check agenda-action-icon" aria-hidden="true"></i> Aceptar
                    </button>
                </div>
            </div>`;
        } else if (app.status === 'PROPOSED') {
            actions = `
            <div class="agenda-card-actions">
                <button type="button" class="btn-agenda-action reject" data-action="reject">
                    <i class="lucide-x agenda-action-icon" aria-hidden="true"></i> Rechazar
                </button>
                <button type="button" class="btn-agenda-action neutral" data-action="withdraw">
                    <i class="lucide-undo-2 agenda-action-icon" aria-hidden="true"></i> Retirar propuesta
                </button>
            </div>`;
        } else if (app.status === 'CONFIRMED' && isFuture(app)) {
            actions = `
            <div class="agenda-card-actions">
                <button type="button" class="btn-agenda-action reject" data-action="cancel">
                    <i class="lucide-ban agenda-action-icon" aria-hidden="true"></i> Anular
                </button>
                <button type="button" class="btn-agenda-action neutral" data-action="reschedule">
                    <i class="lucide-calendar-clock agenda-action-icon" aria-hidden="true"></i> Cambiar hora
                </button>
            </div>`;
        } else if ((app.status === 'CONFIRMED' || app.status === 'COMPLETED') && client.id) {
            actions = `
            <div class="agenda-card-actions">
                <button type="button" class="btn-agenda-action neutral" data-action="no-show">
                    <i class="lucide-user-x agenda-action-icon" aria-hidden="true"></i> No se presentó
                </button>
            </div>`;
        } else if (app.status === 'NO_SHOW') {
            actions = `
            <div class="agenda-card-actions">
                <button type="button" class="btn-agenda-action neutral" data-action="undo-no-show">
                    <i class="lucide-undo-2 agenda-action-icon" aria-hidden="true"></i> Sí vino: deshacer
                </button>
            </div>`;
        }

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
                <span>Profesional: ${esc(app.worker ? app.worker.name : 'Pendiente de asignación')}</span>
            </div>
            <div class="agenda-item-detail agenda-detail-margin">
                <i class="lucide-badge-euro" aria-hidden="true"></i>
                <span class="agenda-price-text">${euros(app.price ?? app.treatment?.price ?? 0)} €</span>
                <span class="agenda-duration-text">(${esc(app.treatment?.duration)} min)</span>
            </div>
            ${clientCardLink ? `<div class="agenda-card-footer">${clientCardLink}</div>` : ''}
            ${alerts}
            ${moved}
            ${proposals}
            ${note}
            ${summary}
            ${summaryButton}
            ${actions}`;

        card.querySelectorAll('[data-action]').forEach(button =>
            button.addEventListener('click', () => runAction(app, button.dataset.action, card)));
        return card;
    }

    async function runAction(app, action, card) {
        if (action === 'propose') return forms.openPropose(app);
        if (action === 'reschedule') return forms.openReschedule(app);
        if (action === 'summary') {
            return window.TreatmentSummary.open({
                id: app.id,
                context: `${app.treatment?.name || 'Cita'} · ${app.client?.name || app.guestName || 'Cliente'} · ${capitalize(longDate(app.dateTime))}`,
                summary: app.treatmentSummary,
                alert: app.treatmentAlert,
                price: app.price,
                listPrice: app.treatment?.price
            }, () => reload());
        }

        const message = card.querySelector('textarea')?.value || '';
        if (action === 'cancel') {
            const ok = await Cuquora.confirm(`Se anulará la cita de ${app.client?.name || 'este cliente'} del ${longDate(app.dateTime)} y le avisaremos.`, {
                title: '¿Anular la cita?',
                confirmText: 'Anular cita'
            });
            if (!ok) return;
        }
        if (action === 'no-show') {
            const ok = await Cuquora.confirm(`La cita de ${app.client?.name || 'este cliente'} del ${longDate(app.dateTime)} quedará como «No se presentó»: no sumará puntos y lo verás en su ficha.`, {
                title: '¿No se presentó?',
                confirmText: 'No se presentó'
            });
            if (!ok) return;
        }
        const endpoints = { approve: 'approve', reject: 'reject', withdraw: 'withdraw-proposal', cancel: 'cancel',
            'no-show': 'no-show', 'undo-no-show': 'no-show' };
        const params = action === 'no-show' ? {} : action === 'undo-no-show' ? { undo: 'true' } : { message };
        if (action === 'approve') params.workerId = card.querySelector('.agenda-worker-select')?.value;
        const buttons = card.querySelectorAll('[data-action]');
        buttons.forEach(b => { b.disabled = true; });
        const result = await forms.post(`/worker/appointments/${encodeURIComponent(app.id)}/${endpoints[action]}`, params);
        if (!result.ok) {
            await Cuquora.alert(result.message, 'No se ha podido actualizar la cita');
            buttons.forEach(b => { b.disabled = false; });
            return;
        }
        await reload({});
    }

    function blockCard(part) {
        const card = document.createElement('article');
        card.className = 'agenda-item-card block-card';
        card.innerHTML = `
            <div class="agenda-card-top">
                <div class="agenda-item-time">
                    <i class="lucide-calendar-off" aria-hidden="true"></i>
                    <span>${esc(blockSpan(part))}</span>
                </div>
                <span class="badge badge-blocked">Bloqueado</span>
            </div>
            <h3 class="agenda-item-treatment">${esc(blockWho(part.block))}</h3>
            ${part.block.reason ? `<div class="agenda-item-detail"><i class="lucide-message-square" aria-hidden="true"></i><span>${esc(part.block.reason)}</span></div>` : ''}
            <div class="agenda-card-actions">
                <button type="button" class="btn-agenda-action neutral" data-remove-block>
                    <i class="lucide-unlock agenda-action-icon" aria-hidden="true"></i> Quitar bloqueo
                </button>
            </div>`;
        card.querySelector('[data-remove-block]').addEventListener('click', () => forms.removeBlock(part.block));
        return card;
    }

    function selectDay(day, month, year, isUserClick) {
        selectedDayKey = dateKey(day, month, year);
        renderCalendar();

        const title = new Date(year, month, day).toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
        dayTitle.textContent = title.charAt(0).toUpperCase() + title.slice(1);

        const apps = appointmentsOn(selectedDayKey);
        const dayBlocks = blocksOn(selectedDayKey);
        const count = status => apps.filter(a => a.status === status).length;
        dayCount.textContent = [
            [count('CONFIRMED'), 'confirmada', 'confirmadas'],
            [count('COMPLETED'), 'completada', 'completadas'],
            [count('NO_SHOW'), 'no se presentó', 'no se presentaron'],
            [count('PENDING'), 'solicitud pendiente', 'solicitudes pendientes'],
            [count('PROPOSED'), 'propuesta enviada', 'propuestas enviadas'],
            [dayBlocks.length, 'bloqueo', 'bloqueos']
        ].filter(([n]) => n > 0).map(([n, one, many]) => plural(n, one, many)).join(', ') || 'Sin citas este día';

        list.innerHTML = '';
        if (apps.length === 0 && dayBlocks.length === 0) {
            list.innerHTML = `
                <div class="notification-empty">
                    <i class="lucide-calendar-days" aria-hidden="true"></i>
                    <span>No hay citas programadas para esta jornada</span>
                </div>`;
        } else {
            dayBlocks.forEach(part => list.appendChild(blockCard(part)));
            apps.forEach(app => list.appendChild(appointmentCard(app)));
        }

        if (isUserClick && window.innerWidth <= 900) {
            dayTitle.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'start' });
            dayTitle.focus({ preventScroll: true });
        }
    }

    function refresh() {
        renderCalendar();
        if (selectedDayKey) {
            const [y, m, d] = selectedDayKey.split('-').map(Number);
            selectDay(d, m - 1, y, false);
        }
        if (currentView === 'week') renderWeek();
        // Si la cita abierta en el modal ha cambiado, se repinta su ficha
        if (modalApp && !document.getElementById('appointmentModal').hidden) {
            modalBody.replaceChildren(appointmentCard(modalApp));
        }
    }

    /* =====================================================================
       Vista semanal
       ===================================================================== */

    function mondayOf(date) {
        const offset = (date.getDay() + 6) % 7; // lunes = 0
        return new Date(date.getFullYear(), date.getMonth(), date.getDate() - offset);
    }

    function weekDays() {
        return Array.from({ length: 7 }, (_, i) =>
            new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + i));
    }

    const activeOn = key => appointmentsOn(key).filter(occupiesAgenda);

    /** Horas propuestas a clientes (bloques fantasma) que caen en un día. */
    const proposalsOn = key => appointments
        .filter(app => app.status === 'PROPOSED')
        .flatMap(app => (app.proposals || [])
            .filter(p => p.dateTime.startsWith(key))
            .map(p => ({ app, start: minutesOfTime(p.dateTime), end: minutesOfTime(p.dateTime) + durationOf(app) })));

    function weekTitleText(days) {
        const first = days[0];
        const last = days[6];
        const month = d => d.toLocaleDateString('es-ES', { month: 'long' });
        if (first.getFullYear() !== last.getFullYear()) {
            return `${first.getDate()} de ${month(first)} de ${first.getFullYear()} – ${last.getDate()} de ${month(last)} de ${last.getFullYear()}`;
        }
        if (first.getMonth() !== last.getMonth()) {
            return `${first.getDate()} de ${month(first)} – ${last.getDate()} de ${month(last)} de ${last.getFullYear()}`;
        }
        return `${first.getDate()} – ${last.getDate()} de ${month(first)} de ${first.getFullYear()}`;
    }

    /**
     * Reparte en carriles las citas que se solapan en un mismo día (p. ej. dos profesionales a la vez):
     * cada cita recibe su carril y el número de carriles de su grupo para dividir el ancho.
     */
    function layoutLanes(items) {
        const sorted = [...items].sort((a, b) => a.start - b.start);
        let group = [];
        let groupEnd = -1;
        let laneEnds = [];
        const closeGroup = () => group.forEach(item => { item.lanes = laneEnds.length; });
        sorted.forEach(item => {
            if (item.start >= groupEnd && group.length) {
                closeGroup();
                group = [];
                laneEnds = [];
            }
            let lane = laneEnds.findIndex(laneEnd => laneEnd <= item.start);
            if (lane === -1) {
                lane = laneEnds.length;
                laneEnds.push(item.end);
            } else {
                laneEnds[lane] = item.end;
            }
            item.lane = lane;
            item.lanes = 1;
            group.push(item);
            groupEnd = Math.max(groupEnd, item.end);
        });
        closeGroup();
        return sorted;
    }

    function renderWeek() {
        const days = weekDays();
        const todayKey = keyOf(new Date());
        const perDay = days.map(date => ({
            date,
            key: keyOf(date),
            apps: activeOn(keyOf(date)),
            ghosts: proposalsOn(keyOf(date)),
            blocks: blocksOn(keyOf(date))
        }));

        weekTitle.textContent = weekTitleText(days);
        renderWeekSummary(perDay);

        // Franja horaria: el horario del centro, ampliado si alguna cita empieza antes o acaba después
        let firstMinute = OPEN_HOUR * 60;
        let lastMinute = CLOSE_HOUR * 60;
        perDay.forEach(day => [...day.apps.map(app => ({ start: startMinutes(app), end: startMinutes(app) + durationOf(app) })), ...day.ghosts]
            .forEach(item => {
                firstMinute = Math.min(firstMinute, Math.floor(item.start / 60) * 60);
                lastMinute = Math.max(lastMinute, Math.ceil(item.end / 60) * 60);
            }));
        const span = lastMinute - firstMinute;
        const percent = minutes => `${((minutes - firstMinute) / span) * 100}%`;

        // Los días cerrados sin citas ocupan menos ancho
        perDay.forEach(day => {
            const isoDay = ((day.date.getDay() + 6) % 7) + 1;
            day.closed = !WORKING_DAYS.includes(isoDay) || day.blocks.some(part => part.allDay && !part.block.workerId);
            day.narrow = day.closed && day.apps.length === 0;
        });

        const inner = document.createElement('div');
        inner.className = 'week-grid-inner';
        inner.style.setProperty('--week-columns',
            perDay.map(day => (day.narrow ? 'minmax(0, 0.45fr)' : 'minmax(0, 1fr)')).join(' '));
        inner.style.setProperty('--hours', String(span / 60));

        inner.appendChild(Object.assign(document.createElement('div'), { className: 'week-corner' }));
        perDay.forEach((day, i) => inner.appendChild(dayHeader(day, i, day.key === todayKey)));

        const times = document.createElement('div');
        times.className = 'week-times';
        times.setAttribute('aria-hidden', 'true');
        for (let minute = firstMinute; minute < lastMinute; minute += 60) {
            const label = document.createElement('span');
            label.textContent = clock(minute);
            label.style.setProperty('--top', percent(minute));
            times.appendChild(label);
        }
        inner.appendChild(times);

        perDay.forEach(day => {
            const column = document.createElement('div');
            column.className = 'week-day-col'
                + (day.closed ? ' is-closed' : '') + (day.narrow ? ' is-narrow' : '')
                + (day.key === todayKey ? ' is-today' : '');
            column.dataset.date = day.key;
            column.setAttribute('role', 'list');
            column.setAttribute('aria-label', capitalize(day.date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })));

            // Horas en las que ese día el centro no abre: antes de abrir, el cierre de mediodía y después de cerrar
            // (no en días cerrados ni con un bloqueo de todo el día, que ya van rayados enteros)
            if (!day.closed && !day.blocks.some(part => part.allDay)) {
                const isoDay = ((day.date.getDay() + 6) % 7) + 1;
                const periods = DAY_PERIODS[isoDay] || [[OPEN_HOUR * 60, CLOSE_HOUR * 60]];
                const gaps = [];
                let cursor = firstMinute;
                periods.forEach(([from, to]) => {
                    gaps.push([cursor, from]);
                    cursor = to;
                });
                gaps.push([cursor, lastMinute]);
                gaps.forEach(([from, to]) => {
                    if (to <= from) return;
                    const band = document.createElement('div');
                    band.className = 'week-closed-band';
                    band.setAttribute('aria-hidden', 'true');
                    band.style.setProperty('--top', percent(from));
                    band.style.setProperty('--height', `${((to - from) / span) * 100}%`);
                    column.appendChild(band);
                });
            }

            // Bloqueos (debajo de las citas)
            day.blocks.forEach(part => {
                const from = Math.max(part.from, firstMinute);
                const to = Math.min(part.to, lastMinute);
                if (to <= from) return;
                const block = document.createElement('button');
                block.type = 'button';
                block.className = 'week-block' + (part.block.workerId ? '' : ' is-center');
                block.style.setProperty('--top', percent(from));
                block.style.setProperty('--height', `${((to - from) / span) * 100}%`);
                block.innerHTML = `<span class="week-block-label"><i class="lucide-calendar-off" aria-hidden="true"></i> ${esc(blockWho(part.block))}</span>`;
                const text = `Bloqueado: ${blockWho(part.block)}, ${blockSpan(part)}${part.block.reason ? ` (${part.block.reason})` : ''}. Pulsa para quitarlo`;
                block.title = text;
                block.setAttribute('aria-label', text);
                block.addEventListener('click', () => forms.removeBlock(part.block));
                column.appendChild(block);
            });

            const items = [
                ...day.apps.map(app => ({ app, start: startMinutes(app), end: startMinutes(app) + durationOf(app), ghost: false })),
                ...day.ghosts.map(g => ({ ...g, ghost: true }))
            ];
            layoutLanes(items).forEach(item => {
                const element = item.ghost ? ghostButton(item) : eventButton(item.app, 'week-event');
                element.setAttribute('role', 'listitem');
                element.style.setProperty('--top', percent(item.start));
                element.style.setProperty('--height', `${((item.end - item.start) / span) * 100}%`);
                element.style.setProperty('--left', `${(item.lane / item.lanes) * 100}%`);
                element.style.setProperty('--width', `${100 / item.lanes}%`);
                if (item.end - item.start <= 30) element.classList.add('is-short');
                else if (item.end - item.start < 50) element.classList.add('is-compact');
                if (!item.ghost && item.app.status === 'CONFIRMED' && isFuture(item.app)) enableDrag(element, item.app);
                column.appendChild(element);
            });
            if (day.key === todayKey) {
                const line = document.createElement('div');
                line.className = 'week-now-line';
                line.setAttribute('aria-hidden', 'true');
                column.appendChild(line);
            }
            column.addEventListener('click', e => {
                if (e.target !== column) return;
                const minute = minuteAt(column, e.clientY);
                if (new Date(`${day.key}T${clock(minute)}`) <= new Date()) return;
                forms.openNew({ date: day.key, minute });
            });
            inner.appendChild(column);
        });

        weekGrid.replaceChildren(inner);
        weekGrid.dataset.firstMinute = String(firstMinute);
        weekGrid.dataset.span = String(span);
        fitWeekToScreen();
        updateNowLine();
        renderWeekList(perDay, todayKey);
    }

    /** En pantalla completa, cada hora mide lo justo para que el horario entero quepa en el alto disponible. */
    function fitWeekToScreen() {
        if (!document.body.classList.contains('is-calendar-fullscreen')) {
            weekView.style.removeProperty('--hour-height');
            return;
        }
        const inner = weekGrid.querySelector('.week-grid-inner');
        if (!inner) return;
        const head = inner.querySelector('.week-day-head');
        const rowGap = parseFloat(getComputedStyle(inner).rowGap) || 0;
        const available = weekGrid.clientHeight - (head ? head.offsetHeight : 0) - rowGap;
        const hoursShown = Number(weekGrid.dataset.span) / 60;
        weekView.style.setProperty('--hour-height', `${Math.max(36, Math.floor(available / hoursShown))}px`);
    }

    /** Minuto del día (redondeado al hueco de reserva) en la altura y de una columna. */
    function minuteAt(column, clientY) {
        const rect = column.getBoundingClientRect();
        const first = Number(weekGrid.dataset.firstMinute);
        const span = Number(weekGrid.dataset.span);
        const raw = first + ((clientY - rect.top) / rect.height) * span;
        const rounded = Math.floor(raw / SLOT) * SLOT;
        return Math.max(first, Math.min(first + span - SLOT, rounded));
    }

    function renderWeekSummary(perDay) {
        const all = perDay.flatMap(day => day.apps);
        const pending = all.filter(app => app.status === 'PENDING').length;
        const proposed = all.filter(app => app.status === 'PROPOSED').length;
        const income = all
            .filter(app => app.status === 'CONFIRMED' || app.status === 'COMPLETED')
            .reduce((sum, app) => sum + Number(app.price ?? app.treatment?.price ?? 0), 0);
        weekSummary.innerHTML = `
            <li><i class="lucide-calendar-check" aria-hidden="true"></i> ${plural(all.length, 'cita', 'citas')}</li>
            <li class="${pending ? 'is-pending' : ''}"><i class="lucide-bell" aria-hidden="true"></i> ${plural(pending, 'solicitud pendiente', 'solicitudes pendientes')}</li>
            ${proposed ? `<li><i class="lucide-calendar-clock" aria-hidden="true"></i> ${plural(proposed, 'propuesta enviada', 'propuestas enviadas')}</li>` : ''}
            <li><i class="lucide-badge-euro" aria-hidden="true"></i> ${euros(income)} € previstos</li>`;
    }

    function dayHeader(day, index, isToday) {
        const head = document.createElement('button');
        head.type = 'button';
        head.className = 'week-day-head' + (isToday ? ' is-today' : '') + (day.narrow ? ' is-narrow' : '');
        const pending = day.apps.filter(isOpenRequest).length;
        const detail = day.apps.length
            ? plural(day.apps.length, 'cita', 'citas')
            : (day.closed ? 'Cerrado' : 'Sin citas');
        head.innerHTML = `
            <span class="week-day-name">${WEEKDAYS_SHORT[index]}</span>
            <span class="week-day-number">${day.date.getDate()}</span>
            <span class="week-day-count">${detail}</span>
            ${pending ? `<span class="week-day-pending">${plural(pending, 'solicitud', 'solicitudes')}</span>` : ''}`;
        const fullDate = day.date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
        head.setAttribute('aria-label', `Ver ${fullDate} en la vista mensual: ${detail}`
            + (pending ? `, ${plural(pending, 'solicitud pendiente', 'solicitudes pendientes')}` : ''));
        if (isToday) head.setAttribute('aria-current', 'date');
        head.title = 'Ver este día en la vista mensual';
        head.addEventListener('click', () => openDayInMonth(day.date));
        return head;
    }

    /** Botón de una cita (bloque de la rejilla o fila de la lista móvil): abre su ficha en el modal. */
    function eventButton(app, className) {
        const status = STATUS[app.status] || STATUS.CONFIRMED;
        const start = startMinutes(app);
        const client = app.client?.name || app.guestName || 'Cliente';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `${className} status-${app.status.toLowerCase()}`;
        button.dataset.appointmentId = String(app.id);
        const flag = app.status === 'PENDING' ? '<span class="week-event-flag">Solicitud</span>'
            : app.status === 'PROPOSED' ? '<span class="week-event-flag">Propuesta enviada</span>'
            : app.status === 'NO_SHOW' ? '<span class="week-event-flag">No se presentó</span>' : '';
        button.innerHTML = `
            <span class="week-event-time">${clock(start)} – ${clock(start + durationOf(app))}</span>
            <span class="week-event-title">${esc(app.treatment?.name)}</span>
            <span class="week-event-client">${esc(client)}${app.worker && WORKERS.length > 1 ? ` · ${esc(app.worker.name)}` : ''}</span>
            ${flag}`;
        button.setAttribute('aria-label',
            `${clock(start)}, ${app.treatment?.name || 'Cita'}, ${client}, ${status.label}. Ver detalle`);
        // En la rejilla el texto puede no caber: el texto completo sale al pasar el ratón
        button.title = `${clock(start)} – ${clock(start + durationOf(app))} · ${app.treatment?.name || 'Cita'} · ${client} (${status.label})`;
        button.addEventListener('click', () => {
            if (button.dataset.dragged === 'true') {
                delete button.dataset.dragged;
                return;
            }
            openAppointment(app);
        });
        return button;
    }

    /** Hora propuesta a un cliente: reservada mientras decide (bloque discontinuo). */
    function ghostButton(item) {
        const client = item.app.client?.name || item.app.guestName || 'Cliente';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'week-event week-ghost';
        button.dataset.appointmentId = String(item.app.id);
        button.innerHTML = `
            <span class="week-event-time">${clock(item.start)} – ${clock(item.end)}</span>
            <span class="week-event-title">Propuesta a ${esc(client)}</span>`;
        const text = `${clock(item.start)}, hora propuesta a ${client} para ${item.app.treatment?.name || 'su cita'}. Ver la solicitud`;
        button.title = text;
        button.setAttribute('aria-label', text);
        button.addEventListener('click', () => openAppointment(item.app));
        return button;
    }

    /* ---------- Arrastrar una cita confirmada a otra hora (escritorio) ---------- */

    function enableDrag(element, app) {
        element.classList.add('is-draggable');
        element.addEventListener('pointerdown', e => {
            if (e.button !== 0 || e.pointerType === 'touch') return;
            const startX = e.clientX;
            const startY = e.clientY;
            const grabOffset = e.clientY - element.getBoundingClientRect().top;
            let dragging = false;
            let target = null;
            let shadow = null;

            const move = ev => {
                if (!dragging && Math.hypot(ev.clientX - startX, ev.clientY - startY) < 6) return;
                if (!dragging) {
                    dragging = true;
                    element.classList.add('is-dragging');
                    shadow = document.createElement('div');
                    shadow.className = 'week-drop-shadow';
                    shadow.setAttribute('aria-hidden', 'true');
                }
                const column = document.elementsFromPoint(ev.clientX, ev.clientY).find(el => el.classList?.contains('week-day-col'));
                if (!column) return;
                const minute = minuteAt(column, ev.clientY - grabOffset + 4);
                const first = Number(weekGrid.dataset.firstMinute);
                const span = Number(weekGrid.dataset.span);
                target = { date: column.dataset.date, minute };
                shadow.style.setProperty('--top', `${((minute - first) / span) * 100}%`);
                shadow.style.setProperty('--height', `${(durationOf(app) / span) * 100}%`);
                shadow.textContent = `${capitalize(new Date(column.dataset.date + 'T00:00:00').toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric' }))} · ${clock(minute)}`;
                if (shadow.parentElement !== column) column.appendChild(shadow);
            };

            const up = async () => {
                document.removeEventListener('pointermove', move);
                document.removeEventListener('pointerup', up);
                element.classList.remove('is-dragging');
                shadow?.remove();
                if (!dragging || !target) return;
                element.dataset.dragged = 'true';
                const dateTime = `${target.date}T${clock(target.minute)}`;
                if (dateTime === app.dateTime.substring(0, 16)) return;
                if (new Date(dateTime) <= new Date()) {
                    await Cuquora.alert('No se pueden poner citas en una hora que ya ha pasado.', 'No se ha podido mover la cita');
                    return;
                }
                const ok = await Cuquora.confirm(`¿Mover la cita de ${app.client?.name || 'este cliente'} al ${longDate(dateTime)}? Le avisaremos del cambio.`, {
                    title: 'Cambiar la hora de la cita',
                    confirmText: 'Mover cita',
                    tone: 'default'
                });
                if (!ok) return;
                const result = await forms.reschedule(app, dateTime, app.worker?.id);
                if (!result.ok && result.message) await Cuquora.alert(result.message, 'No se ha podido mover la cita');
            };

            document.addEventListener('pointermove', move);
            document.addEventListener('pointerup', up);
        });
    }

    function renderWeekList(perDay, todayKey) {
        weekList.replaceChildren(...perDay.map(day => {
            const section = document.createElement('section');
            section.className = 'week-list-day' + (day.key === todayKey ? ' is-today' : '')
                + (day.narrow ? ' is-closed' : '');
            const title = capitalize(day.date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' }));
            const detail = day.apps.length ? plural(day.apps.length, 'cita', 'citas') : (day.closed ? 'Cerrado' : 'Sin citas');
            section.innerHTML = `
                <h3 class="week-list-title">
                    <span>${esc(title)}${day.key === todayKey ? ' <span class="week-list-today">Hoy</span>' : ''}</span>
                    <span class="week-list-count">${detail}</span>
                </h3>`;
            day.blocks.forEach(part => {
                const row = document.createElement('button');
                row.type = 'button';
                row.className = 'week-list-item week-list-block';
                row.innerHTML = `
                    <span class="week-event-time">${esc(blockSpan(part))}</span>
                    <span class="week-event-title"><i class="lucide-calendar-off" aria-hidden="true"></i> Bloqueado: ${esc(blockWho(part.block))}</span>
                    ${part.block.reason ? `<span class="week-event-client">${esc(part.block.reason)}</span>` : ''}`;
                row.setAttribute('aria-label', `Bloqueado: ${blockWho(part.block)}, ${blockSpan(part)}. Pulsa para quitarlo`);
                row.addEventListener('click', () => forms.removeBlock(part.block));
                section.appendChild(row);
            });
            day.apps.forEach(app => section.appendChild(eventButton(app, 'week-list-item')));
            return section;
        }));
    }

    /** Coloca la línea de «ahora» en la columna de hoy (se llama cada 15 s sin repintar la rejilla). */
    function updateNowLine() {
        const line = weekGrid.querySelector('.week-now-line');
        if (!line) return;
        const now = new Date();
        const minutes = now.getHours() * 60 + now.getMinutes();
        const first = Number(weekGrid.dataset.firstMinute);
        const span = Number(weekGrid.dataset.span);
        const inside = minutes >= first && minutes <= first + span;
        line.hidden = !inside;
        if (inside) line.style.setProperty('--top', `${((minutes - first) / span) * 100}%`);
    }

    function openAppointment(app) {
        modalApp = app;
        modalBody.replaceChildren(appointmentCard(app));
        Cuquora.openModal('appointmentModal');
    }

    // Al cerrar el modal, el foco vuelve a la cita (el bloque puede haberse repintado mientras tanto)
    document.getElementById('appointmentModal').addEventListener('modalclose', () => {
        if (!modalApp) return;
        const id = String(modalApp.id);
        modalApp = null;
        const target = [...document.querySelectorAll('[data-appointment-id]')]
            .find(el => el.dataset.appointmentId === id && el.offsetParent !== null);
        target?.focus();
    });

    function openDayInMonth(date) {
        if (document.fullscreenElement) document.exitFullscreen();
        currentYear = date.getFullYear();
        currentMonth = date.getMonth();
        setView('month');
        selectDay(date.getDate(), currentMonth, currentYear, true);
        dayTitle.focus({ preventScroll: window.innerWidth > 900 });
    }

    function setView(view) {
        currentView = view === 'week' ? 'week' : 'month';
        monthView.hidden = currentView !== 'month';
        weekView.hidden = currentView !== 'week';
        viewButtons.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.calendarView === currentView)));
        try { localStorage.setItem(VIEW_KEY, currentView); } catch (e) { /* almacenamiento no disponible */ }
        if (currentView === 'week') {
            const [y, m, d] = (selectedDayKey || keyOf(new Date())).split('-').map(Number);
            weekStart = mondayOf(new Date(y, m - 1, d));
            renderWeek();
        }
    }

    const moveWeek = days => {
        followToday = false;
        weekStart = new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + days);
        renderWeek();
    };
    document.getElementById('prevWeekBtn').addEventListener('click', () => moveWeek(-7));
    document.getElementById('nextWeekBtn').addEventListener('click', () => moveWeek(7));
    document.getElementById('weekTodayBtn').addEventListener('click', () => {
        followToday = true;
        weekStart = mondayOf(new Date());
        renderWeek();
    });
    viewButtons.forEach(b => b.addEventListener('click', () => setView(b.dataset.calendarView)));

    // Pantalla completa de toda la página (no solo de la semana) para que los modales sigan viéndose encima
    if (document.fullscreenEnabled) {
        fullscreenBtn.hidden = false;
        fullscreenBtn.addEventListener('click', () => {
            if (document.fullscreenElement) document.exitFullscreen();
            else document.documentElement.requestFullscreen().catch(err => console.error(err));
        });
        document.addEventListener('fullscreenchange', () => {
            const on = Boolean(document.fullscreenElement);
            document.body.classList.toggle('is-calendar-fullscreen', on);
            fullscreenBtn.setAttribute('aria-pressed', String(on));
            fullscreenBtn.querySelector('i').className = on ? 'lucide-minimize' : 'lucide-maximize';
            fullscreenBtn.querySelector('span').textContent = on ? 'Salir de pantalla completa' : 'Pantalla completa';
            clearInterval(liveTimer);
            liveTimer = null;
            if (on) {
                followToday = true;
                weekStart = mondayOf(new Date());
                liveTimer = setInterval(liveReload, LIVE_RELOAD_MS);
                reload();
            } else {
                renderWeek();
            }
        });
        window.addEventListener('resize', () => {
            if (document.fullscreenElement) fitWeekToScreen();
        });
    }

    /** Recarga periódica en pantalla completa: trae las citas nuevas sin estorbar si se está usando la agenda. */
    function liveReload() {
        if (document.body.classList.contains('modal-open') || weekGrid.querySelector('.is-dragging')) return;
        if (followToday) weekStart = mondayOf(new Date());
        reload();
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

    // «Nueva cita» y «Bloquear horario» abren con el día seleccionado (o hoy)
    const selectedOrToday = () => (selectedDayKey && selectedDayKey >= keyOf(new Date()) ? selectedDayKey : keyOf(new Date()));
    document.getElementById('newAppointmentBtn').addEventListener('click', () => forms.openNew({ date: selectedOrToday() }));
    document.getElementById('newBlockBtn').addEventListener('click', () => forms.openBlock({ date: selectedOrToday() }));
    forms.init({ reload });

    // Día inicial: el de ?date=YYYY-MM-DD (enlaces de las notificaciones) o hoy.
    const target = new URLSearchParams(window.location.search).get('date');
    const start = /^\d{4}-\d{2}-\d{2}$/.test(target || '') ? new Date(target + 'T00:00:00') : new Date();
    currentYear = start.getFullYear();
    currentMonth = start.getMonth();
    selectDay(start.getDate(), currentMonth, currentYear, false);

    // Disposición: ?view=week|month en la URL o la última elegida en este navegador
    let savedView = new URLSearchParams(window.location.search).get('view');
    if (!savedView) {
        try { savedView = localStorage.getItem(VIEW_KEY); } catch (e) { /* almacenamiento no disponible */ }
    }
    if (savedView === 'week') setView('week');

    refreshFinishedAppointments();
    setInterval(() => {
        refreshFinishedAppointments();
        updateNowLine();
    }, 15000);
});
