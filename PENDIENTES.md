# Pendientes · Cuquora CRM

Trabajo pendiente y auditoría de seguridad. Se lee bajo demanda desde `CLAUDE.md`; cuando se cierre un punto, bórralo de aquí.

## Funcionalidades

- **Correo con Brevo.** Crear la cuenta, verificar el dominio en el DNS de Cloudflare (SPF, DKIM y DMARC) y poner los datos SMTP en `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` (plan gratis: 300 correos/día). Opcional: relay en el NAS de Alex que envíe a través de Brevo. No enviar directamente desde la IP de casa (puerto 25 bloqueado, listas negras, sin PTR).
- **Google Calendar por API.** Proyecto en Google Cloud, botón «Conectar con Google» por trabajador (OAuth) y sincronización inmediata de citas creadas, movidas o canceladas. (La alternativa sencilla, un enlace iCal por trabajador, se descartó de momento.)
- **Iniciar sesión con Google.** `oauth2Login` de Spring Security en el mismo proyecto de Google Cloud. Si el email ya existe, vincular a esa cuenta en vez de duplicarla.
- **Lector de código de barras — aparcado, no implementar sin que Alex lo pida.** Propuesta: migración con `products.barcode` opcional y único; campo «Escanear código» con foco en el modal «Añadir al Inventario» (los lectores USB/Bluetooth escriben el número y Enter); si existe, seleccionar el producto o sumar stock; si no, buscar en **Open Beauty Facts** (gratis, sin clave) para rellenar nombre, marca, descripción y foto, y que el trabajador ponga categoría y precio. Cámara del móvil en una segunda fase.

## Legal y SEO

- **Datos del titular en los términos.** La LSSI (art. 10) exige nombre o razón social, NIF y domicilio del titular; hoy `terms.html` solo dice «Cuquora» y el correo de contacto.
- **Política de privacidad propia.** Los términos resumen el RGPD (sección 7), pero conviene una página `/privacidad` con responsable, finalidades, base legal, plazos y encargados (hosting, Brevo cuando se active), y que los centros firmen el contrato de encargado del tratamiento por las fichas técnicas.
- **Sitemap.** Cuando haya dominio, añadir `sitemap.xml` con las páginas públicas y la línea `Sitemap: https://<dominio>/sitemap.xml` en `static/robots.txt`.
- **Logo.** `static/images/big_icon.png` parece decir «COQUORA» y la marca es «Cuquora»: revisar.

## Seguridad (informe del 9 oct 2026, sin corregir todavía)

Los 🟠 hay que resolverlos antes de producción. Resueltos el 10 oct 2026: #1 (seeder solo con `crm.seed-demo-data`, activado en `dev` y tests) y #2 (secretos en `.env`, Postgres sin puerto publicado, contraseña nueva). La antigua `crm_pass` sigue en el historial de git: no reutilizarla en ningún sitio.

| # | Gravedad | Hallazgo | Dónde | Arreglo propuesto |
|---|---|---|---|---|
| 3 | 🟠 | Fuga de datos entre centros: el alta manual de cliente reutiliza cualquier cuenta de la plataforma por teléfono o correo y redirige a su ficha | `services/ClientCardService.java` (`createClientManually` / `findExistingClient`) | Crear siempre un cliente local del centro, o vincular solo si ya tiene citas allí; mensaje genérico sin datos de la cuenta |
| 4 | 🟠 | Sin límite de intentos de login; contraseñas de 6 caracteres | `SecurityConfig`, `forms/PasswordPolicy.java` (`MIN_LENGTH = 6`) | Bloqueo temporal tras 5-10 fallos por cuenta e IP; mínimo 8-10 caracteres y rechazar contraseñas comunes |
| 5 | 🟠 | Cambio de contraseña sin pedir la actual | `services/UserService.java`, `ClientProfileController`, `WorkerCenterController` | Campo `currentPassword` en `ProfileForm` y `passwordEncoder.matches` |
| 6 | 🟠 | `/contact` y `/register-center` envían acuses a cualquier correo (spam/phishing) | `mail/InquiryMailService.java` | Límite por IP, honeypot o captcha, acuse con texto fijo |
| 7 | 🟠 | Sin HTTPS ni cookies `Secure` | `Dockerfile`, `docker-compose.yml`, `application.properties` | Cloudflare Full (strict) + propiedades de «Despliegue» en `CLAUDE.md` |
| 8 | 🟡 | Login por nombre no único (falla si hay dos iguales) | `security/AppUserDetailsService.java`, `UserRepository.findByNameIgnoreCase` | Login solo por correo o usuario único separado del nombre |
| 9 | 🟡 | Productos nuevos de un centro aparecen en el catálogo de todos | `services/InventoryService.java` | `products.center_id` nullable: globales sin centro, propios solo para su centro |
| 10 | 🟡 | Enumeración de cuentas en registro y alta manual | `UserService`, `ClientCardService` | Límite de intentos también en `/register`; mensaje neutro |
| 11 | 🟡 | Cuentas sin contraseña bloquean el registro del dueño real del correo | `ClientCardService`, `UserService.registerClient` | Permitir «reclamar» la cuenta (idealmente verificando el correo) |
| 12 | 🟡 | Leaflet desde unpkg e iconos de marcador desde `raw.githubusercontent.com` sin SRI ni CSP | `templates/centers.html` | Servirlos desde `/static` o añadir `integrity`; CSP más adelante |
| 13 | 🟡 | Solicitudes de cita ilimitadas | `services/BookingService.java` | Máximo de pendientes por cliente y centro (p. ej. 3) |
| 14 | ⚪ | `page * size` desborda en `CenterSearchService`; sesiones abiertas no se cierran al cambiar la contraseña; no se validan precios ni duraciones negativas | varios | Detalles menores |

Revisado y correcto: sin inyección SQL, sin `th:utext`, escapado en JS, CSRF, IDOR entre centros (salvo el #3), redirecciones, BCrypt y fijación de sesión. Dependencias sin comprobar contra CVE: añadir Dependabot o `mvn org.owasp:dependency-check-maven:check`.

## Técnicos

- Versionar en git `CLAUDE.md`, `PENDIENTES.md`, `mvnw`, `mvnw.cmd` y `.mvn/` (el wrapper solo existe en la carpeta de Windows; `.gitignore` ya prevé `.mvn/wrapper/maven-wrapper.jar`).
- Al añadir una migración o un endpoint de trabajador, añadir su test (aislamiento, seguridad, renderizado).
