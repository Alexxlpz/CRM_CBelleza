# CLAUDE.md · Cuquora CRM

Cuquora es una web responsive para centros de belleza: los **trabajadores** gestionan su centro (agenda, citas, clientes y fichas, tratamientos, inventario) y los **clientes** buscan centros y piden citas.

- Repositorio: `github.com/Alexxlpz/CRM_CBelleza`, rama `master`. Paquete raíz: `com.alexxlpz.crm_cbelleza`.
- Documentación para usuarios (HTML estático): `documentación/` (guía de roles, manual de acciones, evolución del proyecto).
- **Trabajo pendiente y auditoría de seguridad: [`PENDIENTES.md`](PENDIENTES.md).** Léelo antes de tocar seguridad, login, correo, inventario o despliegue, y bórrale los puntos que cierres.

### Lee solo lo que necesites (ahorra contexto)

Este fichero se carga en cada sesión; el detalle está aparte y se lee bajo demanda:

| Antes de… | Lee / usa |
|---|---|
| Tocar cualquier cosa visual (CSS, plantillas, JS de interfaz, textos) | skill **`interfaz-cuquora`** (movimiento, componentes, textos que no suenen a IA) |
| Tocar agenda, navegación, mapa, cartera, listados, alta de centro, pie o páginas legales | [`.claude/docs/pantallas.md`](.claude/docs/pantallas.md) |
| Tocar login, registro, sesión, Google o Google Calendar | [`.claude/docs/autenticacion.md`](.claude/docs/autenticacion.md) |
| Tocar `.env`, variables de entorno, Docker o despliegue | [`.claude/docs/configuracion.md`](.claude/docs/configuracion.md) |
| Añadir una funcionalidad con datos (migración, endpoint, pantalla) | skill **`nueva-funcionalidad`** |
| Comprobar un cambio visual (capturas a varios anchos, antes y después) | skill **`capturas`** |

Para buscar código usa `grep`/`rg` con el nombre de clase o la clase CSS; no leas ficheros enteros de 1000+ líneas (`common.css`, `calendar.css`, `worker-calendar.js`) si basta con un trozo.

---

## Cómo trabaja Alex (leer antes de cambiar nada)

- **Textos de la app en español.** Todo lo que ve el usuario (plantillas, mensajes de error, correos, diálogos) va en español.
- **No hagas commit ni push salvo que Alex lo pida**; deja los cambios en la carpeta para que los revise. Cuando los pida: en inglés y breves, título corto y como mucho unas viñetas de una línea:
  ```
  Improve desktop management screens and fix icons

  - Wider layout, stats strip, category filters and grid/list toggle
  - Self-host Lucide icons with the lucide- prefix
  ```
- **Escritorio aprovechando el ancho, sin romper el móvil.** En escritorio, contenedor ancho, más tarjetas por fila y vistas de lista. En móvil todo debe seguir igual.
- **Nada cortado en anchos intermedios.** Media pantalla horizontal (≈960-1365 px) es un uso habitual: ningún botón se sale y los textos hacen salto de línea en lugar de recortarse con «…» (solo se recortan resúmenes que tienen versión completa en otro sitio, como la descripción corta de producto).
- **Iconos con sentido, nunca huecos vacíos.** Si un elemento tiene hueco de icono, pon un icono Lucide que lo identifique; si no aporta nada, quita el hueco. Ante la duda, pon el icono.
- **Coherencia entre pantallas.** Un control que existe en varias pantallas (p. ej. el selector Tarjetas/Lista) va en la misma posición en todas.
- **Al terminar:** pasa los tests, prueba en 1920, 1440, 1100, 960, 768 y 390 px y di qué archivos has tocado y por qué. Si el cambio es visual, adjunta capturas de antes y después (escritorio y móvil).

---

## Entorno, arranque y tests

Alex trabaja en dos máquinas:

| | Windows (IntelliJ) | Linux (Arch) |
|---|---|---|
| Carpeta | `C:\Users\alexl\IdeaProjects\CRM_CBelleza` | `/home/alejandro/IdeaProjects/CRM_CBelleza` |
| Maven | `mvnw.cmd` | `./mvnw` o `mvn` del sistema |
| JDK | 21 | **El `java` por defecto es OpenJDK 27 y no compila** (Lombok falla con `EndPosTable`). Usa siempre `JAVA_HOME=/home/alejandro/.jdks/ms-25.0.4.1` |

