# Pantallas y componentes · Cuquora

Se lee bajo demanda desde `CLAUDE.md` antes de tocar la pantalla correspondiente. Reglas visuales generales: skill `interfaz-cuquora`.

## Pie, cookies y páginas legales
- Pie común `fragments/footer :: footer('texto')` en todas las páginas: enlaces a Términos, Privacidad y Cookies y el **aviso de cookies** (`#cookieBanner`, lógica en `app.js`): barra fija a todo el ancho abajo, con botones grandes (`btn-lg`), «Más información» y «Entendido» a la derecha desde 1100 px, en su propia fila por debajo y apilados en móvil; mientras se ve, `body` reserva espacio abajo para no tapar el pie. Hoy solo hay cookies técnicas, así que el aviso es informativo («Entendido»). Si se añade analítica, hay que pedir consentimiento antes (Aceptar/Rechazar con el mismo peso), subir `COOKIE_NOTICE_VERSION` y actualizar `templates/cookies.html`.
- Páginas legales (`terms.html`, `cookies.html`, `privacy.html`, CSS `legal.css`; titular desde `LegalOwner`): mismo índice lateral que Inicio (`scroll-index.js`) y, por debajo de 1200 px, índice dentro de la página.

## Layout de escritorio
- `.main-container` llega a 1100 px. **`.main-container--wide`** crece hasta 1440 px desde 1100 px de ancho; lo usan tratamientos, inventario y clientes; Centros no tiene tope desde 1280 px. La agenda usa su propio contenedor ancho (hasta 1680 px).
- **Vista Tarjetas / Lista** (solo desde 1100 px): `[data-view-toggle="idDelListado"]` con botones `[data-view="grid|list"]`; `app.js` añade `.is-list-view` al listado y recuerda la elección en el navegador. Por defecto Lista en inventario y clientes, Tarjetas en tratamientos. Las filas reutilizan el marcado de las tarjetas (no dupliques HTML).
- **`.list-toolbar`**: barra entre el resumen y el listado, filtros de categoría a la izquierda y selector de vista a la derecha. Misma posición en las tres pantallas.
- **`--list-row-height: 108px`** (en `common.css`): todas las filas en modo lista miden lo mismo en tratamientos, inventario y clientes (mismo relleno, radio y 12 px de separación). Si un texto largo lo necesita, la fila crece en vez de cortar.
- Franja de resumen arriba (tratamientos: número, precio medio, duración media; inventario: productos, unidades, bajo stock, agotados; clientes: total, fichas al día, pendientes, citas). Se oculta por debajo de 600 px.
- Centros: sin vista lista; el mapa se abre con su botón en lugar del listado. Desde 1280 px el listado ocupa todo el ancho (tarjetas de 340 px mínimo: 5 por fila a 1920 px, 4 a 1440 px), 1 en móvil.
- Inicio (incluye «Más funcionalidades»; `/features` redirige a `/home#funcionalidades`): con índice lateral (desde 1200 px) el contenido llega hasta 1400 px; FAQ y textos largos mantienen ancho de lectura.

## Agenda (`worker/calendar.html` + `worker-calendar.js`)
- Dos disposiciones con el selector **Mes / Semana** en la `.list-toolbar` (a la derecha, con la leyenda de colores a la izquierda). Se ve en todos los anchos (`.view-toggle--always`) y se recuerda en el navegador (`cuquora.calendar.view`); también `?view=week` y `?date=AAAA-MM-DD`.
- **Semana:** rejilla de lunes a domingo con el horario de `crm.booking` (el controlador lo pasa como `CALENDAR_HOURS`), ampliada si alguna cita se sale. Cada cita es un bloque del alto de su duración (`--hour-height`), coloreado por estado; las que se solapan se reparten el ancho; las rechazadas no se pintan. Días cerrados sin citas, estrechos y rayados. Línea roja con la hora actual. Al pulsar una cita se abre `#appointmentModal` con la misma ficha que la vista mensual (Aceptar / Rechazar); al pulsar la cabecera de un día se abre ese día en la vista mensual.
- Por debajo de 769 px la semana es una lista por días.
- **Organizar la agenda** (`worker-agenda-forms.js`, modales en `calendar.html`; API JSON en `WorkerAppointmentApiController`, que tras cada cambio se recarga con `GET /worker/calendar/data`):
  - «Nueva cita» (botón de la barra o pulsar un hueco libre de la semana): cliente de la cartera (buscador `/worker/clients/search`) o nuevo, tratamiento, día, hora y profesional. Nace **confirmada**.
  - Solicitud pendiente: Aceptar (eligiendo profesional), Rechazar u «Otra hora» (propuesta de 1 a 3 horas; el cliente elige en «Mis Citas»; caduca a las 48 h o 2 h antes de la primera). En la semana las horas propuestas son bloques discontinuos azules.
  - Cita confirmada futura: «Cambiar hora» (y profesional) o «Anular»; en escritorio también se arrastra en la semana. Sigue confirmada y el cliente ve «antes: …».
  - Fuera del horario de reservas el servidor responde 409 con `code: OUTSIDE_HOURS` y se pregunta si guardar igualmente (`allowOutsideHours`). Los solapes nunca se permiten.
  - «Bloquear horario»: vacaciones o descansos de un profesional o cierre del centro (rayado en la agenda; se quita pulsándolo). No se puede bloquear un periodo con citas confirmadas.
