package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cartera de clientes y fichas técnicas de un centro.
 * Un cliente forma parte de la cartera si tiene al menos una cita confirmada/completada en el centro
 * o si el centro le ha creado una ficha.
 */
@Service
@Transactional
public class ClientCardService {

    private static final Set<AppointmentStatus> VISIT_STATUSES = Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);
    /** Campo de la plantilla por defecto donde se guardan las notas iniciales del alta manual. */
    private static final String DEFAULT_NOTES_FIELD = "observaciones_preferencias";

    private final ClientCardRepository clientCardRepository;
    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientCardTemplateService templateService;
    private final UserService userService;
    private final ClientCardJson json;
    private final Clock clock;

    public ClientCardService(ClientCardRepository clientCardRepository,
                             CenterRepository centerRepository,
                             UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             ClientCardTemplateService templateService,
                             UserService userService,
                             ClientCardJson json,
                             Clock clock) {
        this.clientCardRepository = clientCardRepository;
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.templateService = templateService;
        this.userService = userService;
        this.json = json;
        this.clock = clock;
    }

    /** Lanza {@link ResourceNotFoundException} si el cliente no pertenece a la cartera del centro. */
    @Transactional(readOnly = true)
    public User requireClientOfCenter(Long centerId, Long clientId) {
        boolean belongs = clientCardRepository.existsByCenterIdAndClientId(centerId, clientId)
                || appointmentRepository.existsByCenterIdAndClientIdAndStatusIn(centerId, clientId, VISIT_STATUSES);
        if (!belongs) {
            throw new ResourceNotFoundException("Este cliente no forma parte de la cartera de tu centro.");
        }
        return userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado."));
    }

    /** Datos que necesita la vista de detalle de un cliente. */
    public ClientCardView getCardView(Long centerId, Long clientId) {
        User client = requireClientOfCenter(centerId, clientId);
        ClientCard card = getOrCreateCard(centerId, client);
        return new ClientCardView(client, card, templateService.getFields(centerId), json.readData(card.getDataJson()));
    }

    public void saveCardData(Long centerId, Long clientId, Map<String, String> values, Long workerId) {
        User client = requireClientOfCenter(centerId, clientId);
        ClientCard card = getOrCreateCard(centerId, client);
        card.setDataJson(json.write(values));
        card.setUpdatedAt(LocalDateTime.now(clock));
        card.setUpdatedBy(workerId != null ? userRepository.getReferenceById(workerId) : null);
        clientCardRepository.save(card);
    }

    @Transactional(readOnly = true)
    public List<ClientSummaryDTO> getClientsForCenter(Long centerId, String searchQuery) {
        Map<Long, ClientCard> cardsByClient = clientCardRepository.findByCenterId(centerId).stream()
                .filter(card -> card.getClient() != null)
                .collect(Collectors.toMap(card -> card.getClient().getId(), Function.identity(), (a, b) -> a));

        Map<Long, List<Appointment>> appointmentsByClient = new LinkedHashMap<>();
        for (Appointment app : appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId)) {
            if (app.getClient() != null) {
                appointmentsByClient.computeIfAbsent(app.getClient().getId(), id -> new ArrayList<>()).add(app);
            }
        }

        List<ClientSummaryDTO> result = new ArrayList<>();
        appointmentsByClient.forEach((clientId, apps) -> {
            ClientCard card = cardsByClient.get(clientId);
            boolean filled = card != null && json.hasAnyValue(card.getDataJson());
            long visits = apps.stream().filter(a -> VISIT_STATUSES.contains(a.getStatus())).count();
            if (visits == 0 && !filled) {
                return; // solo solicitudes pendientes/rechazadas: aún no es cliente del centro
            }
            Appointment lastCompleted = apps.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                    .findFirst()
                    .orElse(null);
            result.add(summary(apps.get(0).getClient(), card, filled, (int) (visits > 0 ? visits : apps.size()),
                    lastCompleted != null ? lastCompleted.getDateTime() : null));
        });

        // Clientes dados de alta manualmente que aún no tienen citas
        cardsByClient.forEach((clientId, card) -> {
            if (!appointmentsByClient.containsKey(clientId)) {
                result.add(summary(card.getClient(), card, json.hasAnyValue(card.getDataJson()), 0, null));
            }
        });

        return result.stream().filter(matches(searchQuery)).toList();
    }

    /**
     * Alta manual de un cliente desde la cartera (o desde la agenda al crear una cita).
     * Solo reutiliza clientes que ya son de este centro (con ficha o citas aquí) con el mismo teléfono o correo;
     * si no, crea un cliente nuevo sin cuenta. Nunca enlaza cuentas de otros centros: no se revela si un
     * correo o teléfono existe en la plataforma ni se da acceso a sus datos.
     */
    public NewClient createClientManually(Long centerId, NewClientForm form, Long workerId) {
        if (FormText.isBlank(form.name()) || FormText.isBlank(form.phone())) {
            throw new BusinessRuleException("El nombre y el teléfono son obligatorios para dar de alta a un cliente.");
        }
        String name = form.name().trim();
        String phone = form.phone().trim();
        String email = FormText.trimToNull(form.email());

        Optional<User> existing = findClientOfCenter(centerId, phone, email);
        boolean emailSkipped = false;
        User client;
        if (existing.isPresent()) {
            client = existing.get();
        } else {
            // Un correo que ya usa otra cuenta no se puede repetir: el cliente se guarda sin él
            emailSkipped = email != null && userRepository.findByEmailIgnoreCase(email).isPresent();
            String storedEmail = email != null && !emailSkipped ? email : placeholderEmail(phone);
            // Sin contraseña: la cuenta no puede iniciar sesión hasta que el cliente se registre.
            client = userService.createUser(name, storedEmail, phone, null, Role.CLIENT, null);
        }

        ClientCard card = getOrCreateCard(centerId, client);
        if (!FormText.isBlank(form.initialNotes())) {
            Map<String, String> data = json.readData(card.getDataJson());
            data.put(notesFieldId(centerId), form.initialNotes().trim());
            card.setDataJson(json.write(data));
        }
        card.setUpdatedAt(LocalDateTime.now(clock));
        card.setUpdatedBy(workerId != null ? userRepository.getReferenceById(workerId) : null);
        clientCardRepository.save(card);
        return new NewClient(client.getId(), emailSkipped);
    }

    /** Cliente de este centro (con ficha o con citas aquí) con ese teléfono o correo. */
    private Optional<User> findClientOfCenter(Long centerId, String phone, String email) {
        String digits = digits(phone);
        List<User> clientsOfCenter = new ArrayList<>();
        clientCardRepository.findByCenterId(centerId).stream()
                .map(ClientCard::getClient)
                .filter(java.util.Objects::nonNull)
                .forEach(clientsOfCenter::add);
        appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId).stream()
                .map(Appointment::getClient)
                .filter(java.util.Objects::nonNull)
                .forEach(clientsOfCenter::add);
        return clientsOfCenter.stream()
                .filter(c -> (!digits.isEmpty() && digits.equals(digits(c.getPhone())))
                        || (email != null && email.equalsIgnoreCase(c.getEmail())))
                .findFirst();
    }

    private static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    /** Resultado del alta: {@code emailSkipped} si el correo no se pudo guardar por estar ya en uso. */
    public record NewClient(Long clientId, boolean emailSkipped) {
    }

    private String placeholderEmail(String phone) {
        String candidate = "cliente_" + phone.replaceAll("[^0-9]", "") + User.PLACEHOLDER_EMAIL_DOMAIN;
        return userRepository.findByEmailIgnoreCase(candidate).isPresent()
                ? "cliente_" + System.currentTimeMillis() + User.PLACEHOLDER_EMAIL_DOMAIN
                : candidate;
    }

    /** Primer campo de texto largo de la plantilla del centro (o el campo de observaciones por defecto). */
    private String notesFieldId(Long centerId) {
        return templateService.getFields(centerId).stream()
                .filter(field -> "textarea".equalsIgnoreCase(field.getType()))
                .map(ClientCardFieldDTO::getId)
                .findFirst()
                .orElse(DEFAULT_NOTES_FIELD);
    }

    private ClientCard getOrCreateCard(Long centerId, User client) {
        return clientCardRepository.findByCenterIdAndClientId(centerId, client.getId())
                .orElseGet(() -> clientCardRepository.save(ClientCard.builder()
                        .center(center(centerId))
                        .client(client)
                        .guestName(client.getName())
                        .guestPhone(client.getPhone())
                        .dataJson("{}")
                        .updatedAt(LocalDateTime.now(clock))
                        .build()));
    }

    private Center center(Long centerId) {
        return centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
    }

    private static ClientSummaryDTO summary(User client, ClientCard card, boolean filled, int total, LocalDateTime last) {
        return ClientSummaryDTO.builder()
                .id(client.getId())
                .name(client.getName())
                .phone(client.getPhone() != null ? client.getPhone() : "")
                .email(client.getEmail() != null ? client.getEmail() : "")
                .totalAppointments(total)
                .lastAppointmentDate(last)
                .hasFilledCard(filled)
                .registered(client.hasAccount())
                .cardUpdatedAt(card != null ? card.getUpdatedAt() : null)
                .cardUpdatedByName(card != null && card.getUpdatedBy() != null ? card.getUpdatedBy().getName() : null)
                .build();
    }

    private static java.util.function.Predicate<ClientSummaryDTO> matches(String query) {
        if (FormText.isBlank(query)) {
            return c -> true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return c -> c.getName().toLowerCase(Locale.ROOT).contains(q)
                || c.getPhone().toLowerCase(Locale.ROOT).contains(q)
                || c.getEmail().toLowerCase(Locale.ROOT).contains(q);
    }

    /** Todo lo que muestra la ficha de un cliente. */
    public record ClientCardView(User client, ClientCard card, List<ClientCardFieldDTO> fields, Map<String, String> data) {
    }
}
