package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.entities.*;
import com.alexxlpz.crm_cbelleza.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Arrays;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AppointmentRepository appointmentRepository;
    private final TreatmentRepository treatmentRepository;

    public DatabaseSeeder(CenterRepository centerRepository,
                          UserRepository userRepository,
                          ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          AppointmentRepository appointmentRepository,
                          TreatmentRepository treatmentRepository) {
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.appointmentRepository = appointmentRepository;
        this.treatmentRepository = treatmentRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (centerRepository.count() > 0) {
            return; // Already seeded
        }

        // 1. Seed Centers
        Center c1 = Center.builder()
                .name("Belleza Centro Histórico")
                .address("Calle Mayor 45, Madrid")
                .phone("910111222")
                .email("centro@cbelleza.com")
                .latitude(40.4168)
                .longitude(-3.7038)
                .build();
        Center c2 = Center.builder()
                .name("Belleza Plaza Norte")
                .address("Av. de la Ilustración 12, Madrid")
                .phone("910222333")
                .email("plazanorte@cbelleza.com")
                .latitude(40.5401)
                .longitude(-3.6143)
                .build();
        Center c3 = Center.builder()
                .name("Belleza Las Condes")
                .address("Av. Las Condes 8900, Santiago")
                .phone("220333444")
                .email("lascondes@cbelleza.com")
                .latitude(-33.4000)
                .longitude(-70.5667)
                .build();
        centerRepository.saveAll(Arrays.asList(c1, c2, c3));

        // 2. Seed Users (Workers & Clients)
        // Workers
        User w1 = User.builder()
                .name("Carlos Mendoza")
                .email("carlos@cbelleza.com")
                .phone("600333444")
                .role(Role.WORKER)
                .center(c1)
                .build();
        User w2 = User.builder()
                .name("Elena Rostova")
                .email("elena@cbelleza.com")
                .phone("600444555")
                .role(Role.WORKER)
                .center(c2)
                .build();
        User w3 = User.builder()
                .name("Ana Valdés")
                .email("ana@cbelleza.com")
                .phone("600555666")
                .role(Role.WORKER)
                .center(c3)
                .build();

        // Clients
        User client1 = User.builder()
                .name("Sofía Martínez")
                .email("sofia@gmail.com")
                .phone("600111222")
                .role(Role.CLIENT)
                .build();
        User client2 = User.builder()
                .name("Lucía Gómez")
                .email("lucia@gmail.com")
                .phone("600222333")
                .role(Role.CLIENT)
                .build();

        userRepository.saveAll(Arrays.asList(w1, w2, w3, client1, client2));

        // 3. Seed Center-Specific Treatments
        // Center 1
        Treatment t1_1 = Treatment.builder()
                .name("Manicura Premium")
                .description("Limpieza de cutículas, limado de uñas, exfoliación de manos y esmaltado de larga duración.")
                .price(25.50)
                .duration(45)
                .type(TreatmentType.MANICURA_PEDICURA)
                .center(c1)
                .build();
        Treatment t1_2 = Treatment.builder()
                .name("Tratamiento Facial Hidratante")
                .description("Limpieza profunda con vapor, exfoliación suave, mascarilla hidratante de ácido hialurónico y masaje facial.")
                .price(40.00)
                .duration(60)
                .type(TreatmentType.FACIAL)
                .center(c1)
                .build();
        Treatment t1_3 = Treatment.builder()
                .name("Masaje Relajante con Aromaterapia")
                .description("Masaje corporal completo de intensidad media utilizando aceites esenciales de lavanda y manzanilla.")
                .price(35.00)
                .duration(50)
                .type(TreatmentType.MASAJES)
                .center(c1)
                .build();

        // Center 2
        Treatment t2_1 = Treatment.builder()
                .name("Champú & Queratina Orgánica")
                .description("Lavado capilar con champú reparador y aplicación de tratamiento de queratina para reducir el encrespamiento.")
                .price(18.00)
                .duration(30)
                .type(TreatmentType.PELUQUERIA)
                .center(c2)
                .build();
        Treatment t2_2 = Treatment.builder()
                .name("Corte de Pelo & Estilo")
                .description("Corte moderno según preferencias del cliente, secado y peinado final con productos de fijación orgánica.")
                .price(22.00)
                .duration(45)
                .type(TreatmentType.PELUQUERIA)
                .center(c2)
                .build();

        // Center 3
        Treatment t3_1 = Treatment.builder()
                .name("Manicura Premium")
                .description("Cuidado de uñas y cutículas completo con exfoliación y crema hidratante aroma floral.")
                .price(25.50)
                .duration(45)
                .type(TreatmentType.MANICURA_PEDICURA)
                .center(c3)
                .build();
        Treatment t3_2 = Treatment.builder()
                .name("Masaje Relajante con Aromaterapia")
                .description("Masaje de espalda y hombros para liberar tensiones acumuladas.")
                .price(35.00)
                .duration(50)
                .type(TreatmentType.MASAJES)
                .center(c3)
                .build();

        treatmentRepository.saveAll(Arrays.asList(t1_1, t1_2, t1_3, t2_1, t2_2, t3_1, t3_2));

        // 4. Seed Global Product Catalog
        Product p1 = Product.builder()
                .name("Sérum Facial Vitamina C")
                .description("Sérum facial antioxidante con vitamina C pura para iluminar, reducir manchas y rejuvenecer la piel.")
                .category("Rostro")
                .price(25.50)
                .build();
        Product p2 = Product.builder()
                .name("Champú de Queratina Orgánica")
                .description("Champú reparador intensivo con queratina y aceite de argán. Libre de sulfatos y parabenos.")
                .category("Cabello")
                .price(18.00)
                .build();
        Product p3 = Product.builder()
                .name("Aceite de Argán Orgánico")
                .description("Aceite 100% puro prensado en frío para nutrir profundamente el cabello seco y la piel del cuerpo.")
                .category("Cabello")
                .price(29.90)
                .build();
        Product p4 = Product.builder()
                .name("Mascarilla de Arcilla Verde")
                .description("Mascarilla purificante facial para pieles grasas o mixtas. Controla el sebo y reduce poros.")
                .category("Rostro")
                .price(15.00)
                .build();
        Product p5 = Product.builder()
                .name("Esmalte de Uñas OPI Rojo Cereza")
                .description("Esmalte de uñas premium de larga duración, color rojo cereza brillante con acabado espejo.")
                .category("Uñas")
                .price(12.50)
                .build();
        Product p6 = Product.builder()
                .name("Crema Hidratante de Noche")
                .description("Crema nutritiva de noche enriquecida con ácido hialurónico, colágeno y manteca de karité.")
                .category("Rostro")
                .price(32.00)
                .build();
        Product p7 = Product.builder()
                .name("Crema Exfoliante Corporal")
                .description("Exfoliante suave con microesferas de albaricoque y extracto de aloe vera para renovar la piel.")
                .category("Cuerpo")
                .price(19.90)
                .build();
        Product p8 = Product.builder()
                .name("Aceite Esencial de Lavanda")
                .description("Aceite esencial puro para aromaterapia, masajes relajantes y reducción del estrés.")
                .category("Cuerpo")
                .price(14.50)
                .build();

        productRepository.saveAll(Arrays.asList(p1, p2, p3, p4, p5, p6, p7, p8));

        // 5. Seed Inventory for Centers
        // Center 1
        inventoryRepository.save(Inventory.builder().center(c1).product(p1).stock(12).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p2).stock(8).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p3).stock(0).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p5).stock(15).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p6).stock(4).build());

        // Center 2
        inventoryRepository.save(Inventory.builder().center(c2).product(p1).stock(5).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p3).stock(10).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p4).stock(12).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p5).stock(20).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p7).stock(2).build());

        // Center 3
        inventoryRepository.save(Inventory.builder().center(c3).product(p2).stock(15).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p3).stock(5).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p6).stock(10).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p8).stock(8).build());

        // 6. Seed Appointments
        // Helper: base dates with clean midnight to avoid leftover seconds/nanos
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime dayAfter = today.plusDays(2);
        // Find next working weekday (skip weekends) for day+3
        LocalDateTime day3 = today.plusDays(3);
        while (day3.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day3.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            day3 = day3.plusDays(1);
        }
        LocalDateTime day5 = today.plusDays(5);
        while (day5.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day5.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            day5 = day5.plusDays(1);
        }
        LocalDateTime day7 = today.plusDays(7);
        while (day7.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day7.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            day7 = day7.plusDays(1);
        }

        // ──────────────────────────────────────────────────────────────────
        // CENTER 1 — "Belleza Centro Histórico"
        // ──────────────────────────────────────────────────────────────────

        // ── TODAY: 2 confirmed appointments ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(today.withHour(11).withMinute(0))
                .treatment(t1_1) // Manicura Premium (45 min → 2 slots)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(today.withHour(15).withMinute(30))
                .treatment(t1_2) // Facial Hidratante (60 min → 2 slots)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ── TOMORROW: heavily booked day (test near-full blocking) ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(9).withMinute(0))
                .treatment(t1_2) // Facial (60 min → 2 slots: 09:00, 09:30)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(10).withMinute(0))
                .treatment(t1_3) // Masaje Relajante (50 min → 2 slots: 10:00, 10:30)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(11).withMinute(0))
                .treatment(t1_1) // Manicura (45 min → 2 slots: 11:00, 11:30)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(12).withMinute(0))
                .treatment(t1_2) // Facial (60 min → 2 slots: 12:00, 12:30)
                .center(c1).guestName("María López").guestPhone("611222333").worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(13).withMinute(0))
                .treatment(t1_1) // Manicura (45 min → 2 slots: 13:00, 13:30)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(14).withMinute(0))
                .treatment(t1_3) // Masaje (50 min → 2 slots: 14:00, 14:30)
                .center(c1).guestName("Pedro García").guestPhone("611333444").worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(15).withMinute(0))
                .treatment(t1_2) // Facial (60 min → 2 slots: 15:00, 15:30)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(16).withMinute(0))
                .treatment(t1_1) // Manicura (45 min → 2 slots: 16:00, 16:30)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(17).withMinute(0))
                .treatment(t1_3) // Masaje (50 min → 2 slots: 17:00, 17:30)
                .center(c1).guestName("Ana Ruiz").guestPhone("611444555").worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(18).withMinute(0))
                .treatment(t1_2) // Facial (60 min → 2 slots: 18:00, 18:30)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(19).withMinute(0))
                .treatment(t1_1) // Manicura (45 min → 2 slots: 19:00, 19:30)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ── DAY AFTER TOMORROW: some gaps to test partial blocking ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(dayAfter.withHour(9).withMinute(0))
                .treatment(t1_1) // Manicura (2 slots: 09:00, 09:30)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(dayAfter.withHour(10).withMinute(30))
                .treatment(t1_2) // Facial (2 slots: 10:30, 11:00)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(dayAfter.withHour(14).withMinute(0))
                .treatment(t1_3) // Masaje (2 slots: 14:00, 14:30)
                .center(c1).guestName("Juan Pérez").guestPhone("611777888").worker(w1)
                .status(AppointmentStatus.PENDING)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(dayAfter.withHour(16).withMinute(0))
                .treatment(t1_1) // Manicura (2 slots: 16:00, 16:30)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ── DAY+3: a REJECTED appointment (should NOT block) + one confirmed ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(day3.withHour(10).withMinute(0))
                .treatment(t1_2) // Facial
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.REJECTED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(day3.withHour(12).withMinute(30))
                .treatment(t1_1) // Manicura (2 slots: 12:30, 13:00)
                .center(c1).client(client1).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ── DAY+5: light booking ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(day5.withHour(17).withMinute(0))
                .treatment(t1_3) // Masaje (2 slots: 17:00, 17:30)
                .center(c1).client(client2).worker(w1)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ──────────────────────────────────────────────────────────────────
        // CENTER 2 — "Belleza Plaza Norte"
        // ──────────────────────────────────────────────────────────────────

        // ── TODAY: 1 confirmed ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(today.withHour(10).withMinute(0))
                .treatment(t2_1) // Champú & Queratina (30 min → 1 slot)
                .center(c2).client(client2).worker(w2)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ── TOMORROW: 3 appointments ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(9).withMinute(30))
                .treatment(t2_2) // Corte & Estilo (45 min → 2 slots: 09:30, 10:00)
                .center(c2).client(client1).worker(w2)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(11).withMinute(0))
                .treatment(t2_1) // Champú (30 min → 1 slot: 11:00)
                .center(c2).client(client2).worker(w2)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(15).withMinute(0))
                .treatment(t2_2) // Corte & Estilo (45 min → 2 slots: 15:00, 15:30)
                .center(c2).guestName("Laura Sánchez").guestPhone("622111222").worker(w2)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ── DAY+7: far future booking ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(day7.withHour(13).withMinute(0))
                .treatment(t2_1) // Champú (1 slot: 13:00)
                .center(c2).client(client1).worker(w2)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        // ──────────────────────────────────────────────────────────────────
        // CENTER 3 — "Belleza Las Condes"
        // ──────────────────────────────────────────────────────────────────

        // ── TOMORROW: 2 appointments ──
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(10).withMinute(0))
                .treatment(t3_1) // Manicura (45 min → 2 slots: 10:00, 10:30)
                .center(c3).client(client1).worker(w3)
                .status(AppointmentStatus.CONFIRMED)
                .build());
        appointmentRepository.save(Appointment.builder()
                .dateTime(tomorrow.withHour(14).withMinute(30))
                .treatment(t3_2) // Masaje (50 min → 2 slots: 14:30, 15:00)
                .center(c3).client(client2).worker(w3)
                .status(AppointmentStatus.CONFIRMED)
                .build());
    }
}
