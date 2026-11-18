/*
 * Modal «Editar tratamiento» de la carta (worker/treatments.html).
 * Un solo modal para todos: se rellena con los datos del botón pulsado. «Borrar» solo sale si el tratamiento
 * nunca ha tenido citas; si las tiene, se explica y se deja «Ocultar» (el servidor lo comprueba igual).
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const form = document.getElementById('treatmentEditForm');
    const hideForm = document.getElementById('treatmentHideForm');
    const deleteForm = document.getElementById('treatmentDeleteForm');
    const deleteNote = document.getElementById('treatmentDeleteNote');
    if (!form || !hideForm || !deleteForm) return;

    const field = id => document.getElementById(id);

    document.querySelectorAll('[data-treatment-edit]').forEach(button => button.addEventListener('click', () => {
        const data = button.dataset;
        const id = encodeURIComponent(data.id);
        form.action = `/worker/treatments/${id}/edit`;
        hideForm.action = `/worker/treatments/${id}/hide`;
        deleteForm.action = `/worker/treatments/${id}/delete`;
        field('editTreatmentName').value = data.name || '';
        field('editTreatmentType').value = data.type || 'OTRO';
        field('editTreatmentDesc').value = data.description || '';
        field('editTreatmentPrice').value = data.price ? Number(data.price).toFixed(2) : '';
        field('editTreatmentDuration').value = data.duration || '';

        const appointments = Number(data.appointments) || 0;
        deleteForm.hidden = appointments > 0;
        deleteNote.hidden = appointments === 0;
        deleteNote.textContent = appointments === 0 ? ''
            : `Tiene ${appointments} ${appointments === 1 ? 'cita' : 'citas'}: no se puede borrar sin quitarlas del historial, pero sí ocultar.`;
        Cuquora.openModal('treatmentEditModal');
        field('editTreatmentName').focus();
    }));
});
