-- Redes sociales del centro (las rellena el dueño en Ajustes > Centro y salen en la ficha del centro)
ALTER TABLE centers ADD COLUMN instagram VARCHAR(30);
ALTER TABLE centers ADD COLUMN tiktok VARCHAR(24);
ALTER TABLE centers ADD COLUMN facebook VARCHAR(80);
ALTER TABLE centers ADD COLUMN website VARCHAR(300);
