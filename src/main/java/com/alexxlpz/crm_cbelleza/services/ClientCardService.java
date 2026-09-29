package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.*;
import com.alexxlpz.crm_cbelleza.repositories.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ClientCardService {

    private final ClientCardRepository clientCardRepository;
    private final ClientCardTemplateRepository templateRepository;
    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClientCardService(ClientCardRepository clientCardRepository,
                             ClientCardTemplateRepository templateRepository,
                             CenterRepository centerRepository,
                             UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             UserService userService) {
        this.clientCardRepository = clientCardRepository;
        this.templateRepository = templateRepository;
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.userService = userService;
    }

    private static final String DEFAULT_FIELDS_JSON = """
        [
          {"id":"tipo_piel_cabello","label":"Tipo de Piel / Cabello","type":"text","placeholder":"Ej. Piel mixta / Cabello fino teñido","required":false},
          {"id":"alergias_sensibilidades","label":"Alergias o Sensibilidades","type":"text","placeholder":"Ej. Alergia al amoníaco, látex, fragancias","required":false},
          {"id":"tratamientos_habituales","label":"Coloración / Tratamientos habituales","type":"text","placeholder":"Ej. Tinte 6.34, Mechas balayage, etc.","required":false},
          {"id":"observaciones_preferencias","label":"Observaciones y Preferencias Técnicas","type":"textarea","placeholder":"Preferencias de temperatura de lavado, notas del especialista, etc.","required":false}
        ]
        """;

    @Transactional
    public ClientCardTemplate getOrCreateTemplateForCenter(Long centerId) {
        return templateRepository.findByCenterId(centerId)
                .orElseGet(() -> {
                    Center center = centerRepository.findById(centerId)
                            .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado"));
                    ClientCardTemplate template = ClientCardTemplate.builder()
                            .center(center)
                            .fieldsJson(DEFAULT_FIELDS_JSON)
                            .build();
                    return templateRepository.save(template);
                });
    }

    public List<ClientCardFieldDTO> parseTemplateFields(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.trim().isEmpty()) {
            fieldsJson = DEFAULT_FIELDS_JSON;
        }
        try {
            return objectMapper.readValue(fieldsJson, new TypeReference<List<ClientCardFieldDTO>>() {});
        } catch (Exception e) {
            try {
                return objectMapper.readValue(DEFAULT_FIELDS_JSON, new TypeReference<List<ClientCardFieldDTO>>() {});
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    @Transactional
    public void saveTemplateFields(Long centerId, List<ClientCardFieldDTO> fields) {
        ClientCardTemplate template = getOrCreateTemplateForCenter(centerId);
        try {
            String json = objectMapper.writeValueAsString(fields);
            template.setFieldsJson(json);
            templateRepository.save(template);
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar la plantilla de ficha del centro", e);
        }
    }

    @Transactional
    public ClientCard getOrCreateCard(Long centerId, Long clientId, String guestPhone, String guestName) {
        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado"));

        if (clientId != null) {
            return clientCardRepository.findByCenterIdAndClientId(centerId, clientId)
                    .orElseGet(() -> {
                        User client = userRepository.findById(clientId).orElse(null);
                        ClientCard card = ClientCard.builder()
                                .center(center)
                                .client(client)
                                .guestName(client != null ? client.getName() : null)
                                .guestPhone(client != null ? client.getPhone() : null)
                                .dataJson("{}")
                                .updatedAt(LocalDateTime.now())
                                .build();
                        return clientCardRepository.save(card);
                    });
        } else if (guestPhone != null && !guestPhone.trim().isEmpty()) {
            return clientCardRepository.findByCenterIdAndGuestPhone(centerId, guestPhone)
                    .orElseGet(() -> {
                        ClientCard card = ClientCard.builder()
                                .center(center)
                                .guestPhone(guestPhone)
                                .guestName(guestName)
                                .dataJson("{}")
                                .updatedAt(LocalDateTime.now())
                                .build();
                        return clientCardRepository.save(card);
                    });
        }

        throw new IllegalArgumentException("Se requiere un ID de cliente o teléfono de invitado");
    }

    public Map<String, String> parseCardData(String dataJson) {
        if (dataJson == null || dataJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(dataJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Transactional
    public void updateCardData(Long centerId, Long clientId, String guestPhone, String guestName,
                               Map<String, String> values, User worker) {
        ClientCard card = getOrCreateCard(centerId, clientId, guestPhone, guestName);
        try {
            String json = objectMapper.writeValueAsString(values != null ? values : Collections.emptyMap());
            card.setDataJson(json);
            card.setUpdatedAt(LocalDateTime.now());
            card.setUpdatedBy(worker);
            if (guestName != null && !guestName.trim().isEmpty() && card.getGuestName() == null) {
                card.setGuestName(guestName);
            }
            clientCardRepository.save(card);
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar la información de la ficha", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ClientSummaryDTO> getClientsForCenter(Long centerId, String searchQuery) {
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);

        // Group appointments by registered client
        Map<String, List<Appointment>> clientAppointmentsMap = new LinkedHashMap<>();

        for (Appointment app : appointments) {
            if (app.getClient() == null) {
                continue; // Ignore any orphan guest appointments
            }
            String key = "user_" + app.getClient().getId();
            clientAppointmentsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(app);
        }

        List<ClientSummaryDTO> result = new ArrayList<>();

        for (Map.Entry<String, List<Appointment>> entry : clientAppointmentsMap.entrySet()) {
            List<Appointment> apps = entry.getValue();
            Appointment latestApp = apps.get(0); // already sorted descending

            Long clientId = null;
            String name = "";
            String phone = "";
            String email = "";
            boolean isGuest = false;

            if (latestApp.getClient() != null) {
                User u = latestApp.getClient();
                clientId = u.getId();
                name = u.getName();
                phone = u.getPhone() != null ? u.getPhone() : "";
                email = u.getEmail() != null ? u.getEmail() : "";
                isGuest = false;
            } else {
                name = latestApp.getGuestName() != null ? latestApp.getGuestName() : "Cliente Invitado";
                phone = latestApp.getGuestPhone() != null ? latestApp.getGuestPhone() : "";
                email = "";
                isGuest = true;
            }

            // Search query filter
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String q = searchQuery.trim().toLowerCase();
                boolean matchesName = name.toLowerCase().contains(q);
                boolean matchesPhone = phone.toLowerCase().contains(q);
                boolean matchesEmail = email.toLowerCase().contains(q);
                if (!matchesName && !matchesPhone && !matchesEmail) {
                    continue;
                }
            }

            // Check card status
            Optional<ClientCard> cardOpt;
            if (clientId != null) {
                cardOpt = clientCardRepository.findByCenterIdAndClientId(centerId, clientId);
            } else {
                cardOpt = clientCardRepository.findByCenterIdAndGuestPhone(centerId, phone);
            }

            boolean hasFilledCard = false;
            LocalDateTime cardUpdatedAt = null;
            String cardUpdatedByName = null;

            if (cardOpt.isPresent()) {
                ClientCard card = cardOpt.get();
                cardUpdatedAt = card.getUpdatedAt();
                if (card.getUpdatedBy() != null) {
                    cardUpdatedByName = card.getUpdatedBy().getName();
                }
                if (card.getDataJson() != null && !card.getDataJson().trim().isEmpty() && !card.getDataJson().equals("{}")) {
                    Map<String, String> data = parseCardData(card.getDataJson());
                    hasFilledCard = data.values().stream().anyMatch(val -> val != null && !val.trim().isEmpty());
                }
            }

            // Requirement: Only consider someone a client (with ficha) if they have at least one CONFIRMED appointment or already filled card
            boolean hasConfirmed = apps.stream().anyMatch(a -> a.getStatus() == AppointmentStatus.CONFIRMED);
            if (!hasConfirmed && !hasFilledCard) {
                continue;
            }

            int confirmedCount = (int) apps.stream().filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).count();
            Appointment latestConfirmedApp = apps.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                    .findFirst()
                    .orElse(latestApp);

            result.add(ClientSummaryDTO.builder()
                    .id(clientId)
                    .name(name)
                    .phone(phone)
                    .email(email)
                    .isGuest(isGuest)
                    .totalAppointments(confirmedCount > 0 ? confirmedCount : apps.size())
                    .lastAppointmentDate(latestConfirmedApp.getDateTime())
                    .hasFilledCard(hasFilledCard)
                    .cardUpdatedAt(cardUpdatedAt)
                    .cardUpdatedByName(cardUpdatedByName)
                    .build());
        }

        // Include manually registered clients who don't have appointments yet
        List<ClientCard> allCards = clientCardRepository.findByCenterId(centerId);
        for (ClientCard card : allCards) {
            if (card.getClient() == null) {
                continue; // Ignore any orphan guest cards
            }
            String key = "user_" + card.getClient().getId();
            if (clientAppointmentsMap.containsKey(key)) {
                continue; // Already included from appointment processing
            }

            Long clientId = card.getClient().getId();
            String name = card.getClient().getName();
            String phone = card.getClient().getPhone() != null ? card.getClient().getPhone() : "";
            String email = card.getClient().getEmail() != null ? card.getClient().getEmail() : "";
            boolean isGuest = false;

            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String q = searchQuery.trim().toLowerCase();
                boolean matchesName = name.toLowerCase().contains(q);
                boolean matchesPhone = phone.toLowerCase().contains(q);
                boolean matchesEmail = email.toLowerCase().contains(q);
                if (!matchesName && !matchesPhone && !matchesEmail) {
                    continue;
                }
            }

            boolean hasFilledCard = false;
            if (card.getDataJson() != null && !card.getDataJson().trim().isEmpty() && !card.getDataJson().equals("{}")) {
                Map<String, String> data = parseCardData(card.getDataJson());
                hasFilledCard = data.values().stream().anyMatch(val -> val != null && !val.trim().isEmpty());
            }

            result.add(ClientSummaryDTO.builder()
                    .id(clientId)
                    .name(name)
                    .phone(phone)
                    .email(email)
                    .isGuest(isGuest)
                    .totalAppointments(0)
                    .lastAppointmentDate(null)
                    .hasFilledCard(hasFilledCard)
                    .cardUpdatedAt(card.getUpdatedAt())
                    .cardUpdatedByName(card.getUpdatedBy() != null ? card.getUpdatedBy().getName() : null)
                    .build());
        }

        return result;
    }

    @Transactional
    public String createClientManually(Long centerId, String name, String phone, String email, String initialNotes, User worker) {
        if (name == null || name.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre y el teléfono son obligatorios para dar de alta a un cliente.");
        }

        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado"));

        String cleanName = name.trim();
        String cleanPhone = phone.trim();
        String cleanEmail = email != null && !email.trim().isEmpty() ? email.trim() : null;

        Optional<User> existingUserOpt = userRepository.findByPhone(cleanPhone);
        if (existingUserOpt.isEmpty() && cleanEmail != null) {
            existingUserOpt = userRepository.findByEmail(cleanEmail);
        }

        User clientUser;
        if (existingUserOpt.isPresent() && existingUserOpt.get().getRole() == Role.CLIENT) {
            clientUser = existingUserOpt.get();
        } else {
            String autoEmail = cleanEmail != null ? cleanEmail : "cliente_" + cleanPhone.replaceAll("[^0-9]", "") + "@cbelleza.local";
            if (userRepository.findByEmailIgnoreCase(autoEmail).isPresent()) {
                autoEmail = "cliente_" + System.currentTimeMillis() + "@cbelleza.local";
            }
            clientUser = userService.registerUser(cleanName, autoEmail, cleanPhone, "password123", Role.CLIENT, null);
        }

        ClientCard card = clientCardRepository.findByCenterIdAndClientId(centerId, clientUser.getId())
                .orElseGet(() -> ClientCard.builder()
                        .center(center)
                        .client(clientUser)
                        .dataJson("{}")
                        .updatedAt(LocalDateTime.now())
                        .build());
        String redirectUrl = "/worker/clients/" + clientUser.getId();

        if (initialNotes != null && !initialNotes.trim().isEmpty()) {
            Map<String, String> data = parseCardData(card.getDataJson());
            String trimmedNotes = initialNotes.trim();
            data.put("observaciones_preferencias", trimmedNotes);
            data.put("observaciones_iniciales", trimmedNotes);
            data.put("Observaciones iniciales", trimmedNotes);
            data.put("notas", trimmedNotes);

            // Also map to first textarea field of the center template if present
            try {
                ClientCardTemplate tpl = getOrCreateTemplateForCenter(centerId);
                List<ClientCardFieldDTO> fields = parseTemplateFields(tpl.getFieldsJson());
                for (ClientCardFieldDTO f : fields) {
                    if ("textarea".equalsIgnoreCase(f.getType()) && (!data.containsKey(f.getId()) || data.get(f.getId()).isEmpty())) {
                        data.put(f.getId(), trimmedNotes);
                    }
                }
            } catch (Exception ignored) {}

            try {
                card.setDataJson(objectMapper.writeValueAsString(data));
            } catch (Exception ignored) {}
        }

        card.setUpdatedAt(LocalDateTime.now());
        card.setUpdatedBy(worker);
        clientCardRepository.save(card);

        return redirectUrl;
    }

    @Transactional(readOnly = true)
    public List<Appointment> getClientAppointmentsInCenter(Long centerId, Long clientId, String guestPhone) {
        if (clientId != null) {
            return appointmentRepository.findByCenterIdAndClientIdOrderByDateTimeDesc(centerId, clientId);
        } else if (guestPhone != null && !guestPhone.trim().isEmpty()) {
            return appointmentRepository.findByCenterIdAndGuestPhoneOrderByDateTimeDesc(centerId, guestPhone);
        }
        return Collections.emptyList();
    }
}
