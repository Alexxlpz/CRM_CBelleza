// Registro de clientes: validación del formulario.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
document.addEventListener('DOMContentLoaded', () => {
    const V = Cuquora.validators;
    Cuquora.validateForm(document.getElementById('registerForm'), {
        name: [V.required('Por favor, introduce tu nombre y apellidos.'), V.minLength(2, 'El nombre debe tener al menos 2 caracteres.')],
        email: [V.required('Por favor, introduce tu correo electrónico.'), V.email()],
        phone: [V.phoneNumber()],
        password: [V.required('Por favor, introduce una contraseña.'), V.minLength(8, 'La contraseña debe tener al menos 8 caracteres.')],
        confirmPassword: [V.required('Por favor, confirma tu contraseña.'), V.sameAs('password', 'Las contraseñas no coinciden. Por favor, revísalas.')]
    });
});
