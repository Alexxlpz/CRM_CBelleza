/*
 * Índice lateral con seguimiento de scroll (landing y funcionalidades).
 * Enlaces: <a class="rail-link" data-target="idDeSeccion">. Marca como activo (y aria-current)
 * el enlace de la sección visible y desplaza suavemente al pulsarlo.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const OFFSET = 90;
    const links = Array.from(document.querySelectorAll('.rail-link'));
    const sections = links
        .map(link => ({ link, el: document.getElementById(link.dataset.target) }))
        .filter(item => item.el);
    if (!sections.length) return;

    function activate(link) {
        links.forEach(l => {
            const active = l === link;
            l.classList.toggle('active', active);
            if (active) l.setAttribute('aria-current', 'true'); else l.removeAttribute('aria-current');
        });
    }

    sections.forEach(({ link, el }) => link.addEventListener('click', e => {
        e.preventDefault();
        window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - OFFSET, behavior: 'smooth' });
        activate(link);
    }));

    function update() {
        const atBottom = window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 70;
        if (atBottom) return activate(sections[sections.length - 1].link);
        const position = window.scrollY + 180;
        const current = [...sections].reverse().find(item => position >= item.el.offsetTop) || sections[0];
        activate(current.link);
    }

    window.addEventListener('scroll', update, { passive: true });
    update();
});
