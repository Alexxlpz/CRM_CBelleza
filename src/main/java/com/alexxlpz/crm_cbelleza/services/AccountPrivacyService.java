package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentMedia;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMovement;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.googlecalendar.GoogleCalendarService;
import com.alexxlpz.crm_cbelleza.repositories.AccountInvitationRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentMediaDataRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentMediaRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.LoyaltyMovementRepository;
import com.alexxlpz.crm_cbelleza.repositories.NotificationDismissalRepository;
import com.alexxlpz.crm_cbelleza.repositories.PasswordResetRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductSaleRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Derechos del RGPD de un cliente desde «Mi Perfil»: descargar todo lo que Cuquora guarda de él y borrar su cuenta.
 * <p>
 * Borrar no quita la fila de la cuenta: la deja sin nombre, correo, teléfono ni credenciales. Así las citas pasadas
 * siguen contando en los números del centro (ocupación, ingresos, reseñas) pero ya no llevan a ninguna persona.
 * Lo que el centro guardaba de él se borra: fichas, resúmenes y fotos de sus tratamientos y puntos de fidelización.
 * Sus citas por delante se anulan como si las anulase él, para que el centro reciba el aviso y libere el hueco.
 */
@Service
@Transactional
public class AccountPrivacyService {

    /** Nombre con el que se queda una cuenta borrada (lo ve el centro en sus citas pasadas y reseñas). */
    public static final String DELETED_NAME = "Cliente eliminado";

    /** Lo que escribe quien entra con Google (no tiene contraseña) para confirmar el borrado. */
    public static final String CONFIRMATION_WORD = "BORRAR";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final AppointmentMediaRepository media;
    private final AppointmentMediaDataRepository mediaData;
    private final ClientCardRepository cards;
    private final ClientCardTemplateService cardTemplates;
    private final ClientCardJson cardJson;
    private final ReviewRepository reviews;
    private final ProductSaleRepository sales;
    private final LoyaltyMovementRepository loyalty;
    private final PasswordResetRepository passwordResets;
    private final AccountInvitationRepository invitations;
    private final NotificationDismissalRepository dismissals;
    private final UserPhotoRepository photos;
    private final BookingService booking;
    private final GoogleCalendarService googleCalendar;
    private final PasswordEncoder passwordEncoder;
    private final PersistentTokenRepository rememberMeTokens;
    private final ObjectMapper json;
    private final Clock clock;

    public AccountPrivacyService(UserRepository users, AppointmentRepository appointments,
                                 AppointmentMediaRepository media, AppointmentMediaDataRepository mediaData,
                                 ClientCardRepository cards, ClientCardTemplateService cardTemplates,
                                 ClientCardJson cardJson, ReviewRepository reviews, ProductSaleRepository sales,
                                 LoyaltyMovementRepository loyalty, PasswordResetRepository passwordResets,
                                 AccountInvitationRepository invitations, NotificationDismissalRepository dismissals,
                                 UserPhotoRepository photos, BookingService booking, GoogleCalendarService googleCalendar,
                                 PasswordEncoder passwordEncoder, PersistentTokenRepository rememberMeTokens,
                                 ObjectMapper json, Clock clock) {
        this.users = users;
        this.appointments = appointments;
        this.media = media;
        this.mediaData = mediaData;
        this.cards = cards;
        this.cardTemplates = cardTemplates;
        this.cardJson = cardJson;
        this.reviews = reviews;
        this.sales = sales;
        this.loyalty = loyalty;
        this.passwordResets = passwordResets;
        this.invitations = invitations;
        this.dismissals = dismissals;
        this.photos = photos;
        this.booking = booking;
        this.googleCalendar = googleCalendar;
        this.passwordEncoder = passwordEncoder;
        this.rememberMeTokens = rememberMeTokens;
        this.json = json;
        this.clock = clock;
    }

    /** Todo lo que Cuquora guarda del cliente, en un JSON legible para descargar. */
    @Transactional(readOnly = true)
    public byte[] export(Long clientId) {
        User client = activeClient(clientId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("descargado", LocalDate.now(clock).toString());
        data.put("cuenta", account(client));

        List<Appointment> clientAppointments = appointments.findByClientIdOrderByDateTimeDesc(clientId);
        Map<Long, Long> mediaCount = clientAppointments.isEmpty() ? Map.of()
                : media.findByAppointmentIdIn(clientAppointments.stream().map(Appointment::getId).toList()).stream()
                .collect(Collectors.groupingBy(AppointmentMedia::getAppointmentId, Collectors.counting()));
        data.put("citas", clientAppointments.stream().map(a -> appointment(a, mediaCount.getOrDefault(a.getId(), 0L))).toList());
        data.put("fichasDeLosCentros", cards.findByClientId(clientId).stream().map(this::card).toList());
        data.put("resenas", reviews.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(AccountPrivacyService::review).toList());
        data.put("compras", sales.findByClientIdOrderBySoldAtDesc(clientId).stream().map(AccountPrivacyService::sale).toList());
        data.put("puntos", loyalty.findByClientId(clientId).stream().map(AccountPrivacyService::points).toList());
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se han podido preparar los datos del cliente " + clientId, e);
        }
    }

