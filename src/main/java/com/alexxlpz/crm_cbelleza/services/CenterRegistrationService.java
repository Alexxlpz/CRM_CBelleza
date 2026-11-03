package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm.Member;
import com.alexxlpz.crm_cbelleza.forms.Emails;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.TaxIds;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Alta de un centro desde /alta-centro. Crea el centro con su horario, su responsable (el dueño del centro) y su
 * equipo como trabajadores sin contraseña (cada uno la pone desde su invitación) y, si se pidió, la tarjeta de puntos.
 * El centro nace oculto: no sale en el buscador ni admite reservas hasta que administración lo publica
 * desde el enlace de su correo. Mientras tanto el equipo ya puede entrar y preparar tratamientos e inventario.
 */
@Service
@Transactional
public class CenterRegistrationService {

    /** Trabajadores que se pueden añadir en el alta además del responsable. */
    public static final int MAX_EXTRA_WORKERS = 20;

    static final int MAX_CENTER_NAME = 120;
    static final int MAX_PERSON_NAME = 100;
    static final int MAX_ADDRESS = 255;
    static final int MAX_NOTES = 2000;
    private static final Set<String> SPANISH_TIME_ZONES = Set.of("Europe/Madrid", "Atlantic/Canary");
    /** Un número de 5 cifras suelto: el código postal de una dirección española. */
    private static final Pattern POSTCODE = Pattern.compile("(?<!\\d)(\\d{5})(?!\\d)");

    private final CenterRepository centers;
    private final UserRepository users;
    private final InvitationService invitations;
    private final LoyaltyService loyalty;
    private final CenterPhotoService photos;
    private final OpeningHoursService openingHours;
    private final CenterService centerService;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public CenterRegistrationService(CenterRepository centers, UserRepository users, InvitationService invitations,
                                     LoyaltyService loyalty, CenterPhotoService photos, OpeningHoursService openingHours,
                                     CenterService centerService, Clock clock, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.centerService = centerService;
        this.centers = centers;
        this.users = users;
        this.invitations = invitations;
        this.loyalty = loyalty;
        this.photos = photos;
        this.openingHours = openingHours;
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
        String centerName = FormText.collapseSpaces(form.centerName());
        checkLength(centerName, 2, MAX_CENTER_NAME, "El nombre del centro");
        // La zona horaria va antes que el CIF: dice si el centro está en España (CIF o NIF español con su letra)
        String timeZone = FormText.isBlank(form.timeZone()) ? CenterTimeZones.DEFAULT : form.timeZone();
        if (!CenterTimeZones.isValid(timeZone)) {
            throw new BusinessRuleException("Elige una zona horaria de la lista.");
        }
        boolean spain = SPANISH_TIME_ZONES.contains(timeZone);
        String taxId = TaxIds.normalize(form.cif(), spain);
        String phone = form.normalizedPhone();
        String address = FormText.collapseSpaces(form.address());
        if (address == null) {
            throw new BusinessRuleException("Escribe la dirección del centro: calle, número, código postal y ciudad.");
        }
        checkLength(address, 5, MAX_ADDRESS, "La dirección");
        if (spain) {
            checkSpanishPostcode(address);
        }
        if (!validCoordinates(form.latitude(), form.longitude())) {
            throw new BusinessRuleException("Marca en el mapa dónde está tu centro para que los clientes puedan encontrarlo.");
        }
        int cabins = form.cabins() == null ? 1 : form.cabins();
        if (cabins < 1 || cabins > CenterService.MAX_CABINS) {
            throw new BusinessRuleException("El número de cabinas tiene que estar entre 1 y " + CenterService.MAX_CABINS + ".");
        }
        OpeningHours hours = openingHours.parse(form.openingHours());
        if (form.notes() != null && form.notes().length() > MAX_NOTES) {
            throw new BusinessRuleException("La información para la revisión no puede pasar de " + MAX_NOTES + " caracteres.");
        }
        if (!Boolean.TRUE.equals(form.terms())) {
            throw new BusinessRuleException("Confirma que el centro tiene actividad legal y acepta los términos para continuar.");
        }

        List<Member> team = new ArrayList<>();
        team.add(new Member(form.contactName(), form.email()));
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
                .name(centerName)
                .taxId(taxId)
                .address(address)
                .phone(phone)
                .email(cleanTeam.getFirst().email())
                .latitude(form.latitude())
                .longitude(form.longitude())
                .timeZone(timeZone)
                .cabins(cabins)
                .published(false)
                .approvalTokenHash(SecureTokens.hash(approvalToken))
                .registeredAt(now)
                .build());
        // El enlace corto se fija ya: si se calculase al vuelo, otro centro podría quitárselo (CenterService.shortSlug)
        centerService.assignSlug(center);

        if (form.mode() != null && !LoyaltyMode.OFF.name().equals(form.mode())) {
            loyalty.updateSettings(center.getId(), form.loyalty());
        }
        preparedPhoto.ifPresent(p -> photos.save(center.getId(), p));
        openingHours.save(center.getId(), hours);

