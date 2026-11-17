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

- «Por aprobar» en el panel del trabajador, encima de «Hoy»: las solicitudes de cita que esperan respuesta, con el cliente, el tratamiento, quién la haría (o «Sin asignar: al aceptarla, contigo»), su teléfono y los botones Aceptar y Rechazar, sin pasar por la agenda. Rechazar pide confirmación y avisa al cliente. Al responder, la fila se va y bajan los contadores de solicitudes; sin solicitudes, el bloque no sale.
- Tu próxima cita arriba de «Centros» para el cliente: día y hora, tratamiento, centro y profesional, con «Cómo llegar» y «Mis citas (N)». Sin próximas citas, o sin cuenta de cliente, no sale.
- Recién reservada: al pedir una cita se vuelve a «Mis reservas» con un aviso verde con título («Solicitud enviada» o «¡Cita confirmada!») y la cita nueva la primera de «Próximas citas», con borde verde y la etiqueta «Acabas de pedirla».
- Teléfonos que se pulsan para llamar: el del centro en «Mis reservas» y en la ficha del centro, y el del cliente en la agenda, en «Hoy», en «Por aprobar» y en su ficha.
- Alergias en el aviso amarillo de la cita, en la agenda y en «Hoy»: salen de la ficha técnica del cliente, de cualquier campo cuyo nombre hable de alergias (como «Alergias o Sensibilidades» de la plantilla).

### Cambiado

- Recordatorio de cita por correo, más claro y sin correos de más: el título dice «Mañana tienes cita» o «Hoy tienes cita», el asunto empieza por el día y la hora («Recordatorio: mañana a las 10:00, Manicura en Centro Histórico») y el botón pasa a «Ver o anular mi cita». Ya no llega justo después del correo de «Cita confirmada» o «Tu cita ha cambiado»: si a la clienta se le avisó de la cita hace menos de 12 horas, espera, y si faltan menos de 2 horas para la cita no se manda. Sin migración.
- Fechas amables en las tarjetas de cita del cliente y en «Por aprobar»: «Hoy · 11:00», «Mañana, lun 16 nov · 09:00» o «vie 20 nov · 10:30» (con el año si no es el de este). Al pasar el ratón se ve la fecha completa; el historial sigue con la fecha entera.
- Si el cliente elige «Cualquiera», la tarjeta dice «Profesional: te la asigna el centro» en vez de «Sin asignar».
- El historial de citas de la ficha del cliente enseña las 6 últimas y «Ver las N citas»; buscar o filtrar busca en todas.
- Al desplegar el historial de citas de la ficha, el mismo botón pasa a «Ver menos» y lo vuelve a plegar a las 6 últimas (si el principio de la lista quedó fuera de la pantalla, sube hasta él).
- «Las mías» / «Todo el centro» en «Hoy» también se puede cambiar cuando no hay citas: cada vista dice lo suyo («Hoy no tienes citas.» o «Hoy no hay citas en el centro.»).
- La trabajadora sin rol de dueño ya no ve el total de las compras del cliente en su ficha, solo cuántas son (igual que el gasto total).
- Fuera la flecha de volver que salía a la izquierda del logo en la cabecera del panel (Clientes, Agenda, Ajustes…): para moverse ya están los apartados de arriba, «Mi Panel» y la miga de pan de cada pantalla.

- «Hoy» en el panel del trabajador: debajo de las cifras, las citas del día por hora con el cliente, el tratamiento, quién la hace, si alguna vez no se presentó y un botón a su ficha. Marca la que está en curso y la siguiente, y aclara las que ya pasaron. «Las mías» / «Todo el centro» elige qué ver: la trabajadora empieza con las suyas y el dueño con todo el centro.
- La barra de arriba del panel lleva Agenda, Clientes, Tratamientos e Inventario (también en el menú del móvil) en vez de los enlaces de la web pública.
- Ficha del cliente: resumen arriba con visitas, gasto total (solo lo ve el dueño; el resto del equipo ve «*****,** €»), última visita y el tratamiento que más repite, y botón «Darle cita», que abre «Nueva cita» de la agenda con ese cliente ya elegido (solo clientes de la cartera del centro).

