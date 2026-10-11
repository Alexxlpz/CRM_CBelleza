-- Zona horaria de cada centro (identificador IANA, p. ej. Europe/Madrid o America/Santiago).
-- La usan los eventos de Google Calendar para que las citas salgan a la hora local del centro.
-- Los centros existentes quedan en la hora peninsular española.

ALTER TABLE centers ADD COLUMN time_zone VARCHAR(64) DEFAULT 'Europe/Madrid' NOT NULL;
