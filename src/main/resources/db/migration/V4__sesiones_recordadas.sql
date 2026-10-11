-- «Recordar mi sesión» con tokens guardados en la base de datos (PersistentTokenBasedRememberMeServices).
-- Ya no dependen de la contraseña, así que también sirven para las cuentas que solo entran con Google.
-- Cada fila es un dispositivo recordado: se borra al cerrar sesión o al cambiar la contraseña.

CREATE TABLE persistent_logins (
    username  VARCHAR(255) NOT NULL,
    series    VARCHAR(64) PRIMARY KEY,
    token     VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

CREATE INDEX ix_persistent_logins_username ON persistent_logins (username);