- «Cómo llegar» abre la ruta hasta el centro en el mapa: un botón al lado de «Compartir» en la ficha del centro (también la pública, sin cuenta), otro a la derecha de «Anular cita» en cada cita próxima de «Mis citas» y un enlace en los correos de cita confirmada, de cambio de hora y de recordatorio. Abre Google Maps; en iPhone, iPad y Mac sale antes una hoja para elegir Apple Maps o Google Maps. Usa las coordenadas del centro y, si no las tiene, la dirección; sin ninguna de las dos no sale. Es solo un enlace: la página no carga nada de Google. Sin migración.
- «Vender producto» (inventario y ficha del cliente) busca el producto por su código de barras: con «Escanear» se abre la cámara dentro del modal y, al leer el código, elige el producto y pasa a la cantidad. También vale un lector USB o escribir el código, y escribir parte del nombre reduce la lista (Intro elige si solo queda uno). Avisa si el código no está en el inventario o el producto está agotado. La cámara no se enciende sola. La cámara es la misma que en «Añadir Producto» (`barcode-camera.js`, común a los dos).
- La API de productos apunta qué centro aporta cada dato: al compartir un producto, Cuquora manda el número del centro (nada más de él) y la API guarda en un registro qué huecos rellenó y cuándo. Así se puede ver quién metió un dato malo y borrar de una vez lo que haya aportado un centro (SQL en `product-api/README.md`). Migración V3 de la API.
- La API de productos guarda las fotos reducidas a 800 px por el lado largo, en JPEG: las de Open Beauty Facts podían pesar varios megas y ahora rondan los 100-200 KB, así que la base de datos y la copia diaria crecen mucho menos. Las que ya son pequeñas se guardan tal cual.
- `docker/corregir-producto.sh` para arreglar a mano un producto de la API de productos sin escribir SQL: ver sus datos y quién lo aportó, cambiar nombre, marca, categoría, formato o descripción, quitar la foto o borrarlo. Cada corrección queda en el registro de aportaciones.
- Crédito a Open Beauty Facts: si la foto de un producto es la que se encontró al escanear y viene de allí, «Ver Info» lo dice debajo («Foto: Open Beauty Facts», licencia CC BY-SA, con enlace a su ficha), y deja de salir si el centro pone su propia foto. Esas fotos tampoco se comparten con la API de productos como si fueran del centro. Párrafo de créditos en los Términos (datos ODbL, fotos CC BY-SA). Migración V32.
- API de productos propia en su propio contenedor (`product-api/`, servicio `product-api` del `docker-compose.yml`): guarda los productos por código de barras en el esquema `productos` de la misma base de datos. Cuando un centro da de alta un producto con código, Cuquora le pasa el nombre, la categoría, la descripción y la foto (nunca el precio ni el stock), y el siguiente centro que lo escanee lo encuentra relleno con el aviso «Otro centro de Cuquora ya lo dio de alta». Si un código no está, la API lo busca en Open Beauty Facts y lo guarda. Lo que aporta un centro solo rellena huecos: no cambia el nombre ni una foto que ya hay. Nueva variable `PRODUCT_API_KEY` en el `.env`.
- Fotos y código de barras en los productos del inventario. Al crear un producto se le puede subir una foto, y desde «Ver Info» se pone, cambia o quita la de los productos propios del centro. En «Añadir Producto» se escanea el código de barras con la cámara del móvil (o con un lector USB, o escribiéndolo): si el producto ya está en el inventario o en el catálogo lo dice o lo elige, y si no, lo busca en Open Beauty Facts y rellena nombre, marca, formato, categoría y foto. Los QR de los envases solo sirven si llevan el código dentro (GS1 Digital Link). El código se guarda con el producto y no se puede repetir en el mismo centro. Migración V31.
- «Descargar mis datos» y «Borrar mi cuenta» en el perfil del cliente (RGPD). La descarga es un JSON con la cuenta, las citas, las fichas y resúmenes que tienen los centros, las reseñas, las compras y los puntos. Al borrar (con la contraseña, o escribiendo BORRAR si entra con Google) se anulan sus citas pendientes con aviso al centro, se borran su ficha, los resúmenes y fotos de sus tratamientos y sus puntos, y la cuenta se queda sin nombre, correo, teléfono ni acceso; las citas pasadas siguen contando en los números del centro y las reseñas se quedan como «Cliente eliminado». Las sesiones abiertas en otros dispositivos se cierran. Migración V30.
- Fotos y vídeos en el resumen del tratamiento: desde la agenda o la ficha del cliente se adjuntan hasta 6 por cita (fotos reducidas, vídeos MP4/MOV/WebM de hasta 30 MB) para enseñar al equipo algo que se entiende mejor viéndolo, como un enrojecimiento poco habitual. Solo los ve el equipo del centro y se ven en grande al pulsarlos.
- «¿Olvidaste tu contraseña?» en el inicio de sesión: llega un enlace al correo para poner una contraseña nueva. Vale una hora y una sola vez; al guardarla se entra directamente en la cuenta y se cierran las sesiones recordadas. Si la cuenta aún no tenía contraseña, llega el enlace para crearla.
- Botón «Rechazar registro» al revisar un centro nuevo: borra el centro sin publicar, las cuentas de su equipo y lo que hayan preparado.
- Botón «Cuquora en Docker» en IntelliJ (`.run/` y `docker-compose.local.yml`): levanta la app, la API de productos y PostgreSQL en http://localhost:8090 con el perfil dev (selector de perfiles y datos de ejemplo), sin chocar con los contenedores de producción.

