package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.security.CenterMembershipInterceptor;
import com.alexxlpz.crm_cbelleza.security.CenterOwnerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Comprobaciones del panel de trabajadores: que quien entra sigue en el equipo del centro y, después,
 * que las acciones reservadas al dueño las pide el dueño.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CenterMembershipInterceptor membershipInterceptor;
    private final CenterOwnerInterceptor ownerInterceptor;

    public WebConfig(CenterMembershipInterceptor membershipInterceptor, CenterOwnerInterceptor ownerInterceptor) {
        this.membershipInterceptor = membershipInterceptor;
        this.ownerInterceptor = ownerInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(membershipInterceptor).addPathPatterns("/worker/**");
        registry.addInterceptor(ownerInterceptor).addPathPatterns("/worker/**");
    }
}
