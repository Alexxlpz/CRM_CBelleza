/*
 * Modal «Quién lo hace» de la carta de tratamientos (worker/treatments.html).
 * Un solo modal para todos: marca a los profesionales del tratamiento pulsado (ninguno = todo el equipo).
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const form = document.getElementById('treatmentWorkersForm');
    if (!form) return;
    const context = document.getElementById('treatmentWorkersContext');
    const boxes = form.querySelectorAll('input[name="workerIds"]');

    document.querySelectorAll('[data-workers-edit]').forEach(button => button.addEventListener('click', () => {
        const data = button.dataset;
        const chosen = (data.workers || '').split(',').filter(Boolean);
        form.action = `/worker/treatments/${encodeURIComponent(data.id)}/workers`;
        context.textContent = data.name;
        boxes.forEach(box => { box.checked = chosen.includes(box.value); });
        Cuquora.openModal('treatmentWorkersModal');
        boxes[0]?.focus();
    }));
});
