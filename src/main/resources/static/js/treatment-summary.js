/*
 * Modal «Resumen del tratamiento» (fragments/treatment-summary.html), compartido por la agenda y la ficha
 * del cliente. TreatmentSummary.open({ id, context, summary, alert, price, listPrice, media }) lo abre con los datos de
 * la cita y, al guardar, llama a onSaved con la cita devuelta por el servidor (mismo formato que la agenda).
 * Las fotos y vídeos se guardan al elegirlos (sin esperar a «Guardar resumen») y también llaman a onSaved, con
 * { media: true } como segundo argumento (el modal sigue abierto).
 * TreatmentSummary.mediaHtml(media) pinta las miniaturas en la agenda y la ficha; al pulsarlas se ven en grande.
 */
(function () {
    'use strict';

    const MAX_MEDIA = 6;
    const MAX_PHOTO_MB = 8;
    const MAX_VIDEO_MB = 30;
    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    /** Miniaturas de solo lectura (agenda y ficha del cliente). */
    function mediaHtml(media) {
        if (!media || !media.length) return '';
        return `<div class="summary-media">${media.map(item => thumbHtml(item, false)).join('')}</div>`;
    }

    function thumbHtml(item, editable) {
        const url = escapeHtml(item.url);
        const preview = item.video
            ? `<video src="${url}#t=0.1" preload="metadata" muted playsinline></video><span class="summary-media-play"><i class="lucide-play" aria-hidden="true"></i></span>`
            : `<img src="${url}" alt="" loading="lazy">`;
        const remove = editable
            ? `<button type="button" class="summary-media-remove" tabindex="-1" data-media-remove="${escapeHtml(item.id)}" aria-label="Quitar"><i class="lucide-trash-2" aria-hidden="true"></i></button>`
            : '';
        return `<div class="summary-media-item">
                <button type="button" class="summary-media-view" tabindex="-1" data-media-id="${escapeHtml(item.id)}" data-media-view="${url}" data-media-video="${item.video}"
                        aria-label="${item.video ? 'Ver vídeo' : 'Ver foto'}">${preview}</button>${remove}</div>`;
    }

    /* ---------- Ver una foto o un vídeo en grande ---------- */
    let viewer = null;
    function openViewer(url, video) {
        if (!viewer) {
            viewer = document.createElement('div');
            viewer.className = 'media-viewer';
            viewer.setAttribute('role', 'dialog');
            viewer.setAttribute('aria-modal', 'true');
            viewer.setAttribute('aria-label', 'Foto o vídeo del tratamiento');
            viewer.innerHTML = `<button type="button" class="media-viewer-close" aria-label="Cerrar"><i class="lucide-x" aria-hidden="true"></i></button>
                <div class="media-viewer-body"></div>`;
            viewer.addEventListener('click', e => {
                if (e.target === viewer || e.target.closest('.media-viewer-close')) closeViewer();
            });
            document.body.appendChild(viewer);
        }
        const body = viewer.querySelector('.media-viewer-body');
        const safe = escapeHtml(url);
        body.innerHTML = video
            ? `<video src="${safe}" controls autoplay playsinline></video>`
            : `<img src="${safe}" alt="Foto del tratamiento">`;
        viewer.hidden = false;
        document.body.classList.add('media-viewer-open');
        viewer.querySelector('.media-viewer-close').focus();
    }

    function closeViewer() {
        if (!viewer || viewer.hidden) return;
        viewer.querySelector('.media-viewer-body').innerHTML = '';
        viewer.hidden = true;
        document.body.classList.remove('media-viewer-open');
    }

    document.addEventListener('click', e => {
        const view = e.target.closest('[data-media-view]');
        if (!view) return;
        e.preventDefault();
        openViewer(view.dataset.mediaView, view.dataset.mediaVideo === 'true');
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && viewer && !viewer.hidden) {
            e.stopPropagation();
            closeViewer();
        }
    }, true);

    window.TreatmentSummary = { mediaHtml, open: () => {} };

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
    const mediaList = document.getElementById('treatmentSummaryMedia');
    const mediaAdd = document.getElementById('treatmentSummaryMediaAdd');
    const mediaInput = document.getElementById('treatmentSummaryMediaInput');
    const mediaAddLabel = mediaAdd.querySelector('span');
    let current = null;

    function renderMedia(media) {
        current.media = media || [];
        mediaList.innerHTML = current.media.map(item => thumbHtml(item, true)).join('');
        mediaList.hidden = !current.media.length;
        mediaAdd.hidden = current.media.length >= MAX_MEDIA;
    }

    function setUploading(uploading) {
        mediaAdd.classList.toggle('is-busy', uploading);
        mediaInput.disabled = uploading;
        mediaAddLabel.textContent = uploading ? 'Subiendo…' : 'Añadir foto o vídeo';
    }

    async function sendMedia(url, body) {
        const response = await fetch(url, { method: 'POST', body });
        if (!response.ok) {
            showError(await Cuquora.errorMessage(response, 'No se ha podido guardar. Inténtalo de nuevo.'));
            return;
        }
        const saved = await response.json();
        renderMedia(saved.media);
        current.onSaved?.(saved, { media: true });
    }

    mediaInput.addEventListener('change', async () => {
        const file = mediaInput.files[0];
        mediaInput.value = '';
        if (!file || !current) return;
        const video = file.type.startsWith('video/');
        const maxMb = video ? MAX_VIDEO_MB : MAX_PHOTO_MB;
        if (file.size > maxMb * 1024 * 1024) {
            return showError(video
                ? `El vídeo pesa más de ${MAX_VIDEO_MB} MB. Grábalo más corto (unos 20 segundos bastan).`
                : `La foto pesa más de ${MAX_PHOTO_MB} MB. Elige una más ligera.`);
        }
        showError('');
        setUploading(true);
        const body = new FormData();
        body.append('file', file);
        try {
            await sendMedia(`/worker/appointments/${encodeURIComponent(current.id)}/media`, body);
        } catch (err) {
            console.error(err);
            showError('Error de conexión. Inténtalo de nuevo.');
        } finally {
            setUploading(false);
        }
    });

    mediaList.addEventListener('click', async e => {
        const remove = e.target.closest('[data-media-remove]');
        if (!remove || !current) return;
        const ok = await Cuquora.confirm('Se quitará de la cita para todo el equipo.', { title: '¿Quitar esta foto o vídeo?', confirmText: 'Quitar' });
        if (!ok) return;
        showError('');
        try {
            await sendMedia(`/worker/appointments/${encodeURIComponent(current.id)}/media/${encodeURIComponent(remove.dataset.mediaRemove)}/delete`);
        } catch (err) {
            console.error(err);
            showError('Error de conexión. Inténtalo de nuevo.');
        }
    });

    function showError(message) {
        error.textContent = message || '';
        error.hidden = !message;
    }

    function open({ id, context, summary, alert, price: charged, listPrice, media }, onSaved) {
        current = { id, onSaved };
        renderMedia(media);
        setUploading(false);
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

    /** Las fotos y vídeos ya pintados dentro de un elemento (ficha del cliente, pintada en el servidor). */
    function mediaIn(element) {
        return [...(element?.querySelectorAll('[data-media-id]') || [])].map(view => ({
            id: view.dataset.mediaId, url: view.dataset.mediaView, video: view.dataset.mediaVideo === 'true'
        }));
    }

    window.TreatmentSummary = { open, mediaHtml, mediaIn };
})();
