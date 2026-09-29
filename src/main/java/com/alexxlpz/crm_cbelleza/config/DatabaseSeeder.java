package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.entities.*;
import com.alexxlpz.crm_cbelleza.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final ClientCardTemplateRepository clientCardTemplateRepository;
    private final ClientCardRepository clientCardRepository;

    public DatabaseSeeder(CenterRepository centerRepository,
                          UserRepository userRepository,
                          ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          AppointmentRepository appointmentRepository,
                          TreatmentRepository treatmentRepository,
                          PasswordEncoder passwordEncoder,
                          ClientCardTemplateRepository clientCardTemplateRepository,
                          ClientCardRepository clientCardRepository) {
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.appointmentRepository = appointmentRepository;
        this.treatmentRepository = treatmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clientCardTemplateRepository = clientCardTemplateRepository;
        this.clientCardRepository = clientCardRepository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) throws Exception {
        // Eliminar a todos los clientes invitados (citas y fichas huérfanas) de la base de datos
        clientCardRepository.deleteGuestCards();
        appointmentRepository.deleteGuestAppointments();

        if (centerRepository.count() > 0) {
            return; // Already seeded (e.g. from init.sql in PostgreSQL)
        }

        // 1. Seed Centers
        Center c1 = Center.builder()
                .name("Cuquora Centro Histórico")
                .address("Calle Mayor 45, 28013 Madrid")
                .phone("+34 910 111 222")
                .email("centro@cbelleza.com")
                .latitude(40.4168)
                .longitude(-3.7038)
                .build();
        Center c2 = Center.builder()
                .name("Cuquora Plaza Norte")
                .address("Av. de la Ilustración 12, 28034 Madrid")
                .phone("+34 910 222 333")
                .email("plazanorte@cbelleza.com")
                .latitude(40.5401)
                .longitude(-3.6143)
                .build();
        Center c3 = Center.builder()
                .name("Cuquora Sarrià-Sant Gervasi")
                .address("Carrer Major de Sarrià 88, 08017 Barcelona")
                .phone("+34 932 444 555")
                .email("barcelona@cbelleza.com")
                .latitude(41.3984)
                .longitude(2.1221)
                .build();
        Center c4 = Center.builder()
                .name("Cuquora Las Condes")
                .address("Av. Las Condes 8900, Santiago")
                .phone("+56 2 2033 3444")
                .email("lascondes@cbelleza.com")
                .latitude(-33.4000)
                .longitude(-70.5667)
                .build();
        centerRepository.saveAll(Arrays.asList(c1, c2, c3, c4));

        // 2. Seed Users with Encrypted Passwords (default demo password: "password123")
        String defaultHashedPassword = passwordEncoder.encode("password123");

        // Workers
        User w1 = User.builder()
                .name("Carlos Mendoza")
                .email("carlos@cbelleza.com")
                .phone("600333444")
                .password(defaultHashedPassword)
                .role(Role.WORKER)
                .center(c1)
                .build();
        User w2 = User.builder()
                .name("Lucía Romero")
                .email("lucia.romero@cbelleza.com")
                .phone("600333555")
                .password(defaultHashedPassword)
                .role(Role.WORKER)
                .center(c1)
                .build();
        User w3 = User.builder()
                .name("Elena Rostova")
                .email("elena@cbelleza.com")
                .phone("600444555")
                .password(defaultHashedPassword)
                .role(Role.WORKER)
                .center(c2)
                .build();
        User w4 = User.builder()
                .name("Marco Bellini")
                .email("marco@cbelleza.com")
                .phone("600444666")
                .password(defaultHashedPassword)
                .role(Role.WORKER)
                .center(c3)
                .build();
        User w5 = User.builder()
                .name("Ana Valdés")
                .email("ana@cbelleza.com")
                .phone("600555666")
                .password(defaultHashedPassword)
                .role(Role.WORKER)
                .center(c4)
                .build();

        // Clients
        User client1 = User.builder()
                .name("Sofía Martínez")
                .email("sofia@gmail.com")
                .phone("600111222")
                .password(defaultHashedPassword)
                .role(Role.CLIENT)
                .build();
        User client2 = User.builder()
                .name("Lucía Gómez")
                .email("lucia@gmail.com")
                .phone("600222333")
                .password(defaultHashedPassword)
                .role(Role.CLIENT)
                .build();
        User client3 = User.builder()
                .name("Valentina Silva")
                .email("valentina.silva@gmail.com")
                .phone("600333111")
                .password(defaultHashedPassword)
                .role(Role.CLIENT)
                .build();
        User client4 = User.builder()
                .name("Javier Navarro")
                .email("javier.navarro@gmail.com")
                .phone("600444222")
                .password(defaultHashedPassword)
                .role(Role.CLIENT)
                .build();
        User client5 = User.builder()
                .name("Carmen Morales")
                .email("carmen.morales@gmail.com")
                .phone("600555333")
                .password(defaultHashedPassword)
                .role(Role.CLIENT)
                .build();

        userRepository.saveAll(Arrays.asList(w1, w2, w3, w4, w5, client1, client2, client3, client4, client5));

        // 3. Seed Center-Specific Treatments (all TreatmentType values)
        // Center 1 (Madrid Centro Histórico)
        Treatment t1_1 = Treatment.builder()
                .name("Manicura Rusa & Semipermanente")
                .description("Limpieza profunda de cutículas con torno, nivelación con base rubber y esmaltado de alta duración.")
                .price(32.00)
                .duration(50)
                .type(TreatmentType.MANICURA_PEDICURA)
                .center(c1)
                .build();
        Treatment t1_2 = Treatment.builder()
                .name("Tratamiento Facial Ácido Hialurónico")
                .description("Limpieza profunda con vapor, exfoliación suave, mascarilla hidratante de ácido hialurónico y masaje facial.")
                .price(48.00)
                .duration(60)
                .type(TreatmentType.FACIAL)
                .center(c1)
                .build();
        Treatment t1_3 = Treatment.builder()
                .name("Masaje Relajante Aromaterapia")
                .description("Masaje corporal completo de intensidad media utilizando aceites esenciales botánicos de lavanda y manzanilla.")
                .price(42.00)
                .duration(55)
                .type(TreatmentType.MASAJES)
                .center(c1)
                .build();
        Treatment t1_4 = Treatment.builder()
                .name("Exfoliación con Sales del Mar Muerto")
                .description("Renovación celular corporal completa con sales minerales y envoltura hidratante de karité.")
                .price(45.00)
                .duration(45)
                .type(TreatmentType.CORPORAL)
                .center(c1)
                .build();
        Treatment t1_5 = Treatment.builder()
                .name("Depilación Láser Diodo Facial y Axilas")
                .description("Tratamiento indoloro con cabezal frío para eliminación duradera del vello.")
                .price(35.00)
                .duration(30)
                .type(TreatmentType.DEPILACION)
                .center(c1)
                .build();
        Treatment t1_6 = Treatment.builder()
                .name("Corte de Autor & Peinado Glam")
                .description("Asesoría visagista personalizada, lavado nutritivo y peinado con secado profesional.")
                .price(28.00)
                .duration(45)
                .type(TreatmentType.PELUQUERIA)
                .center(c1)
                .build();

        // Center 2 (Madrid Plaza Norte)
        Treatment t2_1 = Treatment.builder()
                .name("Champú & Queratina Orgánica")
                .description("Lavado capilar con champú reparador y aplicación de tratamiento de queratina para reducir el encrespamiento.")
                .price(38.00)
                .duration(45)
                .type(TreatmentType.PELUQUERIA)
                .center(c2)
                .build();
        Treatment t2_2 = Treatment.builder()
                .name("Balayage Luminoso & Matiz Gloss")
                .description("Técnica de aclarado degradado a mano alzada con baño de brillo nutritivo.")
                .price(95.00)
                .duration(120)
                .type(TreatmentType.PELUQUERIA)
                .center(c2)
                .build();
        Treatment t2_3 = Treatment.builder()
                .name("Higiene Facial Punta de Diamante")
                .description("Microdermoabrasión suave para eliminar impurezas, puntos negros y afinar la textura.")
                .price(52.00)
                .duration(60)
                .type(TreatmentType.FACIAL)
                .center(c2)
                .build();
        Treatment t2_4 = Treatment.builder()
                .name("Pedicura Spa Rejuvenecedora")
                .description("Baño de sales, torno podal, exfoliación de talones y masaje circulatorio relajante.")
                .price(38.00)
                .duration(50)
                .type(TreatmentType.MANICURA_PEDICURA)
                .center(c2)
                .build();
        Treatment t2_5 = Treatment.builder()
                .name("Depilación Láser Piernas Completas")
                .description("Sesión de alta potencia con tecnología de última generación para piernas suaves.")
                .price(65.00)
                .duration(50)
                .type(TreatmentType.DEPILACION)
                .center(c2)
                .build();

        // Center 3 (Barcelona Sarrià)
        Treatment t3_1 = Treatment.builder()
                .name("Maderoterapia Reductora & Drenante")
                .description("Técnica holística con utensilios de madera de cedro para remodelar y reducir retención.")
                .price(58.00)
                .duration(60)
                .type(TreatmentType.CORPORAL)
                .center(c3)
                .build();
        Treatment t3_2 = Treatment.builder()
                .name("Glow Facial Vitamina C Pura")
                .description("Cóctel antioxidante e iluminador para pieles apagadas o expuestas al estrés urbano.")
                .price(50.00)
                .duration(55)
                .type(TreatmentType.FACIAL)
                .center(c3)
                .build();
        Treatment t3_3 = Treatment.builder()
                .name("Masaje Descontracturante Profundo")
                .description("Terapia muscular focalizada en espalda, cuello y hombros para disolver nudos de tensión.")
                .price(48.00)
                .duration(50)
                .type(TreatmentType.MASAJES)
                .center(c3)
                .build();
        Treatment t3_4 = Treatment.builder()
                .name("Diseño de Cejas con Hilo & Henna")
                .description("Diseño de mirada de precisión con hilo de algodón orgánico y tinte natural de henna.")
                .price(24.00)
                .duration(30)
                .type(TreatmentType.DEPILACION)
                .center(c3)
                .build();

        // Center 4 (Santiago Las Condes)
        Treatment t4_1 = Treatment.builder()
                .name("Manicura Spa de Lujo")
                .description("Cuidado de uñas y cutículas completo con exfoliación y crema hidratante aroma floral.")
                .price(28.50)
                .duration(45)
                .type(TreatmentType.MANICURA_PEDICURA)
                .center(c4)
                .build();
        Treatment t4_2 = Treatment.builder()
                .name("Masaje Balinés con Pindas Calientes")
                .description("Masaje de espalda y hombros para liberar tensiones acumuladas con sacos herbales calientes.")
                .price(65.00)
                .duration(75)
                .type(TreatmentType.MASAJES)
                .center(c4)
                .build();
        Treatment t4_3 = Treatment.builder()
                .name("Tratamiento Capilar Botox & Brillo")
                .description("Relleno de fibra capilar dañada con colágeno vegetal y ácido hialurónico.")
                .price(44.00)
                .duration(60)
                .type(TreatmentType.PELUQUERIA)
                .center(c4)
                .build();

        treatmentRepository.saveAll(Arrays.asList(
                t1_1, t1_2, t1_3, t1_4, t1_5, t1_6,
                t2_1, t2_2, t2_3, t2_4, t2_5,
                t3_1, t3_2, t3_3, t3_4,
                t4_1, t4_2, t4_3
        ));

        // 4. Seed Global Product Catalog
        Product p1 = Product.builder().name("Sérum Facial Vitamina C Pura 15%").description("Sérum facial antioxidante con vitamina C pura para iluminar, reducir manchas y rejuvenecer la piel.").category("Rostro").price(28.50).build();
        Product p2 = Product.builder().name("Crema Hidratante de Noche con Ácido Hialurónico").description("Crema nutritiva de noche enriquecida con ácido hialurónico, colágeno y manteca de karité.").category("Rostro").price(34.00).build();
        Product p3 = Product.builder().name("Mascarilla Facial Purificante de Arcilla Verde").description("Mascarilla purificante facial para pieles grasas o mixtas. Controla el sebo y reduce poros.").category("Rostro").price(16.50).build();
        Product p4 = Product.builder().name("Tónico Facial Calmante con Agua de Rosas").description("Bruma refrescante e hidratante para equilibrar el pH tras la limpieza.").category("Rostro").price(18.00).build();
        Product p5 = Product.builder().name("Champú Reparador de Queratina y Argán").description("Champú reparador intensivo con queratina y aceite de argán. Libre de sulfatos y parabenos.").category("Cabello").price(21.00).build();
        Product p6 = Product.builder().name("Mascarilla Capilar Nutrición Intensa").description("Tratamiento intensivo con manteca de murumuru y aceite de coco virgen.").category("Cabello").price(24.50).build();
        Product p7 = Product.builder().name("Aceite de Argán 100% Puro Prensado en Frío").description("Aceite 100% puro prensado en frío para nutrir profundamente el cabello seco y la piel del cuerpo.").category("Cabello").price(29.90).build();
        Product p8 = Product.builder().name("Sérum Protector Térmico & Escudo Anti-Frizz").description("Protector térmico hasta 230°C con acabado sedoso no graso.").category("Cabello").price(22.00).build();
        Product p9 = Product.builder().name("Exfoliante Corporal con Sales del Mar Muerto").description("Exfoliante suave con microesferas de albaricoque y extracto de aloe vera para renovar la piel.").category("Cuerpo").price(22.50).build();
        Product p10 = Product.builder().name("Aceite Esencial Puro de Lavanda Francesa").description("Aceite esencial puro para aromaterapia, masajes relajantes y reducción del estrés.").category("Cuerpo").price(16.00).build();
        Product p11 = Product.builder().name("Loción Reafirmante con Cafeína y Té Verde").description("Crema corporal drenante y tonificante de rápida absorción.").category("Cuerpo").price(26.00).build();
        Product p12 = Product.builder().name("Esmalte OPI Rojo Borgoña Larga Duración").description("Esmalte de uñas premium de larga duración, color rojo cereza brillante con acabado espejo.").category("Uñas").price(13.50).build();
        Product p13 = Product.builder().name("Aceite Nutritivo para Cutículas con Vitamina E").description("Tratamiento hidratante con gotero para uñas flexibles y cutículas suaves.").category("Uñas").price(11.00).build();
        Product p14 = Product.builder().name("Base Fortalecedora con Calcio y Queratina").description("Tratamiento endurecedor para uñas finas, quebradizas o desvitalizadas.").category("Uñas").price(12.00).build();

        productRepository.saveAll(Arrays.asList(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14));

        // 5. Seed Inventory for Centers
        // Center 1
        inventoryRepository.save(Inventory.builder().center(c1).product(p1).stock(14).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p2).stock(8).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p3).stock(0).build()); // out of stock
        inventoryRepository.save(Inventory.builder().center(c1).product(p5).stock(18).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p7).stock(2).build());  // low stock warning
        inventoryRepository.save(Inventory.builder().center(c1).product(p9).stock(12).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p12).stock(25).build());
        inventoryRepository.save(Inventory.builder().center(c1).product(p13).stock(15).build());

        // Center 2
        inventoryRepository.save(Inventory.builder().center(c2).product(p1).stock(6).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p5).stock(20).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p6).stock(12).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p7).stock(10).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p8).stock(16).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p12).stock(18).build());
        inventoryRepository.save(Inventory.builder().center(c2).product(p14).stock(5).build());

        // Center 3
        inventoryRepository.save(Inventory.builder().center(c3).product(p1).stock(10).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p2).stock(7).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p9).stock(15).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p10).stock(8).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p11).stock(14).build());
        inventoryRepository.save(Inventory.builder().center(c3).product(p13).stock(11).build());

        // Center 4
        inventoryRepository.save(Inventory.builder().center(c4).product(p5).stock(12).build());
        inventoryRepository.save(Inventory.builder().center(c4).product(p7).stock(6).build());
        inventoryRepository.save(Inventory.builder().center(c4).product(p10).stock(10).build());
        inventoryRepository.save(Inventory.builder().center(c4).product(p12).stock(22).build());
        inventoryRepository.save(Inventory.builder().center(c4).product(p14).stock(9).build());

        // 6. Seed Appointments
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime dayAfter = today.plusDays(2);
        LocalDateTime day3 = today.plusDays(3);
        while (day3.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day3.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            day3 = day3.plusDays(1);
        }
        LocalDateTime day5 = today.plusDays(5);
        while (day5.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day5.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            day5 = day5.plusDays(1);
        }

        // Center 1 Appointments
        appointmentRepository.save(Appointment.builder().dateTime(today.withHour(11).withMinute(0)).treatment(t1_1).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).workerMessage("Cliente habitual, prefiere base rubber tono nude.").build());
        appointmentRepository.save(Appointment.builder().dateTime(today.withHour(15).withMinute(30)).treatment(t1_2).center(c1).client(client2).worker(w1).status(AppointmentStatus.CONFIRMED).workerMessage("Piel sensible, usar tónico de rosas sin alcohol.").build());

        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(9).withMinute(0)).treatment(t1_2).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(10).withMinute(0)).treatment(t1_3).center(c1).client(client2).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(11).withMinute(0)).treatment(t1_1).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(12).withMinute(0)).treatment(t1_2).center(c1).client(client3).worker(w1).status(AppointmentStatus.CONFIRMED).workerMessage("Reserva confirmada.").build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(13).withMinute(0)).treatment(t1_1).center(c1).client(client2).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(14).withMinute(0)).treatment(t1_3).center(c1).client(client4).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(15).withMinute(0)).treatment(t1_2).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(16).withMinute(0)).treatment(t1_1).center(c1).client(client2).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(17).withMinute(0)).treatment(t1_3).center(c1).client(client5).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(18).withMinute(0)).treatment(t1_2).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());

        appointmentRepository.save(Appointment.builder().dateTime(dayAfter.withHour(9).withMinute(0)).treatment(t1_1).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(dayAfter.withHour(10).withMinute(30)).treatment(t1_2).center(c1).client(client2).worker(w1).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(dayAfter.withHour(14).withMinute(0)).treatment(t1_3).center(c1).client(client3).worker(w1).status(AppointmentStatus.PENDING).workerMessage("Pendiente de confirmación telefónica.").build());
        appointmentRepository.save(Appointment.builder().dateTime(dayAfter.withHour(16).withMinute(0)).treatment(t1_1).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());

        appointmentRepository.save(Appointment.builder().dateTime(day3.withHour(10).withMinute(0)).treatment(t1_2).center(c1).client(client2).worker(w1).status(AppointmentStatus.REJECTED).workerMessage("Horario no disponible por mantenimiento en cabina.").build());
        appointmentRepository.save(Appointment.builder().dateTime(day3.withHour(12).withMinute(30)).treatment(t1_1).center(c1).client(client1).worker(w1).status(AppointmentStatus.CONFIRMED).build());

        // Center 2 Appointments
        appointmentRepository.save(Appointment.builder().dateTime(today.withHour(10).withMinute(0)).treatment(t2_1).center(c2).client(client2).worker(w3).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(9).withMinute(30)).treatment(t2_2).center(c2).client(client1).worker(w3).status(AppointmentStatus.CONFIRMED).workerMessage("Solicita asesoramiento de corte.").build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(11).withMinute(0)).treatment(t2_1).center(c2).client(client2).worker(w3).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(15).withMinute(0)).treatment(t2_2).center(c2).client(client4).worker(w3).status(AppointmentStatus.CONFIRMED).build());

        // Center 3 Appointments
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(10).withMinute(30)).treatment(t3_1).center(c3).client(client3).worker(w4).status(AppointmentStatus.CONFIRMED).workerMessage("Primera sesión bono de 5 maderoterapia.").build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(16).withMinute(0)).treatment(t3_2).center(c3).client(client4).worker(w4).status(AppointmentStatus.CONFIRMED).build());

        // Center 4 Appointments
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(10).withMinute(0)).treatment(t4_1).center(c4).client(client1).worker(w5).status(AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder().dateTime(tomorrow.withHour(14).withMinute(30)).treatment(t4_2).center(c4).client(client2).worker(w5).status(AppointmentStatus.CONFIRMED).build());

        // 7. Seed Client Card Templates
        String defaultTemplateJson = """
            [
              {"id":"tipo_piel_cabello","label":"Tipo de Piel / Cabello","type":"text","placeholder":"Ej. Piel mixta / Cabello fino teñido","required":false},
              {"id":"alergias_sensibilidades","label":"Alergias o Sensibilidades","type":"text","placeholder":"Ej. Alergia al amoníaco, látex, fragancias","required":false},
              {"id":"tratamientos_habituales","label":"Coloración / Tratamientos habituales","type":"text","placeholder":"Ej. Tinte 6.34, Mechas balayage, etc.","required":false},
              {"id":"observaciones_preferencias","label":"Observaciones y Preferencias Técnicas","type":"textarea","placeholder":"Preferencias de temperatura de lavado, notas del especialista, etc.","required":false}
            ]
            """;
        clientCardTemplateRepository.save(ClientCardTemplate.builder().center(c1).fieldsJson(defaultTemplateJson).build());
        clientCardTemplateRepository.save(ClientCardTemplate.builder().center(c2).fieldsJson(defaultTemplateJson).build());
        clientCardTemplateRepository.save(ClientCardTemplate.builder().center(c3).fieldsJson(defaultTemplateJson).build());
        clientCardTemplateRepository.save(ClientCardTemplate.builder().center(c4).fieldsJson(defaultTemplateJson).build());

        // 8. Seed Sample Client Card for client1 at Center 1
        String client1CardData = """
            {
              "tipo_piel_cabello":"Piel normal sensible. Cabello castaño claro con mechas balayage.",
              "alergias_sensibilidades":"Sensibilidad a tintes con alto contenido en amoníaco.",
              "tratamientos_habituales":"Manicura semipermanente base rubber tono nude y mechas anuales.",
              "observaciones_preferencias":"Prefiere citas por la mañana. Le gusta el café con leche de avena."
            }
            """;
        clientCardRepository.save(ClientCard.builder()
                .center(c1)
                .client(client1)
                .dataJson(client1CardData)
                .updatedAt(LocalDateTime.now().minusDays(1))
                .updatedBy(w1)
                .build());
    }
}
