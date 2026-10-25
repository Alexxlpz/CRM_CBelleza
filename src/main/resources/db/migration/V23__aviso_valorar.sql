-- Cuándo se le propuso al cliente valorar la cita (el aviso «¿Qué tal tu cita?» que sale una sola vez al entrar).
--  · Nulo: todavía no se le ha enseñado. La campana sigue recordándolo aunque ya se haya enseñado.

ALTER TABLE appointments ADD COLUMN review_prompted_at TIMESTAMP;
