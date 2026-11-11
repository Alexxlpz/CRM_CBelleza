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

### Añadido

- API de productos propia en su propio contenedor (`product-api/`, servicio `product-api` del `docker-compose.yml`): guarda los productos por código de barras en el esquema `productos` de la misma base de datos. Cuando un centro da de alta un producto con código, Cuquora le pasa el nombre, la categoría, la descripción y la foto (nunca el precio ni el stock), y el siguiente centro que lo escanee lo encuentra relleno con el aviso «Otro centro de Cuquora ya lo dio de alta». Si un código no está, la API lo busca en Open Beauty Facts y lo guarda. Lo que aporta un centro solo rellena huecos: no cambia el nombre ni una foto que ya hay. Nueva variable `PRODUCT_API_KEY` en el `.env`.
- Fotos y código de barras en los productos del inventario. Al crear un producto se le puede subir una foto, y desde «Ver Info» se pone, cambia o quita la de los productos propios del centro. En «Añadir Producto» se escanea el código de barras con la cámara del móvil (o con un lector USB, o escribiéndolo): si el producto ya está en el inventario o en el catálogo lo dice o lo elige, y si no, lo busca en Open Beauty Facts y rellena nombre, marca, formato, categoría y foto. Los QR de los envases solo sirven si llevan el código dentro (GS1 Digital Link). El código se guarda con el producto y no se puede repetir en el mismo centro. Migración V31.
- «Descargar mis datos» y «Borrar mi cuenta» en el perfil del cliente (RGPD). La descarga es un JSON con la cuenta, las citas, las fichas y resúmenes que tienen los centros, las reseñas, las compras y los puntos. Al borrar (con la contraseña, o escribiendo BORRAR si entra con Google) se anulan sus citas pendientes con aviso al centro, se borran su ficha, los resúmenes y fotos de sus tratamientos y sus puntos, y la cuenta se queda sin nombre, correo, teléfono ni acceso; las citas pasadas siguen contando en los números del centro y las reseñas se quedan como «Cliente eliminado». Las sesiones abiertas en otros dispositivos se cierran. Migración V30.
- Fotos y vídeos en el resumen del tratamiento: desde la agenda o la ficha del cliente se adjuntan hasta 6 por cita (fotos reducidas, vídeos MP4/MOV/WebM de hasta 30 MB) para enseñar al equipo algo que se entiende mejor viéndolo, como un enrojecimiento poco habitual. Solo los ve el equipo del centro y se ven en grande al pulsarlos.
- «¿Olvidaste tu contraseña?» en el inicio de sesión: llega un enlace al correo para poner una contraseña nueva. Vale una hora y una sola vez; al guardarla se entra directamente en la cuenta y se cierran las sesiones recordadas. Si la cuenta aún no tenía contraseña, llega el enlace para crearla.
- Botón «Rechazar registro» al revisar un centro nuevo: borra el centro sin publicar, las cuentas de su equipo y lo que hayan preparado.
- Botón «Cuquora en Docker» en IntelliJ (`.run/` y `docker-compose.local.yml`): levanta la app, la API de productos y PostgreSQL en http://localhost:8090 con el perfil dev (selector de perfiles y datos de ejemplo), sin chocar con los contenedores de producción.

### Cambiado

- «Mi Centro» pasa a llamarse «Ajustes», y la trabajadora que no es dueña ya no ve el aviso de dueño.
- Documentación: API de productos, código de barras y fotos de productos en el manual, la guía por roles, la evolución del proyecto y el README.
- Teléfono con el prefijo de país en su propia lista y el número agrupado al escribir, en el alta de centros, el registro de clientes y «Mi Centro»; se guarda como «+34 612 345 678». La migración V27 pone así los teléfonos de centros ya guardados.
- Alta de centros: se comprueba la letra o el dígito de control del CIF/NIF/NIE y las direcciones españolas tienen que llevar código postal. Una sola regla para los correos en el navegador y en el servidor.
- En los formularios, el tabulador pasa solo de un campo a otro y al botón de enviar; se salta el ojo de la contraseña, los enlaces entre campos, los «Cancelar», las ✕ de quitar filas y los botones del mapa, que siguen funcionando con ratón o dedo.
- Botón del ojo para ver la contraseña en todos los campos de contraseña (nueva contraseña, invitación, perfil del cliente y «Mi cuenta»).

- Si un cliente contesta a un correo de su cita, la respuesta le llega al dueño del centro (antes iba al buzón de Cuquora).
- Los correos llevan también una versión en texto plano, salen a nombre de «Cuquora» (antes «Cuquora CRM») y el aviso de centro nuevo al admin ya no lleva «[Cuquora Auditoría]» en el asunto, para que caigan menos en spam.
- Documentación: diagrama de los correos que envía la app (Cuquora, Brevo y Cloudflare, y un apartado por cada tipo de correo).
- Documentación al día con la 0.9.0-beta: producción en cuquora.com (NAS y túnel de Cloudflare), correo, formato de los datos del alta y «Rechazar registro»; textos más cortos y términos unificados.
- Documentación más visual para entenderla a la primera: 25 capturas de la app (escritorio y móvil) con marcas numeradas, dibujos de cómo se habla con el centro, la vida de una cita, el alta de un centro, el mapa de la web, la arquitectura, el modelo de datos, el despliegue y la línea de tiempo del proyecto; resumen «En 30 segundos» en cada apartado, preguntas frecuentes y tablas largas plegadas. En el móvil la barra superior sigue enseñando los cuatro documentos.

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
