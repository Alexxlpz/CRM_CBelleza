package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMovement;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.LoyaltyMovementRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductSaleRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Venta de productos desde la ficha (resta stock) y tarjeta de puntos del centro. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSaleAndLoyaltyTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private InventoryRepository inventories;
    @Autowired
    private ProductSaleRepository sales;
    @Autowired
    private LoyaltyMovementRepository movements;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;

    private User carlos;
    private User sofia;
    private Center center;
    private Inventory item;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
        item = inventories.findByCenterId(center.getId()).stream()
                .filter(i -> i.getStock() >= 5)
                .findFirst().orElseThrow();
        configureLoyalty(LoyaltyMode.PER_EURO, 1, 50);
    }

    private void configureLoyalty(LoyaltyMode mode, int perUnit, int rewardPoints) {
        center.setLoyaltyMode(mode);
        center.setLoyaltyPointsPerUnit(perUnit);
        center.setLoyaltyRewardPoints(rewardPoints);
        center.setLoyaltyRewardText("Manicura gratis");
        center.setLoyaltySince(LocalDateTime.now().minusYears(1));
        centers.saveAndFlush(center);
    }

    private ResultActions sell(String worker, Long clientId, Long inventoryId, int quantity) throws Exception {
        return mvc.perform(post("/worker/clients/" + clientId + "/sales").with(csrf())
                .param("inventoryId", String.valueOf(inventoryId))
                .param("quantity", String.valueOf(quantity))
                .with(TestUsers.as(users, worker)));
    }

    private int balance() {
        return movements.findByCenterIdAndClientIdOrderByCreatedAtDescIdDesc(center.getId(), sofia.getId()).stream()
                .mapToInt(LoyaltyMovement::getPoints).sum();
    }

    @Test
    void saleSubtractsStockKeepsHistoryAndAwardsPoints() throws Exception {
        int stockBefore = item.getStock();
        int pointsBefore = balance();

        sell(TestUsers.WORKER_CENTER_1, sofia.getId(), item.getId(), 2)
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Venta guardada: 2 × " + item.getProduct().getName())));

        assertEquals(stockBefore - 2, inventories.findById(item.getId()).orElseThrow().getStock());
        List<ProductSale> history = sales.findByCenterIdAndClientIdOrderBySoldAtDesc(center.getId(), sofia.getId());
        assertEquals(1, history.size());
        assertEquals(item.getProduct().getPrice(), history.getFirst().getUnitPrice());
        assertEquals(pointsBefore + (int) Math.floor(item.getProduct().getPrice() * 2), balance());

        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Productos comprados")))
                .andExpect(content().string(containsString("Tarjeta de puntos")));
    }

    @Test
    void cannotSellMoreThanTheStock() throws Exception {
        int stock = item.getStock();

        sell(TestUsers.WORKER_CENTER_1, sofia.getId(), item.getId(), stock + 1)
                .andExpect(flash().attribute("errorMessage", containsString("Solo quedan " + stock)));

        assertEquals(stock, inventories.findById(item.getId()).orElseThrow().getStock());
        assertTrue(sales.findByCenterIdAndClientIdOrderBySoldAtDesc(center.getId(), sofia.getId()).isEmpty());
    }

    @Test
    void cancellingASaleReturnsStockAndPoints() throws Exception {
        int stock = item.getStock();
        int pointsBefore = balance();
        sell(TestUsers.WORKER_CENTER_1, sofia.getId(), item.getId(), 3);
        ProductSale sale = sales.findByCenterIdAndClientIdOrderBySoldAtDesc(center.getId(), sofia.getId()).getFirst();

        mvc.perform(post("/worker/clients/" + sofia.getId() + "/sales/" + sale.getId() + "/cancel").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().is3xxRedirection());

        assertEquals(stock, inventories.findById(item.getId()).orElseThrow().getStock());
        assertTrue(sales.findById(sale.getId()).isEmpty());
        assertEquals(pointsBefore, balance());
    }

    @Test
    void completedTreatmentsEarnPointsOnceAndRewardCanBeRedeemed() throws Exception {
        configureLoyalty(LoyaltyMode.PER_ITEM, 10, 20);
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        Appointment done = appointments.saveAndFlush(Appointment.builder()
                .center(center).treatment(treatment).client(sofia).worker(carlos)
                .status(AppointmentStatus.CONFIRMED)
                .dateTime(LocalDateTime.now().minusDays(2).withHour(10).withMinute(0))
                .createdAt(LocalDateTime.now().minusDays(9))
                .build());

        // Abrir la ficha apunta los puntos de las citas completadas, y solo una vez
        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
        long rewardedTimes = movements.findByCenterIdAndClientIdOrderByCreatedAtDescIdDesc(center.getId(), sofia.getId()).stream()
                .filter(m -> done.getId().equals(m.getAppointmentId())).count();
        assertEquals(1, rewardedTimes);
        int points = balance();
        assertTrue(points >= 20, "Sofía ya tiene citas completadas que suman al menos 20 puntos");

        mvc.perform(post("/worker/clients/" + sofia.getId() + "/loyalty/redeem").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", containsString("Manicura gratis")));
        assertEquals(points - 20, balance());
    }

    @Test
    void cannotRedeemWithoutEnoughPoints() throws Exception {
        configureLoyalty(LoyaltyMode.PER_ITEM, 1, 100_000);

        mvc.perform(post("/worker/clients/" + sofia.getId() + "/loyalty/redeem").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("errorMessage", containsString("Le faltan")));
    }

    @Test
    void centerConfiguresLoyaltyFromMyCenter() throws Exception {
        center.setLoyaltyMode(LoyaltyMode.OFF);
        center.setLoyaltySince(null);
        centers.saveAndFlush(center);

        mvc.perform(post("/worker/center/loyalty").with(csrf())
                        .param("mode", "PER_ITEM").param("pointsPerUnit", "5").param("rewardPoints", "60")
                        .param("rewardText", "Peinado gratis")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("loyaltyMessage", containsString("Tarjeta de puntos activada")));

        Center saved = centers.findById(center.getId()).orElseThrow();
        assertEquals(LoyaltyMode.PER_ITEM, saved.getLoyaltyMode());
        assertEquals(60, saved.getLoyaltyRewardPoints());
        assertNotNull(saved.getLoyaltySince());

        mvc.perform(post("/worker/center/loyalty").with(csrf())
                        .param("mode", "PER_EURO").param("pointsPerUnit", "1").param("rewardPoints", "60").param("rewardText", " ")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("loyaltyError", containsString("Escribe qué recompensa")));
    }

    @Test
    void myCenterKeepsWhatWasTypedWhenSavingFails() throws Exception {
        mvc.perform(get("/worker/center")
                        .flashAttr("loyaltyForm", new com.alexxlpz.crm_cbelleza.forms.LoyaltySettingsForm("PER_ITEM", 7, 70, ""))
                        .flashAttr("loyaltyError", "Escribe qué recompensa da el centro.")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escribe qué recompensa da el centro.")))
                .andExpect(content().string(containsString("value=\"70\"")));
    }

    @Test
    void clientSeesHisPointsInMyAppointments() throws Exception {
        sell(TestUsers.WORKER_CENTER_1, sofia.getId(), item.getId(), 1);

        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mis puntos")))
                .andExpect(content().string(containsString(center.getName())))
                .andExpect(content().string(containsString("Manicura gratis")));
    }

    @Test
    void loyaltyModesComputePoints() {
        assertEquals(10, LoyaltyMode.PER_EURO.pointsFor(10.99, 1, 1));
        assertEquals(15, LoyaltyMode.PER_ITEM.pointsFor(99, 3, 5));
        assertEquals(0, LoyaltyMode.OFF.pointsFor(99, 3, 5));
    }
}
