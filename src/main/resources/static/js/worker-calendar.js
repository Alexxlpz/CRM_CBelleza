/*
 * Agenda del trabajador (worker/calendar.html), con dos disposiciones:
 *  - Mes: calendario con indicadores por día y la lista de citas del día seleccionado.
 *  - Semana: rejilla horaria de lunes a domingo con cada cita como un bloque del alto de su
 *    duración (en móvil, lista por días). Al pulsar una cita se abre su ficha en un modal.
 * En las dos se pueden aprobar o rechazar solicitudes (POST /worker/appointments/{id}/approve|reject).
 * La disposición elegida se recuerda en el navegador.
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const esc = Cuquora.escapeHtml;
    const appointments = window.CALENDAR_APPOINTMENTS || [];
    const hours = window.CALENDAR_HOURS || {};
    const OPEN_HOUR = Number.isInteger(hours.openHour) ? hours.openHour : 9;
    const CLOSE_HOUR = Number.isInteger(hours.closeHour) ? hours.closeHour : 20;
    const WORKING_DAYS = Array.isArray(hours.workingDays) ? hours.workingDays : [1, 2, 3, 4, 5];
    const WEEKDAYS_SHORT = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
    const VIEW_KEY = 'cuquora.calendar.view';
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
    const monthView = document.getElementById('monthView');
    const weekView = document.getElementById('weekView');
    const weekGrid = document.getElementById('weekGrid');
    const weekList = document.getElementById('weekList');
    const weekTitle = document.getElementById('weekTitle');
    const weekSummary = document.getElementById('weekSummary');
    const viewButtons = document.querySelectorAll('[data-calendar-view]');
    const modalBody = document.getElementById('appointmentModalBody');

    let currentYear;
    let currentMonth;
    let selectedDayKey = null;
    let currentView = 'month';
    let weekStart = null;      // lunes de la semana visible
    let modalApp = null;       // cita abierta en el modal de la vista semanal

    const pad = n => String(n).padStart(2, '0');
    const dateKey = (day, month, year) => `${year}-${pad(month + 1)}-${pad(day)}`;
    const appointmentsOn = key => appointments
        .filter(app => app.dateTime && app.dateTime.startsWith(key))
        .sort((a, b) => a.dateTime.localeCompare(b.dateTime));
    const euros = value => Number(value).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const capitalize = text => text.charAt(0).toUpperCase() + text.slice(1);
    const keyOf = date => dateKey(date.getDate(), date.getMonth(), date.getFullYear());
    const startMinutes = app => {
        const [h, m] = app.dateTime.split('T')[1].split(':').map(Number);
        return h * 60 + m;
    };
    const durationOf = app => app.treatment?.duration || 60;
    const clock = minutes => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
    const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

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
        if (currentView === 'week') renderWeek();
        // Si la cita abierta en el modal ha cambiado (aceptada, rechazada, completada), se repinta su ficha
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

    /** Citas que ocupan agenda (las rechazadas no se pintan en la semana), ordenadas por hora. */
    const activeOn = key => appointmentsOn(key).filter(app => app.status !== 'REJECTED');

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
     * Reparte en carriles las citas que se solapan en un mismo día (p. ej. dos estilistas a la vez):
     * cada cita recibe su carril y el número de carriles de su grupo para dividir el ancho.
     */
    function layoutLanes(apps) {
        const placed = [];
        let group = [];
        let groupEnd = -1;
        let laneEnds = [];
        const closeGroup = () => group.forEach(item => { item.lanes = laneEnds.length; });
        apps.forEach(app => {
            const start = startMinutes(app);
            const end = start + durationOf(app);
            if (start >= groupEnd && group.length) {
                closeGroup();
                group = [];
                laneEnds = [];
            }
            let lane = laneEnds.findIndex(laneEnd => laneEnd <= start);
            if (lane === -1) {
                lane = laneEnds.length;
                laneEnds.push(end);
            } else {
                laneEnds[lane] = end;
            }
            const item = { app, start, end, lane, lanes: 1 };
            group.push(item);
            placed.push(item);
            groupEnd = Math.max(groupEnd, end);
        });
        closeGroup();
        return placed;
    }

    function renderWeek() {
        const days = weekDays();
        const todayKey = keyOf(new Date());
        const perDay = days.map(date => ({ date, key: keyOf(date), apps: activeOn(keyOf(date)) }));

        weekTitle.textContent = weekTitleText(days);
        renderWeekSummary(perDay);

        // Franja horaria: el horario del centro, ampliado si alguna cita empieza antes o acaba después
        let firstMinute = OPEN_HOUR * 60;
        let lastMinute = CLOSE_HOUR * 60;
        perDay.forEach(day => day.apps.forEach(app => {
            firstMinute = Math.min(firstMinute, Math.floor(startMinutes(app) / 60) * 60);
            lastMinute = Math.max(lastMinute, Math.ceil((startMinutes(app) + durationOf(app)) / 60) * 60);
        }));
        const span = lastMinute - firstMinute;
        const percent = minutes => `${((minutes - firstMinute) / span) * 100}%`;

        // Los días cerrados sin citas ocupan menos ancho
        perDay.forEach(day => {
            const isoDay = ((day.date.getDay() + 6) % 7) + 1;
            day.closed = !WORKING_DAYS.includes(isoDay);
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
            column.setAttribute('role', 'list');
            column.setAttribute('aria-label', capitalize(day.date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })));
            layoutLanes(day.apps).forEach(item => {
                const block = eventButton(item.app, 'week-event');
                block.setAttribute('role', 'listitem');
                block.style.setProperty('--top', percent(item.start));
                block.style.setProperty('--height', `${((item.end - item.start) / span) * 100}%`);
                block.style.setProperty('--left', `${(item.lane / item.lanes) * 100}%`);
                block.style.setProperty('--width', `${100 / item.lanes}%`);
                if (item.end - item.start <= 30) block.classList.add('is-short');
                else if (item.end - item.start < 50) block.classList.add('is-compact');
                column.appendChild(block);
            });
            if (day.key === todayKey) {
                const line = document.createElement('div');
                line.className = 'week-now-line';
                line.setAttribute('aria-hidden', 'true');
                column.appendChild(line);
            }
            inner.appendChild(column);
        });

        weekGrid.replaceChildren(inner);
        weekGrid.dataset.firstMinute = String(firstMinute);
        weekGrid.dataset.span = String(span);
        updateNowLine();
        renderWeekList(perDay, todayKey);
    }

    function renderWeekSummary(perDay) {
        const all = perDay.flatMap(day => day.apps);
        const pending = all.filter(app => app.status === 'PENDING').length;
        const income = all
            .filter(app => app.status === 'CONFIRMED' || app.status === 'COMPLETED')
            .reduce((sum, app) => sum + Number(app.treatment?.price || 0), 0);
        weekSummary.innerHTML = `
            <li><i class="lucide-calendar-check" aria-hidden="true"></i> ${plural(all.length, 'cita', 'citas')}</li>
            <li class="${pending ? 'is-pending' : ''}"><i class="lucide-bell" aria-hidden="true"></i> ${plural(pending, 'solicitud pendiente', 'solicitudes pendientes')}</li>
            <li><i class="lucide-badge-euro" aria-hidden="true"></i> ${euros(income)} € previstos</li>`;
    }

    function dayHeader(day, index, isToday) {
        const head = document.createElement('button');
        head.type = 'button';
        head.className = 'week-day-head' + (isToday ? ' is-today' : '') + (day.narrow ? ' is-narrow' : '');
        const pending = day.apps.filter(app => app.status === 'PENDING').length;
        const detail = day.apps.length
            ? plural(day.apps.length, 'cita', 'citas')
            : (day.closed ? 'Cerrado' : 'Sin citas');
        head.innerHTML = `
            <span class="week-day-name">${WEEKDAYS_SHORT[index]}</span>
            <span class="week-day-number">${day.date.getDate()}</span>
            <span class="week-day-count">${detail}</span>
            ${pending ? `<span class="week-day-pending">${plural(pending, 'solicitud', 'solicitudes')}</span>` : ''}`;
        const longDate = day.date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
        head.setAttribute('aria-label', `Ver ${longDate} en la vista mensual: ${detail}`
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
        button.innerHTML = `
            <span class="week-event-time">${clock(start)} – ${clock(start + durationOf(app))}</span>
            <span class="week-event-title">${esc(app.treatment?.name)}</span>
            <span class="week-event-client">${esc(client)}</span>
            ${app.status === 'PENDING' ? '<span class="week-event-flag">Solicitud</span>' : ''}`;
        button.setAttribute('aria-label',
            `${clock(start)}, ${app.treatment?.name || 'Cita'}, ${client}, ${status.label}. Ver detalle`);
        // En la rejilla el texto puede no caber: el texto completo sale al pasar el ratón
        button.title = `${clock(start)} – ${clock(start + durationOf(app))} · ${app.treatment?.name || 'Cita'} · ${client} (${status.label})`;
        button.addEventListener('click', () => openAppointment(app));
        return button;
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
        weekStart = new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + days);
        renderWeek();
    };
    document.getElementById('prevWeekBtn').addEventListener('click', () => moveWeek(-7));
    document.getElementById('nextWeekBtn').addEventListener('click', () => moveWeek(7));
    document.getElementById('weekTodayBtn').addEventListener('click', () => {
        weekStart = mondayOf(new Date());
        renderWeek();
    });
    viewButtons.forEach(b => b.addEventListener('click', () => setView(b.dataset.calendarView)));

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