- Las posiciones de los bloques se calculan en JS y se pasan como variables CSS (`--top`, `--height`, `--left`, `--width`, `--week-columns`, `--hours`), con un valor por defecto declarado en `calendar.css` para que IntelliJ las resuelva; no dividas por `var()` dentro de `calc()`.

## Navegación
- Por encima de 1365 px: botones de sesión con icono y texto. Sin sesión, «Iniciar Sesión» y «Registrarse» conservan siempre el texto (entre 993 y 1199 px se compactan los rellenos para que quepan).
- **Entre 993 y 1365 px**: «Mi Panel», «Mi Centro», «Mis Citas», «Mi Perfil» y «Cerrar sesión» solo con icono en botón redondo (el texto va en un `<span>` y en `title`).
- Hasta 992 px: menú hamburguesa (drawer accesible).

## Mapa de centros (`templates/centers.html`)
- Leaflet 1.9.4 desde unpkg y teselas `https://tile.openstreetmap.org/{z}/{x}/{y}.png` con el crédito de OSM: **gratis y sin clave**. No uses CARTO (ahora pide API key) ni Google Maps.
- Al tocar un marcador se abre primero un globo con nombre, dirección, distancia y «Ver tratamientos»; solo al pulsarlo se pide iniciar sesión si no la hay.

## Cartera de clientes
- La etiqueta «Registrado» solo aparece si el cliente tiene cuenta (`ClientSummaryDTO.registered`, `User.hasAccount()`).
- **Alta manual** (`ClientCardService.createClientManually`, también desde «Nueva cita»): solo reutiliza clientes que ya son del centro (ficha o citas) con ese teléfono o correo; si no, crea uno nuevo. Nunca enlaza cuentas de otros centros: si el correo ya lo usa otra cuenta, el cliente se guarda sin él (aviso neutro).
- Un cliente dado de alta sin email recibe un email interno `cliente_<telefono>@cbelleza.local` (`ClientCardService.placeholderEmail`, dominio `User.PLACEHOLDER_EMAIL_DOMAIN`), que hoy se muestra en la cartera. Ese dominio no se acepta en `/register`.
- Si alguien se registra con el correo de un cliente dado de alta a mano (sin contraseña ni Google), `UserService.registerClient` **completa esa cuenta** en vez de crear otra o rechazarlo: prevalecen los datos del cliente (nombre, contraseña y teléfono; el del centro solo se mantiene si deja el suyo vacío). Una cuenta de Google sin contraseña **ya tiene dueño**: no se registra encima, sale el aviso «Ya existe una cuenta… Continuar con Google» y la contraseña solo se añade desde el perfil (sección «Añadir Contraseña», `hasPassword` en el modelo). Aún no se verifica el correo: ver #11 en `PENDIENTES.md`.

---

## Contacto (`contact.html`)
- Pantalla partida desde 960 px: foto con texto y accesos (Mis citas, alta de centro, preguntas frecuentes) a la izquierda y formulario a la derecha. En móvil la foto queda como cabecera.

## Alta de centro (`register-center.html` + `center-registration.js`)
- Asistente de 5 pasos (centro, ubicación, equipo, puntos, enviar) con los pasos como cinta de flechas del mismo tamaño (la letra se ajusta con `cqi` y el nombre se corta con «…»): cada `fieldset.registration-section` es un paso; solo avanza si el paso actual es válido (`validateForm(...).check`). Sin JS se ven todos seguidos. A la derecha (debajo en móvil), vista previa del centro que se rellena al escribir. Al mostrar el paso de ubicación se lanza `resize` para que Leaflet recalcule el mapa.
- Un solo formulario por bloques (`fieldset.registration-section`): el centro, dónde está (dirección + `fragments/location-picker`), tu equipo (responsable + filas de trabajadores que se añaden y quitan, máx. 20), tarjeta de puntos opcional (mismos ids que en «Mi Centro», la rellena `loyalty-settings.js`) y algo más (notas y términos).
- Si el servidor rechaza el alta, la página se vuelve a pintar **con lo escrito** (no redirige). Al terminar, en lugar del formulario sale el resumen «Centro registrado: revisa tu correo».
- `center-approval.html` (enlace del correo de administración) y `invitation.html` (crear contraseña; si caducó, botón para pedir otro enlace) llevan `noindex` y están excluidas en `robots.txt`.
- El mapa (Leaflet + OpenStreetMap) no se arrastra con un dedo en móvil para no bloquear el scroll: se toca para poner el pin, se arrastra el pin y se acerca con + y -.
- Al tocar el mapa, mover el pin o usar «Estoy en el centro», `location-picker.js` pide a Nominatim la dirección de ese punto (geocodificación inversa) y la escribe en el campo de dirección; si no la encuentra, lo dice y se escribe a mano.
