// Inicio de sesión: validación y «Recordar mi sesión» también para Google.
// Va en un fichero aparte (no dentro de la plantilla) para que la CSP no tenga que permitir scripts en línea.
document.addEventListener('DOMContentLoaded', () => {
    const V = Cuquora.validators;
    Cuquora.validateForm(document.getElementById('loginForm'), {
        identifier: [
            V.required('Por favor, introduce tu correo electrónico o usuario.'),
            V.emailOrUsername(3, 'El nombre de usuario debe tener al menos 3 caracteres.')
        ],
        password: [V.required('Por favor, introduce tu contraseña.')]
    });

    // «Recordar mi sesión» también vale para entrar con Google: el botón lleva la casilla en la URL
    const googleBtn = document.getElementById('googleLoginBtn');
    const remember = document.getElementById('rememberMeInput');
    if (googleBtn && remember) {
        const baseUrl = googleBtn.getAttribute('href');
        const sync = () => googleBtn.setAttribute('href', remember.checked ? baseUrl + '?rememberMe=true' : baseUrl);
        remember.addEventListener('change', sync);
        sync();
    }
});
