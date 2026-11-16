-- Unirse a un equipo solo si la persona acepta, y enlace de «Publicar centro» con caducidad.
--  · account_invitations.center_id: centro al que se invita a alguien que ya tenía cuenta de trabajador sin centro
--    (dejó otro equipo). No entra en el equipo hasta que pulsa «Unirme» en el enlace de su correo. Nulo en las
--    invitaciones de siempre (crear la contraseña de quien ya está en el equipo).
--  · centers.approval_requested_at: cuándo se envió a administración el enlace para publicar el centro. Caduca a los
--    30 días; nulo en las altas de antes, que cuentan desde registered_at.

ALTER TABLE account_invitations ADD COLUMN center_id BIGINT;
ALTER TABLE account_invitations ADD CONSTRAINT fk_account_invitations_center
    FOREIGN KEY (center_id) REFERENCES centers (id) ON DELETE CASCADE;

ALTER TABLE centers ADD COLUMN approval_requested_at TIMESTAMP(6);
