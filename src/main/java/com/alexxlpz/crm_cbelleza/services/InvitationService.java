package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.AccountInvitation;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
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
 * <p>
 * También las invitaciones a unirse a un equipo ({@link #inviteToTeam}): quien ya tenía cuenta de trabajador y dejó
 * otro centro no entra en un equipo nuevo hasta que lo acepta desde el enlace de su correo. Así ningún dueño puede
 * meter a alguien en su centro (y ver su agenda a su nombre) sin que esa persona quiera.
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
        return create(user, null);
    }

    /**
     * Invitación a unirse al equipo de {@code center} para alguien que ya tiene cuenta de trabajador sin centro.
     * Entra en el equipo al aceptarla ({@link #join}, o al crear su contraseña si aún no tenía).
     */
    public String inviteToTeam(User user, Center center) {
        return create(user, center);
    }

    private String create(User user, Center center) {
        String token = SecureTokens.newToken();
        LocalDateTime now = LocalDateTime.now(clock);
        invitations.save(AccountInvitation.builder()
                .user(user)
                .tokenHash(SecureTokens.hash(token))
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofDays(VALIDITY_DAYS)))
                .center(center)
                .build());
        return token;
    }

    /** Lo que pinta la página de la invitación. */
    @Transactional(readOnly = true)
    public InvitationView view(String token) {
        AccountInvitation invitation = find(token);
        User user = invitation.getUser();
        return new InvitationView(user.getName(), user.getEmail(), centerNameOf(invitation), stateOf(invitation),
                invitation.isTeamInvitation(), user.hasAccount());
    }

    /** Caduca los enlaces que aún valían y crea otro (el último correo es el único que sirve). */
    public String reinvite(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        invitations.findByUserIdAndUsedAtIsNull(user.getId()).forEach(invitation -> {
            // Las invitaciones a un equipo siguen valiendo: son otra cosa
            if (!invitation.isTeamInvitation() && invitation.getExpiresAt().isAfter(now)) {
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
        if (invitation.isTeamInvitation()) {
            joinTeam(user, invitation.getCenter());
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        invitation.setUsedAt(LocalDateTime.now(clock));
        return users.save(user);
    }

    /**
     * Acepta la invitación a unirse a un equipo de quien ya tiene cuenta: desde ahora entra en ese centro con su correo
     * y su contraseña (o Google).
     */
    public Joined join(String token) {
        AccountInvitation invitation = find(token);
        if (!invitation.isTeamInvitation()) {
            throw new BusinessRuleException("Este enlace no es una invitación a un equipo.");
        }
        switch (stateOf(invitation)) {
            case USED -> throw new BusinessRuleException("Ya has aceptado esta invitación. Inicia sesión con tu correo.");
            case EXPIRED -> throw new BusinessRuleException("La invitación ha caducado. Pide una nueva desde esta misma página.");
            case VALID -> { }
        }
        User user = invitation.getUser();
        if (!user.hasAccount()) {
            throw new BusinessRuleException("Primero crea tu contraseña con el formulario de esta página.");
        }
        Center center = invitation.getCenter();
        joinTeam(user, center);
        invitation.setUsedAt(LocalDateTime.now(clock));
        users.save(user);
        return new Joined(user.getId(), user.getEmail(), center.getName());
    }

    /** Solo quien sigue sin centro, y si al equipo le queda sitio (puede haber pasado tiempo desde la invitación). */
    private void joinTeam(User user, Center center) {
        if (user.getRole() != Role.WORKER) {
            throw new BusinessRuleException("Esta cuenta no es de trabajador: no puede unirse a un equipo.");
        }
        if (user.getCenter() != null) {
            if (user.getCenter().getId().equals(center.getId())) {
                throw new BusinessRuleException("Ya formas parte del equipo de " + center.getName() + ".");
            }
            throw new BusinessRuleException("Ya formas parte del equipo de " + user.getCenter().getName()
                    + ". Para unirte a " + center.getName() + ", antes tienen que sacarte de ese equipo.");
        }
        if (users.countByCenterIdAndRole(center.getId(), Role.WORKER) >= TeamService.MAX_TEAM_SIZE) {
            throw new BusinessRuleException("El equipo de " + center.getName() + " ya está completo. Habla con el dueño del centro.");
        }
        user.setCenter(center);
    }

    /** Un enlace caducado se cambia por otro nuevo para el mismo trabajador (y el mismo equipo, si era a un equipo). */
    public Renewal renew(String token) {
        AccountInvitation invitation = find(token);
        User user = invitation.getUser();
        if (invitation.isTeamInvitation()) {
            if (invitation.getUsedAt() != null || user.getCenter() != null) {
                throw new BusinessRuleException("Ya formas parte de un equipo. Inicia sesión con tu correo.");
            }
        } else if (invitation.getUsedAt() != null || user.getPassword() != null) {
            throw new BusinessRuleException("Ya tienes contraseña. Inicia sesión con tu correo.");
        }
        invitation.setExpiresAt(LocalDateTime.now(clock));
        return new Renewal(user.getName(), user.getEmail(), centerNameOf(invitation),
                create(user, invitation.getCenter()), invitation.isTeamInvitation());
    }

    private static String centerNameOf(AccountInvitation invitation) {
        if (invitation.isTeamInvitation()) {
            return invitation.getCenter().getName();
        }
        User user = invitation.getUser();
        return user.getCenter() != null ? user.getCenter().getName() : null;
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

    /**
     * teamInvitation: invitación a unirse a un equipo (centerName es ese equipo); hasAccount: ya tiene contraseña o
     * Google, así que solo tiene que aceptar.
     */
    public record InvitationView(String name, String email, String centerName, State state,
                                 boolean teamInvitation, boolean hasAccount) {
    }

    /** Quién se ha unido y a qué equipo. */
    public record Joined(Long userId, String email, String centerName) {
    }

    /** Datos para enviar el enlace nuevo por correo. */
    public record Renewal(String name, String email, String centerName, String token, boolean teamInvitation) {
    }
}
