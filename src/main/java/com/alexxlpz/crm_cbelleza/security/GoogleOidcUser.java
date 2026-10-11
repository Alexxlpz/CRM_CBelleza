package com.alexxlpz.crm_cbelleza.security;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;

/**
 * Usuario de Google ya enlazado con su cuenta de Cuquora. Solo vive entre el regreso de Google y
 * {@link RoleBasedAuthenticationSuccessHandler}, que lo sustituye en la sesión por {@link AppUserDetails}
 * para que el resto de la aplicación trate igual a quien entra con Google y con contraseña.
 */
public class GoogleOidcUser extends DefaultOidcUser {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AppUserDetails account;

    public GoogleOidcUser(OidcUser googleUser, AppUserDetails account) {
        super(account.getAuthorities(), googleUser.getIdToken(), googleUser.getUserInfo());
        this.account = account;
    }

    public AppUserDetails getAccount() {
        return account;
    }
}
