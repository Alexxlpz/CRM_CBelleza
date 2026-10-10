package com.alexxlpz.crm_cbelleza.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Añade a todas las vistas la dirección pública del sitio (p. ej. https://cuquora.com), que usan las
 * etiquetas Open Graph del &lt;head&gt;: las redes sociales solo aceptan la imagen con URL absoluta.
 */
@ControllerAdvice(annotations = Controller.class)
public class SiteModelAdvice {

    private final String configuredSiteUrl;

    public SiteModelAdvice(@Value("${crm.site-url:}") String configuredSiteUrl) {
        this.configuredSiteUrl = configuredSiteUrl.strip().replaceAll("/+$", "");
    }

    /** La de SITE_URL si está configurada; si no, la de la petición actual. */
    @ModelAttribute("siteUrl")
    public String siteUrl() {
        if (!configuredSiteUrl.isEmpty()) {
            return configuredSiteUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
