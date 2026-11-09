package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.PasswordReset;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.Emails;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.repositories.PasswordResetRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.LoginAttemptService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * «¿Olvidaste tu contraseña?»: manda al correo de la cuenta un enlace de un solo uso que caduca a la
 * {@link #VALIDITY hora}. Quien pide el enlace nunca sabe si el correo tiene cuenta (la respuesta es siempre la misma),
 * así no sirve para averiguar quién usa Cuquora. Una cuenta puede pedir como mucho {@link #MAX_REQUESTS_PER_HOUR}
 * enlaces por hora, para que nadie llene de correos el buzón de otra persona.
 */
@Service
@Transactional
public class PasswordResetService {

    public static final Duration VALIDITY = Duration.ofHours(1);
    public static final int MAX_REQUESTS_PER_HOUR = 3;

    private final PasswordResetRepository resets;
    private final UserRepository users;
    private final InvitationService invitations;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final PersistentTokenRepository rememberMeTokens;
    private final LoginAttemptService loginAttempts;
    private final Clock clock;

    public PasswordResetService(PasswordResetRepository resets, UserRepository users, InvitationService invitations,
                                PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
                                PersistentTokenRepository rememberMeTokens, LoginAttemptService loginAttempts, Clock clock) {
        this.resets = resets;
        this.users = users;
        this.invitations = invitations;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.rememberMeTokens = rememberMeTokens;
        this.loginAttempts = loginAttempts;
        this.clock = clock;
    }

    /** Qué correo hay que mandar: un enlace para cambiar la contraseña o, si aún no la ha creado, su invitación. */
    public sealed interface Request {
        String name();

        String email();

        String token();
    }

    /** Cuenta con contraseña (o con Google): enlace para poner una contraseña nueva. */
    public record ResetLink(String name, String email, String token) implements Request {
    }

    /**
     * Trabajador que nunca abrió su invitación, o cliente dado de alta a mano por un centro: no tiene contraseña que
     * recuperar, así que se le reenvía el enlace para crearla (el de siempre, el anterior deja de valer).
     */
    public record Invitation(String name, String email, String centerName, String token) implements Request {
    }

    /**
     * Prepara el correo para ese correo electrónico, si procede. Vacío si no hay cuenta, si el correo es interno
     * o si esa cuenta ya ha pedido demasiados enlaces en la última hora: quien lo pide ve el mismo mensaje en todos los casos.
     */
    public Optional<Request> request(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (FormText.isBlank(email) || !Emails.isValid(email) || email.endsWith(User.PLACEHOLDER_EMAIL_DOMAIN)) {
            return Optional.empty();
        }
        Optional<User> found = users.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (resets.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= MAX_REQUESTS_PER_HOUR) {
            return Optional.empty();
        }
        if (!user.hasAccount()) {
            // También cuenta para el tope: si no, este camino serviría para mandarle invitaciones sin parar
            saveReset(user, now).setUsedAt(now);
            String centerName = user.getCenter() != null ? user.getCenter().getName() : null;
            return Optional.of(new Invitation(user.getName(), user.getEmail(), centerName, invitations.reinvite(user)));
        }
        expirePending(user, now);
        String token = SecureTokens.newToken();
        saveReset(user, now).setTokenHash(SecureTokens.hash(token));
        return Optional.of(new ResetLink(user.getName(), user.getEmail(), token));
    }

    /** Lo que pinta la página del enlace. */
    @Transactional(readOnly = true)
    public ResetView view(String token) {
        PasswordReset reset = find(token);
        return new ResetView(reset.getUser().getEmail(), stateOf(reset));
    }

    /**
     * Pone la contraseña nueva y gasta el enlace. Como el enlace llegó a su correo, el correo queda verificado; se
     * olvidan los dispositivos con «Recordar mi sesión» y se quita el bloqueo por intentos fallidos de la cuenta.
     */
    public User reset(String token, String password, String confirmation) {
        PasswordReset reset = find(token);
        switch (stateOf(reset)) {
            case USED -> throw new BusinessRuleException("Este enlace ya se ha usado. Si no recuerdas la contraseña, pide otro.");
            case EXPIRED -> throw new BusinessRuleException("El enlace ha caducado. Pide otro desde «¿Olvidaste tu contraseña?».");
            case VALID -> { }
        }
        passwordPolicy.validateRequired(password, confirmation);
        User user = reset.getUser();
        LocalDateTime now = LocalDateTime.now(clock);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        reset.setUsedAt(now);
        expirePending(user, now);
        rememberMeTokens.removeUserTokens(AppUserDetails.usernameOf(user));
        loginAttempts.recordSuccess(user.getEmail());
        return users.save(user);
    }

    private PasswordReset saveReset(User user, LocalDateTime now) {
        return resets.save(PasswordReset.builder()
                .user(user)
                // Huella provisional única; quien llama pone la del enlace (o la deja si no hay enlace)
                .tokenHash(SecureTokens.hash(SecureTokens.newToken()))
                .createdAt(now)
                .expiresAt(now.plus(VALIDITY))
                .build());
    }

    /** El último correo es el único que sirve. */
    private void expirePending(User user, LocalDateTime now) {
        resets.findByUserIdAndUsedAtIsNull(user.getId()).forEach(reset -> {
            if (reset.getExpiresAt().isAfter(now)) {
                reset.setExpiresAt(now);
            }
        });
    }

    private PasswordReset find(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("El enlace no existe.");
        }
        return resets.findByTokenHash(SecureTokens.hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("El enlace no existe."));
    }

    private State stateOf(PasswordReset reset) {
        if (reset.getUsedAt() != null) return State.USED;
        return reset.isUsable(LocalDateTime.now(clock)) ? State.VALID : State.EXPIRED;
    }

    public enum State { VALID, USED, EXPIRED }

    public record ResetView(String email, State state) {
    }
}
