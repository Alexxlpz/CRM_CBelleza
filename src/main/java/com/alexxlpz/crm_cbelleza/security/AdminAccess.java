package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Administración de Cuquora: la cuenta con el correo de {@code CRM_ADMIN_EMAIL}, el mismo que recibe las altas de
 * centros. Revisar, publicar y rechazar un alta exige iniciar sesión con ella: el enlace del correo solo lleva a la
 * página, así que reenviarlo o que lo vea otra persona ya no basta para decidir (seguridad #17).
 *
 * <p>El correo tiene que estar comprobado: el registro público crea cuentas sin comprobar, y si no cualquiera podría
 * registrarse con ese correo antes que administración. Para comprobarlo basta con entrar con Google o usar una vez
 * «¿Olvidaste tu contraseña?».
 */
@Component
public class AdminAccess {

    private final UserRepository users;
    private final String adminEmail;

    public AdminAccess(UserRepository users, @Value("${crm.mail.admin-email}") String adminEmail) {
        this.users = users;
        this.adminEmail = adminEmail == null ? "" : adminEmail.strip();
    }

    public boolean isAdmin(AppUserDetails viewer) {
        if (viewer == null || adminEmail.isEmpty()) {
            return false;
        }
        User user = users.findById(viewer.getId()).orElse(null);
        return user != null && user.isEmailVerified() && user.getDeletedAt() == null
                && adminEmail.equalsIgnoreCase(user.getEmail());
    }

    /** Para cualquier otra cuenta, el enlace no existe (404): no se cuenta que es una página de administración. */
    public void require(AppUserDetails viewer) {
        if (!isAdmin(viewer)) {
            throw new ResourceNotFoundException("El enlace no es válido o el centro ya está publicado.");
        }
    }
}
