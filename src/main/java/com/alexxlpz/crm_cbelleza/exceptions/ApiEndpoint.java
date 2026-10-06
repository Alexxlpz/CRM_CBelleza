package com.alexxlpz.crm_cbelleza.exceptions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marca los controladores que responden JSON para que sus errores se devuelvan también como JSON. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiEndpoint {
}
