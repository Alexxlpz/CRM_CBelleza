// Ficha del cliente (trabajador): campos que crecen, resumen de cada cita y avisos.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('fichaForm');

    // Los campos de texto crecen con su contenido para que se lea todo de un vistazo.
    const autoResize = textarea => {
        textarea.style.height = 'auto';
        textarea.style.height = Math.max(textarea.scrollHeight + 4, 46) + 'px';
    };
    const resizeAll = () => document.querySelectorAll('.field-textarea-auto').forEach(autoResize);
    document.querySelectorAll('.field-textarea-auto').forEach(t => t.addEventListener('input', () => autoResize(t)));
    window.addEventListener('resize', resizeAll);
    document.fonts?.ready.then(resizeAll);
    // Si el navegador restauró valores no guardados, se vuelve a los datos reales del servidor.
    form.reset();
    resizeAll();

    // Resumen del tratamiento de cada cita: al guardarlo se repinta la cita y la lista de avisos
    const esc = Cuquora.escapeHtml;
    const summaryHtml = app => app.treatmentSummary || app.media?.length ? `
        <div class="treatment-summary${app.treatmentAlert ? ' is-alert' : ''}">
            <p class="treatment-summary-head">
                <i class="${app.treatmentAlert ? 'lucide-alert-triangle' : 'lucide-clipboard-pen-line'}" aria-hidden="true"></i>
                <span>${app.treatmentAlert ? 'Resumen del tratamiento · aviso' : 'Resumen del tratamiento'}</span>
            </p>
            ${app.treatmentSummary ? `<p class="treatment-summary-text">${esc(app.treatmentSummary)}</p>` : ''}
            ${TreatmentSummary.mediaHtml(app.media)}
            <p class="treatment-summary-meta">Guardado ahora</p>
        </div>` : '';

    function renderAlerts() {
        const items = [...document.querySelectorAll('.history-item')]
            .filter(item => item.querySelector('.treatment-summary.is-alert'));
        document.getElementById('clientAlertsList').innerHTML = items.map(item => `
            <li><strong>${esc(item.dataset.date)} · ${esc(item.dataset.treatment)}:</strong>
                <span>${esc(item.querySelector('.treatment-summary-text').textContent)}</span></li>`).join('');
        document.getElementById('clientAlerts').hidden = items.length === 0;
    }

    document.querySelectorAll('[data-summary-id]').forEach(button => button.addEventListener('click', () => {
        TreatmentSummary.open({
            id: button.dataset.summaryId,
            context: button.dataset.summaryContext,
            summary: button.dataset.summaryText,
            alert: button.dataset.summaryAlert === 'true',
            price: button.dataset.summaryPrice,
            listPrice: button.dataset.summaryListPrice,
            media: TreatmentSummary.mediaIn(button.closest('.history-item').querySelector('.treatment-summary'))
        }, (saved, { media } = {}) => {
            button.closest('.history-item').querySelector('.treatment-summary')?.remove();
            button.insertAdjacentHTML('beforebegin', summaryHtml(saved));
            button.dataset.summaryText = saved.treatmentSummary || '';
            button.dataset.summaryAlert = String(saved.treatmentAlert);
            button.dataset.summaryPrice = String(saved.price);
            button.closest('.history-item').querySelector('.history-price').textContent =
                Number(saved.price).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';
            button.querySelector('span').textContent = saved.treatmentSummary || saved.media?.length ? 'Editar resumen y precio' : 'Añadir resumen y precio';
            renderAlerts();
            // Al subir o quitar una foto el modal sigue abierto: el foco se queda en él
            if (!media) button.focus();
        });
    }));

    // Historial plegado: solo las 6 primeras hasta pulsar «Ver las N citas» (o si se llega con #cita-<id> a una plegada).
    // Desplegado, el mismo botón pasa a «Ver menos» y vuelve a dejar las 6 primeras.
    const historyList = document.getElementById('historyList');
    const historyMore = document.getElementById('historyMore');
    let historyExpanded = false;
    const setSearching = searching => {
        if (!historyList || !historyMore) return;
        historyList.classList.toggle('is-collapsed', !historyExpanded && !searching);
        historyMore.hidden = searching;
        historyMore.setAttribute('aria-expanded', String(historyExpanded));
        historyMore.querySelector('i').className = historyExpanded ? 'lucide-chevrons-up' : 'lucide-chevrons-down';
        historyMore.querySelector('span').textContent = historyExpanded
            ? historyMore.dataset.lessLabel : historyMore.dataset.moreLabel;
    };
    if (historyList && historyMore) {
        const target = location.hash.startsWith('#cita-') ? document.getElementById(location.hash.slice(1)) : null;
        historyExpanded = !!target?.classList.contains('history-item--extra');
        setSearching(false);
        historyMore.addEventListener('click', () => {
            historyExpanded = !historyExpanded;
            setSearching(false);
            if (historyExpanded) {
                // El foco pasa a la primera cita que estaba plegada
                const first = historyList.querySelector('.history-item--extra:not([hidden])');
                if (first) {
                    first.tabIndex = -1;
                    first.focus({ preventScroll: true });
                }
            } else if (historyList.getBoundingClientRect().top < 0) {
                // Al plegar, la lista encoge: si su principio quedó fuera de la pantalla, se vuelve a él
                historyList.closest('section').scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    }

    // Filtro del historial: por tratamiento y por texto, sin distinguir mayúsculas ni tildes
    const historySearch = document.getElementById('historySearch');
    const historyTreatment = document.getElementById('historyTreatment');
    if (historySearch && historyTreatment) {
        const items = [...document.querySelectorAll('.history-item')];
        const count = document.getElementById('historyCount');
        const total = count.textContent.trim();
        const noResults = document.getElementById('historyNoResults');
        const normalize = text => text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

        [...new Set(items.map(item => item.dataset.treatment))]
            .sort((a, b) => a.localeCompare(b, 'es'))
            .forEach(name => historyTreatment.add(new Option(name, name)));

        // El botón de resumen no cuenta: su texto («Añadir resumen y precio») coincidiría en todas
        const searchableText = item => normalize([...item.children]
            .filter(child => child.tagName !== 'BUTTON')
            .map(child => child.textContent).join(' '));

        const applyFilter = () => {
            const words = normalize(historySearch.value).split(/\s+/).filter(Boolean);
            const treatment = historyTreatment.value;
            // Buscando o filtrando se mira en todas las citas, también en las plegadas
            setSearching(words.length > 0 || treatment !== '');
            let visible = 0;
            items.forEach(item => {
                const text = searchableText(item);
                const matches = (!treatment || item.dataset.treatment === treatment)
                    && words.every(word => text.includes(word));
                item.hidden = !matches;
                if (matches) visible++;
            });
            const filtered = words.length > 0 || treatment !== '';
            count.textContent = filtered ? `${visible} de ${items.length} citas` : total;
            noResults.hidden = visible > 0;
        };
        historySearch.addEventListener('input', applyFilter);
        historyTreatment.addEventListener('change', applyFilter);
    }

    Cuquora.guardUnsavedChanges({
        form,
        modalId: 'unsavedChangesModal',
        exitUrl: '/worker/clients',
        saveButton: document.getElementById('btnSaveFicha'),
        successMessage: 'Ficha técnica guardada.',
        onSaved: () => {
            const now = new Date();
            const pad = n => String(n).padStart(2, '0');
            const meta = document.querySelector('.card-meta-text');
            if (meta) {
                meta.innerHTML = `Última edición: <strong>${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}</strong> (guardado ahora)`;
            }
            const status = document.querySelector('.badge-card-status');
            if (status) {
                status.className = 'badge-card-status filled';
                status.innerHTML = '<i class="lucide-check-circle-2" aria-hidden="true"></i> Activa';
            }
        }
    });
});
