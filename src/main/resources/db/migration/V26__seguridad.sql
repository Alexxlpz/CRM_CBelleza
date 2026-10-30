-- Arreglos del reporte de seguridad del 30 oct 2026.
--  · centers.slug: el enlace corto (/c/<slug>) queda guardado y ya no cambia al renombrar un centro ni al dar de alta
--    otro con el mismo nombre (S-03). Lo rellena CenterService al arrancar para los centros que aún no lo tienen.
--  · products.center_id: un producto creado por un centro solo lo ve ese centro; nulo = catálogo general (S-11).
--  · users.email_verified: si el correo de la cuenta está comprobado (por un enlace de correo o por Google). Las cuentas
--    que ya existían cuentan como comprobadas; las nuevas del registro público, no (S-04).

ALTER TABLE centers ADD COLUMN slug VARCHAR(120);
ALTER TABLE centers ADD CONSTRAINT uk_centers_slug UNIQUE (slug);

ALTER TABLE products ADD COLUMN center_id BIGINT;
ALTER TABLE products ADD CONSTRAINT fk_products_center FOREIGN KEY (center_id) REFERENCES centers (id);

ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT TRUE NOT NULL;
