// Inventario: filtro, alta (catálogo o nuevo) y ajuste rápido de stock.
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
            document.querySelectorAll('#inventoryGrid .inventory-card').forEach(card => {
                card.hidden = category !== 'ALL' && card.dataset.category !== category;
            });
        });
    });

    /* ---- Alta: catálogo existente o producto nuevo ---- */
    const modeInput = document.getElementById('input-mode');
    const productSelect = document.getElementById('productId');
    const newRequired = [document.getElementById('name'), document.getElementById('price')];
    function applyMode(mode) {
        modeInput.value = mode;
        productSelect.required = mode === 'existing';
        newRequired.forEach(input => { input.required = mode === 'new'; });
    }
    const tablist = document.getElementById('addModeTabs');
    tablist.addEventListener('tabchange', e => applyMode(e.detail.tab.dataset.mode));
    applyMode(tablist.querySelector('[aria-selected="true"]')?.dataset.mode || 'existing');

    /* ---- Ajuste rápido de stock ---- */
    function stockBadge(badge, stock, lowThreshold) {
        badge.classList.remove('badge-rejected', 'badge-pending', 'badge-confirmed');
        if (stock === 0) { badge.classList.add('badge-rejected'); badge.textContent = 'Agotado'; }
        else if (stock <= lowThreshold) { badge.classList.add('badge-pending'); badge.textContent = 'Bajo Stock'; }
        else { badge.classList.add('badge-confirmed'); badge.textContent = 'Normal'; }
    }

    /* Resumen superior: se recalcula con el stock visible en las tarjetas */
    function updateStats(lowThreshold) {
        const stocks = [...document.querySelectorAll('#inventoryGrid .stock-count-val')].map(el => Number(el.textContent));
        const set = (name, value) => {
            const el = document.querySelector(`[data-stat="${name}"]`);
            if (el) el.textContent = value;
        };
        set('units', stocks.reduce((total, stock) => total + stock, 0));
        set('low', stocks.filter(stock => stock > 0 && stock <= lowThreshold).length);
        set('out', stocks.filter(stock => stock === 0).length);
    }

    document.querySelectorAll('.btn-adjust').forEach(button => {
        button.addEventListener('click', async () => {
            const card = button.closest('.inventory-card');
            const stockValue = card.querySelector('.stock-count-val');
            const decrement = card.querySelector('.btn-decrement');
            card.querySelectorAll('.btn-adjust').forEach(b => { b.disabled = true; });
            try {
                const response = await fetch(`/worker/inventory/${encodeURIComponent(button.dataset.id)}/adjust-stock`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ change: button.dataset.change })
                });
                if (!response.ok) {
                    throw new Error(await Cuquora.errorMessage(response, 'No se ha podido actualizar el stock.'));
                }
                const { stock, lowStockThreshold, maxStock } = await response.json();
                stockValue.textContent = stock;
                stockValue.classList.remove('pulsing');
                void stockValue.offsetWidth;
                stockValue.classList.add('pulsing');
                stockBadge(card.querySelector('.stock-badge'), stock, lowStockThreshold);
                updateStats(lowStockThreshold);
                // En el tope (999) no se puede sumar más: el botón se apaga en lugar de dar error
                card.querySelector('.btn-increment').disabled = stock >= maxStock;
                decrement.disabled = stock === 0;
            } catch (err) {
                card.querySelectorAll('.btn-adjust').forEach(b => { b.disabled = false; });
                decrement.disabled = Number(stockValue.textContent) === 0;
                Cuquora.alert(err.message || 'Error de conexión al actualizar el stock.', 'No se ha podido actualizar el stock');
            }
        });
    });
});