```bash
# En Linux, anteponer JAVA_HOME=/home/alejandro/.jdks/ms-25.0.4.1 a cada comando y usar mvn en vez de ./mvnw

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # desarrollo: H2 en memoria, /login-selector y consola H2
./mvnw test                                             # todos los tests (225, verde a 22 oct 2026)
./mvnw -Dtest=CenterIsolationTests test                 # un solo test
./mvnw -DskipTests clean package                        # jar en target/
docker compose up --build                               # app en :8080 + PostgreSQL 16 (necesita el .env, ver abajo)
```

- En IntelliJ: Run Configuration > Active profiles = `dev`. La app escucha en `http://localhost:8080`.
- Con `mvn spring-boot:run` la app sirve CSS, JS y plantillas desde `target/classes`: tras editarlos fuera de IntelliJ, ejecuta `mvn -o resources:resources` para verlos.
- `application-dev.properties` desactiva las cachés de Thymeleaf y del hash de recursos: los cambios de plantillas, CSS y JS se ven sin reiniciar. CSS y JS se sirven con hash de contenido en la URL, así que tras un despliegue no hace falta Ctrl+F5.
- En `dev` la BD H2 en memoria **se borra en cada reinicio** (también los de devtools al compilar). Con `DB_CLOSE_DELAY=-1` sobrevivía a medias y los UPDATE fallaban con «Check constraint invalid» / «La base de datos ha sido cerrada».
- Usuarios demo: los crea `DatabaseSeeder` si la BD está vacía **y** `crm.seed-demo-data=true`, que solo activan el perfil `dev` y los tests (`src/test/resources/config/application.properties`). Contraseña `password123`, pública: nunca en producción.
  - Trabajadores: `carlos@cbelleza.com` y `lucia.romero@cbelleza.com` (Centro Histórico), `elena@cbelleza.com` (Plaza Norte), `marco@cbelleza.com` (Sarrià-Sant Gervasi), `ana@cbelleza.com` (Las Condes).
  - Clientes: `sofia@gmail.com` y otros.
  - Con el perfil `dev`, `/login-selector` permite entrar como cualquiera sin contraseña.
- **Secretos solo en `.env`** (en `.gitignore`); **nunca** contraseñas en `docker-compose.yml`, `application*.properties` ni en el código. Si añades una variable, añádela también a `.env.example`. Tabla de variables y despliegue: [`.claude/docs/configuracion.md`](.claude/docs/configuracion.md).
- Si el 8080 está ocupado (Alex suele tener la app abierta en IntelliJ), no lo pares: arranca tu copia con `-Dspring-boot.run.arguments=--server.port=8091`.

## Stack

- **Java 21** (`java.version` del pom), **Spring Boot 4.1.0** (`spring-boot-starter-webmvc`, Thymeleaf, Security, Data JPA, Mail), Lombok, devtools.
- **Flyway** para el esquema (`src/main/resources/db/migration`), `ddl-auto=validate`.
- BD: **H2 en memoria** por defecto (local y tests); **PostgreSQL 16** con Docker. El driver de MySQL está incluido pero no se usa (necesitaría `flyway-mysql`).
- Front: Thymeleaf + CSS propio + JS sin framework. Tipografía Plus Jakarta Sans (Google Fonts). Iconos **Lucide 0.321.0** servidos por la propia app. Mapa con **Leaflet 1.9.4** y teselas de **OpenStreetMap**.
- Tests: JUnit 5 + Spring Boot Test + spring-security-test.

## Estructura

