-- Elegir profesional al reservar y cambiar la hora desde «Mis reservas».
--  · treatment_workers: qué profesionales hacen cada tratamiento. Un tratamiento sin filas lo hace todo el equipo
--    (así siguen todos los que ya existían).
--  · centers.client_reschedule_hours: hasta cuántas horas antes de la cita puede el cliente cambiarla de hora
--    él mismo. 0 = no puede (tiene que llamar al centro). Lo decide el dueño en «Mi Centro» > Reservas de clientes.

CREATE TABLE treatment_workers (
    treatment_id BIGINT NOT NULL,
    worker_id    BIGINT NOT NULL,
    CONSTRAINT pk_treatment_workers PRIMARY KEY (treatment_id, worker_id),
    CONSTRAINT fk_treatment_workers_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (id) ON DELETE CASCADE,
    CONSTRAINT fk_treatment_workers_worker FOREIGN KEY (worker_id) REFERENCES users (id) ON DELETE CASCADE
);

ALTER TABLE centers ADD COLUMN client_reschedule_hours INT DEFAULT 24 NOT NULL;
ALTER TABLE centers ADD CONSTRAINT ck_centers_client_reschedule_hours CHECK (client_reschedule_hours >= 0);
