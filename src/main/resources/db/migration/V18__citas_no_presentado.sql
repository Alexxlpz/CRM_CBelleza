-- Nuevo estado de cita NO_SHOW: el cliente no se presentó. Lo marca el centro en una cita que ya ha empezado;
-- no suma puntos ni cuenta como visita, y la ficha del cliente muestra cuántas veces ha pasado.

ALTER TABLE appointments DROP CONSTRAINT ck_appointments_status;
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_status
    CHECK (status IN ('PENDING', 'PROPOSED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));
