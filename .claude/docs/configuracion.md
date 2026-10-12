# Configuración, secretos y despliegue · Cuquora

Se lee bajo demanda desde `CLAUDE.md` (variables de entorno, `.env`, Docker, Cloudflare).

## Secretos: fichero `.env`

- Todo lo sensible (contraseña de PostgreSQL, SMTP, `REMEMBER_ME_KEY`…) va en **`.env`** en la raíz, que está en `.gitignore` y `.dockerignore`. **Nunca escribas contraseñas en `docker-compose.yml`, `application*.properties` ni en el código.**
- `.env.example` es la plantilla sin secretos que sí se sube: si añades una variable, añádela a los dos.
- `docker compose` lee el `.env` y se niega a arrancar si falta una obligatoria (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REMEMBER_ME_KEY`).
- PostgreSQL no publica puerto: solo lo ve la app dentro de la red de Docker (hay unas líneas comentadas para abrirlo solo en `127.0.0.1`).
- Cambiar `POSTGRES_PASSWORD` no cambia la de un volumen ya creado: hay que hacer `ALTER USER` dentro de Postgres o borrar el volumen (`docker compose down -v`, se pierden los datos).

## Configuración (`application.properties`, todo por variables de entorno)

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_DRIVER_CLASS_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DIALECT` | Base de datos (por defecto H2 en memoria) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP (Brevo: `smtp-relay.brevo.com`, 587, login y clave SMTP). Si `MAIL_HOST` está vacío, los correos se escriben en consola (`LoggingEmailGateway`) |
| `CRM_ADMIN_EMAIL`, `CRM_FROM_EMAIL` | Destinatario de contacto/altas y remitente (por defecto `cuquora@hotmail.com`) |
| `REMEMBER_ME_KEY` | Firma de «Recordar mi sesión». Vacía = aleatoria en cada arranque (los dispositivos recordados dejan de valer al reiniciar) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | «Iniciar sesión con Google» (ID de cliente OAuth tipo «Aplicación web»). Vacías = sin botón de Google |
| `GOOGLE_TOKEN_KEY` | Cifra los tokens de Google Calendar guardados. Vacía (o sin las dos anteriores) = interruptor de Google Calendar apagado con «Próximamente» |
| `SEED_DEMO_DATA` | `true` = carga los datos demo (por defecto `false`; el perfil `dev` los activa) |
| `SITE_URL` | Dirección pública (p. ej. `https://cuquora.com`) para las etiquetas Open Graph y la vuelta de Google Calendar. Vacía = la de la petición |
| `LEGAL_OWNER_NAME`, `LEGAL_OWNER_NIF`, `LEGAL_OWNER_ADDRESS` | Titular de la web en Términos y Privacidad (LSSI). Vacías = solo «Cuquora» |
| `TZ` | Zona horaria del servidor (Docker: `Europe/Madrid` por defecto). Las citas se guardan en hora local |
| `H2_CONSOLE_ENABLED` | Consola H2 (la activa el perfil `dev`) |
| `crm.booking.*` | Horario de reservas: 9-20 h, huecos de 30 min, lunes a viernes; `proposal-hours=48` (plazo para responder a una propuesta) |

---


## Despliegue previsto

- **Cloudflare** delante de la app: proxy activado (nube naranja), SSL **Full (strict)**, HTTPS gratis. El mismo dominio servirá para el correo.
- Detrás del proxy hace falta: `server.forward-headers-strategy=native`, `server.servlet.session.cookie.secure=true`, `server.servlet.session.cookie.same-site=lax`, HSTS y `REMEMBER_ME_KEY` fija.
- Antes de producción hay que cerrar los puntos 🟠 de [`PENDIENTES.md`](PENDIENTES.md), en especial las cookies seguras y el límite de intentos de login.
