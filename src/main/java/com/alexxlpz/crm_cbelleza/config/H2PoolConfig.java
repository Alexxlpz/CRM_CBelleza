package com.alexxlpz.crm_cbelleza.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Con H2 en memoria el pool no renueva nunca sus conexiones.
 * <p>
 * H2 deja cada CHECK (estados de cita, roles…) atado a la conexión que lo creó, que es la que usó Flyway.
 * Hikari renueva cada conexión a los 30 min (max-lifetime): al cerrar la de Flyway, todos los UPDATE e INSERT
 * de esas tablas fallaban con «Check constraint invalid» (causa: «La base de datos ha sido cerrada»).
 * Con PostgreSQL no cambia nada.
 */
@Component
public class H2PoolConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource hikari && hikari.getJdbcUrl() != null
                && hikari.getJdbcUrl().startsWith("jdbc:h2:mem:")) {
            hikari.setMaxLifetime(0);
            hikari.setIdleTimeout(0);
        }
        return bean;
    }
}
