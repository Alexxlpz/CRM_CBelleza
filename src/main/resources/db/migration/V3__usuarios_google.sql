-- Iniciar sesión con Google: identificador estable ("sub") de la cuenta de Google vinculada.
-- Nulo en las cuentas que solo usan correo y contraseña.

ALTER TABLE users ADD COLUMN google_id VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);
