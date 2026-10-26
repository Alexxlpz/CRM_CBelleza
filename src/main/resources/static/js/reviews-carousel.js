/*
 * Opiniones del centro: el carrusel de la ficha (flechas y puntos que siguen al desplazamiento)
 * y los filtros de «Todas las opiniones», que se aplican nada más cambiarlos.
 */
(function () {
    document.querySelectorAll('[data-review-carousel]').forEach(function (root) {
        const track = root.querySelector('.reviews-carousel');
        const cards = track ? Array.prototype.slice.call(track.children) : [];
        const dots = Array.prototype.slice.call(root.querySelectorAll('.reviews-carousel-dots > span'));
        const prev = root.querySelector('[data-carousel-prev]');
        const next = root.querySelector('[data-carousel-next]');
        if (!cards.length) {
            return;
        }

        function step() {
            const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
            return cards[0].getBoundingClientRect().width + gap;
        }

        function update() {
            let index = Math.round(track.scrollLeft / step());
            const atEnd = track.scrollLeft + track.clientWidth >= track.scrollWidth - 2;
            if (atEnd) {
                index = cards.length - 1;
            }
            dots.forEach(function (dot, i) {
                dot.classList.toggle('is-active', i === index);
            });
            if (prev) {
                prev.disabled = track.scrollLeft <= 2;
            }
            if (next) {
                next.disabled = atEnd;
            }
        }

        if (prev) {
            prev.addEventListener('click', function () {
                track.scrollBy({ left: -step(), behavior: 'smooth' });
            });
        }
        if (next) {
            next.addEventListener('click', function () {
                track.scrollBy({ left: step(), behavior: 'smooth' });
            });
        }
        track.addEventListener('scroll', function () {
            window.requestAnimationFrame(update);
        }, { passive: true });
        window.addEventListener('resize', update);
        update();
    });

    document.querySelectorAll('form[data-auto-submit]').forEach(function (form) {
        form.addEventListener('change', function () {
            // Fuera de la URL lo que está vacío, para que quede limpia («?estrellas=5»)
            form.querySelectorAll('select').forEach(function (select) {
                select.disabled = select.value === '' || (select.name === 'orden' && select.value === 'recientes');
            });
            form.submit();
        });
    });

    // Al volver atrás el navegador puede devolver la página tal cual: que los filtros vuelvan a funcionar
    window.addEventListener('pageshow', function () {
        document.querySelectorAll('form[data-auto-submit] select').forEach(function (select) {
            select.disabled = false;
        });
    });
})();
