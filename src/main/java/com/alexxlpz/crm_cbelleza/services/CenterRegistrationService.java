package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm.Member;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Alta de un centro desde /alta-centro. Crea el centro, su responsable y su equipo como trabajadores sin
 * contraseña (cada uno la pone desde su invitación) y, si se pidió, la tarjeta de puntos.
 * El centro nace oculto: no sale en el buscador ni admite reservas hasta que administración lo publica
 * desde el enlace de su correo. Mientras tanto el equipo ya puede entrar y preparar tratamientos e inventario.
 */
@Service
@Transactional
public class CenterRegistrationService {

    /** Trabajadores que se pueden añadir en el alta además del responsable. */
    public static final int MAX_EXTRA_WORKERS = 20;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TAX_ID = Pattern.compile("^[A-Z0-9]{5,20}$");

    private final CenterRepository centers;
    private final UserRepository users;
    private final InvitationService invitations;
    private final LoyaltyService loyalty;
    private final CenterPhotoService photos;
    private final Clock clock;

    public CenterRegistrationService(CenterRepository centers, UserRepository users, InvitationService invitations,
                                     LoyaltyService loyalty, CenterPhotoService photos, Clock clock) {
        this.centers = centers;
        this.users = users;
        this.invitations = invitations;
        this.loyalty = loyalty;
        this.photos = photos;
        this.clock = clock;
    }

    public Registration register(CenterRegistrationForm form) {
        return register(form, null);
    }

    /** Igual que {@link #register(CenterRegistrationForm)}, con la foto de presentación opcional. */
    public Registration register(CenterRegistrationForm form, MultipartFile photo) {
        if (FormText.isBlank(form.centerName()) || FormText.isBlank(form.cif()) || FormText.isBlank(form.phone())
                || FormText.isBlank(form.contactName()) || FormText.isBlank(form.email())) {
            throw new BusinessRuleException("Completa los datos del centro y del responsable.");
        }
        String taxId = form.cif().replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT);
        if (!TAX_ID.matcher(taxId).matches()) {
            throw new BusinessRuleException("Revisa el CIF o NIF: solo letras y números, por ejemplo B12345678.");
        }
        if (form.phone().replaceAll("\\D", "").length() < 6) {
            throw new BusinessRuleException("Introduce un teléfono válido (mínimo 6 dígitos).");
        }
        if (FormText.isBlank(form.address())) {
            throw new BusinessRuleException("Escribe la dirección del centro: calle, número, código postal y ciudad.");
        }
        if (!validCoordinates(form.latitude(), form.longitude())) {
            throw new BusinessRuleException("Marca en el mapa dónde está tu centro para que los clientes puedan encontrarlo.");
        }
        String timeZone = FormText.isBlank(form.timeZone()) ? CenterTimeZones.DEFAULT : form.timeZone();
        if (!CenterTimeZones.isValid(timeZone)) {
            throw new BusinessRuleException("Elige una zona horaria de la lista.");
        }
        int cabins = form.cabins() == null ? 1 : form.cabins();
        if (cabins < 1 || cabins > CenterService.MAX_CABINS) {
            throw new BusinessRuleException("El número de cabinas tiene que estar entre 1 y " + CenterService.MAX_CABINS + ".");
        }
        if (!Boolean.TRUE.equals(form.terms())) {
            throw new BusinessRuleException("Confirma que el centro tiene actividad legal y acepta los términos para continuar.");
        }

        List<Member> team = new ArrayList<>();
        team.add(new Member(form.contactName().trim(), form.email()));
        List<Member> extra = form.extraWorkers();
        if (extra.size() > MAX_EXTRA_WORKERS) {
            throw new BusinessRuleException("Puedes dar de alta hasta " + MAX_EXTRA_WORKERS
                    + " trabajadores ahora; el resto, más adelante desde tu panel.");
        }
        for (Member member : extra) {
            if (member.name() == null || member.email() == null) {
                throw new BusinessRuleException("A cada trabajador le falta el nombre o el correo. Complétalo o quita la fila.");
            }
            team.add(member);
        }
        List<Member> cleanTeam = checkEmails(team);
        // La foto se comprueba antes de crear nada: si no vale, no queda un centro a medias
        Optional<CenterPhotoService.PreparedPhoto> preparedPhoto = photos.prepare(photo);

