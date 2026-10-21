/*
 * Calendario de la reserva (client/center_details.html + fragments/datetime-picker.html).
 * Pide la disponibilidad a /api/centers/{id}/availability y solo deja elegir
 * franjas libres en las que el tratamiento seleccionado cabe completo.
 * Al entrar no hay servicio elegido: las horas aparecen al elegir uno.
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const form = document.getElementById('bookingForm');
    const dateInput = document.getElementById('dateTime');
    const picker = document.getElementById('bookingPicker');
    if (!form || !picker || !dateInput) return;

    const centerId = form.elements.centerId.value;
    const calendarDays = document.getElementById('calendarDays');
    const calendarMonth = document.getElementById('calendarMonth');
    const previousMonth = document.getElementById('previousMonth');
    const nextMonth = document.getElementById('nextMonth');
    const timesTitle = document.getElementById('bookingTimesTitle');
    const timeOptions = document.getElementById('timeOptions');
    const message = document.getElementById('availabilityMessage');
    const summary = document.getElementById('bookingSummary');
    const treatmentInputs = form.querySelectorAll('input[name="treatmentId"]');

    const fallback = { slotMinutes: 30, workingDays: [1, 2, 3, 4, 5], openHour: 9, closeHour: 20, days: {}, closedDates: new Set() };
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const firstMonth = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastMonth = new Date(today.getFullYear(), today.getMonth() + 6, 1);
    // Las horas desde las 14:00 se agrupan como «Tarde»
    const afternoonFrom = 14 * 60;

    let month = new Date(firstMonth);
    let selectedDate = '';
    let selectedTime = '';
    let config = fallback;
    let occupied = new Map();
    let loaded = false;

    const pad = n => String(n).padStart(2, '0');
    const key = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    const dayOfWeek = date => (date.getDay() === 0 ? 7 : date.getDay());
    const timeLabel = minutes => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
    const checkedTreatment = () => form.querySelector('input[name="treatmentId"]:checked');
    const serviceDuration = () => Number(checkedTreatment()?.dataset.duration) || 30;
    const requiredSlots = () => Math.ceil(serviceDuration() / config.slotMinutes);
    const longDate = day => new Date(`${day}T00:00:00`).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' }).replace(',', '');

    function setMessage(text, isError) {
        message.textContent = text;
        message.classList.toggle('is-error', !!isError);
    }

    /** Tramos en los que abre el centro ese día (en minutos); dos tramos = jornada partida. */
    function periodsOf(day) {
        const periods = config.days[dayOfWeek(new Date(`${day}T00:00:00`))];
        return periods || [[config.openHour * 60, config.closeHour * 60]];
    }

    function slots(day) {
        const taken = occupied.get(day) || new Set();
        const result = [];
        periodsOf(day).forEach(([from, to]) => {
            for (let minutes = from; minutes < to; minutes += config.slotMinutes) {
                const value = timeLabel(minutes);
                result.push({ value, minutes, taken: taken.has(value), past: new Date(`${day}T${value}:00`) <= new Date() });
            }
        });
        return result;
    }

    /** ¿Cabe el tratamiento empezando en la franja `index`? (sin saltar el cierre de mediodía) */
    function canFit(index, daySlots) {
        const required = requiredSlots();
        if (index + required > daySlots.length) return false;
        return daySlots.slice(index, index + required)
            .every((slot, i) => !slot.taken && !slot.past && slot.minutes === daySlots[index].minutes + i * config.slotMinutes);
    }

    function freeSlots(day) {
        const daySlots = slots(day);
        return daySlots.filter((_, index) => canFit(index, daySlots));
    }

    function state(date) {
        if (date < today) return 'past';
        const day = key(date);
        if (!config.workingDays.includes(dayOfWeek(date)) || config.closedDates.has(day)) return 'closed';
        return freeSlots(day).length > 0 ? 'free' : 'full';
    }

    function updateSummary() {
        const treatment = checkedTreatment()?.closest('.treatment-select-card')?.querySelector('.treatment-card-title')?.textContent.trim();
        if (!treatment) {
            summary.textContent = 'Elige un servicio, un día y una hora para tu cita.';
            return;
        }
        if (!selectedDate || !selectedTime) {
            summary.textContent = selectedDate ? 'Elige una hora para tu cita.' : 'Elige un día y una hora para tu cita.';
            return;
        }
        summary.innerHTML = `<strong>${Cuquora.escapeHtml(treatment || 'Tu cita')}</strong> · ${Cuquora.escapeHtml(longDate(selectedDate))} a las ${selectedTime}`;
    }

    function hint(text) {
        timeOptions.innerHTML = '';
        const paragraph = document.createElement('p');
        paragraph.className = 'booking-times-hint';
        paragraph.textContent = text;
        timeOptions.appendChild(paragraph);
    }

    function timeGroup(label, icon, groupSlots, day) {
        const group = document.createElement('div');
        group.className = 'booking-times-group';
        const title = document.createElement('p');
        title.className = 'booking-times-group-label';
        title.innerHTML = `<i class="${icon}" aria-hidden="true"></i> ${label}`;
        const grid = document.createElement('div');
        grid.className = 'booking-times-grid';
        groupSlots.forEach(slot => {
            const option = document.createElement('button');
            option.type = 'button';
            option.className = 'time-option';
            option.textContent = slot.value;
            option.setAttribute('aria-pressed', String(slot.value === selectedTime));
            option.classList.toggle('is-selected', slot.value === selectedTime);
            option.addEventListener('click', () => {
                selectedTime = slot.value;
                dateInput.value = `${day}T${slot.value}`;
                timeOptions.querySelectorAll('.time-option').forEach(item => {
                    item.classList.toggle('is-selected', item === option);
                    item.setAttribute('aria-pressed', String(item === option));
                });
                setMessage('');
                updateSummary();
            });
            grid.appendChild(option);
        });
        group.append(title, grid);
        return group;
    }

    function renderTimes() {
        if (!checkedTreatment()) {
            timesTitle.textContent = 'Horas libres';
            hint('Elige un servicio para ver las horas libres.');
            return;
        }
        if (!selectedDate) {
            timesTitle.textContent = 'Horas libres';
            hint('Elige un día para ver las horas libres.');
            return;
        }
        timesTitle.textContent = `Horas libres el ${longDate(selectedDate)}`;
        const free = freeSlots(selectedDate);
        if (free.length === 0) {
            hint('Ese día ya no quedan horas libres. Prueba con otro.');
            return;
        }
        timeOptions.innerHTML = '';
        const morning = free.filter(slot => slot.minutes < afternoonFrom);
        const afternoon = free.filter(slot => slot.minutes >= afternoonFrom);
        if (morning.length) timeOptions.appendChild(timeGroup('Mañana', 'lucide-sun', morning, selectedDate));
        if (afternoon.length) timeOptions.appendChild(timeGroup('Tarde', 'lucide-sunset', afternoon, selectedDate));
    }

    function render() {
        calendarDays.innerHTML = '';
        const monthName = month.toLocaleDateString('es-ES', { month: 'long' });
        calendarMonth.textContent = `${monthName.charAt(0).toUpperCase()}${monthName.slice(1)} ${month.getFullYear()}`;
        const offset = (new Date(month.getFullYear(), month.getMonth(), 1).getDay() + 6) % 7;
        for (let i = 0; i < offset; i++) calendarDays.appendChild(document.createElement('span'));

        const total = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
        for (let number = 1; number <= total; number++) {
            const date = new Date(month.getFullYear(), month.getMonth(), number);
            const day = key(date);
            const dayState = state(date);
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = number;
            button.className = `booking-day is-${dayState}`;
            button.disabled = dayState !== 'free';
            button.setAttribute('aria-label', longDate(day)
                + (dayState === 'full' ? ', completo' : dayState === 'closed' ? ', cerrado' : ''));
            if (dayState === 'full') button.title = 'Completo: no quedan horas libres';
            if (dayState === 'closed') button.title = 'El centro está cerrado';
            if (day === key(today)) button.classList.add('is-today');
            if (day === selectedDate) {
                button.classList.add('is-selected');
                button.setAttribute('aria-pressed', 'true');
            }
            button.addEventListener('click', () => selectDate(day));
            calendarDays.appendChild(button);
        }
        previousMonth.disabled = month <= firstMonth;
        nextMonth.disabled = month >= lastMonth;
    }

    /** Primer día con horas libres para el tratamiento elegido, o '' si no hay ninguno en los próximos meses. */
    function firstFreeDay() {
        const end = new Date(lastMonth.getFullYear(), lastMonth.getMonth() + 1, 0);
        for (const date = new Date(today); date <= end; date.setDate(date.getDate() + 1)) {
            if (state(date) === 'free') return key(date);
        }
        return '';
    }

    /** Deja elegido el primer día libre y muestra su mes, sin mover la página. */
    function selectFirstFreeDay() {
        const day = firstFreeDay();
        selectedDate = day;
        selectedTime = '';
        dateInput.value = '';
        if (day) {
            const date = new Date(`${day}T00:00:00`);
            month = new Date(date.getFullYear(), date.getMonth(), 1);
        }
        render();
        renderTimes();
        updateSummary();
    }

    function selectDate(day) {
        if (state(new Date(`${day}T00:00:00`)) !== 'free') return;
        selectedDate = day;
        selectedTime = '';
        dateInput.value = '';
        render();
        renderTimes();
        updateSummary();
        // En pantallas estrechas las horas quedan debajo del mes: se acercan si no se ven
        const times = timesTitle.closest('.booking-times');
        if (times.getBoundingClientRect().top > window.innerHeight - 120) {
            times.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'start' });
        }
    }

    previousMonth.addEventListener('click', () => { month = new Date(month.getFullYear(), month.getMonth() - 1, 1); render(); });
    nextMonth.addEventListener('click', () => { month = new Date(month.getFullYear(), month.getMonth() + 1, 1); render(); });

    // Móvil: al elegir servicio la lista se pliega al elegido y se ve el día y la hora
    const services = form.querySelector('.booking-services');
    const changeService = document.getElementById('changeService');
    const narrowQuery = window.matchMedia('(max-width: 1099px)');

    function setServicesCollapsed(collapsed) {
        if (!services || !changeService) return;
        services.classList.toggle('is-collapsed', collapsed);
        changeService.hidden = !collapsed;
    }

    changeService?.addEventListener('click', () => {
        setServicesCollapsed(false);
        services.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'start' });
    });
    narrowQuery.addEventListener('change', () => { if (!narrowQuery.matches) setServicesCollapsed(false); });

    treatmentInputs.forEach(input => input.addEventListener('change', () => {
        if (!narrowQuery.matches) return;
        setServicesCollapsed(true);
        picker.closest('.booking-datetime-group').scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'start' });
    }));

    // «Todos los servicios / En oferta»: solo esconde tarjetas, el servicio elegido sigue elegido
    const filterButtons = form.querySelectorAll('[data-booking-filter]');
    filterButtons.forEach(button => button.addEventListener('click', () => {
        const onlyOffers = button.dataset.bookingFilter === 'offers';
        filterButtons.forEach(b => b.setAttribute('aria-pressed', String(b === button)));
        form.querySelectorAll('.treatment-select-card').forEach(card => {
            card.hidden = onlyOffers && card.dataset.offer !== 'true';
        });
    }));

    // Otro tratamiento puede durar más: se recalcula qué días y horas caben
    treatmentInputs.forEach(input => input.addEventListener('change', () => {
        if (message.classList.contains('is-error') && loaded) setMessage('');
        if (!selectedDate || state(new Date(`${selectedDate}T00:00:00`)) !== 'free') {
            selectFirstFreeDay();
            return;
        }
        selectedTime = '';
        dateInput.value = '';
        render();
        renderTimes();
        updateSummary();
    }));

    form.addEventListener('submit', event => {
        if (!checkedTreatment()) {
            event.preventDefault();
            setMessage('Elige un servicio para tu cita.', true);
            services.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'start' });
            return;
        }
        if (dateInput.value) return;
        event.preventDefault();
        setMessage(selectedDate ? 'Elige una hora para tu cita.' : 'Elige un día y una hora para tu cita.', true);
        picker.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'start' });
    });

    picker.classList.add('is-loading');
    setMessage('Consultando las horas libres…');
    fetch(`/api/centers/${encodeURIComponent(centerId)}/availability`, { headers: { Accept: 'application/json' } })
        .then(response => {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.json();
        })
        .then(data => {
            config = {
                slotMinutes: Number(data.slotMinutes) || fallback.slotMinutes,
                workingDays: data.workingDays || fallback.workingDays,
                openHour: Number(data.openHour),
                closeHour: Number(data.closeHour),
                days: data.days || {},
                closedDates: new Set(data.closedDates || [])
            };
            occupied = new Map();
            (data.occupied || []).forEach(value => {
                const day = value.slice(0, 10);
                if (!occupied.has(day)) occupied.set(day, new Set());
                occupied.get(day).add(value.slice(11, 16));
            });
            setMessage('');
            loaded = true;
        })
        .catch(error => {
            setMessage('No se pudieron cargar las horas libres. Recarga la página o inténtalo más tarde.', true);
            console.error(error);
        })
        .finally(() => {
            picker.classList.remove('is-loading');
            if (loaded && checkedTreatment()) {
                selectFirstFreeDay();
            } else {
                render();
                renderTimes();
            }
        });

    render();
});
