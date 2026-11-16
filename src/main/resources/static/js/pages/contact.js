// Formulario de contacto: validación y botón «Enviando...».
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
document.addEventListener('DOMContentLoaded', () => {
    const V = Cuquora.validators;
    const form = document.getElementById('contactForm');
    const submitBtn = document.getElementById('contactSubmitBtn');
    Cuquora.validateForm(form, {
        name: [V.required('Por favor, introduce tu nombre y apellidos.')],
        email: [V.required('Por favor, introduce tu correo electrónico.'), V.email()],
        subject: [V.required('Por favor, selecciona un motivo de consulta.')],
        message: [V.required('Por favor, escribe tu consulta o mensaje.')]
    }, {
        submitButton: submitBtn,
        onSubmit: () => {
            submitBtn.classList.add('is-loading');
            submitBtn.setAttribute('aria-busy', 'true');
            submitBtn.querySelector('span').textContent = 'Enviando...';
        }
    });
});
