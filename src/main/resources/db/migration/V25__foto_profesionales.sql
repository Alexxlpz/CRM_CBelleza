-- Foto de perfil de cada profesional («Mi Centro» > Mi foto), que sale al reservar («¿Con quién?») y en el equipo.
--  · users.photo_updated_at: cuándo se subió la foto actual (nulo = sin foto); versiona la URL de la imagen.
--  · user_photos: la imagen ya recortada en cuadrado (JPEG de 400 px, ~30 KB), como center_photos.

ALTER TABLE users ADD COLUMN photo_updated_at TIMESTAMP(6);

CREATE TABLE user_photos (
    user_id       BIGINT       NOT NULL PRIMARY KEY,
    content_type  VARCHAR(32)  NOT NULL,
    data          BYTEA        NOT NULL,
    CONSTRAINT fk_user_photos_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
