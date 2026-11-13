/*
 * Venta de productos desde la ficha del cliente o el inventario (fragments/product-sale.html).
 * Antes de confirmar enseña el stock actual y el que quedará, el total y los puntos,
 * y avisa si la cantidad no es válida o no hay stock suficiente. La venta la valida el servidor.
 * El producto se busca con la cámara («Escanear», barcode-camera.js), con un lector USB
 * (escribe el código y pulsa Intro) o escribiendo parte del nombre, que filtra la lista.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('productSaleForm');
    if (!form) return;

    const clientSelect = document.getElementById('saleClientId');
    const select = document.getElementById('saleInventoryId');
    const quantity = document.getElementById('saleQuantity');
    const preview = document.getElementById('salePreview');
    const warning = document.getElementById('saleStockWarning');
    const error = document.getElementById('saleError');
    const submit = document.getElementById('saleSubmit');
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

        const blocked = units < 1 || after < 0;
        warning.hidden = !message;
        warning.classList.toggle('is-error', blocked);
        warning.querySelector('span').textContent = message;
        submit.disabled = blocked;
        preview.hidden = false;
    }

    /* ---------- Buscar el producto: cámara, lector o nombre ---------- */
    const scan = form.querySelector('[data-sale-scan]');
    const search = document.getElementById('saleSearch');
    const status = scan.querySelector('[data-sale-scan-status]');
    const camera = scan.querySelector('[data-sale-camera]');
    const video = camera.querySelector('video');
    const scanButton = scan.querySelector('[data-sale-scan-start]');
    const overlay = form.closest('.ui-modal-overlay');
    const placeholder = select.options[0];
    const products = Array.from(select.options).slice(1);
    const defaultStatus = status.textContent.trim();
    const searchable = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    let stopCamera = null;

    function say(message, tone) {
        status.textContent = message;
        status.classList.toggle('barcode-status--error', tone === 'error');
        status.classList.toggle('barcode-status--ok', tone === 'ok');
    }

    function choose(option) {
        select.value = option.value;
        update();
        quantity.focus();
        quantity.select();
    }

    /** Deja en la lista solo los productos cuyo nombre o código contiene lo escrito (el elegido se queda). */
    function filter(text) {
        const wanted = searchable(text.trim());
        const current = select.value;
        const matching = o => searchable(o.dataset.name || o.text).includes(wanted) || (o.dataset.barcode || '').includes(wanted);
        const shown = wanted ? products.filter(o => o.value === current || matching(o)) : products;
        select.replaceChildren(placeholder, ...shown);
        select.value = current;
        return wanted ? shown.filter(o => !o.disabled && matching(o)) : [];
    }

    /** Elige el producto con ese código de barras. Devuelve false si el texto no es un código. */
    function byBarcode(raw) {
        const code = CuquoraBarcode.normalize(raw);
        if (!code) return false;
        const current = select.value;
        select.replaceChildren(placeholder, ...products);
        select.value = current;
        const option = products.find(o => o.dataset.barcode === code);
        if (!option) {
            say(`El código ${code} no está en el inventario del centro.`, 'error');
        } else if (option.disabled) {
            say(`«${option.dataset.name}» está agotado.`, 'error');
        } else {
            search.value = '';
            say(`«${option.dataset.name}» elegido. Pon la cantidad y confirma.`, 'ok');
            navigator.vibrate?.(60);
            choose(option);
        }
        return true;
    }

    search.addEventListener('input', () => {
        const matches = filter(search.value);
        if (!search.value.trim()) {
            say(defaultStatus);
        } else if (!matches.length) {
            say('Ningún producto del inventario coincide con lo que has escrito.', 'error');
        } else if (matches.length === 1) {
            say('Pulsa Intro para elegir «' + matches[0].dataset.name + '».');
        } else {
            say(`${matches.length} productos coinciden: elígelo en la lista.`);
        }
    });
    // Los lectores USB escriben el código y pulsan Intro: que lo busque en vez de enviar la venta
    search.addEventListener('keydown', e => {
        if (e.key !== 'Enter') return;
        e.preventDefault();
        if (byBarcode(search.value)) return;
        const matches = filter(search.value);
        if (matches.length === 1) {
            say(`«${matches[0].dataset.name}» elegido. Pon la cantidad y confirma.`, 'ok');
            choose(matches[0]);
        } else if (matches.length) {
            select.focus();
        }
    });

    function stop() {
        if (stopCamera) stopCamera();
        stopCamera = null;
        camera.hidden = true;
        scanButton.disabled = false;
    }

    async function startCamera() {
        if (!CuquoraBarcode.supported()) {
            say('Este navegador no deja usar la cámara aquí. Búscalo por su nombre o usa un lector de códigos.', 'error');
            return;
        }
        scanButton.disabled = true;
        camera.hidden = false;
        say('Apunta al código de barras de la etiqueta.');
        try {
            const stopper = await CuquoraBarcode.start(video, code => {
                stop();
                if (!byBarcode(code)) say('Eso no es un código de barras de producto. Escanea el de la etiqueta.', 'error');
            });
            if (camera.hidden) stopper(); // se cerró mientras arrancaba
            else stopCamera = stopper;
        } catch (err) {
            stop();
            say(CuquoraBarcode.errorMessage(err, 'búscalo por su nombre'), 'error');
        }
    }

    scanButton.addEventListener('click', startCamera);
    scan.querySelector('[data-sale-scan-stop]').addEventListener('click', () => {
        stop();
        say(defaultStatus);
        search.focus();
    });

    // Al cerrar la venta se apaga la cámara
    new MutationObserver(() => { if (overlay.hidden) stop(); })
        .observe(overlay, { attributes: true, attributeFilter: ['hidden'] });
    window.addEventListener('pagehide', stop);

    select.addEventListener('change', update);
    quantity.addEventListener('input', update);
    clientSelect?.addEventListener('change', () => { error.hidden = true; });
    form.addEventListener('submit', e => {
        if (clientSelect && !clientSelect.value) {
            e.preventDefault();
            error.textContent = 'Elige el cliente que se lleva el producto.';
            error.hidden = false;
            clientSelect.focus();
            return;
        }
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
