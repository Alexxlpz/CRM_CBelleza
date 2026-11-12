-- De dónde salió la foto de un producto, para dar el crédito que pide su licencia.
--  · products.photo_source: nula = la subió el centro; 'OPEN_BEAUTY_FACTS' = la foto encontrada al escanear venía de
--    Open Beauty Facts (licencia CC BY-SA: se muestra «Foto: Open Beauty Facts» en la ficha). Las fotos de
--    Open Beauty Facts tampoco se comparten con la API de productos como si fueran de un centro.

ALTER TABLE products ADD COLUMN photo_source VARCHAR(30);
