#!/bin/sh
# Corrige un producto de la API de productos (esquema «productos» de la base de datos), en el servidor, junto al
# docker-compose.yml. Lo que aportan los centros no se puede cambiar desde Cuquora (a propósito): esto es para ti.
#
#   sh docker/corregir-producto.sh <código> ver
#   sh docker/corregir-producto.sh <código> nombre "Alissi Brontë Sublime Shampoo"
#   sh docker/corregir-producto.sh <código> marca|categoria|formato|descripcion "<texto>"   ("" lo deja vacío)
#   sh docker/corregir-producto.sh <código> quitar-foto
#   sh docker/corregir-producto.sh <código> borrar
#
# Las categorías válidas son Rostro, Cabello, Uñas y Cuerpo. Cada corrección queda en el registro de aportaciones
# («corregido:<campo>», sin centro). Solo cambia la API: los productos que ya tienen los centros en su inventario son
# suyos y no se tocan.
# En tu PC con Docker local: DB_CONTAINER=cuquora_local_db sh docker/corregir-producto.sh ...
set -eu

DB_CONTAINER="${DB_CONTAINER:-crm_cbelleza_db}"

usage() {
    sed -n '5,9p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
}

[ $# -ge 2 ] || usage
code=$(printf '%s' "$1" | tr -d ' -')
action="$2"

case "$code" in
    ''|*[!0-9]*) echo "El código solo puede tener números: $1" >&2; exit 2 ;;
esac
# Igual que la API: un UPC de 12 cifras y un GTIN-14 que empieza por 0 son el mismo producto que su EAN-13
case ${#code} in
    8|13) ;;
    12) code="0$code" ;;
    14) case "$code" in 0*) code="${code#0}" ;; esac ;;
    *) echo "El código tiene que tener 8, 12, 13 o 14 cifras: $1" >&2; exit 2 ;;
esac

# psql dentro del contenedor de la base de datos. El código y el texto van como variables de psql (:'gtin',
# :'valor'): psql los escapa, así que ninguna comilla del texto puede colarse en el SQL.
run_sql() {
    docker exec -i "$DB_CONTAINER" sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' psql "$@"
}

now="date_trunc('second', now() AT TIME ZONE 'Europe/Madrid')"

check_found() {
    found=$(run_sql -t -A -v gtin="$code" <<'SQL'
SELECT count(*) FROM productos.products WHERE gtin = :'gtin';
SQL
)
    if [ "$found" != "1" ]; then
        echo "No hay ningún producto con el código $code en la API." >&2
        exit 1
    fi
}

case "$action" in
    ver)
        check_found
        run_sql -v gtin="$code" <<'SQL'
\pset expanded on
SELECT p.gtin AS codigo, p.name AS nombre, p.brand AS marca, p.category AS categoria, p.quantity AS formato,
       p.description AS descripcion, p.source AS origen, p.created_at AS creado, p.updated_at AS cambiado,
       CASE WHEN i.gtin IS NULL THEN 'no' ELSE 'sí (' || pg_size_pretty(length(i.data)::bigint) || ')' END AS foto
  FROM productos.products p LEFT JOIN productos.product_images i ON i.gtin = p.gtin
 WHERE p.gtin = :'gtin';
\pset expanded off
\echo 'Registro de aportaciones:'
SELECT coalesce(center_id::text, '-') AS centro, CASE WHEN created THEN 'sí' ELSE '' END AS alta,
       fields AS rellenó, created_at AS cuando
  FROM productos.product_contributions WHERE gtin = :'gtin' ORDER BY id;
SQL
        ;;

    nombre|marca|categoria|formato|descripcion)
        [ $# -eq 3 ] || { echo "Falta el texto nuevo (entre comillas)." >&2; usage; }
        value=$(printf '%s' "$3" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
        case "$action" in
            nombre)      column=name;        max=200 ;;
            marca)       column=brand;       max=100 ;;
            categoria)   column=category;    max=40 ;;
            formato)     column=quantity;    max=40 ;;
            descripcion) column=description; max=1000 ;;
        esac
        if [ "$action" = nombre ] && [ -z "$value" ]; then
            echo "El nombre no puede quedar vacío." >&2; exit 2
        fi
        if [ "$action" = categoria ] && [ -n "$value" ]; then
            case "$value" in
                Rostro|Cabello|Uñas|Cuerpo) ;;
                *) echo "La categoría tiene que ser Rostro, Cabello, Uñas o Cuerpo." >&2; exit 2 ;;
            esac
        fi
        check_found
        # El largo se mira en la base de datos: cuenta letras, no bytes («ñ» es una)
        length=$(run_sql -t -A -v valor="$value" <<'SQL'
SELECT char_length(:'valor');
SQL
)
        if [ "$length" -gt "$max" ]; then
            echo "El texto tiene $length caracteres y no puede pasar de $max." >&2; exit 2
        fi
        run_sql -v gtin="$code" -v valor="$value" -v campo="$action" <<SQL
BEGIN;
UPDATE productos.products SET $column = NULLIF(:'valor', ''), updated_at = $now WHERE gtin = :'gtin';
INSERT INTO productos.product_contributions (gtin, center_id, created, fields, created_at)
VALUES (:'gtin', NULL, false, 'corregido:' || :'campo', $now);
COMMIT;
\echo 'Hecho:' :campo 'de' :gtin '->' :'valor'
SQL
        ;;

    quitar-foto)
        check_found
        run_sql -v gtin="$code" <<SQL
BEGIN;
DELETE FROM productos.product_images WHERE gtin = :'gtin';
UPDATE productos.products SET image_updated_at = NULL, updated_at = $now WHERE gtin = :'gtin';
INSERT INTO productos.product_contributions (gtin, center_id, created, fields, created_at)
VALUES (:'gtin', NULL, false, 'corregido:quitar-foto', $now);
COMMIT;
\echo 'Foto quitada de' :gtin
SQL
        ;;

    borrar)
        check_found
        printf 'Se borra el producto %s de la API (con su foto y su registro). ¿Seguro? [s/N] ' "$code"
        read -r answer
        case "$answer" in s|S|si|sí|SI) ;; *) echo "No se ha borrado nada."; exit 0 ;; esac
        run_sql -v gtin="$code" <<'SQL'
DELETE FROM productos.products WHERE gtin = :'gtin';
\echo 'Borrado' :gtin
SQL
        echo "Volverá a crearse la próxima vez que un centro lo dé de alta."
        ;;

    *) usage ;;
esac
