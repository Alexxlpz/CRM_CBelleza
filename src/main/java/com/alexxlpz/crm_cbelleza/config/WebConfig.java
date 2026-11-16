package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.security.CenterMembershipInterceptor;
import com.alexxlpz.crm_cbelleza.security.CenterOwnerInterceptor;
import com.alexxlpz.crm_cbelleza.security.DeletedAccountInterceptor;
import com.alexxlpz.crm_cbelleza.security.PasswordChangeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Comprobaciones en cada petición: que la sesión no se inició antes del último cambio de contraseña; en el panel de
 * trabajadores, que quien entra sigue en el equipo del centro y que las acciones reservadas al dueño las pide el dueño;
 * en el área de cliente, que la cuenta no se ha borrado.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CenterMembershipInterceptor membershipInterceptor;
    private final CenterOwnerInterceptor ownerInterceptor;
    private final DeletedAccountInterceptor deletedAccountInterceptor;
    private final PasswordChangeInterceptor passwordChangeInterceptor;

    public WebConfig(CenterMembershipInterceptor membershipInterceptor, CenterOwnerInterceptor ownerInterceptor,
                     DeletedAccountInterceptor deletedAccountInterceptor, PasswordChangeInterceptor passwordChangeInterceptor) {
        this.membershipInterceptor = membershipInterceptor;
        this.ownerInterceptor = ownerInterceptor;
        this.deletedAccountInterceptor = deletedAccountInterceptor;
        this.passwordChangeInterceptor = passwordChangeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Antes que nada: una sesión iniciada antes de cambiar la contraseña ya no vale en ninguna pantalla
        registry.addInterceptor(passwordChangeInterceptor).addPathPatterns("/**");
        registry.addInterceptor(membershipInterceptor).addPathPatterns("/worker/**");
        registry.addInterceptor(ownerInterceptor).addPathPatterns("/worker/**");
        registry.addInterceptor(deletedAccountInterceptor).addPathPatterns("/client/**");
    }
}
