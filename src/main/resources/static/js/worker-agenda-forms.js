/*
 * Formularios de la agenda del trabajador (worker/calendar.html):
 *  - Nueva cita (cliente de la cartera o nuevo, tratamiento, día, hora y profesional): nace confirmada.
 *  - Cambiar hora o profesional de una cita confirmada.
 *  - Proponer al cliente de 1 a 3 horas alternativas.
 *  - Bloquear horario (vacaciones, descansos o cierre del centro).
 * Fuera del horario de reservas el servidor responde 409 con code OUTSIDE_HOURS y se pregunta si guardar igualmente.
 * worker-calendar.js abre estos formularios y recarga la agenda cuando se guardan (AgendaForms.init({ reload })).
 */
(function () {
    'use strict';

    const esc = Cuquora.escapeHtml;
    const hours = window.CALENDAR_HOURS || {};
    const staff = window.CALENDAR_STAFF || {};
    const SLOT = Number.isInteger(hours.slotMinutes) ? hours.slotMinutes : 30;
    const OPEN = Number.isInteger(hours.openHour) ? hours.openHour : 9;
    const CLOSE = Number.isInteger(hours.closeHour) ? hours.closeHour : 20;
    const WORKING_DAYS = Array.isArray(hours.workingDays) ? hours.workingDays : [1, 2, 3, 4, 5];
    // Tramos de cada día (1 = lunes) en minutos: [[540, 840], [960, 1200]] es jornada partida
    const DAYS = hours.days || {};
    // El trabajador puede poner citas algo antes o después del horario de reservas (con confirmación)
    const FIRST_MINUTE = Math.min(OPEN, 7) * 60;
    const LAST_MINUTE = Math.max(CLOSE, 22) * 60;
    const workers = Array.isArray(staff.workers) ? staff.workers : [];
    const treatments = Array.isArray(staff.treatments) ? staff.treatments : [];
    const me = staff.currentWorkerId;
    const isOwner = staff.isOwner === true;

    const pad = n => String(n).padStart(2, '0');
    const clock = minutes => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
    const todayKey = () => {
        const d = new Date();
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    };
    const minutesOf = time => {
        const [h, m] = time.split(':').map(Number);
        return h * 60 + m;
    };

    let reload = async () => {};

    /* ---------- Utilidades comunes ---------- */

    function isOpenAt(dateKey, minute, duration) {
        const iso = ((new Date(dateKey + 'T00:00:00').getDay() + 6) % 7) + 1;
        const periods = DAYS[iso];
        if (!periods) return WORKING_DAYS.includes(iso) && minute >= OPEN * 60 && minute + duration <= CLOSE * 60;
        return periods.some(([from, to]) => minute >= from && minute + duration <= to);
    }

    /** Rellena un select de horas cada SLOT minutos; las de fuera del horario de reservas lo indican. */
    function fillTimes(select, dateKey, selectedMinute, duration) {
        const options = [];
        for (let minute = FIRST_MINUTE; minute < LAST_MINUTE; minute += SLOT) {
            const outside = dateKey && !isOpenAt(dateKey, minute, duration || SLOT);
            options.push(`<option value="${clock(minute)}"${minute === selectedMinute ? ' selected' : ''}>${clock(minute)}${outside ? ' · fuera de horario' : ''}</option>`);
        }
        select.innerHTML = options.join('');
        if (selectedMinute == null) select.value = clock(OPEN * 60);
    }

    function fillWorkers(select, selectedId) {
        select.innerHTML = workers.map(w =>
            `<option value="${esc(String(w.id))}"${w.id === selectedId ? ' selected' : ''}>${esc(w.name)}${w.id === me ? ' (tú)' : ''}</option>`).join('');
    }

    function showError(form, message) {
        const box = form.querySelector('.agenda-form-error');
        box.textContent = message || '';
        box.hidden = !message;
    }

    function setBusy(form, busy) {
        form.querySelectorAll('button[type="submit"]').forEach(b => { b.disabled = busy; });
    }

    /**
     * POST con parámetros de formulario. Si la hora queda fuera del horario de reservas, pregunta
     * y repite la petición con allowOutsideHours=true. Devuelve { ok, data, message }.
     */
    async function post(url, params, { askOutsideHours = false } = {}) {
        const body = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (Array.isArray(value)) value.forEach(v => body.append(key, v));
            else if (value !== undefined && value !== null && value !== '') body.append(key, value);
        });
        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (response.ok) return { ok: true, data: await response.json() };
            let payload = {};
            try { payload = await response.json(); } catch (e) { /* respuesta sin JSON */ }
            if (askOutsideHours && payload.code === 'OUTSIDE_HOURS') {
                const go = await Cuquora.confirm(`${payload.message} ¿Quieres guardarla igualmente?`, {
                    title: 'Fuera del horario de reservas',
                    confirmText: 'Guardar igualmente',
                    tone: 'default'
                });
                if (!go) return { ok: false, message: '' };
                return post(url, { ...params, allowOutsideHours: 'true' });
            }
            return { ok: false, message: payload.message || 'No se ha podido guardar. Revisa los datos e inténtalo de nuevo.' };
        } catch (err) {
            console.error(err);
            return { ok: false, message: 'Error de conexión. Inténtalo de nuevo.' };
        }
    }

    /* ---------- Nueva cita ---------- */

    const newForm = document.getElementById('newAppointmentForm');
    const clientSearch = document.getElementById('clientSearch');
    const clientResults = document.getElementById('clientResults');
    const clientSelected = document.getElementById('clientSelected');
    const clientId = document.getElementById('clientId');
    const newTreatment = document.getElementById('newTreatment');
    const newDate = document.getElementById('newDate');
    const newTime = document.getElementById('newTime');
    const newWorker = document.getElementById('newWorker');
    let searchTimer = null;

    const treatmentDuration = id => treatments.find(t => String(t.id) === String(id))?.duration || SLOT;

    function clientMode() {
        return newForm.querySelector('input[name="clientMode"]:checked').value;
    }

    function updateClientMode() {
        const mode = clientMode();
        newForm.querySelectorAll('[data-client-mode]').forEach(el => { el.hidden = el.dataset.clientMode !== mode; });
    }

    function selectClient(client) {
        clientId.value = client ? String(client.id) : '';
        clientSelected.textContent = client
            ? `Cliente: ${client.name}${client.phone ? ' · ' + client.phone : ''}`
            : 'Ningún cliente elegido';
        clientSelected.classList.toggle('is-selected', Boolean(client));
        clientResults.hidden = true;
    }

    async function searchClients() {
        const q = clientSearch.value.trim();
        if (q.length < 2) {
            clientResults.hidden = true;
            return;
        }
        try {
            const response = await fetch(`/worker/clients/search?q=${encodeURIComponent(q)}`);
            const clients = response.ok ? await response.json() : [];
            clientResults.innerHTML = clients.length
                ? clients.map((c, i) => `
                    <li role="option" tabindex="-1" id="clientOption${i}" data-index="${i}">
                        <i class="lucide-user" aria-hidden="true"></i>
                        <span><strong>${esc(c.name)}</strong>${c.phone ? ` · ${esc(c.phone)}` : ''}</span>
                    </li>`).join('')
                : '<li class="is-empty">Sin resultados. Puedes darlo de alta como «Cliente nuevo».</li>';
            clientResults.hidden = false;
            clientResults.querySelectorAll('[data-index]').forEach(item => {
                const client = clients[Number(item.dataset.index)];
                item.addEventListener('click', () => selectClient(client));
                item.addEventListener('keydown', e => {
                    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectClient(client); clientSearch.focus(); }
                    if (e.key === 'ArrowDown') { e.preventDefault(); item.nextElementSibling?.focus(); }
                    if (e.key === 'ArrowUp') { e.preventDefault(); (item.previousElementSibling || clientSearch).focus(); }
                });
            });
        } catch (err) {
            console.error(err);
        }
    }

    function openNew({ date, minute } = {}) {
        newForm.reset();
        showError(newForm, '');
        selectClient(null);
        updateClientMode();
        newTreatment.innerHTML = treatments.length
            ? treatments.map(t => `<option value="${esc(String(t.id))}">${esc(t.name)} · ${esc(String(t.duration))} min</option>`).join('')
            : '<option value="">No hay tratamientos: añade uno en «Tratamientos»</option>';
        newDate.value = date || todayKey();
        newDate.min = todayKey();
        fillTimes(newTime, newDate.value, minute ?? null, treatmentDuration(newTreatment.value));
        fillWorkers(newWorker, me);
        Cuquora.openModal('newAppointmentModal');
        setTimeout(() => clientSearch.focus(), 50);
    }

    newForm.querySelectorAll('input[name="clientMode"]').forEach(radio => radio.addEventListener('change', updateClientMode));
    clientSearch.addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(searchClients, 250);
    });
    clientSearch.addEventListener('keydown', e => {
        if (e.key === 'ArrowDown' && !clientResults.hidden) {
            e.preventDefault();
            clientResults.querySelector('[data-index]')?.focus();
        }
    });
    const refreshNewTimes = () => fillTimes(newTime, newDate.value, minutesOf(newTime.value || clock(OPEN * 60)), treatmentDuration(newTreatment.value));
    newDate.addEventListener('change', refreshNewTimes);
    newTreatment.addEventListener('change', refreshNewTimes);

    newForm.addEventListener('submit', async e => {
        e.preventDefault();
        showError(newForm, '');
        const params = {
            treatmentId: newTreatment.value,
            dateTime: `${newDate.value}T${newTime.value}`,
            workerId: newWorker.value,
            message: document.getElementById('newMessage').value.trim()
        };
        if (clientMode() === 'existing') {
            if (!clientId.value) return showError(newForm, 'Busca y elige un cliente de tu cartera, o da de alta uno nuevo.');
            params.clientId = clientId.value;
        } else {
            params.newClientName = document.getElementById('newClientName').value.trim();
            params.newClientPhone = document.getElementById('newClientPhone').value.trim();
            params.newClientEmail = document.getElementById('newClientEmail').value.trim();
            if (!params.newClientName || !params.newClientPhone) return showError(newForm, 'Escribe el nombre y el teléfono del cliente nuevo.');
        }
        if (!newDate.value || !newTime.value) return showError(newForm, 'Elige el día y la hora.');
        setBusy(newForm, true);
        const result = await post('/worker/appointments', params, { askOutsideHours: true });
        setBusy(newForm, false);
        if (!result.ok) return showError(newForm, result.message);
        Cuquora.closeModal('newAppointmentModal');
        await reload({ focusDate: newDate.value });
    });

    /* ---------- Cambiar hora ---------- */

    const rescheduleForm = document.getElementById('rescheduleForm');
    const rescheduleDate = document.getElementById('rescheduleDate');
    const rescheduleTime = document.getElementById('rescheduleTime');
    const rescheduleWorker = document.getElementById('rescheduleWorker');
    let rescheduleApp = null;

    function openReschedule(app) {
        rescheduleApp = app;
        showError(rescheduleForm, '');
        const [date, time] = app.dateTime.split('T');
        document.getElementById('rescheduleSummary').textContent =
            `${app.treatment?.name || 'Cita'} con ${app.client?.name || app.guestName || 'el cliente'}. La cita sigue confirmada y avisaremos al cliente del cambio.`;
        rescheduleDate.value = date;
        rescheduleDate.min = todayKey();
        fillTimes(rescheduleTime, date, minutesOf(time.substring(0, 5)), app.treatment?.duration);
        fillWorkers(rescheduleWorker, app.worker?.id ?? me);
        Cuquora.openModal('rescheduleModal');
    }

    rescheduleDate.addEventListener('change', () =>
        fillTimes(rescheduleTime, rescheduleDate.value, minutesOf(rescheduleTime.value), rescheduleApp?.treatment?.duration));

    /** Mover una cita (también desde el arrastre de la vista semanal). Devuelve true si se ha guardado. */
    async function reschedule(app, dateTime, workerId) {
        const result = await post(`/worker/appointments/${encodeURIComponent(app.id)}/reschedule`,
            { dateTime, workerId }, { askOutsideHours: true });
        if (result.ok) await reload({ focusDate: dateTime.substring(0, 10) });
        return result;
    }

    rescheduleForm.addEventListener('submit', async e => {
        e.preventDefault();
        showError(rescheduleForm, '');
        setBusy(rescheduleForm, true);
        const result = await reschedule(rescheduleApp, `${rescheduleDate.value}T${rescheduleTime.value}`, rescheduleWorker.value);
        setBusy(rescheduleForm, false);
        if (!result.ok) return showError(rescheduleForm, result.message);
        Cuquora.closeModal('rescheduleModal');
    });

    /* ---------- Proponer otra hora ---------- */

    const proposeForm = document.getElementById('proposeForm');
    const proposeList = document.getElementById('proposeList');
    const proposeAdd = document.getElementById('proposeAdd');
    let proposeApp = null;
    const MAX_PROPOSALS = 3;

    function proposeRow(date, minute) {
        const index = proposeList.children.length + 1;
        const row = document.createElement('li');
        row.className = 'propose-row';
        row.innerHTML = `
            <span class="propose-number" aria-hidden="true">${index}</span>
            <label class="visually-hidden" for="proposeDate${index}">Día de la opción ${index}</label>
            <input type="date" id="proposeDate${index}" class="form-control" required min="${todayKey()}" value="${esc(date)}">
            <label class="visually-hidden" for="proposeTime${index}">Hora de la opción ${index}</label>
            <select id="proposeTime${index}" class="form-control"></select>
            <button type="button" class="icon-btn" aria-label="Quitar la opción ${index}" title="Quitar"><i class="lucide-x" aria-hidden="true"></i></button>`;
        const dateInput = row.querySelector('input');
        const timeSelect = row.querySelector('select');
        const duration = proposeApp?.treatment?.duration;
        fillTimes(timeSelect, date, minute, duration);
        dateInput.addEventListener('change', () => fillTimes(timeSelect, dateInput.value, minutesOf(timeSelect.value), duration));
        row.querySelector('button').addEventListener('click', () => {
            row.remove();
            renumberProposals();
        });
        proposeList.appendChild(row);
        renumberProposals();
    }

    function renumberProposals() {
        [...proposeList.children].forEach((row, i) => {
            row.querySelector('.propose-number').textContent = String(i + 1);
            row.querySelector('button').hidden = proposeList.children.length === 1;
        });
        proposeAdd.hidden = proposeList.children.length >= MAX_PROPOSALS;
    }

    function openPropose(app) {
        proposeApp = app;
        showError(proposeForm, '');
        document.getElementById('proposeMessage').value = '';
        document.getElementById('proposeSummary').textContent =
            `${app.client?.name || 'El cliente'} pidió ${app.treatment?.name || 'una cita'} el ${new Date(app.dateTime).toLocaleString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })}. `
            + 'Propón hasta 3 horas: el cliente elegirá una desde «Mis Citas» y quedan reservadas para él mientras decide.';
        proposeList.innerHTML = '';
        const [date, time] = app.dateTime.split('T');
        proposeRow(date < todayKey() ? todayKey() : date, minutesOf(time.substring(0, 5)) + 60);
        Cuquora.openModal('proposeModal');
    }

    proposeAdd.addEventListener('click', () => {
        const last = proposeList.lastElementChild;
        proposeRow(last?.querySelector('input').value || todayKey(), last ? minutesOf(last.querySelector('select').value) + 60 : OPEN * 60);
        proposeList.lastElementChild.querySelector('input').focus();
    });

    proposeForm.addEventListener('submit', async e => {
        e.preventDefault();
        showError(proposeForm, '');
        const dateTimes = [...proposeList.children].map(row =>
            `${row.querySelector('input').value}T${row.querySelector('select').value}`);
        if (dateTimes.some(value => value.startsWith('T'))) return showError(proposeForm, 'Elige el día de cada opción.');
        setBusy(proposeForm, true);
        const result = await post(`/worker/appointments/${encodeURIComponent(proposeApp.id)}/propose`, {
            dateTime: dateTimes,
            message: document.getElementById('proposeMessage').value.trim()
        });
        setBusy(proposeForm, false);
        if (!result.ok) return showError(proposeForm, result.message);
        Cuquora.closeModal('proposeModal');
        await reload({});
    });

    /* ---------- Bloquear horario ---------- */

    const blockForm = document.getElementById('blockForm');
    const blockWho = document.getElementById('blockWho');
    const blockAllDay = document.getElementById('blockAllDay');

    function updateBlockTimes() {
        blockForm.querySelectorAll('[data-block-time]').forEach(el => { el.hidden = blockAllDay.checked; });
    }

    function openBlock({ date } = {}) {
        blockForm.reset();
        showError(blockForm, '');
        // Un trabajador estándar solo bloquea su agenda; cerrar el centro o bloquear a otros es cosa del dueño
        blockWho.innerHTML = workers.filter(w => isOwner || w.id === me).map(w =>
            `<option value="${esc(String(w.id))}"${w.id === me ? ' selected' : ''}>${esc(w.name)}${w.id === me ? ' (tú)' : ''}</option>`).join('')
            + (isOwner ? '<option value="center">Todo el centro (cerrado)</option>' : '');
        blockWho.disabled = !isOwner;
        const day = date && date >= todayKey() ? date : todayKey();
        document.getElementById('blockStartDate').value = day;
        document.getElementById('blockEndDate').value = day;
        document.getElementById('blockStartDate').min = todayKey();
        document.getElementById('blockEndDate').min = todayKey();
        updateBlockTimes();
        Cuquora.openModal('blockModal');
    }

    blockAllDay.addEventListener('change', updateBlockTimes);

    blockForm.addEventListener('submit', async e => {
        e.preventDefault();
        showError(blockForm, '');
        const startDate = document.getElementById('blockStartDate').value;
        const endDate = document.getElementById('blockEndDate').value;
        if (!startDate || !endDate) return showError(blockForm, 'Elige el día de inicio y el de fin.');
        let startsAt;
        let endsAt;
        if (blockAllDay.checked) {
            const next = new Date(endDate + 'T00:00:00');
            next.setDate(next.getDate() + 1);
            startsAt = `${startDate}T00:00`;
            endsAt = `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())}T00:00`;
        } else {
            startsAt = `${startDate}T${document.getElementById('blockStartTime').value}`;
            endsAt = `${endDate}T${document.getElementById('blockEndTime').value}`;
        }
        const wholeCenter = blockWho.value === 'center';
        setBusy(blockForm, true);
        const result = await post('/worker/agenda-blocks', {
            wholeCenter: String(wholeCenter),
            workerId: wholeCenter ? '' : blockWho.value,
            startsAt,
            endsAt,
            reason: document.getElementById('blockReason').value.trim()
        });
        setBusy(blockForm, false);
        if (!result.ok) return showError(blockForm, result.message);
        Cuquora.closeModal('blockModal');
        await reload({ focusDate: startDate });
    });

    async function removeBlock(block) {
        const who = block.workerName ? `de ${block.workerName}` : 'del centro';
        const ok = await Cuquora.confirm(`Se quitará el bloqueo ${who}${block.reason ? ` («${block.reason}»)` : ''} y se podrán volver a poner citas en ese horario.`, {
            title: '¿Quitar el bloqueo?',
            confirmText: 'Quitar bloqueo'
        });
        if (!ok) return;
        const result = await post(`/worker/agenda-blocks/${encodeURIComponent(block.id)}/delete`, {});
        if (!result.ok) {
            await Cuquora.alert(result.message, 'No se ha podido quitar el bloqueo');
            return;
        }
        await reload({});
    }

    window.AgendaForms = {
        init(options) {
            if (options && typeof options.reload === 'function') reload = options.reload;
        },
        post,
        openNew,
        openReschedule,
        openPropose,
        openBlock,
        reschedule,
        removeBlock,
        slotMinutes: SLOT
    };
})();
