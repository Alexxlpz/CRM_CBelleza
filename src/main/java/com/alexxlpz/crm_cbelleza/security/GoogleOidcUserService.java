package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Al volver de Google, busca o crea la cuenta de Cuquora correspondiente (ver {@link UserService#signInWithGoogle}).
 * Si no se puede, el login falla con el mensaje para el usuario, que muestra {@link GoogleLoginFailureHandler}.
 */
@Service
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    /** Código de error propio: su descripción es un mensaje en español que se puede enseñar tal cual. */
    static final String ACCOUNT_ERROR = "cuquora_account";

    private final OidcUserService googleUsers = new OidcUserService();
    private final UserService userService;

    public GoogleOidcUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser googleUser = googleUsers.loadUser(request);
        try {
            User user = userService.signInWithGoogle(
                    googleUser.getSubject(),
                    googleUser.getEmail(),
                    Boolean.TRUE.equals(googleUser.getEmailVerified()),
                    googleUser.getFullName());
            return new GoogleOidcUser(googleUser, AppUserDetails.from(user));
        } catch (BusinessRuleException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ACCOUNT_ERROR, e.getMessage(), null), e.getMessage(), e);
        }
    }
}
