# Subir Cuquora a producción

Lo que hay que hacer a mano (el código ya está preparado). Dominio: `cuquora.com`.

## 1. Cloudflare (dominio cuquora.com, ya comprado en Cloudflare)
1. Cloudflare > Zero Trust > Networks > Tunnels > **Create a tunnel** > tipo **Cloudflared**, nombre `cuquora`.
2. En «Install and run a connector» elige Docker y copia el token (lo que va detrás de `--token`). Va al `.env` del servidor
   como `CLOUDFLARE_TUNNEL_TOKEN` (no lo compartas ni lo subas a git).
3. «Public hostname»: subdominio vacío, dominio `cuquora.com`, servicio **HTTP**, URL **`app:8080`**.
   Añade otro igual con subdominio `www`, o una regla de redirección de `www.cuquora.com` a `https://cuquora.com`.
4. SSL/TLS: **Full (strict)**, y en «Edge Certificates» activa **Always Use HTTPS**.

El servicio `cloudflared` del `docker-compose.yml` arranca solo con `COMPOSE_PROFILES=cloudflare` en el `.env`.
No hace falta abrir puertos en el router ni en el servidor.

## 2. El fichero `.env` del servidor (copia `.env.example`)
- `COMPOSE_PROFILES=cloudflare` y `CLOUDFLARE_TUNNEL_TOKEN=` el token del paso 1.
- `SITE_URL=https://cuquora.com` (con https, sin barra final).
- `REMEMBER_ME_KEY` y `GOOGLE_TOKEN_KEY`: `openssl rand -base64 48` cada una (nuevas, no las de tu equipo).
- `POSTGRES_PASSWORD`: una contraseña larga nueva.
- `LEGAL_OWNER_NAME`, `LEGAL_OWNER_NIF`, `LEGAL_OWNER_ADDRESS` (LSSI).
- `SEED_DEMO_DATA=false` y **sin** `SPRING_PROFILES_ACTIVE`, `COOKIE_SECURE` ni `FORWARD_HEADERS_STRATEGY` (los valores de producción ya vienen por defecto).

## 3. Correo (Brevo)
1. Brevo > Remitentes y dominios: añade `cuquora.com` y copia en Cloudflare DNS los registros SPF, DKIM y DMARC que te da.
2. En el `.env`: `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_USERNAME` y `MAIL_PASSWORD` (clave SMTP), `CRM_FROM_EMAIL=citas@cuquora.com`, `CRM_ADMIN_EMAIL=` el correo donde quieres recibir altas y contacto.

## 4. Google (si usas «Entrar con Google» o Google Calendar)
Google Cloud Console > Credenciales > tu ID de cliente: añade las URI de redirección
`https://cuquora.com/login/oauth2/code/google` y `https://cuquora.com/worker/google-calendar/callback`,
y en «Pantalla de consentimiento» el dominio autorizado y los enlaces a `/privacidad` y `/terminos`. Publica la app (sale de «Prueba»).

## 5. Arrancar
```sh
docker compose up -d --build
docker compose logs -f app      # debe decir «Producción: configuración revisada» y «Started»
docker compose ps               # app «healthy» y crm_cbelleza_tunnel en marcha
docker compose logs cloudflared  # debe decir «Registered tunnel connection»
```
Si algo falta en el `.env`, la app no arranca y el log dice qué variable corregir. Los avisos `Producción: …` no paran el arranque, pero conviene resolverlos.

## 6. Después
- Copia de seguridad cada noche: `crontab -e` → `0 3 * * * cd /ruta/a/CRM_CBelleza && sh docker/backup-db.sh` (y guarda alguna copia fuera del servidor).
- Da de alta tu primer centro desde `/alta-centro`; el enlace «Publicar» llega a `CRM_ADMIN_EMAIL`.
- Google Search Console: añade el dominio y envía `https://cuquora.com/sitemap.xml`.
- Imprime de nuevo los carteles con QR: el QR lleva la dirección de `SITE_URL`.
- Para actualizar: `git pull` y `docker compose up -d --build` (las migraciones de Flyway se aplican solas al arrancar).
