package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.AccountInvitation;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.repositories.AccountInvitationRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Invitaciones para que los trabajadores dados de alta con su centro pongan su contraseña (y para que un cliente
 * que un centro dio de alta a mano active su cuenta desde su correo).
 * Cada enlace sirve una vez y caduca a los {@link #VALIDITY_DAYS} días; si caduca, la propia página
 * envía uno nuevo al correo del trabajador (nunca a otro).
 */
@Service
@Transactional
public class InvitationService {

    public static final int VALIDITY_DAYS = 7;

    private final AccountInvitationRepository invitations;
    private final UserRepository users;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public InvitationService(AccountInvitationRepository invitations, UserRepository users,
                             PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, Clock clock) {
        this.invitations = invitations;
        this.users = users;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Crea una invitación y devuelve el enlace secreto (solo se guarda su huella). */
    public String invite(User user) {
        String token = SecureTokens.newToken();
        LocalDateTime now = LocalDateTime.now(clock);
        invitations.save(AccountInvitation.builder()
                .user(user)
                .tokenHash(SecureTokens.hash(token))
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofDays(VALIDITY_DAYS)))
                .build());
        return token;
    }

    /** Lo que pinta la página de la invitación. */
    @Transactional(readOnly = true)
    public InvitationView view(String token) {
        AccountInvitation invitation = find(token);
        User user = invitation.getUser();
        return new InvitationView(user.getName(), user.getEmail(),
                user.getCenter() != null ? user.getCenter().getName() : null, stateOf(invitation));
    }

    /** Caduca los enlaces que aún valían y crea otro (el último correo es el único que sirve). */
    public String reinvite(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        invitations.findByUserIdAndUsedAtIsNull(user.getId()).forEach(invitation -> {
            if (invitation.getExpiresAt().isAfter(now)) {
                invitation.setExpiresAt(now);
            }
        });
        return invite(user);
    }

    /** Pone la contraseña (trabajador o cliente que activa su cuenta) y gasta el enlace: el correo queda verificado. */
    public User accept(String token, String password, String confirmation) {
        AccountInvitation invitation = find(token);
        switch (stateOf(invitation)) {
            case USED -> throw new BusinessRuleException("Este enlace ya se ha usado. Inicia sesión con tu correo y tu contraseña.");
            case EXPIRED -> throw new BusinessRuleException("El enlace ha caducado. Pide uno nuevo desde esta misma página.");
            case VALID -> { }
        }
        passwordPolicy.validateRequired(password, confirmation);
        User user = invitation.getUser();
        user.setPassword(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        invitation.setUsedAt(LocalDateTime.now(clock));
        return users.save(user);
    }

    /** Un enlace caducado se cambia por otro nuevo para el mismo trabajador. */
    public Renewal renew(String token) {
        AccountInvitation invitation = find(token);
        if (invitation.getUsedAt() != null || invitation.getUser().getPassword() != null) {
            throw new BusinessRuleException("Ya tienes contraseña. Inicia sesión con tu correo.");
        }
        invitation.setExpiresAt(LocalDateTime.now(clock));
        User user = invitation.getUser();
        return new Renewal(user.getName(), user.getEmail(),
                user.getCenter() != null ? user.getCenter().getName() : null, invite(user));
    }

    private AccountInvitation find(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("La invitación no existe.");
        }
        return invitations.findByTokenHash(SecureTokens.hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("La invitación no existe."));
    }

    private State stateOf(AccountInvitation invitation) {
        if (invitation.getUsedAt() != null) return State.USED;
        return invitation.isUsable(LocalDateTime.now(clock)) ? State.VALID : State.EXPIRED;
    }

    public enum State { VALID, USED, EXPIRED }

    public record InvitationView(String name, String email, String centerName, State state) {
    }

    /** Datos para enviar el enlace nuevo por correo. */
    public record Renewal(String name, String email, String centerName, String token) {
    }
}
