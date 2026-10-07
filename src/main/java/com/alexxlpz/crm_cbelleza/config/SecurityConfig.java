package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AppUserDetailsService;
import com.alexxlpz.crm_cbelleza.security.LoginFailureHandler;
import com.alexxlpz.crm_cbelleza.security.RoleAwareAccessDeniedHandler;
import com.alexxlpz.crm_cbelleza.security.RoleBasedAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.UUID;

/**
 * Seguridad de la aplicación:
 * <ul>
 *     <li>Formulario de login propio en /login (correo o usuario + contraseña).</li>
 *     <li>/client/** solo para clientes y /worker/** solo para trabajadores con centro asignado.</li>
 *     <li>Protección CSRF activa en todos los formularios y peticiones fetch.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PAGES = {
            "/", "/home", "/features", "/funcionalidades", "/centers",
            "/register-center", "/alta-centro", "/contact", "/contacto",
            "/login", "/register", "/error",
            // Solo existen con el perfil "dev" (DevLoginController); en producción devuelven 404.
            "/login-selector", "/select-session"
    };

    private static final String[] STATIC_RESOURCES = {
            "/css/**", "/js/**", "/images/**", "/icons/**", "/favicon.ico"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppUserDetailsService userDetailsService,
                                                   RoleBasedAuthenticationSuccessHandler successHandler,
                                                   LoginFailureHandler failureHandler,
                                                   RoleAwareAccessDeniedHandler accessDeniedHandler,
                                                   @Value("${crm.security.remember-me-key:}") String rememberMeKey,
                                                   @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled)
            throws Exception {

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(STATIC_RESOURCES).permitAll();
            auth.requestMatchers(PUBLIC_PAGES).permitAll();
            auth.requestMatchers("/api/centers/**").permitAll();
            if (h2ConsoleEnabled) {
                auth.requestMatchers("/h2-console/**").permitAll();
            }
            auth.requestMatchers("/client/**").hasRole("CLIENT");
            auth.requestMatchers("/worker/**").access(workerWithCenter());
            auth.anyRequest().authenticated();
        });

        http.formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("identifier")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll());

        http.rememberMe(remember -> remember
                .rememberMeParameter("rememberMe")
                .key(rememberMeKey.isBlank() ? UUID.randomUUID().toString() : rememberMeKey)
                .userDetailsService(userDetailsService)
                .tokenValiditySeconds(14 * 24 * 60 * 60));

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll());

        http.exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler));

        // Tras iniciar sesión se vuelve a la página protegida que se pidió (p. ej. el centro elegido),
        // sin añadir el parámetro "?continue" a la URL.
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        http.requestCache(cache -> cache.requestCache(requestCache));

        if (h2ConsoleEnabled) {
            http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
            http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        }

        return http.build();
    }

    /** Un trabajador solo puede usar el panel si tiene un centro asignado. */
    private static AuthorizationManager<RequestAuthorizationContext> workerWithCenter() {
        return (authentication, context) -> {
            var auth = authentication.get();
            boolean granted = auth != null
                    && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof AppUserDetails user
                    && user.isWorker()
                    && user.getCenterId() != null;
            return new AuthorizationDecision(granted);
        };
    }
}
