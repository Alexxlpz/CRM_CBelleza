// Mis citas (cliente): valorar una cita del historial.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
// Valorar una cita del historial: un solo modal que se rellena con la cita del botón pulsado
const reviewForm = document.getElementById('reviewForm');
const reviewStarsLabel = document.getElementById('reviewStarsLabel');
const starWords = ['', 'Mal', 'Regular', 'Bien', 'Muy bien', 'Excelente'];
document.querySelectorAll('[data-review-open]').forEach(button => button.addEventListener('click', () => {
    reviewForm.reset();
    reviewForm.action = `/client/appointments/${encodeURIComponent(button.dataset.id)}/review`;
    document.getElementById('reviewModalContext').textContent = button.dataset.context;
    reviewStarsLabel.textContent = 'Elige de 1 a 5 estrellas';
    document.getElementById('reviewStars').classList.remove('has-error');
    Cuquora.openModal('reviewModal');
    document.getElementById('reviewRating5').focus();
}));
// Desde «¿Qué tal tu cita?»: ?valorar=<cita>&estrellas=<n> abre el modal con esas estrellas ya marcadas
const toReview = new URLSearchParams(location.search);
const reviewButton = toReview.get('valorar')
    && document.querySelector(`[data-review-open][data-id="${CSS.escape(toReview.get('valorar'))}"]`);
if (toReview.has('valorar')) history.replaceState(null, '', location.pathname);
// Cuando app.js (diferido) ya ha definido Cuquora.openModal
if (reviewButton) document.addEventListener('DOMContentLoaded', () => {
    reviewButton.click();
    const preset = document.getElementById('reviewRating' + Number(toReview.get('estrellas')));
    if (preset) {
        preset.checked = true;
        const n = Number(preset.value);
        reviewStarsLabel.textContent = `${starWords[n]} · ${n === 1 ? '1 estrella' : n + ' estrellas'}`;
        document.getElementById('reviewComment').focus();
    }
});
reviewForm?.addEventListener('change', e => {
    if (e.target.name !== 'rating') return;
    const n = Number(e.target.value);
    reviewStarsLabel.textContent = `${starWords[n]} · ${n === 1 ? '1 estrella' : n + ' estrellas'}`;
    document.getElementById('reviewStars').classList.remove('has-error');
});
reviewForm?.addEventListener('submit', e => {
    if (reviewForm.querySelector('input[name="rating"]:checked')) return;
    e.preventDefault();
    const stars = document.getElementById('reviewStars');
    stars.classList.add('has-error');
    reviewStarsLabel.textContent = 'Elige cuántas estrellas le das antes de publicar';
    stars.classList.remove('shake-field');
    void stars.offsetWidth;
    stars.classList.add('shake-field');
});

// Carrusel de puntos (móvil y tablet): marca el punto de la tarjeta visible y lleva a la tarjeta al pulsarlo.
const loyaltyCarousel = document.getElementById('loyaltyCarousel');
const loyaltyDots = Array.from(document.querySelectorAll('.client-loyalty-dot'));
if (loyaltyCarousel && loyaltyDots.length) {
    const loyaltyCards = Array.from(loyaltyCarousel.children);
    const markDot = index => loyaltyDots.forEach((dot, i) => {
        dot.classList.toggle('is-active', i === index);
        if (i === index) dot.setAttribute('aria-current', 'true'); else dot.removeAttribute('aria-current');
    });
    loyaltyCarousel.addEventListener('scroll', () => {
        const start = loyaltyCarousel.getBoundingClientRect().left;
        const distances = loyaltyCards.map(card => Math.abs(card.getBoundingClientRect().left - start));
        // Al final del carrusel la última tarjeta no llega al borde izquierdo: se marca igualmente
        const atEnd = loyaltyCarousel.scrollLeft + loyaltyCarousel.clientWidth >= loyaltyCarousel.scrollWidth - 2;
        markDot(atEnd ? loyaltyCards.length - 1 : distances.indexOf(Math.min(...distances)));
    }, { passive: true });
    loyaltyDots.forEach((dot, i) => dot.addEventListener('click', () => {
        loyaltyCarousel.scrollTo({ left: loyaltyCarousel.scrollLeft + loyaltyCards[i].getBoundingClientRect().left - loyaltyCarousel.getBoundingClientRect().left, behavior: Cuquora.scrollBehavior() });
    }));
}
