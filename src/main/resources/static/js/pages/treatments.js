// Tratamientos del centro: filtro por categoría.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
document.addEventListener('DOMContentLoaded', () => {
    /* ---- Filtro por categoría ---- */
    document.querySelectorAll('[data-category-filter]').forEach(button => {
        button.addEventListener('click', () => {
            const category = button.dataset.categoryFilter;
            document.querySelectorAll('[data-category-filter]').forEach(b => {
                b.classList.toggle('active', b === button);
                b.setAttribute('aria-pressed', String(b === button));
            });
            document.querySelectorAll('#treatmentsList .treatment-item-card').forEach(card => {
                const matches = category === 'OFFER' ? card.dataset.offer === 'true' : card.dataset.category === category;
                card.hidden = category !== 'ALL' && !matches;
            });
        });
    });
});
