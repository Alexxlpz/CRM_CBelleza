package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quién manda en cada centro. El dueño (quien lo dio de alta) cambia los datos del centro, su horario, foto,
 * carta, inventario, ficha de clientes, tarjeta de puntos y reservas automáticas, ve el análisis y cierra el
 * centro en la agenda. El resto del equipo son trabajadores estándar: atienden la agenda y a los clientes y
 * gestionan su propia cuenta (contraseña, Google Calendar, sus bloqueos de agenda).
 */
@Service
@Transactional
public class CenterAccess {

    /** Aviso para un trabajador estándar que intenta una acción del dueño. */
    public static final String OWNER_ONLY_MESSAGE = "Solo el dueño del centro puede hacer este cambio. Pídeselo a %s.";

    private final CenterRepository centers;
    private final UserRepository users;

    public CenterAccess(CenterRepository centers, UserRepository users) {
        this.centers = centers;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Long centerId, Long userId) {
        return centerId != null && userId != null
                && centers.findOwnerId(centerId).map(userId::equals).orElse(false);
    }

    /** Nombre del dueño para los avisos («Pídeselo a Carlos Mendoza»). */
    @Transactional(readOnly = true)
    public String ownerName(Long centerId) {
        return centers.findOwnerId(centerId)
                .flatMap(users::findById)
                .map(User::getName)
                .orElse("la persona que dio de alta el centro");
    }

    public String ownerOnlyMessage(Long centerId) {
        return OWNER_ONLY_MESSAGE.formatted(ownerName(centerId));
    }

    /**
     * El dueño cede el centro a otra persona del equipo con cuenta activa; él pasa a ser trabajador estándar.
     */
    public User transferOwnership(Long centerId, Long ownerId, Long newOwnerId) {
        Center center = centers.findByIdForUpdate(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        if (!center.isOwnedBy(ownerId)) {
            throw new BusinessRuleException(ownerOnlyMessage(centerId));
        }
        if (newOwnerId == null || newOwnerId.equals(ownerId)) {
            throw new BusinessRuleException("Elige a otra persona del equipo.");
        }
        User newOwner = users.findByIdAndCenterIdAndRole(newOwnerId, centerId, Role.WORKER)
                .orElseThrow(() -> new ResourceNotFoundException("Esa persona no es de tu centro."));
        if (!newOwner.hasAccount()) {
            throw new BusinessRuleException(newOwner.getName() + " todavía no ha activado su cuenta: espera a que entre por primera vez.");
        }
        center.setOwnerId(newOwner.getId());
        centers.save(center);
        return newOwner;
    }
}
