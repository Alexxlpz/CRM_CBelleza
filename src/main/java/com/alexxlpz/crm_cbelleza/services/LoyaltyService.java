package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMovement;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMovement.Reason;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.LoyaltySettingsForm;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.LoyaltyMovementRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fidelización por puntos de cada centro. Los tratamientos suman cuando la cita pasa a completada
 * (se apuntan al consultar el saldo, ya que el estado de la cita se calcula al leerla) y los productos al venderlos.
 * El trabajador canjea la recompensa cuando el cliente llega al umbral.
 */
@Service
@Transactional
public class LoyaltyService {

    public static final int MAX_POINTS_PER_UNIT = 1000;
    public static final int MAX_REWARD_POINTS = 100_000;
    public static final int REWARD_TEXT_MAX_LENGTH = 200;
    /** Movimientos que se enseñan en la ficha. */
    private static final int RECENT_MOVEMENTS = 6;

    private final LoyaltyMovementRepository movements;
    private final AppointmentRepository appointments;
    private final CenterRepository centers;
    private final UserRepository users;
    private final Clock clock;

    public LoyaltyService(LoyaltyMovementRepository movements, AppointmentRepository appointments,
                          CenterRepository centers, UserRepository users, Clock clock) {
        this.movements = movements;
        this.appointments = appointments;
        this.centers = centers;
        this.users = users;
        this.clock = clock;
    }

    /** Saldo y estado de la tarjeta de puntos de un cliente (ya comprobado que es del centro). */
    public LoyaltyCard cardFor(Center center, User client) {
        if (!center.isLoyaltyEnabled()) {
            return LoyaltyCard.disabled();
        }
        awardCompletedTreatments(center, client);
        List<LoyaltyMovement> history = movements.findByCenterIdAndClientIdOrderByCreatedAtDescIdDesc(center.getId(), client.getId());
        int balance = history.stream().mapToInt(LoyaltyMovement::getPoints).sum();
        return new LoyaltyCard(true, balance, center.getLoyaltyRewardPoints(), center.getLoyaltyRewardText(),
                center.getLoyaltyMode(), center.getLoyaltyPointsPerUnit(),
                history.stream().limit(RECENT_MOVEMENTS).toList());
    }

    /** Cómo suma puntos el centro, sin saldo: para la previsión de la venta cuando aún no se ha elegido cliente. */
    public LoyaltyCard settingsOf(Center center) {
        if (!center.isLoyaltyEnabled()) {
            return LoyaltyCard.disabled();
        }
        return new LoyaltyCard(true, 0, center.getLoyaltyRewardPoints(), center.getLoyaltyRewardText(),
                center.getLoyaltyMode(), center.getLoyaltyPointsPerUnit(), List.of());
    }

