-- Google Calendar también para clientes: la conexión deja de ser solo de trabajadores
-- (worker_id pasa a user_id) y cada cita apunta aparte el evento del calendario del cliente.

ALTER TABLE google_calendar_links RENAME COLUMN worker_id TO user_id;

ALTER TABLE appointments ADD COLUMN client_google_event_id VARCHAR(255);
