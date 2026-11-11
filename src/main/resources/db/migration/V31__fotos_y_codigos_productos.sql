-- Fotos y código de barras de los productos del inventario.
--  · products.barcode: EAN/UPC del envase (solo dígitos). Al escanearlo se busca primero en Cuquora y, si no está,
--    en Open Beauty Facts para rellenar nombre, marca, categoría y foto.
--  · products.photo_updated_at: cuándo se puso la foto actual (nulo = sin foto); versiona la URL de la imagen.
--  · product_photos: la imagen ya reducida (JPEG de 800 px por el lado largo), aparte para no leerla al listar.

ALTER TABLE products ADD COLUMN barcode VARCHAR(32);
ALTER TABLE products ADD COLUMN photo_updated_at TIMESTAMP(6);

CREATE INDEX idx_products_barcode ON products (barcode);

CREATE TABLE product_photos (
    product_id    BIGINT       NOT NULL PRIMARY KEY,
    content_type  VARCHAR(32)  NOT NULL,
    data          BYTEA        NOT NULL,
    CONSTRAINT fk_product_photos_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
