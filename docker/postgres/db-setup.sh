#!/bin/sh
# Lo lanza el servicio «db-setup» del docker-compose.yml en cada «up», antes de la app y de la API de productos.
# Tarda un segundo. Ninguna de las dos entra en PostgreSQL con el superusuario (POSTGRES_USER):
#   - cuquora_app (la app): dueño solo de las tablas de Cuquora (esquema public). Puede crear y cambiar sus tablas
#     (Flyway), pero no crear usuarios ni bases de datos, ni leer el esquema «productos», ni tocar el servidor.
#   - productos_api (la API de productos): dueño solo de su esquema «productos», sin ningún permiso sobre las tablas
#     de Cuquora (clientes, citas...).
# Si algún día alguien consiguiera colar una consulta en una de las dos, no podría salir de lo suyo.
# Se puede repetir sin miedo: si todo está bien, no cambia nada; y tras restaurar una copia de seguridad (que deja las
# tablas a nombre de POSTGRES_USER) vuelve a poner bien los dueños.
set -eu

check_password() {
    if [ "${#2}" -lt 16 ]; then
        echo "$1 tiene que tener al menos 16 caracteres (por ejemplo, openssl rand -hex 24)." >&2
        exit 1
    fi
}
check_password APP_DB_PASSWORD "${APP_DB_PASSWORD:-}"
check_password PRODUCT_API_DB_PASSWORD "${PRODUCT_API_DB_PASSWORD:-}"

export PGPASSWORD="$POSTGRES_PASSWORD"
# Las contraseñas van como variables de psql (:'pw'): psql las escapa
psql -X -q -v ON_ERROR_STOP=1 -h db -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
     -v app_pw="$APP_DB_PASSWORD" -v pw="$PRODUCT_API_DB_PASSWORD" <<'SQL'
-- ---- La app: cuquora_app, dueño de las tablas de Cuquora ----
SELECT 'CREATE ROLE cuquora_app LOGIN'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cuquora_app') \gexec
ALTER ROLE cuquora_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_pw';
-- Crear tablas nuevas en public (las migraciones de Flyway). Desde PostgreSQL 15 nadie más puede.
GRANT USAGE, CREATE ON SCHEMA public TO cuquora_app;
-- Sus tablas y vistas (también flyway_schema_history), para que Flyway pueda cambiarlas. Las secuencias de las
-- columnas IDENTITY o serial cambian de dueño con su tabla; las sueltas, aparte.
SELECT format('ALTER %s public.%I OWNER TO cuquora_app',
              CASE c.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' WHEN 'S' THEN 'SEQUENCE' ELSE 'TABLE' END,
              c.relname)
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
   AND pg_get_userbyid(c.relowner) <> 'cuquora_app'
   AND NOT EXISTS (SELECT 1 FROM pg_depend d
                    WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid AND d.deptype IN ('a', 'i')) \gexec

-- ---- La API de productos: productos_api, dueño del esquema productos ----
SELECT 'CREATE ROLE productos_api LOGIN'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'productos_api') \gexec
ALTER ROLE productos_api WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'pw';

CREATE SCHEMA IF NOT EXISTS productos AUTHORIZATION productos_api;
ALTER SCHEMA productos OWNER TO productos_api;
-- Sus tablas (también flyway_schema_history), para que Flyway de la API siga pudiendo migrar.
-- Las secuencias de las columnas IDENTITY cambian de dueño con su tabla.
SELECT format('ALTER TABLE productos.%I OWNER TO productos_api', tablename)
  FROM pg_tables WHERE schemaname = 'productos' AND tableowner <> 'productos_api' \gexec
SQL
echo "Usuarios listos: cuquora_app solo tiene las tablas de Cuquora y productos_api solo el esquema productos."
