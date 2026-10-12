-- Resumen del tratamiento realizado: lo escribe el trabajador cuando la cita ya ha empezado
-- (productos usados, cómo ha reaccionado la piel, qué no le ha sentado bien...).
-- treatment_alert marca el resumen como aviso: se muestra en la ficha del cliente y en sus próximas citas.
-- Es información interna del centro: el cliente no la ve.

ALTER TABLE appointments ADD COLUMN treatment_summary VARCHAR(2000);
ALTER TABLE appointments ADD COLUMN treatment_alert BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE appointments ADD COLUMN summary_updated_at TIMESTAMP(6);
ALTER TABLE appointments ADD COLUMN summary_updated_by_id BIGINT;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_summary_updated_by
    FOREIGN KEY (summary_updated_by_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX ix_appointments_center_alert ON appointments (center_id, treatment_alert);
