package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.AccountInvitation;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.AccountInvitationRepository;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El equipo de un centro en «Mi Centro». Todos lo ven; solo el dueño invita a gente nueva, reenvía invitaciones
 * y saca del equipo a quien se va. Quien sale conserva su cuenta y su historial (sus citas pasadas siguen
 * firmadas con su nombre), pero deja de entrar en el panel del centro al momento.
 */
@Service
@Transactional
public class TeamService {

    /** Mismo tope que en el alta: el responsable y hasta 20 personas más. */
    public static final int MAX_TEAM_SIZE = CenterRegistrationService.MAX_EXTRA_WORKERS + 1;

    /** Citas que impiden sacar a alguien del equipo hasta que se reasignen o se anulen. */
    private static final EnumSet<AppointmentStatus> UPCOMING = EnumSet.of(
            AppointmentStatus.PENDING, AppointmentStatus.PROPOSED, AppointmentStatus.CONFIRMED);

    private final UserRepository users;
    private final CenterRepository centers;
    private final AccountInvitationRepository invitationRepository;
    private final AppointmentRepository appointments;
    private final AgendaBlockRepository blocks;
    private final InvitationService invitations;
    private final CenterAccess access;
    private final Clock clock;

    public TeamService(UserRepository users, CenterRepository centers,
                       AccountInvitationRepository invitationRepository, AppointmentRepository appointments,
                       AgendaBlockRepository blocks, InvitationService invitations, CenterAccess access, Clock clock) {
        this.users = users;
        this.centers = centers;
        this.invitationRepository = invitationRepository;
        this.appointments = appointments;
        this.blocks = blocks;
        this.invitations = invitations;
        this.access = access;
        this.clock = clock;
    }