### Seguridad

- Cambiar la contraseña (perfil del cliente o «Mi cuenta» del trabajador) o restablecerla con «¿Olvidaste tu contraseña?» cierra las demás sesiones abiertas de esa cuenta: quien la tuviera abierta en otro navegador sale en su siguiente pantalla con el aviso «Tu contraseña ha cambiado. Vuelve a iniciar sesión.». La sesión desde la que se cambia sigue abierta. Antes solo se olvidaban los dispositivos con «Recordar mi sesión».
- La app entra en PostgreSQL con su propio usuario (`cuquora_app`), dueño solo de las tablas de Cuquora: puede crear y cambiar sus tablas (las migraciones de Flyway), pero no crear usuarios ni bases de datos ni leer el esquema `productos`. Antes usaba `POSTGRES_USER`, que es superusuario y ahora solo usan `db-setup` y la copia de seguridad. Lo crea `db-setup`, que tras restaurar una copia vuelve a poner bien los dueños. Nueva variable `APP_DB_PASSWORD` en el `.env` (sin ella, `docker compose up` se para con un aviso).
- Sin scripts en línea: los 11 que había dentro de las plantillas (login, registro, contacto, buscador de centros, agenda, ficha del cliente, plantilla de la ficha, tratamientos, inventario, horario del profesional y «Mis citas») pasan a ficheros de `js/pages/`, y la política de seguridad del navegador (CSP) ya solo deja ejecutar los `.js` de la propia web (`script-src 'self'`, sin `'unsafe-inline'`). Un HTML que alguien consiguiera colar no podría ejecutar nada. Los datos de la agenda van en un bloque JSON que no se ejecuta. Un test nuevo falla si vuelve a aparecer un script en línea.
- S-12: nadie entra en un equipo sin aceptarlo. Si el dueño añade el correo de un trabajador que ya tiene cuenta y está sin centro, le llega «Centro te invita a su equipo» y solo entra al pulsar «Unirme a Centro» en el enlace (o al crear su contraseña, si aún no tenía). Antes entraba directamente. La invitación dura 7 días y se puede pedir otra en la misma página.
- El enlace «Publicar centro» del correo a administración caduca a los 30 días. Después, la página dice «Enlace caducado» y deja pedir otro, que llega siempre al correo de administración; el viejo deja de valer.
- Ajuste rápido de stock (+/−): cada ajuste suma o resta como mucho 10 unidades, ningún producto pasa de 999 (también al darlo de alta) y la fila se bloquea mientras tanto, así dos ajustes a la vez se aplican los dos. Antes un número enorme podía desbordar el contador.
- `cloudflared` con versión fija (`2026.10.7`) en vez de `latest`: una reconstrucción ya no trae una versión nueva sin probar.
- Fuera `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` del `docker-compose.yml`: solo hacía falta para bases de datos de antes de Flyway. Si un día faltase el historial de Flyway, la app no arranca en vez de dar por hechas migraciones que no se han aplicado.
- La API de productos entra en PostgreSQL con su propio usuario (`productos_api`), dueño solo del esquema `productos` y sin ningún permiso sobre las tablas de Cuquora. Antes usaba `POSTGRES_USER`, que es superusuario. Lo prepara el servicio nuevo `db-setup` del `docker-compose.yml` en cada arranque. Nueva variable `PRODUCT_API_DB_PASSWORD` en el `.env`.
- Librerías al día con sus parches de seguridad: Spring Boot pasa de 4.1.0 a 4.1.2 en la app y en la API de productos, lo que trae Tomcat 11.0.27 (corrige, entre otros, saltos de las restricciones de seguridad CVE-2026-65182 y CVE-2026-76183, respuestas cruzadas entre peticiones CVE-2026-86350 y CVE-2026-101356 y varios cuelgues por HTTP/2 y WebSocket), Spring Framework 7.0.10 (CVE-2026-59281, XSS en mensajes de error), Spring Security 7.1.2 y el driver de PostgreSQL 42.7.14 (CVE-2026-54291). La librería de códigos de barras zxing pasa de 3.5.3 a 3.5.4.

