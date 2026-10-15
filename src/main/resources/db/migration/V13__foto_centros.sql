-- Foto de presentación de cada centro (alta de centro y «Mi Centro»), que sale en el buscador y en la ficha del centro.
--  · centers.photo_updated_at: cuándo se subió la foto actual (nulo = sin foto). Va en la URL de la imagen para
--    que el navegador la guarde en caché y pida la nueva al cambiarla, sin leer la imagen al listar centros.
--  · center_photos: la imagen ya redimensionada (JPEG de 1200 px como mucho, ~150 KB). Se guarda en la base de
--    datos y no en disco para que entre en las copias de PostgreSQL y Docker no necesite otro volumen.

ALTER TABLE centers ADD COLUMN photo_updated_at TIMESTAMP(6);

CREATE TABLE center_photos (
    center_id     BIGINT       NOT NULL PRIMARY KEY,
    content_type  VARCHAR(32)  NOT NULL,
    data          BYTEA        NOT NULL,
    CONSTRAINT fk_center_photos_center FOREIGN KEY (center_id) REFERENCES centers (id) ON DELETE CASCADE
);
