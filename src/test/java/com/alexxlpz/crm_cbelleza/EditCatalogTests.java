package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.forms.WorkerAppointmentForm;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.ProductSaleService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Editar la carta y el inventario (18 nov 2026): editar, ocultar y borrar tratamientos; editar productos propios;
 * aviso de stock bajo por producto (sin aviso por defecto) y corregir los datos de un cliente creado por el centro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EditCatalogTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private InventoryRepository inventories;
    @Autowired
    private ProductRepository products;
    @Autowired
    private TreatmentService treatmentService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private NotificationService notifications;
    @Autowired
    private DashboardService dashboard;
    @Autowired
    private ProductSaleService saleService;
    @Autowired
    private ClientCardService clientCards;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ClientCardRepository clientCardRows;

    private User carlos;
    private Long centerId;
    private Long otherCenterId;
    private Treatment treatment;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        centerId = carlos.getCenter().getId();
        otherCenterId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_2).orElseThrow().getCenter().getId();
        treatment = treatments.findByCenterId(centerId).getFirst();
    }

    private static LocalDateTime farMonday(int weeksAhead, int hour) {
        LocalDate monday = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(monday, LocalTime.of(hour, 0));
    }

    private User sofia() {
        return users.findByEmailIgnoreCase("sofia@gmail.com").orElseThrow();
    }

    private ResultActions editTreatment(String as, Long id, String price, String duration) throws Exception {
        return mvc.perform(post("/worker/treatments/" + id + "/edit").with(csrf())
                .param("name", "Manicura nueva").param("type", "FACIAL").param("description", "  Otra descripción  ")
                .param("price", price).param("duration", duration)
                .with(TestUsers.as(users, as)));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /* ---------- Tratamientos ---------- */

    @Test
    void ownerEditsTreatmentAndBookedAppointmentsKeepPriceAndDuration() throws Exception {
        int oldDuration = treatment.getDuration();
        double oldPrice = treatment.getPrice();
        Appointment booked = bookingService.requestBooking(sofia().getId(), centerId, treatment.getId(), farMonday(20, 10));
        flushAndClear();

        editTreatment(TestUsers.WORKER_CENTER_1, treatment.getId(), "99.999", String.valueOf(oldDuration + 30))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("mantienen su precio y su duración")));
        flushAndClear();

        Treatment saved = treatments.findById(treatment.getId()).orElseThrow();
        assertEquals("Manicura nueva", saved.getName());
        assertEquals("Otra descripción", saved.getDescription());
        assertEquals(100.0, saved.getPrice());
        assertEquals(oldDuration + 30, saved.getDuration());

        Appointment again = appointments.findById(booked.getId()).orElseThrow();
        assertEquals(oldPrice, again.getPrice());
        assertEquals(oldDuration, again.getDurationMinutes());
        assertEquals(again.getDateTime().plusMinutes(oldDuration), again.getEndDateTime());

        // Una cita nueva ya lleva la duración nueva
        Appointment fresh = bookingService.requestBooking(sofia().getId(), centerId, treatment.getId(), farMonday(21, 10));
        assertEquals(oldDuration + 30, fresh.getDurationMinutes());
    }

    @Test
    void treatmentEditIsValidated() throws Exception {
        editTreatment(TestUsers.WORKER_CENTER_1, treatment.getId(), "-1", "30")
                .andExpect(flash().attribute("errorMessage", containsString("negativo")));
        editTreatment(TestUsers.WORKER_CENTER_1, treatment.getId(), "20", "2")
                .andExpect(flash().attribute("errorMessage", containsString("duración")));
        editTreatment(TestUsers.WORKER_CENTER_1, treatment.getId(), "20000", "30")
                .andExpect(flash().attribute("errorMessage", containsString("10.000")));

        // Con oferta, el precio de carta no puede quedar por debajo de ella
        treatment.setOfferPrice(15.0);
        treatments.saveAndFlush(treatment);
        editTreatment(TestUsers.WORKER_CENTER_1, treatment.getId(), "15", "30")
                .andExpect(flash().attribute("errorMessage", containsString("mayor que el de la oferta")));
        assertNotEquals("Manicura nueva", treatments.findById(treatment.getId()).orElseThrow().getName());
    }

    @Test
    void onlyTheOwnerOfTheSameCenterChangesTheMenu() throws Exception {
        String name = treatment.getName();
        editTreatment(LUCIA, treatment.getId(), "20", "30")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", not(containsString("guardado"))));
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/hide").with(csrf()).with(TestUsers.as(users, LUCIA)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/delete").with(csrf()).with(TestUsers.as(users, LUCIA)))
                .andExpect(status().is3xxRedirection());
        flushAndClear();
        Treatment saved = treatments.findById(treatment.getId()).orElseThrow();
        assertEquals(name, saved.getName());
        assertFalse(saved.isHidden());

        // Otro centro: 404
        editTreatment(TestUsers.WORKER_CENTER_2, treatment.getId(), "20", "30").andExpect(status().isNotFound());
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/hide").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2))).andExpect(status().isNotFound());
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/delete").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2))).andExpect(status().isNotFound());
    }

    @Test
    void hiddenTreatmentLeavesTheMenuButKeepsItsAppointments() throws Exception {
        Appointment booked = bookingService.requestBooking(sofia().getId(), centerId, treatment.getId(), farMonday(20, 10));
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/hide").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", containsString("Ocultos")));
        flushAndClear();

        String slug = carlos.getCenter().getSlug();
        mvc.perform(get("/c/" + slug)).andExpect(content().string(not(containsString("value=\"" + treatment.getId() + "\""))))
                .andExpect(content().string(not(containsString("tratamiento=" + treatment.getId() + "\""))));
        assertTrue(treatmentService.getVisibleTreatmentsByCenter(centerId).stream().noneMatch(t -> t.getId().equals(treatment.getId())));

        // Ni el cliente ni el equipo dan citas nuevas con él
        assertThrows(BusinessRuleException.class, () -> bookingService.requestBooking(
                sofia().getId(), centerId, treatment.getId(), farMonday(22, 10)));
        assertThrows(BusinessRuleException.class, () -> bookingService.createByWorker(centerId, carlos.getId(),
                new WorkerAppointmentForm(sofia().getId(), null, null, null, treatment.getId(),
                        farMonday(22, 12).toString(), carlos.getId(), null, true)));

        // La cita que ya tenía sigue ahí y en la carta del trabajador sale en «Ocultos»
        assertTrue(appointments.findById(booked.getId()).isPresent());
        mvc.perform(get("/worker/treatments").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Ocultos (1)")))
                .andExpect(content().string(containsString("/worker/treatments/" + treatment.getId() + "/show")));
        mvc.perform(get("/worker/treatments").with(TestUsers.as(users, LUCIA)))
                .andExpect(content().string(not(containsString("Ocultos ("))));

        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/show").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))).andExpect(status().is3xxRedirection());
        flushAndClear();
        assertFalse(treatments.findById(treatment.getId()).orElseThrow().isHidden());
    }

    @Test
    void onlyATreatmentWithoutAppointmentsIsDeleted() throws Exception {
        bookingService.requestBooking(sofia().getId(), centerId, treatment.getId(), farMonday(20, 10));
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("errorMessage", containsString("Ocúltalo")));
        assertTrue(treatments.findById(treatment.getId()).isPresent());

        mvc.perform(post("/worker/treatments/add").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                .param("name", "Prueba sin citas").param("type", "OTRO").param("description", "x")
                .param("price", "10").param("duration", "30"));
        Treatment unused = treatments.findByCenterId(centerId).stream()
                .filter(t -> t.getName().equals("Prueba sin citas")).findFirst().orElseThrow();
        mvc.perform(post("/worker/treatments/" + unused.getId() + "/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", containsString("borrado")));
        flushAndClear();
        assertTrue(treatments.findById(unused.getId()).isEmpty());
    }

    /* ---------- Productos ---------- */

    private Inventory ownProduct() {
        Product product = inventoryService.addToInventory(centerId,
                new InventoryItemForm("new", null, "Bálsamo propio", "Viejo", "Rostro", 5.0, 4));
        return inventories.findByCenterIdAndProductId(centerId, product.getId()).orElseThrow();
    }

    private ResultActions editProduct(String as, Long inventoryId, String price) throws Exception {
        return mvc.perform(post("/worker/inventory/" + inventoryId + "/edit").with(csrf())
                .param("name", "Bálsamo renovado").param("category", "Cuerpo").param("price", price)
                .param("description", "Nueva fórmula")
                .with(TestUsers.as(users, as)));
    }

    @Test
    void ownerEditsOwnProductsButNotCatalogOnes() throws Exception {
        Inventory own = ownProduct();
        editProduct(TestUsers.WORKER_CENTER_1, own.getId(), "7.456")
                .andExpect(flash().attribute("successMessage", containsString("guardado")));
        flushAndClear();
        Product saved = products.findById(own.getProduct().getId()).orElseThrow();
        assertEquals("Bálsamo renovado", saved.getName());
        assertEquals("Cuerpo", saved.getCategory());
        assertEquals(7.46, saved.getPrice());
        assertEquals("Nueva fórmula", saved.getDescription());

        Inventory catalog = inventories.findByCenterId(centerId).stream()
                .filter(i -> i.getProduct().getCenterId() == null).findFirst().orElseThrow();
        String catalogName = catalog.getProduct().getName();
        editProduct(TestUsers.WORKER_CENTER_1, catalog.getId(), "1")
                .andExpect(flash().attribute("errorMessage", containsString("catálogo general")));
        editProduct(TestUsers.WORKER_CENTER_1, own.getId(), "-3")
                .andExpect(flash().attribute("errorMessage", containsString("precio")));
        flushAndClear();
        assertEquals(catalogName, products.findById(catalog.getProduct().getId()).orElseThrow().getName());
    }

    @Test
    void productEditIsForTheOwnerOfThatCenter() throws Exception {
        Inventory own = ownProduct();
        editProduct(LUCIA, own.getId(), "9").andExpect(status().is3xxRedirection());
        editProduct(TestUsers.WORKER_CENTER_2, own.getId(), "9").andExpect(status().isNotFound());
        mvc.perform(post("/worker/inventory/" + own.getId() + "/low-stock").param("units", "2").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2))).andExpect(status().isNotFound());
        mvc.perform(post("/worker/inventory/" + own.getId() + "/low-stock").param("units", "2").with(csrf())
                .with(TestUsers.as(users, LUCIA))).andExpect(status().is3xxRedirection());
        flushAndClear();
        Inventory saved = inventories.findById(own.getId()).orElseThrow();
        assertEquals("Bálsamo propio", saved.getProduct().getName());
        assertNull(saved.getLowStockAlert());
    }

    /* ---------- Aviso de stock bajo ---------- */

    @Test
    void withoutAlertThereIsNoLowStockWarningOnlySoldOut() throws Exception {
        Inventory item = ownProduct();
        item.setStock(1);
        inventories.saveAndFlush(item);
        String name = item.getProduct().getName();
        int lowBefore = dashboard.statsFor(centerId).lowStockCount();

        assertTrue(notifications.getNotificationsForCenter(centerId).stream().noneMatch(n -> n.message().contains(name)));
        mvc.perform(post("/worker/inventory/" + item.getId() + "/adjust-stock").param("change", "1").with(csrf())
                        .with(TestUsers.as(users, LUCIA)))
                .andExpect(jsonPath("$.stock").value(2))
                .andExpect(jsonPath("$.lowStock").value(false));

        // Con aviso en 5: avisa la campana, el panel cuenta uno más y el ajuste dice «bajo»
        mvc.perform(post("/worker/inventory/" + item.getId() + "/low-stock").param("units", "5").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", containsString("5 unidades o menos")));
        flushAndClear();
        assertTrue(notifications.getNotificationsForCenter(centerId).stream()
                .anyMatch(n -> n.message().equals("Stock bajo: quedan 2 ud de " + name + ".")));
        assertEquals(lowBefore + 1, dashboard.statsFor(centerId).lowStockCount());
        mvc.perform(post("/worker/inventory/" + item.getId() + "/adjust-stock").param("change", "1").with(csrf())
                        .with(TestUsers.as(users, LUCIA)))
                .andExpect(jsonPath("$.lowStock").value(true));

        // Vacío lo quita; agotado avisa siempre
        mvc.perform(post("/worker/inventory/" + item.getId() + "/low-stock").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
        Inventory saved = inventories.findById(item.getId()).orElseThrow();
        assertNull(saved.getLowStockAlert());
        saved.setStock(0);
        inventories.saveAndFlush(saved);
        assertTrue(notifications.getNotificationsForCenter(centerId).stream()
                .anyMatch(n -> n.message().startsWith("¡Agotado! " + name)));
    }

    @Test
    void lowStockAlertIsValidated() throws Exception {
        Inventory item = ownProduct();
        for (String units : new String[]{"0", "1000", "-2"}) {
            mvc.perform(post("/worker/inventory/" + item.getId() + "/low-stock").param("units", units).with(csrf())
                            .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                    .andExpect(flash().attribute("errorMessage", containsString("de 1 a 999")));
        }
        assertNull(inventories.findById(item.getId()).orElseThrow().getLowStockAlert());
    }

    @Test
    void saleMessageUsesTheProductAlert() {
        Inventory item = ownProduct();
        item.setStock(2);
        var withoutAlert = new ProductSaleService.SaleResult(null, 2, item.isLowStock());
        assertFalse(withoutAlert.lowStock());
        item.setLowStockAlert(3);
        assertTrue(item.isLowStock());
        item.setStock(0);
        assertFalse(item.isLowStock());
    }

    /* ---------- Datos del cliente ---------- */

    private Long manualClient(Long center, String phone) {
        return clientCards.createClientManually(center, new NewClientForm("Ana Pruebas", phone, null, null), null).clientId();
    }

    private ResultActions editContact(String as, Long clientId, String name, String phone, String email) throws Exception {
        return mvc.perform(post("/worker/clients/" + clientId + "/contact").with(csrf())
                .param("name", name).param("phone", phone).param("email", email)
                .with(TestUsers.as(users, as)));
    }

    @Test
    void anyWorkerFixesTheDataOfAClientCreatedByTheCenter() throws Exception {
        Long clientId = manualClient(centerId, "699 000 111");
        mvc.perform(get("/worker/clients/" + clientId).with(TestUsers.as(users, LUCIA)))
                .andExpect(content().string(containsString("Editar datos")))
                // El correo inventado no se enseña
                .andExpect(content().string(not(containsString(User.PLACEHOLDER_EMAIL_DOMAIN))));

        editContact(LUCIA, clientId, "  Ana   Pruebas Gil ", "+34 600 111 333", "ana.gil@example.com")
                .andExpect(flash().attribute("successMessage", "Datos del cliente guardados."));
        flushAndClear();
        User saved = users.findById(clientId).orElseThrow();
        assertEquals("Ana Pruebas Gil", saved.getName());
        assertEquals("+34 600 111 333", saved.getPhone());
        assertEquals("ana.gil@example.com", saved.getEmail());

        // Un correo que ya tiene otra cuenta no se guarda, el resto sí
        editContact(LUCIA, clientId, "Ana Gil", "600111333", "sofia@gmail.com")
                .andExpect(flash().attribute("successMessage", containsString("menos el correo")));
        flushAndClear();
        saved = users.findById(clientId).orElseThrow();
        assertEquals("Ana Gil", saved.getName());
        assertEquals("ana.gil@example.com", saved.getEmail());

        // Sin correo vuelve a uno inventado
        editContact(LUCIA, clientId, "Ana Gil", "600111333", "").andExpect(status().is3xxRedirection());
        flushAndClear();
        assertFalse(users.findById(clientId).orElseThrow().hasRealEmail());

        editContact(LUCIA, clientId, "", "600111333", "").andExpect(flash().attribute("errorMessage", containsString("obligatorios")));
        editContact(LUCIA, clientId, "Ana", "12", "").andExpect(flash().attribute("errorMessage", containsString("teléfono")));
        editContact(LUCIA, clientId, "Ana", "600111333", "no-es-correo").andExpect(flash().attribute("errorMessage", containsString("correo")));
    }

    @Test
    void clientsWithTheirOwnAccountOrFromAnotherCenterAreNotEdited() throws Exception {
        User sofia = sofia();
        String sofiaName = sofia.getName();
        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(not(containsString("Editar datos"))));
        editContact(TestUsers.WORKER_CENTER_1, sofia.getId(), "Otra", "600000000", "")
                .andExpect(flash().attribute("errorMessage", containsString("propia cuenta")));
        assertEquals(sofiaName, users.findById(sofia.getId()).orElseThrow().getName());

        Long otherClient = manualClient(otherCenterId, "611 222 333");
        editContact(TestUsers.WORKER_CENTER_1, otherClient, "Otra", "600000000", "").andExpect(status().isNotFound());

        // Creado aquí pero con ficha también en otro centro: tampoco (lo comparten)
        Long shared = manualClient(centerId, "622 333 444");
        clientCardRows.saveAndFlush(ClientCard.builder()
                .center(users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_2).orElseThrow().getCenter())
                .client(users.findById(shared).orElseThrow()).dataJson("{}").build());
        flushAndClear();
        assertFalse(clientCards.canEditContact(centerId, users.findById(shared).orElseThrow()));
        editContact(TestUsers.WORKER_CENTER_1, shared, "Otra", "600000000", "")
                .andExpect(flash().attribute("errorMessage", containsString("propia cuenta")));
    }
}
