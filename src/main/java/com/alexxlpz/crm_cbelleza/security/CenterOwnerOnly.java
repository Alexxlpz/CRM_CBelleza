package com.alexxlpz.crm_cbelleza.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Acción que solo puede hacer el dueño del centro (cambia datos, horario, carta, configuración...).
 * La comprueba {@link CenterOwnerInterceptor}; el resto del equipo recibe un aviso en lugar de la acción.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CenterOwnerOnly {
}
