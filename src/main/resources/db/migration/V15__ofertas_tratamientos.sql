-- Oferta de un tratamiento: precio rebajado y último día en que se puede reservar con él.
-- Sin fecha de fin, la oferta dura hasta que el centro la quita. Una oferta vencida no se borra:
-- el trabajador la ve como «terminada» y puede renovarla.

ALTER TABLE treatments ADD COLUMN offer_price DOUBLE PRECISION;
ALTER TABLE treatments ADD COLUMN offer_ends_on DATE;
