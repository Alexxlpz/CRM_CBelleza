-- Productos por código de barras (GTIN: EAN-8, EAN-13 o GTIN-14, solo dígitos).
--  · source: de dónde salió el producto: CUQUORA (lo dio de alta un centro) u OPEN_BEAUTY_FACTS.
--  · No se guarda nunca el precio, el stock ni la descripción de un centro: son suyos.
--  · product_images: la foto del envase, aparte para no leerla al consultar los datos.

CREATE TABLE products (
    gtin              VARCHAR(14)   NOT NULL PRIMARY KEY,
    name              VARCHAR(200)  NOT NULL,
    brand             VARCHAR(100),
    category          VARCHAR(40),
    quantity          VARCHAR(40),
    source            VARCHAR(20)   NOT NULL,
    created_at        TIMESTAMP(6)  NOT NULL,
    updated_at        TIMESTAMP(6)  NOT NULL,
    image_updated_at  TIMESTAMP(6)
);

CREATE TABLE product_images (
    gtin          VARCHAR(14)  NOT NULL PRIMARY KEY,
    content_type  VARCHAR(32)  NOT NULL,
    data          BYTEA        NOT NULL,
    CONSTRAINT fk_product_images_product FOREIGN KEY (gtin) REFERENCES products (gtin) ON DELETE CASCADE
);
