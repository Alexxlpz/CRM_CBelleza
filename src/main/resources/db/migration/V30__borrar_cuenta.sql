-- «Borrar mi cuenta» (RGPD): la cuenta del cliente no se borra de la tabla, se vacía. Se quitan el nombre, el correo,
-- el teléfono y las credenciales, y se apunta cuándo. Sus citas pasadas quedan sin datos personales para que el
-- centro no pierda sus números; lo personal que tenía el centro (ficha, resúmenes, fotos y puntos) se borra.

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
