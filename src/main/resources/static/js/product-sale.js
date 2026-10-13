/*
 * Venta de productos desde la ficha del cliente (fragments/product-sale.html).
 * Antes de confirmar enseña el stock actual y el que quedará, el total y los puntos,
 * y avisa si el producto queda bajo mínimos o agotado. La venta la valida el servidor.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('productSaleForm');
    if (!form) return;

    const select = document.getElementById('saleInventoryId');
    const quantity = document.getElementById('saleQuantity');
    const preview = document.getElementById('salePreview');
    const warning = document.getElementById('saleStockWarning');
    const error = document.getElementById('saleError');
    const submit = document.getElementById('saleSubmit');
    const lowStock = Number(form.dataset.lowStock) || 0;
    const mode = form.dataset.loyaltyMode;
    const perUnit = Number(form.dataset.loyaltyPerUnit) || 0;
    const euros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });

    function points(total, units) {
        if (mode === 'PER_EURO') return Math.floor(total * perUnit + 1e-9);
        if (mode === 'PER_ITEM') return units * perUnit;
        return 0;
    }

    function update() {
        const option = select.selectedOptions[0];
        const units = Math.trunc(Number(quantity.value));
        error.hidden = true;
        if (!option || !option.value) {
            preview.hidden = true;
            submit.disabled = false;
            return;
        }
        const stock = Number(option.dataset.stock);
        const total = Number(option.dataset.price) * Math.max(units, 0);
        const after = stock - units;
        quantity.max = String(Math.min(stock, 99));

        document.getElementById('saleStockNow').textContent = stock;
        document.getElementById('saleStockAfter').textContent = Math.max(after, 0);
        document.getElementById('saleTotal').textContent = euros.format(total);
        const pointsEl = document.getElementById('salePoints');
        if (pointsEl) pointsEl.textContent = '+' + points(total, Math.max(units, 0));

        let message = '';
        if (units < 1) message = 'La cantidad tiene que ser al menos 1.';
        else if (after < 0) message = stock === 1 ? 'Solo queda 1 unidad.' : `Solo quedan ${stock} unidades.`;
        else if (after === 0) message = 'Con esta venta el producto se agota.';
        else if (after <= lowStock) message = after === 1 ? 'Quedará 1 unidad: stock bajo.' : `Quedarán ${after} unidades: stock bajo.`;

        const blocked = units < 1 || after < 0;
        warning.hidden = !message;
        warning.classList.toggle('is-error', blocked);
        warning.querySelector('span').textContent = message;
        submit.disabled = blocked;
        preview.hidden = false;
    }

    select.addEventListener('change', update);
    quantity.addEventListener('input', update);
    form.addEventListener('submit', e => {
        if (!select.value) {
            e.preventDefault();
            error.textContent = 'Elige el producto que se lleva el cliente.';
            error.hidden = false;
            select.focus();
            return;
        }
        submit.disabled = true;
    });
    update();
});