    /** El equipo: primero el dueño y después por nombre, con el estado de la cuenta de cada uno. */
    @Transactional(readOnly = true)
    public List<Member> members(Long centerId) {
        Long ownerId = centers.findOwnerId(centerId).orElse(null);
        List<User> workers = users.findByCenterIdAndRole(centerId, Role.WORKER);
        Map<Long, AccountInvitation> latest = invitationRepository
                .findLatestByUserIdIn(workers.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(i -> i.getUser().getId(), Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now(clock);
        return workers.stream()
                .map(w -> new Member(w.getId(), w.getName(), w.getEmail(), w.getPhotoUrl(), w.getId().equals(ownerId),
                        statusOf(w, latest.get(w.getId()), now)))
                .sorted(Comparator.comparing(Member::owner).reversed()
                        .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * Añade a alguien al equipo y le prepara la invitación para que cree su contraseña.
     * Si ese correo ya era de un trabajador que dejó otro centro, se le vuelve a dar de alta con su cuenta.
     */
    public Invitee invite(Long centerId, Long ownerId, String name, String email) {
        Center center = ownedCenter(centerId, ownerId);
        String cleanName = FormText.isBlank(name) ? null : name.trim();
        String cleanEmail = FormText.isBlank(email) ? null : email.trim().toLowerCase(Locale.ROOT);
        if (cleanName == null || cleanEmail == null) {
            throw new BusinessRuleException("Escribe el nombre y el correo de la persona que quieres añadir.");
        }
        if (cleanName.length() > 100) {
            throw new BusinessRuleException("El nombre es demasiado largo.");
        }
        if (!CenterRegistrationService.EMAIL.matcher(cleanEmail).matches() || cleanEmail.endsWith(User.PLACEHOLDER_EMAIL_DOMAIN)) {
            throw new BusinessRuleException("Revisa el correo «" + email.trim() + "»: no es válido.");
        }
        if (users.countByCenterIdAndRole(centerId, Role.WORKER) >= MAX_TEAM_SIZE) {
            throw new BusinessRuleException("Tu equipo ya tiene " + MAX_TEAM_SIZE + " personas, el máximo. Escríbenos desde Contacto si necesitas más.");
        }
        User worker = users.findByEmailIgnoreCase(cleanEmail)
                .map(existing -> rejoin(existing, center))
                .orElseGet(() -> users.save(User.builder()
                        .name(cleanName)
                        .email(cleanEmail)
                        .role(Role.WORKER)
                        .center(center)
                        .build()));
        // Quien ya tenía cuenta entra con ella: no hace falta enlace para crear contraseña
        String token = worker.hasAccount() ? null : invitations.invite(worker);
        return new Invitee(worker.getName(), worker.getEmail(), center.getName(), token);
    }

    /** Un enlace nuevo para quien todavía no ha creado su contraseña; el anterior deja de valer. */
    public Invitee resend(Long centerId, Long ownerId, Long workerId) {
        Center center = ownedCenter(centerId, ownerId);
        User worker = member(centerId, workerId);
        if (worker.hasAccount()) {
            throw new BusinessRuleException(worker.getName() + " ya ha activado su cuenta: puede entrar con su correo.");
        }
        expireInvitations(worker.getId());
        return new Invitee(worker.getName(), worker.getEmail(), center.getName(), invitations.invite(worker));
    }

    /**
     * Saca a alguien del equipo. No puede ser el dueño (antes tiene que ceder el centro) ni tener citas por
     * delante: se reasignan o se anulan primero en la agenda. Sus bloqueos de agenda pendientes se quitan.
     */
    public User remove(Long centerId, Long ownerId, Long workerId) {
        Center center = ownedCenter(centerId, ownerId);
        User worker = member(centerId, workerId);
        if (center.isOwnedBy(worker.getId())) {
            throw new BusinessRuleException("No puedes salir del equipo siendo el dueño. Si te vas, cede antes el centro a otra persona.");
        }
        LocalDateTime now = CenterClock.now(clock, center);
        long upcoming = appointments.countByWorkerIdAndStatusInAndDateTimeAfter(worker.getId(), UPCOMING, now);
        if (upcoming > 0) {
            throw new BusinessRuleException(worker.getName() + " tiene " + (upcoming == 1 ? "1 cita" : upcoming + " citas")
                    + " por delante en la agenda. Pásalas a otra persona o anúlalas y vuelve a intentarlo.");
        }
        blocks.deleteUpcomingOfWorker(worker.getId(), now);
        expireInvitations(worker.getId());
        worker.setCenter(null);
        return users.save(worker);
    }

    private User rejoin(User existing, Center center) {
        if (existing.getRole() == Role.WORKER && existing.getCenter() != null && existing.getCenter().getId().equals(center.getId())) {
            throw new BusinessRuleException(existing.getName() + " ya está en tu equipo.");
        }
        if (existing.getRole() != Role.WORKER || existing.getCenter() != null) {
            throw new BusinessRuleException("El correo " + existing.getEmail() + " ya tiene una cuenta en Cuquora de cliente o de otro centro. "
                    + "Pídele que use otro correo para trabajar en tu centro.");
        }
        existing.setCenter(center);
        return users.save(existing);
    }

    private Center ownedCenter(Long centerId, Long ownerId) {
        Center center = centers.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        if (!center.isOwnedBy(ownerId)) {
            throw new BusinessRuleException(access.ownerOnlyMessage(centerId));
        }
        return center;
    }

    private User member(Long centerId, Long workerId) {
        if (workerId == null) {
            throw new ResourceNotFoundException("Esa persona no es de tu equipo.");
        }
        return users.findByIdAndCenterIdAndRole(workerId, centerId, Role.WORKER)
                .orElseThrow(() -> new ResourceNotFoundException("Esa persona no es de tu equipo."));
    }

    private void expireInvitations(Long workerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        invitationRepository.findByUserIdAndUsedAtIsNull(workerId).forEach(invitation -> {
            if (invitation.getExpiresAt().isAfter(now)) {
                invitation.setExpiresAt(now);
            }
        });
    }

    private static Status statusOf(User worker, AccountInvitation invitation, LocalDateTime now) {
        if (worker.hasAccount()) return Status.ACTIVE;
        if (invitation != null && invitation.isUsable(now)) return Status.INVITED;
        return Status.EXPIRED;
    }

    public enum Status {
        ACTIVE("Cuenta activa"),
        INVITED("Invitación enviada"),
        EXPIRED("Invitación caducada");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Member(Long id, String name, String email, String photoUrl, boolean owner, Status status) {
        public boolean pending() {
            return status != Status.ACTIVE;
        }
    }

    /** Datos para enviar la invitación por correo; {@code token} es nulo si ya tenía cuenta. */
    public record Invitee(String name, String email, String centerName, String token) {
    }
}
