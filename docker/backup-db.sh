#!/bin/sh
# Copia de seguridad de la base de datos de Cuquora (en el servidor, junto al docker-compose.yml):
#   sh docker/backup-db.sh        ->  backups/cuquora-AAAA-MM-DD_HHMM.sql.gz  (se borran las de más de 30 días)
# Para hacerla cada noche a las 3:00, en «crontab -e»:
#   0 3 * * * cd /ruta/a/CRM_CBelleza && sh docker/backup-db.sh
# Restaurar (con la app parada: docker compose stop app):
#   gunzip -c backups/cuquora-....sql.gz | docker exec -i crm_cbelleza_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
# Guarda también alguna copia fuera del servidor (si se rompe el disco, se pierden las dos).
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/cuquora-$(date +%F_%H%M).sql.gz"
docker exec crm_cbelleza_db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists' | gzip > "$file"
find backups -name 'cuquora-*.sql.gz' -mtime +30 -delete
echo "Copia guardada en $file"