    /**
     * Tarjetas del propio cliente: una por cada centro con la tarjeta activada donde ha tenido citas o tiene puntos.
     * Con saldo primero; luego por nombre del centro.
     */
    public List<ClientLoyaltyCard> cardsOfClient(Long clientId) {
        User client = users.findById(clientId).orElseThrow();
        Map<Long, Center> visited = new LinkedHashMap<>();
        appointments.findByClientIdOrderByDateTimeDesc(clientId).stream()
                .filter(app -> app.getStatus() == AppointmentStatus.CONFIRMED || app.getStatus() == AppointmentStatus.COMPLETED)
                .forEach(app -> visited.putIfAbsent(app.getCenter().getId(), app.getCenter()));
        movements.findByClientId(clientId).forEach(m -> visited.putIfAbsent(m.getCenter().getId(), m.getCenter()));
        return visited.values().stream()
                .filter(Center::isLoyaltyEnabled)
                .map(center -> new ClientLoyaltyCard(center.getId(), center.getName(), cardFor(center, client)))
                .sorted(Comparator.comparing((ClientLoyaltyCard c) -> c.card().balance() == 0)
                        .thenComparing(ClientLoyaltyCard::centerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Puntos de una venta de productos (si el centro tiene la fidelización activada). */
    public void awardSale(ProductSale sale, Long workerId) {
        Center center = sale.getCenter();
        if (!center.isLoyaltyEnabled()) {
            return;
        }
        int points = center.getLoyaltyMode().pointsFor(sale.getTotal(), sale.getQuantity(), center.getLoyaltyPointsPerUnit());
        if (points <= 0) {
            return;
        }
        movements.save(LoyaltyMovement.builder()
                .center(center)
                .client(sale.getClient())
                .points(points)
                .reason(Reason.PRODUCT)
                .description(sale.getQuantity() + " × " + sale.getProduct().getName())
                .productSaleId(sale.getId())
                .createdAt(CenterClock.now(clock, center))
                .createdBy(workerId != null ? users.getReferenceById(workerId) : null)
                .build());
    }

    /** Quita los puntos que dio una venta anulada. */
    public void revokeSale(Long saleId) {
        movements.deleteByProductSaleId(saleId);
    }

    /** Canjea la recompensa del centro: resta el umbral del saldo. */
    public void redeem(Center center, User client, Long workerId) {
        // Bloquea el centro: dos pulsaciones seguidas no pueden canjear dos veces el mismo saldo
        centers.findByIdForUpdate(center.getId());
        LoyaltyCard card = cardFor(center, client);
        if (!card.enabled()) {
            throw new BusinessRuleException("Tu centro no tiene activada la tarjeta de puntos. Actívala en «Mi Centro».");
        }
        if (!card.canRedeem()) {
            throw new BusinessRuleException("Le faltan " + card.missing() + " puntos para canjear la recompensa.");
        }
        movements.save(LoyaltyMovement.builder()
                .center(center)
                .client(client)
                .points(-card.rewardPoints())
                .reason(Reason.REDEMPTION)
                .description(card.rewardText())
                .createdAt(CenterClock.now(clock, center))
                .createdBy(workerId != null ? users.getReferenceById(workerId) : null)
                .build());
    }

    /** Configuración desde «Mi Centro». Al activarla empieza a contar desde ahora (las citas pasadas no suman). */
    public Center updateSettings(Long centerId, LoyaltySettingsForm form) {
        Center center = centers.findById(centerId).orElseThrow();
        LoyaltyMode mode = parseMode(form.mode());
        if (mode == LoyaltyMode.OFF) {
            center.setLoyaltyMode(LoyaltyMode.OFF);
            return centers.save(center);
        }
        int perUnit = form.pointsPerUnit() == null ? 0 : form.pointsPerUnit();
        if (perUnit < 1 || perUnit > MAX_POINTS_PER_UNIT) {
            throw new BusinessRuleException("Los puntos que se ganan tienen que estar entre 1 y " + MAX_POINTS_PER_UNIT + ".");
        }
        int rewardPoints = form.rewardPoints() == null ? 0 : form.rewardPoints();
        if (rewardPoints < 1 || rewardPoints > MAX_REWARD_POINTS) {
            throw new BusinessRuleException("Los puntos para la recompensa tienen que estar entre 1 y " + MAX_REWARD_POINTS + ".");
        }
        if (FormText.isBlank(form.rewardText())) {
            throw new BusinessRuleException("Escribe qué recompensa da el centro (por ejemplo, «Manicura gratis»).");
        }
        String rewardText = form.rewardText().trim();
        if (rewardText.length() > REWARD_TEXT_MAX_LENGTH) {
            throw new BusinessRuleException("La recompensa no puede pasar de " + REWARD_TEXT_MAX_LENGTH + " caracteres.");
        }
        if (!center.isLoyaltyEnabled()) {
            center.setLoyaltySince(CenterClock.now(clock, center));
        }
        center.setLoyaltyMode(mode);
        center.setLoyaltyPointsPerUnit(perUnit);
        center.setLoyaltyRewardPoints(rewardPoints);
        center.setLoyaltyRewardText(rewardText);
        return centers.save(center);
    }

    /** Apunta los puntos de las citas completadas desde que el centro activó la tarjeta y que aún no los tienen. */
    private void awardCompletedTreatments(Center center, User client) {
        Set<Long> rewarded = new HashSet<>(movements.findRewardedAppointmentIds(center.getId(), client.getId()));
        LocalDateTime since = center.getLoyaltySince();
        for (Appointment app : appointments.findByCenterIdAndClientIdOrderByDateTimeDesc(center.getId(), client.getId())) {
            if (app.getStatus() != AppointmentStatus.COMPLETED || rewarded.contains(app.getId())
                    || (since != null && app.getDateTime().isBefore(since))) {
                continue;
            }
            int points = center.getLoyaltyMode().pointsFor(app.getPrice(), 1, center.getLoyaltyPointsPerUnit());
            if (points <= 0) {
                continue;
            }
            movements.save(LoyaltyMovement.builder()
                    .center(center)
                    .client(client)
                    .points(points)
                    .reason(Reason.TREATMENT)
                    .description(app.getTreatment().getName())
                    .appointmentId(app.getId())
                    .createdAt(app.getEndDateTime())
                    .build());
        }
    }

    private static LoyaltyMode parseMode(String value) {
        try {
            return value == null ? LoyaltyMode.OFF : LoyaltyMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Elige cómo se ganan los puntos.");
        }
    }

    /** Tarjeta de un centro tal como la ve el cliente en «Mis citas». */
    public record ClientLoyaltyCard(Long centerId, String centerName, LoyaltyCard card) {
    }

    /** Lo que pinta la ficha del cliente. */
    public record LoyaltyCard(boolean enabled, int balance, int rewardPoints, String rewardText,
                              LoyaltyMode mode, int pointsPerUnit, List<LoyaltyMovement> recent) {

        static LoyaltyCard disabled() {
            return new LoyaltyCard(false, 0, 0, null, LoyaltyMode.OFF, 0, List.of());
        }

        public boolean canRedeem() {
            return enabled && rewardPoints > 0 && balance >= rewardPoints;
        }

        public int missing() {
            return Math.max(0, rewardPoints - balance);
        }

        /** Avance hacia la recompensa, para la barra (0 a rewardPoints). */
        public int progress() {
            return Math.max(0, Math.min(balance, rewardPoints));
        }
    }
}
