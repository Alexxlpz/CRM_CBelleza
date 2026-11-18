-- Editar la carta y el inventario.
--  · treatments.hidden: el dueño oculta un tratamiento de la carta sin borrarlo (sus citas siguen en el historial
--    y en el análisis). Oculto no sale al cliente ni al crear citas en la agenda, y se puede volver a mostrar.
--  · appointments.duration_minutes: duración de cada cita, como ya se hace con el precio. Así cambiar la duración
--    de un tratamiento solo vale para las citas nuevas. Las de antes se rellenan con la duración de hoy.
--  · inventories.low_stock_alert: unidades a partir de las que avisa «Stock bajo» en ese producto; nulo = no avisa.

ALTER TABLE treatments ADD COLUMN hidden BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE appointments ADD COLUMN duration_minutes INTEGER;
UPDATE appointments SET duration_minutes = (SELECT t.duration FROM treatments t WHERE t.id = appointments.treatment_id);

ALTER TABLE inventories ADD COLUMN low_stock_alert INTEGER;
