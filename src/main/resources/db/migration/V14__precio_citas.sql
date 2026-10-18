-- Precio de cada cita: el de la carta al reservar, y el trabajador lo corrige si cobró otra cosa (descuento, suplemento).
-- Así subir una tarifa no cambia los ingresos ni los puntos de las citas ya hechas.
-- Las citas que ya existían se quedan con el precio actual de su tratamiento (no se guardaba otro).

ALTER TABLE appointments ADD COLUMN price DOUBLE PRECISION;
UPDATE appointments SET price = (SELECT t.price FROM treatments t WHERE t.id = appointments.treatment_id);
ALTER TABLE appointments ALTER COLUMN price SET NOT NULL;
