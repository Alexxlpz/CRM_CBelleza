-- 1) Una sola ficha técnica por cliente y centro.
--    Antes, abrir la ficha creaba una vacía y dos aperturas a la vez podían dejar dos filas; desde entonces la ficha
--    de ese cliente daba error. Si hay repetidas se queda la que tiene datos y, entre iguales, la más reciente.
DELETE FROM client_cards c
WHERE c.client_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM client_cards o
    WHERE o.center_id = c.center_id
      AND o.client_id = c.client_id
      AND o.id <> c.id
      AND ((CASE WHEN COALESCE(LENGTH(o.data_json), 0) > 2 THEN 1 ELSE 0 END)
               > (CASE WHEN COALESCE(LENGTH(c.data_json), 0) > 2 THEN 1 ELSE 0 END)
           OR ((CASE WHEN COALESCE(LENGTH(o.data_json), 0) > 2 THEN 1 ELSE 0 END)
                   = (CASE WHEN COALESCE(LENGTH(c.data_json), 0) > 2 THEN 1 ELSE 0 END)
               AND o.id > c.id)));

DROP INDEX IF EXISTS idx_client_cards_center_client;
ALTER TABLE client_cards ADD CONSTRAINT uk_client_cards_center_client UNIQUE (center_id, client_id);

-- 2) Consentimiento explícito de la clienta para guardar su ficha técnica (puede llevar datos de salud, como
--    alergias: art. 9 RGPD). Quién del centro lo marcó y cuándo. Las fichas que ya tienen datos se quedan sin él
--    hasta que alguien del centro lo marque.
ALTER TABLE client_cards ADD COLUMN health_consent_at TIMESTAMP(6);
ALTER TABLE client_cards ADD COLUMN health_consent_by_id BIGINT;
ALTER TABLE client_cards ADD CONSTRAINT fk_client_cards_consent_by FOREIGN KEY (health_consent_by_id) REFERENCES users (id);

-- 3) Contrato de encargo del tratamiento (art. 28 RGPD) aceptado por el centro: versión, cuándo y con qué correo.
--    Sin clave ajena al usuario: el registro tiene que quedar aunque esa cuenta se borre o cambie de centro.
ALTER TABLE centers ADD COLUMN dpa_version VARCHAR(20);
ALTER TABLE centers ADD COLUMN dpa_accepted_at TIMESTAMP(6);
ALTER TABLE centers ADD COLUMN dpa_accepted_by VARCHAR(255);