    /**
     * Borra la cuenta del cliente. Pide la contraseña si tiene; si entra solo con Google, que escriba
     * {@link #CONFIRMATION_WORD}. Lanza {@link BusinessRuleException} con un mensaje para el usuario si no la confirma.
     */
    public void deleteClientAccount(Long clientId, String confirmation) {
        User client = activeClient(clientId);
        checkConfirmation(client, confirmation);

        List<Appointment> clientAppointments = appointments.findByClientIdOrderByDateTimeDesc(clientId);
        for (Appointment appointment : clientAppointments) {
            if (isCancellable(appointment)) {
                booking.cancelByClient(clientId, appointment.getId());
            }
        }
        googleCalendar.disconnect(clientId);

        if (!clientAppointments.isEmpty()) {
            List<AppointmentMedia> files = media.findByAppointmentIdIn(clientAppointments.stream().map(Appointment::getId).toList());
            files.forEach(file -> mediaData.deleteById(file.getId()));
            media.deleteAll(files);
        }
        for (Appointment appointment : clientAppointments) {
            appointment.setTreatmentSummary(null);
            appointment.setTreatmentAlert(false);
            appointment.setGuestName(null);
            appointment.setGuestPhone(null);
            appointment.setClientGoogleEventId(null);
        }
        cards.deleteAll(cards.findByClientId(clientId));
        loyalty.deleteAll(loyalty.findByClientId(clientId));
        passwordResets.deleteAll(passwordResets.findByUserId(clientId));
        invitations.deleteAll(invitations.findByUserId(clientId));
        dismissals.deleteByUserId(clientId);
        photos.findById(clientId).ifPresent(photos::delete);
        // Antes de vaciar el correo: con él se buscan los dispositivos con «Recordar mi sesión»
        rememberMeTokens.removeUserTokens(AppUserDetails.usernameOf(client));

        client.setName(DELETED_NAME);
        client.setEmail(null);
        client.setPhone(null);
        client.setPassword(null);
        client.setGoogleId(null);
        client.setEmailVerified(false);
        client.setPhotoUpdatedAt(null);
        client.setNotificationsSeenAt(null);
        client.setReviewPromptsOff(true);
        client.setDeletedAt(LocalDateTime.now(clock));
        users.save(client);
    }

    private User activeClient(Long clientId) {
        User client = users.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        if (client.getRole() != Role.CLIENT || client.isDeleted()) {
            throw new BusinessRuleException("Esta cuenta no se puede borrar desde aquí.");
        }
        return client;
    }

    private void checkConfirmation(User client, String confirmation) {
        if (client.getPassword() != null) {
            if (confirmation == null || !passwordEncoder.matches(confirmation, client.getPassword())) {
                throw new BusinessRuleException("La contraseña no es correcta. Tu cuenta no se ha borrado.");
            }
        } else if (confirmation == null || !CONFIRMATION_WORD.equalsIgnoreCase(confirmation.trim())) {
            throw new BusinessRuleException("Escribe " + CONFIRMATION_WORD + " para confirmar. Tu cuenta no se ha borrado.");
        }
    }

    /** Solicitud, propuesta o cita confirmada que aún no ha empezado: la que el cliente podría anular él mismo. */
    private boolean isCancellable(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        return (status.isOpen() || status == AppointmentStatus.CONFIRMED)
                && appointment.getDateTime().isAfter(CenterClock.now(clock, appointment.getCenter()));
    }

    private static Map<String, Object> account(User client) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("nombre", client.getName());
        account.put("correo", client.getEmail());
        account.put("telefono", client.getPhone());
        account.put("entraConContrasena", client.getPassword() != null);
        account.put("entraConGoogle", client.getGoogleId() != null);
        account.put("correoComprobado", client.isEmailVerified());
        account.put("avisosDeValoracionDesactivados", client.isReviewPromptsOff());
        return account;
    }

    private static Map<String, Object> appointment(Appointment a, long mediaCount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fecha", DATE_TIME.format(a.getDateTime()));
        item.put("centro", a.getCenter().getName());
        item.put("tratamiento", a.getTreatment().getName());
        item.put("profesional", a.getWorker() != null ? a.getWorker().getName() : null);
        item.put("estado", a.getStatus().label());
        item.put("precio", a.getPrice());
        item.put("mensajeDelCentro", a.getWorkerMessage());
        item.put("resumenDelTratamiento", a.getTreatmentSummary());
        item.put("fotosYVideosDelTratamiento", mediaCount);
        return item;
    }

    private Map<String, Object> card(ClientCard card) {
        Map<String, String> values = cardJson.readData(card.getDataJson());
        Map<String, String> labels = cardTemplates.getFields(card.getCenter().getId()).stream()
                .collect(Collectors.toMap(ClientCardFieldDTO::getId, ClientCardFieldDTO::getLabel, (a, b) -> a));
        Map<String, String> named = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                named.put(labels.getOrDefault(key, key), value);
            }
        });
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("centro", card.getCenter().getName());
        item.put("actualizada", card.getUpdatedAt() != null ? DATE_TIME.format(card.getUpdatedAt()) : null);
        item.put("datos", named);
        return item;
    }

    private static Map<String, Object> review(Review r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fecha", DATE_TIME.format(r.getCreatedAt()));
        item.put("centro", r.getCenter().getName());
        item.put("tratamiento", r.getAppointment().getTreatment().getName());
        item.put("estrellas", r.getRating());
        item.put("comentario", r.getComment());
        item.put("respuestaDelCentro", r.getReply());
        return item;
    }

    private static Map<String, Object> sale(ProductSale s) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fecha", DATE_TIME.format(s.getSoldAt()));
        item.put("centro", s.getCenter().getName());
        item.put("producto", s.getProduct().getName());
        item.put("cantidad", s.getQuantity());
        item.put("precioPorUnidad", s.getUnitPrice());
        return item;
    }

    private static Map<String, Object> points(LoyaltyMovement m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fecha", DATE_TIME.format(m.getCreatedAt()));
        item.put("centro", m.getCenter().getName());
        item.put("puntos", m.getPoints());
        item.put("concepto", m.getDescription());
        return item;
    }
}
