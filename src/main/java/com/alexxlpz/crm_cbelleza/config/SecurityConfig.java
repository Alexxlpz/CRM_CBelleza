package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.AppUserDetailsService;
import com.alexxlpz.crm_cbelleza.security.GoogleAuthorizationRequestResolver;
import com.alexxlpz.crm_cbelleza.security.GoogleLoginFailureHandler;
import com.alexxlpz.crm_cbelleza.security.GoogleOidcUserService;
import com.alexxlpz.crm_cbelleza.security.JdbcRememberMeTokenRepository;
import com.alexxlpz.crm_cbelleza.security.LoginFailureHandler;
import com.alexxlpz.crm_cbelleza.security.RememberMe;
import com.alexxlpz.crm_cbelleza.security.RoleAwareAccessDeniedHandler;
import com.alexxlpz.crm_cbelleza.security.RoleBasedAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.RequestPath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Seguridad de la aplicación:
 * <ul>
 *     <li>Formulario de login propio en /login (correo o usuario + contraseña).</li>
 *     <li>Acceso con Google (OpenID Connect) si están configurados GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET.</li>
 *     <li>/client/** solo para clientes y /worker/** solo para trabajadores con centro asignado.</li>
 *     <li>Protección CSRF activa en todos los formularios y peticiones fetch.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PAGES = {
            "/", "/home", "/features", "/funcionalidades", "/centers",
            "/register-center", "/alta-centro", "/contact", "/contacto",
            "/terms", "/terminos", "/cookies", "/politica-cookies", "/privacy", "/privacidad",
            "/login", "/register", "/error",
            // Enlaces de los correos del alta de centros: el secreto va en la propia ruta
            "/register-center/approve/*", "/invitation/*", "/invitation/*/renew",
            // Solo existen con el perfil "dev" (DevLoginController); en producción devuelven 404.
            "/login-selector", "/select-session"
    };

    private static final String[] STATIC_RESOURCES = {
            "/css/**", "/js/**", "/images/**", "/icons/**", "/favicon.ico", "/robots.txt"
    };

    /** Firma los datos de «Recordar mi sesión»; sin REMEMBER_ME_KEY es aleatoria y los dispositivos recordados caducan al reiniciar. */
    private final String rememberMeKey;

    public SecurityConfig(@Value("${crm.security.remember-me-key:}") String rememberMeKey) {
        this.rememberMeKey = rememberMeKey.isBlank() ? UUID.randomUUID().toString() : rememberMeKey;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Dispositivos recordados (tabla persistent_logins, migración V4). */
    @Bean
    public PersistentTokenRepository rememberMeTokens(DataSource dataSource) {
        return new JdbcRememberMeTokenRepository(new JdbcTemplate(dataSource));
    }

    /**
     * «Recordar mi sesión» 14 días con tokens guardados en la base de datos: no dependen de la contraseña
     * (sirven para cuentas solo de Google), se renuevan en cada uso y se borran al cerrar sesión.
     */
    @Bean
    public PersistentTokenBasedRememberMeServices rememberMeServices(AppUserDetailsService userDetailsService,
                                                                     PersistentTokenRepository rememberMeTokens) {
        var services = new PersistentTokenBasedRememberMeServices(rememberMeKey, userDetailsService, rememberMeTokens);
        services.setParameter(RememberMe.PARAMETER);
        services.setTokenValiditySeconds(RememberMe.VALIDITY_SECONDS);
        return services;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RoleBasedAuthenticationSuccessHandler successHandler,
                                                   LoginFailureHandler failureHandler,
                                                   RoleAwareAccessDeniedHandler accessDeniedHandler,
                                                   GoogleLoginSettings google,
                                                   GoogleOidcUserService googleUserService,
                                                   GoogleLoginFailureHandler googleFailureHandler,
                                                   PersistentTokenBasedRememberMeServices rememberMeServices,
                                                   @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled,
                                                   ApplicationContext context)
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
            // Una dirección que no existe responde con la página 404 también sin sesión, en vez de mandar al login
            auth.requestMatchers(unmappedRoute(context)).permitAll();
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

        // Sin credenciales de Google no se registra nada: /oauth2/** y /login/oauth2/** responden 404
        if (google.isEnabled()) {
            var registrations = new InMemoryClientRegistrationRepository(google.registration());
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .clientRegistrationRepository(registrations)
                    .authorizationEndpoint(authorization -> authorization
                            .authorizationRequestResolver(new GoogleAuthorizationRequestResolver(registrations)))
                    // Los tokens de Google no se usan después: se guardan en la sesión y desaparecen con ella
                    .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleUserService))
                    .successHandler(successHandler)
                    .failureHandler(googleFailureHandler));
        }

        http.rememberMe(remember -> remember
                .rememberMeServices(rememberMeServices)
                .key(rememberMeKey));

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

    /**
     * Peticiones que no atiende ningún controlador (p. ej. una URL mal escrita o un enlace antiguo).
     * Se dejan pasar para que respondan 404 con la página propia; las rutas que sí existen siguen
     * exigiendo sesión, así que un controlador nuevo nunca queda público por error.
     */
    private static RequestMatcher unmappedRoute(ApplicationContext context) {
        return request -> {
            HandlerMapping controllers = context.getBean("requestMappingHandlerMapping", HandlerMapping.class);
            // El DispatcherServlet aún no ha analizado la ruta (estamos en el filtro de seguridad)
            RequestPath previous = ServletRequestPathUtils.hasParsedRequestPath(request)
                    ? ServletRequestPathUtils.getParsedRequestPath(request) : null;
            ServletRequestPathUtils.parseAndCache(request);
            try {
                return controllers.getHandler(request) == null;
            } catch (Exception e) {
                // La ruta existe con otro método o formato: decide la regla general (iniciar sesión)
                return false;
            } finally {
                ServletRequestPathUtils.setParsedRequestPath(previous, request);
            }
        };
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