```
src/main/java/com/alexxlpz/crm_cbelleza/
  booking/       OpeningHours (horario de un centro), BookingProperties, ClientVisitAnalyzer, AgendaSnapshot (¿cabe una cita?), CenterClock, CenterTimeZones
  config/        SecurityConfig, DatabaseSeeder (+ DemoHistory), JacksonConfig, TimeConfig
  controllers/   un controlador por área (sin lógica de negocio ni repositorios); DevLoginController solo en dev
  dto/           DTOs de lectura (ClientSummaryDTO, CenterSummaryDTO, AppointmentCalendarDTO…)
  entities/      entidades JPA + enums
  exceptions/    BusinessRuleException, ResourceNotFoundException, handlers MVC y JSON (@ApiEndpoint)
  forms/         formularios como records; PasswordPolicy, FormText
  googlecalendar/ sincronización de citas con Google Calendar (GoogleCalendarService, Gateway HTTP, cifrado de tokens)
  mail/          EmailGateway (Smtp / Logging), InquiryMailService, AppointmentMailService (+ Listener), MailConfig
  repositories/  Spring Data JPA
  scheduler/     AppointmentStatusScheduler (cada 60 s marca citas pasadas como COMPLETED)
  security/      AppUserDetails(Service), handlers de login/acceso, RoleHomes, CurrentUserModelAdvice
  services/      lógica de negocio y validación
src/main/resources/
  db/migration/  V1__esquema_inicial.sql, V2__eliminar_clientes_invitados.sql, V3__usuarios_google.sql,
                 V4__sesiones_recordadas.sql, V5__calendario_google.sql, V6__zona_horaria_centros.sql,
                 V7__agenda_flexible.sql, V8__resumen_tratamiento.sql,
                 V9__ventas_productos.sql, V10__fidelizacion.sql, V11__alta_de_centros.sql,
                 V12__calendario_google_clientes.sql, V13__foto_centros.sql, V14__precio_citas.sql,
                 V15__ofertas_tratamientos.sql, V16__auto_aceptar_citas.sql, V17__horario_y_dueno_de_centro.sql,
                 V18__citas_no_presentado.sql
  templates/     públicas en la raíz, client/, worker/, fragments/, email/
  static/css/    tokens.css + common.css + un CSS por área
  static/js/     app.js, form-validation.js, sheets.js, unsaved-changes.js, booking-picker.js,
                 worker-calendar.js, worker-agenda-forms.js, treatment-summary.js, scroll-index.js,
                 center-registration.js, location-picker.js, invitation.js, loyalty-settings.js, photo-picker.js,
                 treatment-offer.js
  static/icons/lucide/  fuente de Lucide (woff/woff2)
src/test/java/…  BookingServiceTests, CenterIsolationTests, SecurityIntegrationTests, PageRenderingTests,
                 UnitTests, CrmCBellezaApplicationTests, GoogleLoginTests, GoogleCalendarTests, AgendaTests, RegistrationTests, TreatmentSummaryTests, ProductSaleAndLoyaltyTests, CenterRegistrationTests, CenterPhotoTests, AnalyticsTests, TreatmentOfferTests, security/CurrentUserModelAdviceTest, TestUsers
```

### Reglas de backend

- Controladores finos: reciben el formulario (record), llaman al servicio y eligen la vista. La validación vive en los servicios y lanza `BusinessRuleException` (mensaje en español) o `ResourceNotFoundException` (404).
- Endpoints JSON marcados con `@ApiEndpoint` devuelven errores en JSON (`ApiExceptionHandler`).
- `BookingService` concentra todos los cambios de cita (solicitar, aceptar, rechazar, crear, mover, proponer, anular) con bloqueo por centro y respeta el horario del centro (`OpeningHoursService.forCenter`). Si cabe una cita lo decide `AgendaSnapshot` (cargado por `AgendaAvailabilityService`): centro no cerrado, profesional sin otra cita ni bloqueo, y a la vez no más citas que `centers.cabins` ni que profesionales libres. Las horas propuestas reservan hueco frente a clientes y otras propuestas, pero el trabajador puede ocuparlas (la propuesta pierde esa opción). Cada cambio anota `lastChange` y publica `AppointmentChangedEvent` (Google Calendar y correos, tras el commit y en segundo plano). «Ahora» al reservar = `CenterClock.now` (zona del centro).
- Sin N+1: usa `@EntityGraph` o cálculos en memoria. Con `open-in-view=false`, el servicio carga todo lo que la vista necesite.
- Totales y medias que se muestran en pantalla se calculan en el controlador/servicio y se pasan al modelo. **No uses proyecciones de colección en Thymeleaf** (`lista.![campo]`, `lista.?[cond]`): IntelliJ las marca como error.
- Correos: plantillas Thymeleaf en `templates/email/` enviadas con `EmailGateway`.

## Modelo de datos

Tablas (ver `V1__esquema_inicial.sql`):

