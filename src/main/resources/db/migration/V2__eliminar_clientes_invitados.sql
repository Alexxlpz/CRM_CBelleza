-- Las reservas ya no admiten clientes invitados (sin cuenta).
-- Elimina las citas y fichas antiguas que no tienen cliente asociado.
-- Antes lo hacía DatabaseSeeder en cada arranque.

DELETE FROM client_cards WHERE client_id IS NULL;
DELETE FROM appointments WHERE client_id IS NULL;
