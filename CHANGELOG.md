# Registro de cambios

Todos los cambios importantes de Cuquora se apuntan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y las versiones siguen [Versionado Semántico](https://semver.org/lang/es/).

## Cómo numeramos las versiones

Mientras Cuquora esté en beta, las versiones son `0.x.y-beta`:

- **`0.x.0`** (sube la x): funcionalidades nuevas o cambios que se notan en la web.
- **`0.x.y`** (sube la y): arreglos y retoques pequeños, sin funcionalidad nueva.
- **`1.0.0`**: la primera versión estable, cuando Cuquora se abra al público en cuquora.com.

Al sacar una versión:

1. Pasa lo de «Sin publicar» a una sección nueva con su número y la fecha.
2. Pon el mismo número en `<version>` de `pom.xml`.
3. Haz el commit y una etiqueta: `git tag -a v0.9.1-beta -m "Cuquora 0.9.1-beta"`.

## [Sin publicar]

### Cambiado

- Si un cliente contesta a un correo de su cita, la respuesta le llega al dueño del centro (antes iba al buzón de Cuquora).
- Documentación: diagrama de los correos que envía la app (Cuquora, Brevo y Cloudflare, y un apartado por cada tipo de correo).

## [0.9.0-beta] - 2026-11-03

Primera versión numerada. Recoge todo lo hecho desde el 17 de julio de 2026
(las «beta 1» y «beta 2» de julio eran demos sin número).

### Añadido

**Para los clientes**

- Buscador de centros con mapa, filtros («Abierto ahora», «Con ofertas», «4 o más estrellas»),
  primer hueco libre de los próximos 14 días y si el centro está abierto ahora.
- Ficha pública de cada centro en `/c/<nombre>`, sin iniciar sesión: carta con precios y ofertas,
  horario, opiniones y botón «Compartir». Reservar pide entrar y vuelve con el tratamiento elegido.
- Reserva de citas paso a paso con calendario propio, paso «¿Con quién?» (cualquiera o un profesional)
  y huecos según el horario del centro y de cada profesional.
- «Mis reservas»: próximas e historial, volver a reservar, cambiar la hora hasta el plazo que pone el centro
  y sincronización con Google Calendar.
- Reseñas verificadas (solo de citas completadas), aviso «¿Qué tal tu cita?» y opiniones con filtros.
- Tarjeta de puntos de cada centro en «Mis citas».
- Campana de avisos y correos de citas (Brevo); los avisos se pueden quitar uno a uno o todos.
- Registro, inicio de sesión con Google y sesión recordada.

**Para los centros**

- Alta de centros paso a paso con vista previa, foto, horario, pin en el mapa e invitaciones al equipo;
  administración revisa y publica.
- Agenda flexible (día, semana y pantalla completa): cabinas, citas creadas o movidas por el trabajador,
  propuestas de hora, cancelaciones, bloqueos y «no vino». Sincronización con Google Calendar.
- Aceptación automática de reservas (opcional).
- Clientes con fichas personalizables por centro, resumen de cada tratamiento con avisos
  (alergias, reacciones) y venta de productos con control de stock.
- Inventario y carta de tratamientos con vista de tarjetas o lista, filtros y ofertas con fecha de fin.
- «Mi Centro»: datos, horario, foto, dueño del centro, equipo (invitar, reenviar, quitar),
  horario de cada profesional, foto de perfil, tarjeta de puntos y enlace corto para reservar.
- Cartel A4 con código QR para el mostrador y descarga del QR en PNG y SVG.
- Análisis del centro: 8 cifras (ingresos, visitas, ocupación, clientes atendidos…), vista por meses
  y por año, rendimiento por profesional y exportación a CSV.
- Respuesta del dueño a las reseñas.

**Web y despliegue**

- Página de inicio con funcionalidades, contacto, aviso legal, privacidad, cookies y 404 propia.
- Modo oscuro completo y diseño adaptado a móvil, media pantalla y escritorio ancho.
- Google: ficha con datos estructurados (schema.org), `sitemap.xml` y `robots.txt`.
- Docker con PostgreSQL 16, migraciones Flyway (V1 a V26) y copia diaria de la base de datos.
- Perfil `prod` que no arranca si falta algo importante (SITE_URL https, claves, cookies seguras…).
- Túnel de Cloudflare (`cloudflared`) para publicar en cuquora.com sin abrir puertos.

### Cambiado

- Las direcciones del cliente usan el nombre del centro, nunca su id.
- Cabecera común en las pantallas del panel (icono, miga de pan y título).
- Iconos Lucide servidos desde la propia web.
- Al estirar la página en iPhone/iPad, arriba y abajo se ve el color de la cabecera y del pie
  en vez de blanco; el menú móvil también llega hasta el borde.
- Alta de centros en móvil: flechas de los pasos más altas, con sitio para su icono.

### Corregido

- Recordatorios de citas que fallaban con «Check constraint invalid».
- Test de análisis que fallaba antes de las 11:00.
- Muchos arreglos visuales en móvil y anchos intermedios.

### Seguridad

- Spring Security con roles de cliente y trabajador, CSRF y aislamiento entre centros
  (un centro nunca ve datos de otro).
- Acciones solo para el dueño del centro.
- Login bloqueado 15 minutos tras varios fallos, contraseñas de 8 caracteres o más y fuera las más usadas.
- Arreglos de la auditoría del 30 de octubre de 2026 (S-01 a S-16): límites por IP en formularios,
  tope de correos por centro, CSP y cabeceras de seguridad, cookies `HttpOnly`/`SameSite`/`Secure`.
- Secretos solo en `.env`, nunca en el código.

[Sin publicar]: https://github.com/Alexxlpz/CRM_CBelleza/compare/v0.9.0-beta...HEAD
[0.9.0-beta]: https://github.com/Alexxlpz/CRM_CBelleza/releases/tag/v0.9.0-beta
