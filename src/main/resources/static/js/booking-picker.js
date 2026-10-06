/*
 * Selector de fecha y hora de la reserva (client/center_details.html).
 * Pide la disponibilidad a /api/centers/{id}/availability y solo deja elegir
 * franjas libres en las que el tratamiento seleccionado cabe completo.
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const form = document.getElementById('bookingForm');
    const dateInput = document.getElementById('dateTime');
    const trigger = document.getElementById('datepickerTrigger');
    if (!form || !trigger || !dateInput) return;

    const centerId = form.elements.centerId.value;
    const triggerValue = document.getElementById('datepickerValue');
    const popover = document.getElementById('calendarPopover');
    const calendarDays = document.getElementById('calendarDays');
    const calendarMonth = document.getElementById('calendarMonth');
    const message = document.getElementById('availabilityMessage');
    const previousMonth = document.getElementById('previousMonth');
    const nextMonth = document.getElementById('nextMonth');
    const timeTrigger = document.getElementById('timepickerTrigger');
    const timeValue = document.getElementById('timepickerValue');
    const timePopover = document.getElementById('timepickerPopover');
    const timeOptions = document.getElementById('timepickerOptions');
    const treatmentInputs = form.querySelectorAll('input[name="treatmentId"]');

    const fallback = { slotMinutes: 30, workingDays: [1, 2, 3, 4, 5], openHour: 9, closeHour: 20, closedDates: new Set() };
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const firstMonth = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastMonth = new Date(today.getFullYear(), today.getMonth() + 6, 1);

    let month = new Date(firstMonth);
    let selectedDate = '';
    let config = fallback;
    let occupied = new Map();
    let serviceDuration = Number(form.querySelector('input[name="treatmentId"]:checked')?.dataset.duration) || 30;

    const pad = n => String(n).padStart(2, '0');
    const key = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    const dayOfWeek = date => (date.getDay() === 0 ? 7 : date.getDay());
    const timeLabel = minutes => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
    const requiredSlots = () => Math.ceil(serviceDuration / config.slotMinutes);

    function setMessage(text, isError) {
        message.textContent = text;
        message.classList.toggle('is-error', !!isError);
    }

    function slots(day) {
        const taken = occupied.get(day) || new Set();
        const result = [];
        for (let minutes = config.openHour * 60; minutes < config.closeHour * 60; minutes += config.slotMinutes) {
            const value = timeLabel(minutes);
            result.push({ value, taken: taken.has(value), past: new Date(`${day}T${value}:00`) <= new Date() });
        }
        return result;
    }

    /** ¿Cabe el tratamiento empezando en la franja `index`? */
    function canFit(index, daySlots) {
        const required = requiredSlots();
        if (index + required > daySlots.length) return false;
        return daySlots.slice(index, index + required).every(slot => !slot.taken && !slot.past);
    }

    function state(date) {
        if (date < today) return 'past';
        const day = key(date);
        if (!config.workingDays.includes(dayOfWeek(date)) || config.closedDates.has(day)) return 'closed';
        const daySlots = slots(day);
        return daySlots.some((_, index) => canFit(index, daySlots)) ? 'free' : 'full';
    }

    function closeTimePopover() {
        timePopover.hidden = true;
        timeTrigger.setAttribute('aria-expanded', 'false');
    }

    function closeDatePopover() {
        popover.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
    }

    function renderTimes(day) {
        timeOptions.innerHTML = '';
        const daySlots = slots(day);
        const required = requiredSlots();
        const visibleSlots = daySlots.filter((slot, index) => index + required <= daySlots.length && !slot.past);

        if (visibleSlots.length === 0) {
            timeTrigger.disabled = true;
            timeValue.textContent = 'No quedan horas disponibles hoy';
            return;
        }

        let anyValid = false;
        visibleSlots.forEach(slot => {
            const fits = canFit(daySlots.indexOf(slot), daySlots);
            anyValid = anyValid || fits;
            const option = document.createElement('button');
            option.type = 'button';
            option.className = 'time-option';
            option.setAttribute('role', 'option');
            option.textContent = slot.value + (slot.taken ? ' (ocupada)' : !fits ? ' (sin tiempo suficiente)' : '');
            if (!fits) {
                option.disabled = true;
                option.classList.add(slot.taken ? 'is-occupied' : 'is-no-fit');
            } else {
                option.addEventListener('click', () => {
                    dateInput.value = `${day}T${slot.value}`;
                    timeValue.textContent = slot.value;
                    timeOptions.querySelectorAll('.time-option').forEach(item => {
                        item.classList.remove('is-selected');
                        item.setAttribute('aria-selected', 'false');
                    });
                    option.classList.add('is-selected');
                    option.setAttribute('aria-selected', 'true');
                    setMessage('');
                    closeTimePopover();
                    timeTrigger.focus();
                });
            }
            timeOptions.appendChild(option);
        });
        timeTrigger.disabled = !anyValid;
        timeValue.textContent = anyValid ? 'Selecciona una hora' : 'No hay horas disponibles';
    }

    function render() {
        calendarDays.innerHTML = '';
        calendarMonth.textContent = month.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
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
            button.dataset.date = day;
            button.className = 'is-' + dayState;
            button.disabled = dayState !== 'free';
            button.setAttribute('aria-label', date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })
                + (dayState === 'full' ? ', completo' : dayState === 'closed' ? ', cerrado' : ''));
            if (dayState === 'full') button.title = 'Día completo: no hay horas disponibles';
            if (day === key(today)) button.classList.add('is-today');
            if (day === selectedDate) {
                button.classList.add('selected');
                button.setAttribute('aria-pressed', 'true');
            }
            button.addEventListener('click', () => selectDate(day));
            calendarDays.appendChild(button);
        }
        previousMonth.disabled = month <= firstMonth;
        nextMonth.disabled = month >= lastMonth;
    }

    function selectDate(day) {
        const date = new Date(`${day}T00:00:00`);
        if (state(date) !== 'free') return;
        selectedDate = day;
        triggerValue.textContent = date.toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
        dateInput.value = '';
        renderTimes(day);
        render();
        closeDatePopover();
        if (!timeTrigger.disabled) timeTrigger.focus();
    }

    trigger.addEventListener('click', () => {
        popover.hidden = !popover.hidden;
        trigger.setAttribute('aria-expanded', String(!popover.hidden));
        if (!popover.hidden) render();
    });
    timeTrigger.addEventListener('click', () => {
        if (timeTrigger.disabled) return;
        timePopover.hidden = !timePopover.hidden;
        timeTrigger.setAttribute('aria-expanded', String(!timePopover.hidden));
    });
    previousMonth.addEventListener('click', () => { month = new Date(month.getFullYear(), month.getMonth() - 1, 1); render(); });
    nextMonth.addEventListener('click', () => { month = new Date(month.getFullYear(), month.getMonth() + 1, 1); render(); });

    // Cerrar los desplegables al hacer clic fuera o con Escape
    document.addEventListener('click', event => {
        if (!popover.hidden && !popover.contains(event.target) && !trigger.contains(event.target)) closeDatePopover();
        if (!timePopover.hidden && !timePopover.contains(event.target) && !timeTrigger.contains(event.target)) closeTimePopover();
    });
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        if (!popover.hidden) { closeDatePopover(); trigger.focus(); }
        if (!timePopover.hidden) { closeTimePopover(); timeTrigger.focus(); }
    });

    treatmentInputs.forEach(input => input.addEventListener('change', () => {
        serviceDuration = Number(input.dataset.duration) || 30;
        selectedDate = '';
        dateInput.value = '';
        triggerValue.textContent = 'Elige una fecha';
        timeOptions.innerHTML = '';
        timeValue.textContent = 'Selecciona una hora';
        timeTrigger.disabled = true;
        closeTimePopover();
        render();
        setMessage(`Disponibilidad actualizada para un servicio de ${serviceDuration} minutos.`);
    }));

    form.addEventListener('submit', event => {
        if (!dateInput.value) {
            event.preventDefault();
            setMessage('Selecciona una fecha y una hora disponibles.', true);
            popover.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            render();
            trigger.focus();
        }
    });

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
                closedDates: new Set(data.closedDates || [])
            };
            occupied = new Map();
            (data.occupied || []).forEach(value => {
                const day = value.slice(0, 10);
                if (!occupied.has(day)) occupied.set(day, new Set());
                occupied.get(day).add(value.slice(11, 16));
            });
            setMessage('Selecciona un día y una hora disponibles.');
            render();
        })
        .catch(error => {
            setMessage('No se pudo cargar la disponibilidad. Inténtalo de nuevo más tarde.', true);
            console.error(error);
            render();
        });

    render();
});
