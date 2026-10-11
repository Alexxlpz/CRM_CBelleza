package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/** Si falla el acceso con Google (cancelado, correo sin verificar…) vuelve al login con un aviso en español. */
@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        FlashMap flashMap = new FlashMap();
        flashMap.put("errorMessage", messageFor(exception));
        flashMap.setTargetRequestPath("/login");
        flashMapManager.saveOutputFlashMap(flashMap, request, response);
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private static String messageFor(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth) {
            String code = oauth.getError().getErrorCode();
            if (GoogleOidcUserService.ACCOUNT_ERROR.equals(code)) {
                return oauth.getError().getDescription();
            }
            if ("access_denied".equals(code)) {
                return "Has cancelado el acceso con Google. Puedes intentarlo de nuevo o entrar con tu correo y contraseña.";
            }
        }
        return "No se ha podido iniciar sesión con Google. Inténtalo de nuevo o entra con tu correo y contraseña.";
    }
}