        List<Invitee> invitees = new ArrayList<>();
        for (int i = 0; i < cleanTeam.size(); i++) {
            Member member = cleanTeam.get(i);
            User worker = users.save(User.builder()
                    .name(member.name())
                    .email(member.email())
                    .phone(i == 0 ? phone : null)
                    .role(Role.WORKER)
                    .center(center)
                    .build());
            invitees.add(new Invitee(worker.getName(), worker.getEmail(), invitations.invite(worker)));
            if (i == 0) {
                // Quien da de alta el centro es su dueño
                center.setOwnerId(worker.getId());
            }
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

    /**
     * Rechaza un alta sin publicar: borra el centro, las cuentas de su equipo y todo lo que hayan preparado mientras
     * tanto (tratamientos, inventario, agenda, fichas…). Los clientes con cuenta son de Cuquora, no del centro: se
     * quedan; los que el equipo apuntó a mano (sin contraseña ni Google) se borran si no salen en ningún otro centro.
     * El enlace deja de valer. Devuelve el nombre del centro, para el aviso.
     */
    public String reject(String approvalToken) {
        Center center = findByApprovalToken(approvalToken);
        Long id = center.getId();
        String name = center.getName();
        List<Long> team = jdbc.queryForList("SELECT id FROM users WHERE center_id = ?", Long.class, id);
        List<Long> manualClients = jdbc.queryForList("""
                SELECT u.id FROM users u WHERE u.role = 'CLIENT' AND u.password IS NULL AND u.google_id IS NULL
                  AND (u.id IN (SELECT client_id FROM client_cards WHERE center_id = ?)
                       OR u.id IN (SELECT client_id FROM appointments WHERE center_id = ?))""", Long.class, id, id);
        // De lo que cuelga de lo demás hacia arriba; las tablas con ON DELETE CASCADE se vacían solas
        // (horario, foto, reseñas, propuestas de cita, invitaciones, Google Calendar, horario y foto de cada uno…)
        jdbc.update("DELETE FROM loyalty_movements WHERE center_id = ?", id);
        jdbc.update("DELETE FROM product_sales WHERE center_id = ?", id);
        jdbc.update("DELETE FROM reviews WHERE center_id = ?", id);
        jdbc.update("DELETE FROM appointments WHERE center_id = ?", id);
        jdbc.update("DELETE FROM agenda_blocks WHERE center_id = ?", id);
        jdbc.update("DELETE FROM client_cards WHERE center_id = ?", id);
        jdbc.update("DELETE FROM client_card_templates WHERE center_id = ?", id);
        jdbc.update("DELETE FROM inventories WHERE center_id = ?", id);
        jdbc.update("DELETE FROM treatments WHERE center_id = ?", id);
        jdbc.update("DELETE FROM products WHERE center_id = ?", id);
        jdbc.update("UPDATE centers SET owner_id = NULL WHERE id = ?", id);
        for (Long userId : team) {
            // Sesiones recordadas («Recordar mi sesión») del trabajador: van por correo, sin clave ajena
            jdbc.update("DELETE FROM persistent_logins WHERE username = (SELECT email FROM users WHERE id = ?)", userId);
        }
        jdbc.update("DELETE FROM users WHERE center_id = ?", id);
        jdbc.update("DELETE FROM centers WHERE id = ?", id);
        for (Long clientId : manualClients) {
            jdbc.update("""
                    DELETE FROM users WHERE id = ?
                      AND NOT EXISTS (SELECT 1 FROM client_cards WHERE client_id = ?)
                      AND NOT EXISTS (SELECT 1 FROM appointments WHERE client_id = ?)
                      AND NOT EXISTS (SELECT 1 FROM product_sales WHERE client_id = ?)
                      AND NOT EXISTS (SELECT 1 FROM loyalty_movements WHERE client_id = ?)""",
                    clientId, clientId, clientId, clientId, clientId);
        }
        return name;
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
            String name = FormText.collapseSpaces(member.name());
            checkLength(name, 2, MAX_PERSON_NAME, "El nombre de «" + name + "»");
            String email = member.email().trim().toLowerCase(Locale.ROOT);
            if (!Emails.isValid(email) || email.endsWith(User.PLACEHOLDER_EMAIL_DOMAIN)) {
                throw new BusinessRuleException("Revisa el correo «" + member.email().trim() + "»: no es válido.");
            }
            if (!seen.add(email)) {
                throw new BusinessRuleException("El correo " + email + " está repetido: cada persona necesita el suyo.");
            }
            if (users.findByEmailIgnoreCase(email).isPresent()) {
                throw new BusinessRuleException("Ya hay una cuenta de Cuquora con el correo " + email
                        + ". Usa otro correo para esa persona o escríbenos desde Contacto.");
            }
            clean.add(new Member(name, email));
        }
        return clean;
    }

    private static void checkLength(String text, int min, int max, String what) {
        if (text.length() < min) {
            throw new BusinessRuleException(what + " tiene que tener al menos " + min + " caracteres.");
        }
        if (text.length() > max) {
            throw new BusinessRuleException(what + " no puede pasar de " + max + " caracteres.");
        }
    }

    /** En España la dirección lleva su código postal: 5 cifras y las dos primeras, la provincia (01 a 52). */
    static void checkSpanishPostcode(String address) {
        var matcher = POSTCODE.matcher(address);
        boolean found = false;
        while (matcher.find()) {
            int province = Integer.parseInt(matcher.group(1).substring(0, 2));
            if (province >= 1 && province <= 52) {
                return;
            }
            found = true;
        }
        throw new BusinessRuleException(found
                ? "Revisa el código postal de la dirección: en España empieza por 01 a 52 (ej.: Calle Mayor 12, 28013 Madrid)."
                : "Añade el código postal a la dirección (5 cifras, ej.: Calle Mayor 12, 28013 Madrid).");
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
