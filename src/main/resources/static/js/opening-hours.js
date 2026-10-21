/*
 * Editor del horario de apertura (fragments/opening-hours.html), en el alta de centros y en «Mi Centro».
 * Abre y cierra días, añade o quita el tramo de tarde (jornada partida) y avisa si algo no cuadra.
 * Los campos de un día cerrado o de un tramo quitado se deshabilitan para que no se envíen.
 * El servidor vuelve a validarlo todo (OpeningHoursService).
 */
(function () {
    'use strict';

    const minutes = time => {
        const [h, m] = time.split(':').map(Number);
        return h * 60 + m;
    };

    function setEnabled(period, enabled) {
        period.hidden = !enabled;
        period.querySelectorAll('input, select').forEach(field => { field.disabled = !enabled; });
    }

    function dayState(row) {
        return {
            open: row.querySelector('[data-hours-open]').checked,
            split: !row.querySelector('[data-hours-period="2"]').hidden
        };
    }

    function render(row, open, split) {
        row.classList.toggle('is-closed', !open);
        setEnabled(row.querySelector('[data-hours-period="1"]'), open);
        setEnabled(row.querySelector('[data-hours-period="2"]'), open && split);
        row.querySelector('[data-hours-add]').hidden = !open || split;
        row.querySelector('.hours-closed').hidden = open;
    }

    /** Primer problema del horario, o '' si está bien. */
    function problem(editor) {
        const rows = [...editor.querySelectorAll('.hours-row')];
        if (!rows.some(row => dayState(row).open)) return 'Marca al menos un día en el que abra el centro.';
        for (const row of rows) {
            const { open, split } = dayState(row);
            if (!open) continue;
            const day = row.querySelector('.hours-day span').textContent.trim().toLowerCase();
            const ranges = [...row.querySelectorAll('.hours-period')]
                .filter(period => !period.hidden)
                .map(period => period.querySelectorAll('select'))
                .map(([from, to]) => [minutes(from.value), minutes(to.value)]);
            if (ranges.some(([from, to]) => to <= from)) return `El ${day} la hora de cierre tiene que ser posterior a la de apertura.`;
            if (split && ranges[1][0] < ranges[0][1]) return `Los tramos del ${day} se pisan: la tarde tiene que empezar cuando acaba la mañana o después.`;
        }
        return '';
    }

    function refresh(editor) {
        const message = problem(editor);
        const error = editor.querySelector('.hours-error');
        error.textContent = message;
        error.hidden = !message;
        const openRows = [...editor.querySelectorAll('.hours-row')].filter(row => dayState(row).open);
        editor.querySelector('[data-hours-copy]').hidden = openRows.length < 2;
        return message;
    }

    function copyFirst(editor) {
        const rows = [...editor.querySelectorAll('.hours-row')].filter(row => dayState(row).open);
        const [first, ...others] = rows;
        const { split } = dayState(first);
        const values = [...first.querySelectorAll('select')].map(select => select.value);
        others.forEach(row => {
            render(row, true, split);
            row.querySelectorAll('select').forEach((select, i) => { select.value = values[i]; });
        });
    }

    function init(editor) {
        const error = document.createElement('p');
        error.className = 'field-error hours-error';
        error.setAttribute('role', 'alert');
        error.hidden = true;
        editor.appendChild(error);

        editor.addEventListener('change', e => {
            const row = e.target.closest('.hours-row');
            if (row && e.target.matches('[data-hours-open]')) {
                render(row, e.target.checked, dayState(row).split);
            }
            refresh(editor);
        });
        editor.addEventListener('click', e => {
            const row = e.target.closest('.hours-row');
            if (e.target.closest('[data-hours-add]')) {
                render(row, true, true);
                row.querySelector('[data-hours-period="2"] select').focus();
            } else if (e.target.closest('[data-hours-remove]')) {
                render(row, true, false);
                row.querySelector('[data-hours-add]').focus();
            } else if (e.target.closest('[data-hours-copy]')) {
                copyFirst(editor);
            } else {
                return;
            }
            refresh(editor);
        });
        // Si el horario no cuadra, no se envía: el aviso explica qué cambiar
        editor.closest('form')?.addEventListener('submit', e => {
            if (refresh(editor)) {
                e.preventDefault();
                e.stopImmediatePropagation();
                error.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'center' });
            }
        });
        refresh(editor);
    }

    window.OpeningHoursEditor = {
        problem: editor => refresh(editor)
    };

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('[data-hours-editor]').forEach(init);
    });
})();