### Cambiado

- Tope de memoria de la app en Docker: 1 GB (`mem_limit` en `docker-compose.yml`). Java usa como mucho el 75 %, unos 768 MB; así no se come la RAM del NAS ni deja sin memoria a los demás programas.
- Fuera los avisos azules «esto lo hace el dueño del centro» que veía un trabajador que no es el dueño en Inventario, Tratamientos y Reseñas. Los botones que no puede usar siguen sin aparecer.
- El healthcheck de la API de productos (`/health`) comprueba la base de datos: si no responde en 2 segundos dice «DOWN» y Docker marca el contenedor como «unhealthy». Antes decía «UP» siempre.
- Reconstruir las imágenes de Docker es mucho más rápido: los dos `Dockerfile` bajan las dependencias de Maven en una capa aparte que se reutiliza mientras no cambie el `pom.xml` (en las pruebas, la app pasó de 81 a 19 segundos y la API de productos de 49 a 10).
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
### Corregido

- Una clienta ya no puede pedir dos citas que se pisan (pasaba al pulsar dos veces o al pedir la misma hora en otro centro): al reservar y al cambiar la hora se comprueba que no tenga otra cita pendiente, con propuesta o confirmada a esa hora, en ese centro o en otro, y si la tiene se le dice cuál. Se compara en hora real, porque cada centro guarda sus citas en su hora local.
- El logo de la cabecera decía «COQUORA»: ahora dice «CUQUORA» (`big_icon.png`, también en la documentación).
- Centros fuera de la hora de España (Canarias, Portugal, América): las citas se marcaban como completadas antes de tiempo (en Canarias una cita de 30 minutos, antes de empezar; en México, horas antes) y los recordatorios de 24 h se contaban con la hora de España. Ahora se usa la hora local de cada centro para completar citas, enviar recordatorios, decidir si una cita ya ha pasado, en «Mis reservas» y en los contadores de hoy y mañana del panel y de la campana.
- Copia de seguridad (`docker/backup-db.sh`): si `pg_dump` fallaba se guardaba un archivo vacío como si fuera una copia buena y se seguían borrando las de más de 30 días. Ahora comprueba que la copia está completa, sale con error si no lo está y no borra nada; siempre conserva las 7 más recientes y puede dejar una segunda copia en otra carpeta (`BACKUP_COPY_DIR`).
- Solicitudes que nadie contestó a tiempo: cuando pasaba su hora seguían contando como pendientes en el contador del panel, en la campana (que llevaba a un día ya pasado) y en la agenda, aunque «Por aprobar» no las enseñaba; y aún se podían aceptar o proponerles otra hora. Ahora no cuentan, no se pueden aceptar ni proponer, y en la agenda, en la ficha del cliente y en el historial de «Mis reservas» salen como «Sin respuesta».
- Al aceptar una de las horas que propone el centro se comprueba, como al reservar, que la clienta no tenga otra cita a esa hora (en ese centro o en otro).
- Inventario: el botón «+1» se apaga al llegar a 999 unidades en vez de dar error.
- Anular una venta devuelve las unidades bloqueando el producto, como vender y ajustar: un ajuste a la vez ya no se pierde.

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
