/*
 * Modal «Resumen del tratamiento» (fragments/treatment-summary.html), compartido por la agenda y la ficha
 * del cliente. TreatmentSummary.open({ id, context, summary, alert, price, listPrice }) lo abre con los datos de la cita y,
 * al guardar, llama a onSaved con la cita devuelta por el servidor (mismo formato que la agenda).
 */
(function () {
    'use strict';

    const form = document.getElementById('treatmentSummaryForm');
    if (!form) return;
    const text = document.getElementById('treatmentSummaryText');
    const alertBox = document.getElementById('treatmentSummaryAlert');
    const price = document.getElementById('treatmentSummaryPrice');
    const priceHint = document.getElementById('treatmentSummaryPriceHint');
    const defaultPriceHint = priceHint.textContent;
    const euros = value => Number(value).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const error = form.querySelector('.agenda-form-error');
    const submit = form.querySelector('button[type="submit"]');
    let current = null;

    function showError(message) {
        error.textContent = message || '';
        error.hidden = !message;
    }

    function open({ id, context, summary, alert, price: charged, listPrice }, onSaved) {
        current = { id, onSaved };
        document.getElementById('treatmentSummaryContext').textContent = context || '';
        text.value = summary || '';
        alertBox.checked = Boolean(alert);
        price.value = charged != null && charged !== '' ? Number(charged).toFixed(2) : '';
        priceHint.textContent = listPrice != null && Number(listPrice) !== Number(charged)
            ? `${defaultPriceHint} Precio de carta: ${euros(listPrice)} €.`
            : defaultPriceHint;
        showError('');
        Cuquora.openModal('treatmentSummaryModal');
        text.focus();
    }

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const summary = text.value.trim();
        if (!summary && alertBox.checked) return showError('Escribe el resumen para poder marcarlo como aviso.');
        const amount = price.value.trim();
        if (amount === '' || !price.checkValidity()) {
            price.focus();
            return showError('Escribe el precio cobrado, entre 0 y 10.000 €.');
        }
        submit.disabled = true;
        try {
            const response = await fetch(`/worker/appointments/${encodeURIComponent(current.id)}/summary`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ summary, alert: String(alertBox.checked), price: amount })
            });
            if (!response.ok) {
                return showError(await Cuquora.errorMessage(response, 'No se ha podido guardar el resumen. Inténtalo de nuevo.'));
            }
            const saved = await response.json();
            Cuquora.closeModal('treatmentSummaryModal');
            current.onSaved?.(saved);
        } catch (err) {
            console.error(err);
            showError('Error de conexión. Inténtalo de nuevo.');
        } finally {
            submit.disabled = false;
        }
    });

    window.TreatmentSummary = { open };
})();
