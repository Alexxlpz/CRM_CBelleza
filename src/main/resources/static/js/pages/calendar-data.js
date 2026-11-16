// Datos de la agenda que pinta el servidor (bloque JSON #calendarData de worker/calendar.html), en las variables que
// usan worker-calendar.js y worker-agenda-forms.js. Va antes que ellos (los scripts diferidos se ejecutan en orden).
(function () {
    const data = JSON.parse(document.getElementById('calendarData').textContent);
    window.CALENDAR_APPOINTMENTS = data.appointments || [];
    window.CALENDAR_HOURS = data.hours || {};
    window.CALENDAR_BLOCKS = data.blocks || [];
    window.CALENDAR_STAFF = data.staff || {};
    window.CALENDAR_NEW_FOR = data.newFor ?? null;
})();
