/*
 * Cartel con QR (worker/center_poster.html): el botón «Imprimir cartel» abre el diálogo de impresión.
 * El CSS de impresión (poster.css) deja solo el cartel a hoja completa.
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-print]').forEach(button => button.addEventListener('click', () => window.print()));
});
