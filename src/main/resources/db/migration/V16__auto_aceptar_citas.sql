-- Aceptar automáticamente las citas que piden los clientes («Mi Centro» > Reservas de clientes).
--  · centers.auto_accept_bookings: si está activo, una solicitud en un hueco libre queda confirmada al momento
--    con el profesional libre que menos citas tiene ese día. Desactivado en todos los centros hasta que un
--    trabajador lo active.

ALTER TABLE centers ADD COLUMN auto_accept_bookings BOOLEAN DEFAULT FALSE NOT NULL;
