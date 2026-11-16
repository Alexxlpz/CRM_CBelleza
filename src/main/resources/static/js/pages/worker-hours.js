// Horario de un profesional: «Todo el horario del centro» esconde el editor.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
// «Todo el horario del centro» esconde el editor y no envía sus tramos
(function () {
    const editor = document.getElementById('workerHoursEditor');
    const sync = () => {
        const custom = document.querySelector('input[name="custom"]:checked')?.value === 'true';
        editor.hidden = !custom;
        editor.querySelectorAll('.hours-row').forEach(row => {
            const open = row.querySelector('[data-hours-open]').checked;
            row.querySelectorAll('[data-hours-period]').forEach(period => {
                period.querySelectorAll('input, select').forEach(field => {
                    field.disabled = !custom || !open || period.hidden;
                });
            });
        });
    };
    document.querySelectorAll('input[name="custom"]').forEach(radio => radio.addEventListener('change', sync));
    sync();
})();
