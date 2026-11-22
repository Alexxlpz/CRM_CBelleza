/**
 * «Hoy» del panel del trabajador (worker/dashboard.html): filtro «Las mías» / «Todo el centro».
 * Sin JavaScript se ve lo que eligió el servidor (la trabajadora, las suyas; el dueño, todo el centro).
 */
(function () {
    'use strict';

    const panel = document.getElementById('hoy');
    if (!panel) return;

    const list = panel.querySelector('.today-list--scroll');
    // La fila empieza en la cita en curso o la siguiente, no en las de quien no vino esta mañana
    function scrollToCurrent() {
        const current = list?.querySelector('.today-item.is-now:not([hidden]), .today-item.is-next:not([hidden])');
        if (list) list.scrollLeft = current ? current.offsetLeft : 0;
    }
    scrollToCurrent();

    const me = panel.dataset.me;
    const buttons = panel.querySelectorAll('[data-today-filter]');
    const items = panel.querySelectorAll('.today-item');
    const empty = panel.querySelector('.today-empty');
    const emptyMine = panel.querySelector('.today-empty-mine');
    const emptyAll = panel.querySelector('.today-empty-all');
    // Sin citas hoy el filtro también se puede cambiar: solo cambia el mensaje vacío
    if (!buttons.length || !empty) return;

    function show(filter) {
        let visible = 0;
        items.forEach(item => {
            const hide = filter === 'mine' && item.dataset.worker !== me;
            item.hidden = hide;
            if (!hide) visible++;
        });
        buttons.forEach(btn => btn.setAttribute('aria-pressed', String(btn.dataset.todayFilter === filter)));
        empty.hidden = visible > 0;
        if (emptyMine) emptyMine.hidden = filter !== 'mine';
        if (emptyAll) emptyAll.hidden = filter === 'mine';
        scrollToCurrent();
    }

    buttons.forEach(btn => btn.addEventListener('click', () => show(btn.dataset.todayFilter)));
})();
