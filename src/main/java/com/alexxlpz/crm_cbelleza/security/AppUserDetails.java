package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Usuario autenticado que Spring Security guarda en la sesión.
 * Es inmutable: cuando cambian el nombre o el centro se sustituye por una
 * instancia nueva mediante {@link AuthenticationSessionService#refresh}.
 */
public final class AppUserDetails implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final Long centerId;
    private final String centerName;

    private AppUserDetails(Long id, String name, String username, String passwordHash,
                           Role role, Long centerId, String centerName) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.centerId = centerId;
        this.centerName = centerName;
    }

    public static AppUserDetails from(User user) {
        Center center = user.getCenter();
        String username = user.getEmail() != null ? user.getEmail() : user.getName();
        return new AppUserDetails(
                user.getId(),
                user.getName(),
                username,
                user.getPassword(),
                user.getRole(),
                center != null ? center.getId() : null,
                center != null ? center.getName() : null);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public Long getCenterId() { return centerId; }
    public String getCenterName() { return centerName; }

    public boolean isWorker() { return role == Role.WORKER; }
    public boolean isClient() { return role == Role.CLIENT; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isEnabled() {
        // Las cuentas creadas por un trabajador desde la cartera no tienen contraseña: no pueden iniciar sesión.
        return passwordHash != null && !passwordHash.isBlank();
    }
}
