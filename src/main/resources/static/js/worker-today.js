/**
 * «Hoy» del panel del trabajador (worker/dashboard.html): filtro «Las mías» / «Todo el centro».
 * Sin JavaScript se ve lo que eligió el servidor (la trabajadora, las suyas; el dueño, todo el centro).
 */
(function () {
    'use strict';

    const panel = document.getElementById('hoy');
    if (!panel) return;

    const me = panel.dataset.me;
    const buttons = panel.querySelectorAll('[data-today-filter]');
    const items = panel.querySelectorAll('.today-item');
    const empty = panel.querySelector('.today-empty');
    if (!buttons.length || !empty || !items.length) return;

    function show(filter) {
        let visible = 0;
        items.forEach(item => {
            const hide = filter === 'mine' && item.dataset.worker !== me;
            item.hidden = hide;
            if (!hide) visible++;
        });
        buttons.forEach(btn => btn.setAttribute('aria-pressed', String(btn.dataset.todayFilter === filter)));
        empty.hidden = visible > 0;
    }

    buttons.forEach(btn => btn.addEventListener('click', () => show(btn.dataset.todayFilter)));
})();
