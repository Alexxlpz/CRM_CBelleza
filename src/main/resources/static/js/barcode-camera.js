/*
 * Lector de códigos de barras con la cámara, común al alta de productos (product-scanner.js)
 * y a la venta (product-sale.js).
 *  - Usa el lector del navegador (BarcodeDetector, Chrome y Android) y, si no lo hay (iPhone, Firefox),
 *    ZXing, que solo se descarga la primera vez que se enciende la cámara.
 *  - CuquoraBarcode.start(video, onCode) enciende la cámara y devuelve una función para apagarla.
 *  - CuquoraBarcode.normalize(texto) deja el código como lo guarda el servidor (ProductLookupService.normalize).
 */
(() => {
    'use strict';

    const constraints = { video: { facingMode: { ideal: 'environment' } }, audio: false };
    const DIGITAL_LINK = /\/01\/(\d{14})(?:[/?#]|$)/;

    function supported() {
        return Boolean(navigator.mediaDevices?.getUserMedia) && window.isSecureContext;
    }

    function errorMessage(err, alternative) {
        const name = err && err.name;
        if (name === 'NotAllowedError' || name === 'SecurityError') {
            return `No tenemos permiso para usar la cámara. Actívalo en el navegador o ${alternative}.`;
        }
        if (name === 'NotFoundError' || name === 'OverconstrainedError') {
            return `No encontramos ninguna cámara. ${alternative.charAt(0).toUpperCase() + alternative.slice(1)}.`;
        }
        return `No se ha podido abrir la cámara. ${alternative.charAt(0).toUpperCase() + alternative.slice(1)}.`;
    }

    /** Mismo criterio que el servidor: GS1 Digital Link, sin espacios ni guiones, UPC-A con un 0 delante. */
    function normalize(raw) {
        let value = String(raw || '').trim();
        const link = value.match(DIGITAL_LINK);
        if (link) value = link[1];
        if (!/^[0-9 -]+$/.test(value)) return null;
        let digits = value.replace(/[ -]/g, '');
        if (digits.length === 14 && digits.startsWith('0')) digits = digits.substring(1);
        if (digits.length === 12) digits = '0' + digits;
        return [8, 13, 14].includes(digits.length) ? digits : null;
    }

    async function nativeDetector() {
        if (!('BarcodeDetector' in window)) return null;
        try {
            const formats = await window.BarcodeDetector.getSupportedFormats();
            const wanted = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'qr_code'].filter(f => formats.includes(f));
            return wanted.includes('ean_13') ? new window.BarcodeDetector({ formats: wanted }) : null;
        } catch (e) {
            return null;
        }
    }

    let zxingLoading = null;
    function loadZxing() {
        if (window.ZXing) return Promise.resolve(window.ZXing);
        zxingLoading ??= new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = '/vendor/zxing/zxing.min.js';
            script.onload = () => resolve(window.ZXing);
            script.onerror = () => { zxingLoading = null; reject(new Error('zxing')); };
            document.head.appendChild(script);
        });
        return zxingLoading;
    }

    async function startNative(detector, video, onCode) {
        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        video.srcObject = stream;
        await video.play();
        let timer = null;
        let stopped = false;
        const tick = async () => {
            if (stopped) return;
            try {
                const codes = video.readyState >= 2 ? await detector.detect(video) : [];
                if (codes.length && !stopped) {
                    onCode(codes[0].rawValue);
                    return;
                }
            } catch (e) { /* fotograma sin leer: se prueba con el siguiente */ }
            timer = setTimeout(tick, 200);
        };
        tick();
        return () => {
            stopped = true;
            clearTimeout(timer);
            stream.getTracks().forEach(track => track.stop());
            video.srcObject = null;
        };
    }

    async function startZxing(video, onCode) {
        const ZXing = await loadZxing();
        const hints = new Map();
        hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, [
            ZXing.BarcodeFormat.EAN_13, ZXing.BarcodeFormat.EAN_8, ZXing.BarcodeFormat.UPC_A,
            ZXing.BarcodeFormat.UPC_E, ZXing.BarcodeFormat.QR_CODE
        ]);
        const reader = new ZXing.BrowserMultiFormatReader(hints, 250);
        let done = false;
        await reader.decodeFromConstraints(constraints, video, result => {
            if (result && !done) {
                done = true;
                onCode(result.getText());
            }
        });
        return () => { done = true; reader.reset(); };
    }

    /** Enciende la cámara en el vídeo y llama a onCode con el primer código que lea. Devuelve cómo apagarla. */
    async function start(video, onCode) {
        const detector = await nativeDetector();
        return detector ? startNative(detector, video, onCode) : startZxing(video, onCode);
    }

    window.CuquoraBarcode = { supported, start, normalize, errorMessage };
})();
