package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.entities.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Inicia sesión de forma programática (tras registrarse) y actualiza los datos del usuario
 * guardados en la sesión cuando cambian (nombre, centro).
 */
@Service
public class AuthenticationSessionService {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public void login(User user, HttpServletRequest request, HttpServletResponse response) {
        // Evita la fijación de sesión: se genera un id nuevo al autenticar.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        store(AppUserDetails.from(user), request, response);
    }

    public void refresh(User user, HttpServletRequest request, HttpServletResponse response) {
        store(AppUserDetails.from(user), request, response);
    }

    private void store(AppUserDetails principal, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