        LocalDateTime now = LocalDateTime.now(clock);
        String approvalToken = SecureTokens.newToken();
        Center center = centers.save(Center.builder()
                .name(form.centerName().trim())
                .taxId(taxId)
                .address(form.address().trim())
                .phone(form.phone().trim())
                .email(cleanTeam.getFirst().email())
                .latitude(form.latitude())
                .longitude(form.longitude())
                .timeZone(timeZone)
                .cabins(cabins)
                .published(false)
                .approvalTokenHash(SecureTokens.hash(approvalToken))
                .registeredAt(now)
                .build());

        if (form.mode() != null && !LoyaltyMode.OFF.name().equals(form.mode())) {
            loyalty.updateSettings(center.getId(), form.loyalty());
        }
        preparedPhoto.ifPresent(p -> photos.save(center.getId(), p));

        List<Invitee> invitees = new ArrayList<>();
        for (int i = 0; i < cleanTeam.size(); i++) {
            Member member = cleanTeam.get(i);
            User worker = users.save(User.builder()
                    .name(member.name())
                    .email(member.email())
                    .phone(i == 0 ? form.phone().trim() : null)
                    .role(Role.WORKER)
                    .center(center)
                    .build());
            invitees.add(new Invitee(worker.getName(), worker.getEmail(), invitations.invite(worker)));
        }
        return new Registration(center.getId(), center.getName(), approvalToken, invitees);
    }

    /** Centro pendiente de revisión al que apunta el enlace de administración. */
    @Transactional(readOnly = true)
    public Center pendingCenter(String approvalToken) {
        return findByApprovalToken(approvalToken);
    }

    /** Publica el centro: sale en el buscador y el mapa y admite reservas. El enlace deja de valer. */
    public Center publish(String approvalToken) {
        Center center = findByApprovalToken(approvalToken);
        center.setPublished(true);
        center.setApprovalTokenHash(null);
        return centers.save(center);
    }

    private Center findByApprovalToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("El enlace no es válido o el centro ya está publicado.");
        }
        return centers.findByApprovalTokenHash(SecureTokens.hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("El enlace no es válido o el centro ya está publicado."));
    }

    /** Correos en minúscula, válidos, sin repetir y sin cuenta previa en Cuquora. */
    private List<Member> checkEmails(List<Member> team) {
        Set<String> seen = new HashSet<>();
        List<Member> clean = new ArrayList<>();
        for (Member member : team) {
            String email = member.email().trim().toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches() || email.endsWith(User.PLACEHOLDER_EMAIL_DOMAIN)) {
                throw new BusinessRuleException("Revisa el correo «" + member.email().trim() + "»: no es válido.");
            }
            if (!seen.add(email)) {
                throw new BusinessRuleException("El correo " + email + " está repetido: cada persona necesita el suyo.");
            }
            if (users.findByEmailIgnoreCase(email).isPresent()) {
                throw new BusinessRuleException("Ya hay una cuenta de Cuquora con el correo " + email
                        + ". Usa otro correo para esa persona o escríbenos desde Contacto.");
            }
            clean.add(new Member(member.name().trim(), email));
        }
        return clean;
    }

    private static boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    /** Resultado del alta: lo necesario para los correos (enlaces en claro, que no se guardan). */
    public record Registration(Long centerId, String centerName, String approvalToken, List<Invitee> invitees) {

        public Invitee owner() {
            return invitees.getFirst();
        }
    }

    public record Invitee(String name, String email, String invitationToken) {
    }
}