- `centers`: nombre, dirección, teléfono, email, `latitude`/`longitude` (el mapa usa estas coordenadas, no geocodifica), `time_zone` (IANA, elegible en «Mi Centro» entre `CenterTimeZones.OPTIONS`; la usan las reservas y Google Calendar), `cabins` (citas a la vez, 1-50, en «Mi Centro»), `published` (sale en el buscador y admite reservas; los dados de alta en /alta-centro nacen ocultos), `tax_id` (CIF/NIF), `approval_token_hash`/`registered_at`.
- `users`: `role` CLIENT | WORKER, `email` único, `password` (BCrypt, **nula en clientes dados de alta a mano y en cuentas solo de Google**), `google_id` único (claim `sub` de Google, nulo si no ha entrado nunca con Google), `center_id` solo para trabajadores. `User.hasAccount()` = tiene contraseña o Google. `notifications_seen_at`: última vez que el cliente abrió su campana.
- `treatments`: por centro, `treatment_type` (MANICURA_PEDICURA, FACIAL, CORPORAL, MASAJES, PELUQUERIA, DEPILACION, OTRO), precio y duración en minutos, y oferta opcional: `offer_price` (menor que el de carta) y `offer_ends_on` (último día, hora del centro; nulo = hasta que se quite).
- **Ofertas de tratamientos** (`TreatmentService.saveOffer/removeOffer`, `POST /worker/treatments/{id}/offer` y `/offer/delete`, modal `offerModal` en `worker/fragments/treatment-modals.html` + `treatment-offer.js`): el trabajador las pone desde la carta (botón «Poner en oferta» en cada tratamiento y filtro «En oferta»). Valen para las citas que se **piden** mientras dura la oferta (`Treatment.priceOn(hoy)` en `BookingService`, se guarda en `appointments.price`), aunque la cita sea para después. Una oferta vencida no se borra: sale como «Oferta terminada» y se puede renovar. El cliente las ve en la reserva (filtro «Todos los servicios / En oferta», etiqueta «-20 % · hasta el 30 oct» y precio de carta tachado) y en el buscador de Centros (`CenterSummaryDTO.offers`, una sola línea «3 tratamientos en oferta · hasta -20 %» en la tarjeta y en el globo del mapa (no se listan para que no se líe con muchas), botón «Con ofertas» = `withOffers=true`). Cada tarjeta y globo del mapa dice si el centro está abierto ahora y cuándo cierra o vuelve a abrir (`CenterSummaryDTO.openNow`/`hoursLabel`, de `OpeningHours.stateAt` con su horario, sus cierres de todo el centro y su zona horaria); el botón «Abierto ahora» = `openNow=true`. Tests: `OpenNowTests`. En `dev` hay ofertas de ejemplo (con `crm.seed-demo-history`). Packs y bonos: pendientes.
- `products`: catálogo **global** (sin centro). `inventories`: stock por centro y producto (único por par).
- `appointments`: centro, tratamiento, cliente, trabajador, `status` PENDING | PROPOSED | CONFIRMED | REJECTED | CANCELLED | COMPLETED | NO_SHOW (etiquetas en `AppointmentStatus.label()`; NO_SHOW, V18: «No se presentó», lo marca cualquier trabajador en la agenda en una cita confirmada que ya ha empezado con `POST /worker/appointments/{id}/no-show` (`undo=true` lo deshace); quita los puntos de esa cita, no avisa a nadie y la ficha del cliente y la agenda muestran cuántas veces ha pasado), `price` (lo que se cobra: el precio de carta al reservar, `@PrePersist` si nadie lo pone; el trabajador lo corrige en el modal del resumen. Lo usan la agenda, la ficha, «Mis citas», los puntos y el análisis, nunca `treatment.price`), `worker_message`, `last_change`/`last_change_at` (`AppointmentChange`: qué cambió y quién, para campanas y correos), `previous_date_time` (hora anterior si el centro la movió), `proposal_expires_at`, `reminder_sent_at`, `google_event_id`/`google_event_owner_id`, `treatment_summary` + `treatment_alert` + `summary_updated_at`/`summary_updated_by_id` (resumen del tratamiento realizado, ver abajo).
- **Horario de cada centro** (`center_opening_hours`, V17; `OpeningHoursService`, `booking/OpeningHours`, editor `fragments/opening-hours.html` + `opening-hours.js`): uno o dos tramos por día de la semana (jornada partida), de media en media hora. Se elige en el alta (paso «Horario») y lo cambia el dueño en «Mi Centro» (`POST /worker/center/hours`). Lo usan los huecos del cliente (`/api/centers/{id}/availability` manda `days`: tramos en minutos por día ISO), `BookingService` (reservar, proponer y colocar citas), la rejilla de la agenda (franjas rayadas fuera de horario y a mediodía) y la ficha del centro (`OpeningHours.describe()`). `crm.booking.*` solo da la franja (30 min) y el horario por defecto de un centro nuevo o sin horario.
- **Dueño del centro** (`centers.owner_id`, V17; `CenterAccess`): quien lo dio de alta. En los centros anteriores, el trabajador con el correo del centro o el primero creado. En `dev`, Carlos (Centro Histórico), Elena, Marco y Ana; Lucía es trabajadora estándar. Se puede ceder desde «Mi Centro» (`POST /worker/center/owner`) a alguien del equipo con cuenta activa.
- **Equipo del centro** (`TeamService`, tarjeta «Equipo» de «Mi Centro»): todos ven quién trabaja y el estado de su cuenta (activa, invitación enviada o caducada). El dueño invita (`POST /worker/center/team`: crea el trabajador sin contraseña y le manda la invitación; si el correo es de un trabajador sin centro, lo vuelve a unir con su cuenta), reenvía la invitación (`/team/{id}/resend`, la anterior caduca) y saca del equipo (`/team/{id}/remove`: no al dueño ni a quien tenga citas pendientes, propuestas o confirmadas por delante; quita sus bloqueos futuros y le deja sin centro, con su historial). `CenterMembershipInterceptor` comprueba en cada petición de /worker/** que sigue en su centro: si no, cierra su sesión (también la recordada) y le manda al login con un aviso. Máximo `TeamService.MAX_TEAM_SIZE` personas. Tests: `TeamTests`.
- `appointment_proposals`: horas que el centro propone para una cita PROPOSED (máx. 3). `agenda_blocks`: bloqueos de agenda (`worker_id` nulo = todo el centro cerrado).
- `persistent_logins`: dispositivos con «Recordar mi sesión» (tabla estándar de `JdbcTokenRepositoryImpl`, sin entidad JPA).
- `google_calendar_links`: trabajadores **y clientes** con Google Calendar activado (`user_id`; token de actualización cifrado con `GOOGLE_TOKEN_KEY`, id del calendario creado en su cuenta). `appointments.google_event_id` apunta el evento en el calendario del trabajador y `client_google_event_id` el del cliente.
- **Resumen del tratamiento** (`TreatmentSummaryService`, `POST /worker/appointments/{id}/summary`, modal `fragments/treatment-summary.html` + `treatment-summary.js`): el trabajador lo escribe desde la agenda o el historial de la ficha cuando la cita confirmada **ya ha empezado** (hora del centro). El mismo modal lleva el «Precio cobrado» (0-10.000 €; si no llega `price`, no se toca). Si lo marca como **aviso** (alergias, reacciones, productos que no le sientan bien), sale arriba en la ficha del cliente y en sus demás citas de la agenda (`AppointmentCalendarDTO.clientAlerts`). Es interno: el cliente nunca lo ve.
- **Alta de centros** (`CenterRegistrationService`, `CenterRegistrationController`, `register-center.html`): crea el centro **oculto** (`published=false`: fuera del buscador y del mapa, `/client/centers/{id}` da 404 y `BookingService` no reserva), al responsable y a su equipo como trabajadores **sin contraseña** y, si se pide, la tarjeta de puntos (`LoyaltyService.updateSettings`). Ubicación con pin arrastrable (`fragments/location-picker.html` + `location-picker.js`, búsqueda en Nominatim; también en «Mi Centro»). Administración recibe un correo con el enlace `/register-center/approve/{token}` para revisar y publicar. Cada trabajador recibe una invitación (`account_invitations`, `InvitationService`: un solo uso, 7 días, se puede pedir otra desde la misma página) para poner su contraseña; también puede entrar con Google. En la BD solo va la huella SHA-256 de los enlaces (`SecureTokens`). Sin SMTP, `LoggingEmailGateway` imprime los enlaces de cada correo en la consola.
- **Foto del centro** (`center_photos`, `CenterPhotoService`, `CenterPhotoController`, `fragments/photo-picker.html` + `photo-picker.js`): se sube en el alta y en «Mi Centro» (`POST /worker/center/photo`, `/photo/delete`). Se lee como imagen (JPG o PNG, hasta 8 MB, mínimo 400×250, se endereza con la orientación EXIF) y se guarda como JPEG de 1200 px como mucho en la BD (no en disco: entra en las copias de PostgreSQL y Docker no necesita otro volumen). `centers.photo_updated_at` dice si hay foto y versiona la URL `/centers/{id}/photo?v=…` (caché de un año). La de un centro sin publicar solo la ven su equipo y administración (`/register-center/approve/{token}/photo`). Sin foto, el hueco muestra las iniciales (`Center.getInitials()` / `Cuquora.initials`).
- **Análisis del centro** (`AnalyticsService`, `GET /worker/analytics?meses=3|6|12` o `?anio=2025` (vacío = el actual) en `WorkerDashboardController`, `worker/analytics.html` + `analytics.css`): sin tablas propias, se calcula en memoria con una consulta de citas y otra de ventas. Visita = cita completada; ingresos = `appointments.price` + ventas de productos. Dos vistas: últimos meses (cifras del mes en curso frente al mismo tramo del anterior) y **por año** (el año hasta hoy frente al mismo tramo del anterior, barras de los dos años juntas, «Qué ha cambiado»: ingresos = visitas × ticket, clientes que no han vuelto y tratamientos que suben o bajan, y diferencias en los listados). El **primer mes con Cuquora** (`centers.registered_at` o el primer dato del centro) marca el límite: no se puede ir a años anteriores, los meses previos salen «sin datos» (no a cero), solo se comparan los meses que hay en los dos años y en los gráficos lleva una bolita. «Clientes a recuperar»: dos visitas o más, sin cita y sin venir el doble de su intervalo habitual (mínimo 60 días). El panel principal enseña las tres primeras cifras. Colores de los gráficos: `--chart-1`/`--chart-2` de `tokens.css` (pareja validada para daltonismo). En `dev`, `crm.seed-demo-history=true` añade visitas y ventas a Centro Histórico desde enero del año anterior (`DemoHistory`); los tests no lo activan.
- `client_cards`: ficha técnica por centro y cliente, `data_json` con los valores. `client_card_templates`: campos de la ficha por centro (`fields_json`).
- **Venta de productos** (`product_sales`, `ProductSaleService`, `ClientSalesController`, modal `fragments/product-sale.html` + `product-sale.js`): desde la ficha del cliente el trabajador elige un producto del inventario y la cantidad; antes de confirmar ve el stock actual y el que quedará (aviso si queda bajo o se agota). Al guardar se resta del inventario con la fila bloqueada (`InventoryRepository.findForUpdate`); nunca se vende más que el stock. «Anular» devuelve las unidades y quita sus puntos. `unit_price` guarda el precio del momento.
- **Tarjeta de puntos** (`centers.loyalty_*`, `loyalty_movements`, `LoyaltyService`): cada centro la configura en «Mi Centro» (`POST /worker/center/loyalty`): desactivada, puntos por euro (`PER_EURO`) o por tratamiento/unidad de producto (`PER_ITEM`), puntos para la recompensa y texto. Los productos suman al venderse; los tratamientos, cuando la cita está completada (se apuntan al abrir la ficha, porque el estado se calcula al leer; `appointment_id` único evita sumar dos veces) y solo desde `loyalty_since`. Canje desde la ficha (`POST /worker/clients/{id}/loyalty/redeem`, bloquea el centro). Los puntos se guardan al ganarse: cambiar la configuración no altera lo ya ganado. El cliente ve sus tarjetas en «Mis citas» (`LoyaltyService.cardsOfClient`: centros con la tarjeta activada donde tiene citas o puntos).

**Migraciones:** todo cambio de esquema es un nuevo `V<n>__descripcion.sql` (nombre en español, SQL compatible con H2 y PostgreSQL). Nunca edites una migración ya aplicada. La siguiente libre es **`V19`**. En Docker, `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` adopta BD creadas antes de Flyway.

## Roles y aislamiento entre centros

- `/client/**` solo `ROLE_CLIENT`. `/worker/**` solo trabajadores **con centro asignado** (`SecurityConfig.workerWithCenter`).
- Públicas (`SecurityConfig.PUBLIC_PAGES`): `/`, `/home`, `/features` (`/funcionalidades`, redirige a Inicio), `/centers`, `/contact` (`/contacto`), `/register-center` (`/alta-centro`), `/terms` (`/terminos`), `/cookies` (`/politica-cookies`), `/privacy` (`/privacidad`), `/login`, `/register`, `/error`, `/register-center/approve/{token}`, `/invitation/{token}` (y `/renew`), `/api/centers/**`, `/robots.txt` y estáticos.
- Detalle de login, «Recordar mi sesión», Google y Google Calendar: [`.claude/docs/autenticacion.md`](.claude/docs/autenticacion.md).
- **Aislamiento:** toda acción de `/worker/**` usa el `centerId` de la sesión y consultas `findByIdAndCenterId`; un recurso de otro centro responde 404. Las rutas de cliente usan el id de la sesión. Mantén esta regla en todo endpoint nuevo y añade un caso a `CenterIsolationTests`.
- Cliente: `/client/appointments/{id}/accept-proposal|decline-proposal|cancel` buscan la cita con `findByIdAndClientId` (de otro cliente → 404). Campana del cliente: `ClientNotificationService` (citas de hoy/mañana y cambios del centro de los últimos 7 días); al abrirla, `POST /client/notifications/seen`.
- Un trabajador no puede ver otros centros como cliente: desde tarjeta o mapa sale un aviso con enlace a su panel.
- **Dueño y trabajadores estándar:** las acciones solo del dueño llevan `@CenterOwnerOnly` (las frena `CenterOwnerInterceptor`: formulario → vuelve a su pantalla con `errorMessage`; fetch → 403 JSON). Son: datos y horario del centro, foto, tarjeta de puntos, reservas automáticas, ceder el centro, alta y ofertas de tratamientos, alta y baja de productos del inventario, plantilla de ficha de clientes y análisis (y las cifras del mes del panel). Cerrar el centro o bloquear/desbloquear la agenda de otra persona también es solo del dueño (`AgendaBlockService`). Todos: agenda y citas, clientes y fichas, ventas, stock, canjes, su cuenta, Google Calendar y sus propios bloqueos. Las vistas usan `isCenterOwner` y `ownerName` (`CurrentUserModelAdvice`) para esconder botones y avisar con `fragments/alerts :: ownerOnly`. Tests: `CenterOwnershipTests`.
- `/login-selector`, `/select-session` y la consola H2 solo existen con el perfil `dev`.

---

## Convenciones de UI

### Base
- **Colores solo desde `tokens.css`** (`--primary`, `--text-main`, `--surface`…), con modo oscuro completo y contraste AA. Ningún color en hex en otros CSS.
- Sin estilos inline, sin `onclick`, sin `!important` (salvo `[hidden]` y reduced-motion).
- `<head>` común: `th:replace="~{fragments/head :: meta('Título')}"` + el CSS propio de la página. Incluye la descripción y las etiquetas Open Graph (imagen `static/images/og-image.jpg`, 1200×630, en JPEG porque las redes no aceptan bien WebP); para una descripción propia, el controlador pone `pageDescription` en el modelo.
- Diálogos propios: `Cuquora.confirm`, `Cuquora.alert`, `data-confirm="…"` en formularios, modales `.ui-modal-overlay` con `data-modal-open` / `data-modal-close`. Nunca `alert()`/`confirm()` del navegador.
- Todo texto del servidor insertado con `innerHTML` pasa por `Cuquora.escapeHtml`.
- Imágenes en WebP con `loading="lazy"`.

### Iconos (Lucide 0.321.0, servidos en local)
- `<i class="lucide-calendar" aria-hidden="true"></i>`. CSS en `static/css/lucide-icons.css` (el de Lucide con el prefijo `icon-` cambiado a `lucide-`), fuente en `static/icons/lucide/`.
- **No cargues Lucide desde la CDN**: allí las clases se llaman `icon-…` y los iconos salen vacíos.
- Usa solo nombres que existan en la 0.321.0 (p. ej. `edit-3` no existe; se usa `pen-line`). Si dudas, búscalo en `lucide-icons.css`.
