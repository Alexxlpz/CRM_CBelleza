#!/bin/sh
# Lo lanza el servicio «db-setup» del docker-compose.yml en cada «up», antes de la API de productos. Tarda un segundo.
# Deja la API de productos con su propio usuario de PostgreSQL (productos_api), dueño solo de su esquema «productos»:
# sin ningún permiso sobre las tablas de Cuquora (clientes, citas...). Si algún día alguien entrase en la API, no
# podría leerlas ni borrarlas. Se puede repetir sin miedo: si todo está bien, no cambia nada; y tras restaurar una
# copia de seguridad (que deja las tablas a nombre de POSTGRES_USER) vuelve a poner bien los dueños.
set -eu

if [ "${#PRODUCT_API_DB_PASSWORD}" -lt 16 ]; then
    echo "PRODUCT_API_DB_PASSWORD tiene que tener al menos 16 caracteres (por ejemplo, openssl rand -hex 24)." >&2
    exit 1
fi

export PGPASSWORD="$POSTGRES_PASSWORD"
# La contraseña va como variable de psql (:'pw'): psql la escapa
psql -X -q -v ON_ERROR_STOP=1 -h db -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v pw="$PRODUCT_API_DB_PASSWORD" <<'SQL'
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
echo "Usuario productos_api listo: solo tiene el esquema productos."
