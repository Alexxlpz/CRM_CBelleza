#!/bin/sh
# Copia de seguridad de la base de datos de Cuquora (en el servidor, junto al docker-compose.yml):
#   sh docker/backup-db.sh   ->  backups/cuquora-AAAA-MM-DD_HHMM.sql.gz
# Solo guarda la copia si pg_dump termina bien y el archivo está completo; si no, sale con error (código 1) y no
# borra ninguna copia antigua. Se borran las de más de 30 días, pero siempre se conservan las 7 más recientes.
#
# Segunda copia en otro disco o carpeta (recomendado: si se rompe el disco, se pierden las dos):
#   BACKUP_COPY_DIR=/volume1/copias/cuquora sh docker/backup-db.sh
#
# Cada noche a las 3:00:
#   - Synology: Panel de control > Programador de tareas > Crear > Tarea programada > Script definido por el usuario,
#     usuario root, y en «Ejecutar comando»:  cd /ruta/a/cuquora && sh docker/backup-db.sh
#     En «Configuración de tareas», marca «Enviar detalles de ejecución por correo electrónico» y
#     «Enviar detalles de ejecución solo cuando el script termine de forma anormal».
#   - Linux: «crontab -e» ->  0 3 * * * cd /ruta/a/cuquora && sh docker/backup-db.sh
#
# Restaurar (con la app parada: docker compose stop app):
#   gunzip -c backups/cuquora-....sql.gz | docker exec -i crm_cbelleza_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
set -eu
cd "$(dirname "$0")/.."

KEEP_DAYS=30
KEEP_MIN=7
DB_CONTAINER=crm_cbelleza_db

fail() {
    echo "ERROR en la copia de seguridad: $*" >&2
    exit 1
}

mkdir -p backups
stamp=$(date +%F_%H%M)
file="backups/cuquora-$stamp.sql.gz"
part="backups/.cuquora-$stamp.sql.gz.part"
status="backups/.cuquora-$stamp.status"
trap 'rm -f "$part" "$status"' EXIT

# En una tubería «pg_dump | gzip» el error de pg_dump se pierde: se apunta su código de salida aparte
{
    if docker exec "$DB_CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists'; then
        echo 0 > "$status"
    else
        echo $? > "$status"
    fi
} | gzip > "$part"

code=$(cat "$status" 2>/dev/null || echo "desconocido")
[ "$code" = "0" ] || fail "pg_dump ha fallado (código $code). No se ha guardado copia ni se ha borrado ninguna."
gzip -t "$part" 2>/dev/null || fail "el archivo comprimido está dañado. No se ha borrado ninguna copia."
gzip -dc "$part" | tail -n 20 | grep -q "PostgreSQL database dump complete" \
    || fail "la copia está incompleta (falta el final de pg_dump). No se ha borrado ninguna copia."
mv "$part" "$file"
echo "Copia guardada en $file ($(du -h "$file" | cut -f1))"

# Borra las de más de KEEP_DAYS días, pero nunca las KEEP_MIN más recientes
rotate() {
    ls -1 "$1"/cuquora-*.sql.gz 2>/dev/null | sort -r | tail -n +$((KEEP_MIN + 1)) | while read -r old; do
        if [ -n "$(find "$old" -mtime +$KEEP_DAYS)" ]; then
            rm -f "$old"
        fi
    done
}
rotate backups

if [ -n "${BACKUP_COPY_DIR:-}" ]; then
    mkdir -p "$BACKUP_COPY_DIR"
    cp "$file" "$BACKUP_COPY_DIR/" || fail "no se ha podido copiar a $BACKUP_COPY_DIR (la copia local sí está en $file)."
    rotate "$BACKUP_COPY_DIR"
    echo "Segunda copia en $BACKUP_COPY_DIR"
fi
