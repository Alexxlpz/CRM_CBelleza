-- El cliente ha pulsado «No mostrar más» en el aviso «¿Qué tal tu cita?»: no se le vuelve a enseñar para ninguna cita.
--  · La campana sigue recordando las citas sin valorar (eso se quita con su «x»).

ALTER TABLE users ADD COLUMN review_prompts_off BOOLEAN DEFAULT FALSE NOT NULL;
