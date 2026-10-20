/*
 * Modal «Poner en oferta» de la carta de tratamientos (worker/treatments.html).
 * Un solo modal para todos: se rellena con los datos del botón pulsado.
 */
document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const form = document.getElementById('treatmentOfferForm');
    const removeForm = document.getElementById('treatmentOfferRemoveForm');
    if (!form || !removeForm) return;

    const title = document.getElementById('treatmentOfferTitle');
    const context = document.getElementById('treatmentOfferContext');
    const priceInput = document.getElementById('offerPrice');
    const endsInput = document.getElementById('offerEndsOn');
    const hint = document.getElementById('offerPriceHint');
    const defaultHint = hint.textContent;
    const money = value => value.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';
    let listPrice = 0;

    function updateHint() {
        const offer = parseFloat(priceInput.value);
        if (!offer || offer <= 0) {
            hint.textContent = defaultHint;
            return;
        }
        hint.textContent = offer < listPrice
            ? `Descuento del ${Math.round((1 - offer / listPrice) * 100)} %: los clientes verán ${money(listPrice)} tachado.`
            : `Tiene que ser menor que el precio de carta (${money(listPrice)}).`;
    }

    document.querySelectorAll('[data-offer-edit]').forEach(button => button.addEventListener('click', () => {
        const data = button.dataset;
        const hasOffer = data.hasOffer === 'true';
        listPrice = parseFloat(data.price);
        form.action = `/worker/treatments/${encodeURIComponent(data.id)}/offer`;
        removeForm.action = `/worker/treatments/${encodeURIComponent(data.id)}/offer/delete`;
        removeForm.hidden = !hasOffer;
        title.lastChild.textContent = hasOffer ? ' Editar oferta' : ' Poner en oferta';
        context.textContent = `${data.name} · precio de carta ${money(listPrice)}`;
        priceInput.max = (listPrice - 0.01).toFixed(2);
        priceInput.value = data.offerPrice || '';
        // Una oferta vencida se renueva con una fecha nueva
        endsInput.value = data.endsOn && data.endsOn >= endsInput.min ? data.endsOn : '';
        updateHint();
        Cuquora.openModal('treatmentOfferModal');
        priceInput.focus();
    }));

    priceInput.addEventListener('input', updateHint);
});
