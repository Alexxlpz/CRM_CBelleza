/*
 * Alta de productos del inventario (worker/fragments/inventory-modals.html):
 *  - Código de barras: con la cámara del móvil («Escanear»), con un lector USB (escribe el código y pulsa Intro)
 *    o a mano. Se busca en Cuquora y en la API de productos u Open Beauty Facts (/worker/inventory/barcode) y se
 *    rellena el formulario.
 *  - La cámara es la de barcode-camera.js (BarcodeDetector o ZXing), la misma que usa la venta.
 *  - «Poner foto» en «Ver Info»: el formulario se envía al elegir la foto.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';

    /* ---------- «Poner foto» / «Cambiar foto» en el detalle de un producto ---------- */
    document.querySelectorAll('[data-photo-autosubmit]').forEach(form => {
        const input = form.querySelector('input[type="file"]');
        input.addEventListener('change', () => {
            const file = input.files[0];
            if (!file) return;
            if (!['image/jpeg', 'image/png'].includes(file.type)) {
                Cuquora.alert('Elige una foto en JPG o PNG.', 'No se puede usar esa foto');
                input.value = '';
                return;
            }
            form.submit();
        });
    });

    const box = document.querySelector('[data-barcode-box]');
    if (!box) return;

    const form = box.closest('form');
    const input = box.querySelector('#barcode');
    const status = box.querySelector('[data-barcode-status]');
    const camera = box.querySelector('[data-barcode-camera]');
    const video = camera.querySelector('video');
    const scanButton = box.querySelector('[data-barcode-scan]');
    const sheet = box.closest('.sheet-overlay');
    const useFoundPhoto = form.querySelector('#useFoundPhoto');
    const photoInput = form.querySelector('#productPhoto');
    const photoImg = form.querySelector('.photo-picker--product [data-photo-img]');
    const photoIcon = form.querySelector('.photo-picker--product [data-photo-initials]');
    const field = id => form.querySelector('#' + id);
    const defaultStatus = status.textContent.trim();
    let stopCamera = null;
    let lastLookup = null;

    function say(message, tone) {
        status.textContent = message;
        status.classList.toggle('barcode-status--error', tone === 'error');
        status.classList.toggle('barcode-status--ok', tone === 'ok');
    }

    function selectTab(id) {
        const tab = document.getElementById(id);
        if (tab && tab.getAttribute('aria-selected') !== 'true') tab.click();
    }

    function showFoundPhoto(code) {
        photoInput.value = '';
        form.querySelector('.photo-picker--product [data-photo-name]').hidden = true;
        photoImg.src = '/worker/inventory/barcode/photo?code=' + encodeURIComponent(code);
        photoImg.hidden = false;
        photoIcon.hidden = true;
        useFoundPhoto.value = 'true';
        // Si al final no se puede traer (formato raro), vuelve el hueco vacío
        photoImg.onerror = () => {
            photoImg.removeAttribute('src');
            photoImg.hidden = true;
            photoIcon.hidden = false;
            useFoundPhoto.value = 'false';
        };
    }

    function clearFoundPhoto() {
        if (useFoundPhoto.value !== 'true') return;
        useFoundPhoto.value = 'false';
        if (!photoInput.files.length) {
            photoImg.removeAttribute('src');
            photoImg.hidden = true;
            photoIcon.hidden = false;
        }
    }

    photoInput.addEventListener('change', () => { useFoundPhoto.value = 'false'; });

    function fill(id, value) {
        const el = field(id);
        if (el && value) el.value = value;
    }

    async function lookup(raw) {
        const code = (raw || '').trim();
        if (!code) {
            say('Escribe o escanea un código de barras.', 'error');
            input.focus();
            return;
        }
        if (code === lastLookup) return;
        lastLookup = code;
        say('Buscando el producto…');
        clearFoundPhoto();
        try {
            const response = await fetch('/worker/inventory/barcode?code=' + encodeURIComponent(code),
                { headers: { Accept: 'application/json' } });
            if (!response.ok) {
                throw new Error(await Cuquora.errorMessage(response, 'No se ha podido buscar el código.'));
            }
            const result = await response.json();
            input.value = result.barcode;
            lastLookup = result.barcode;
            navigator.vibrate?.(60);
            switch (result.source) {
                case 'INVENTORY':
                    say(`«${result.name}» ya está en tu inventario: súmale stock con +1 en su tarjeta.`, 'ok');
                    break;
                case 'CATALOG': {
                    selectTab('tab-existing');
                    const select = field('productId');
                    select.value = String(result.productId);
                    say(`«${result.name}» ya está en Cuquora y lo hemos elegido del catálogo. Pon el stock y guarda.`, 'ok');
                    field('stock').focus();
                    break;
                }
                case 'FOUND':
                    selectTab('tab-new');
                    fill('name', result.name);
                    fill('description', result.description);
                    if (result.category) field('category').value = result.category;
                    if (result.photo) showFoundPhoto(result.barcode);
                    say(result.origin === 'CUQUORA'
                        ? 'Otro centro de Cuquora ya lo dio de alta. Revisa los datos y pon tu precio.'
                        : 'Encontrado en Open Beauty Facts. Revisa los datos y pon el precio.', 'ok');
                    field('price').focus();
                    break;
                default:
                    selectTab('tab-new');
                    say('No lo hemos encontrado. Rellena los datos a mano: el código se guarda con el producto.');
                    field('name').focus();
            }
        } catch (err) {
            lastLookup = null;
            say(err.message || 'No se ha podido buscar el código.', 'error');
        }
    }

    // Los lectores USB escriben el código y pulsan Intro: que busque en vez de enviar el formulario
    input.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            lookup(input.value);
        }
    });
    input.addEventListener('input', () => { lastLookup = null; });
    box.querySelector('[data-barcode-search]').addEventListener('click', () => lookup(input.value));

    /* ---------- Cámara (barcode-camera.js) ---------- */
    function found(code) {
        stop();
        input.value = code;
        lastLookup = null;
        lookup(code);
    }

    function stop() {
        if (stopCamera) stopCamera();
        stopCamera = null;
        camera.hidden = true;
        scanButton.disabled = false;
    }

    scanButton.addEventListener('click', async () => {
        if (!CuquoraBarcode.supported()) {
            say('Este navegador no deja usar la cámara aquí. Escribe el código o usa un lector de códigos.', 'error');
            return;
        }
        scanButton.disabled = true;
        camera.hidden = false;
        say('Apunta al código de barras de la etiqueta.');
        try {
            const stopper = await CuquoraBarcode.start(video, found);
            if (camera.hidden) {
                stopper(); // se cerró mientras arrancaba
            } else {
                stopCamera = stopper;
            }
        } catch (err) {
            stop();
            say(CuquoraBarcode.errorMessage(err, 'escribe el código o usa un lector de códigos'), 'error');
        }
    });
    box.querySelector('[data-barcode-stop]').addEventListener('click', () => {
        stop();
        say(defaultStatus);
    });

    // Al cerrar el panel se apaga la cámara
    if (sheet) {
        new MutationObserver(() => { if (sheet.hidden) stop(); })
            .observe(sheet, { attributes: true, attributeFilter: ['hidden'] });
    }
    window.addEventListener('pagehide', stop);
});
