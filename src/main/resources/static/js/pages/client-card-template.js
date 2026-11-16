// Plantilla de la ficha de clientes: añadir y quitar campos con aviso de cambios sin guardar.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('templateForm');
    const container = document.getElementById('fieldsContainer');

    const guard = Cuquora.guardUnsavedChanges({
        form,
        modalId: 'unsavedChangesModal',
        exitUrl: '/worker/clients',
        saveButton: document.getElementById('btnSaveTemplate'),
        successMessage: 'Plantilla de la ficha guardada.',
        // El estado incluye el orden y número de filas (añadir o quitar campos también cuenta como cambio).
        snapshot: () => JSON.stringify(Array.from(container.querySelectorAll('.template-field-row')).map(row => [
            row.querySelector('[name="fieldIds"]').value,
            row.querySelector('[name="fieldLabels"]').value.trim(),
            row.querySelector('[name="fieldTypes"]').value,
            row.querySelector('[name="fieldPlaceholders"]').value.trim()
        ]))
    });

    document.getElementById('btnAddField').addEventListener('click', () => {
        const row = document.getElementById('fieldRowTemplate').content.firstElementChild.cloneNode(true);
        container.appendChild(row);
        row.querySelector('input[name="fieldLabels"]').focus();
        guard.armGuard();
    });

    container.addEventListener('click', e => {
        const button = e.target.closest('[data-remove-field]');
        if (!button) return;
        if (container.querySelectorAll('.template-field-row').length <= 1) {
            Cuquora.alert('La plantilla debe contener al menos una variable.', 'No se puede eliminar');
            return;
        }
        button.closest('.template-field-row').remove();
        guard.armGuard();
    });
});
