package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductSaleRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Visitas y ventas pasadas en un centro demo desde enero del año anterior, para que la pantalla «Análisis» tenga
 * algo que enseñar (también la comparación de un año con el anterior).
 * Solo con crm.seed-demo-history=true (perfil dev): los tests cuentan con los datos demo de siempre.
 * Semilla fija: cada arranque genera el mismo historial.
 */
final class DemoHistory {

    private static final String[] NAMES = {
            "Marta Ruiz", "Paula Herrero", "Irene Castillo", "Nuria Vidal", "Alba Serrano", "Raquel Ortega",
            "Beatriz Molina", "Elena Prieto", "Cristina Lozano", "Patricia Fuentes", "Andrea Cabrera",
            "Silvia Pascual", "Teresa Delgado", "Rocío Iglesias", "Clara Benítez", "Miriam Santos"
    };
    /** Clientes que ya venían desde el primer mes; el resto llega poco a poco. */
    private static final int REGULARS_FROM_START = 8;
    /** Las anulaciones se quedan fuera de las dos últimas semanas para no llenar la campana del centro. */
    private static final int NO_CANCELLATIONS_LAST_DAYS = 14;

    private final AppointmentRepository appointments;
    private final ProductSaleRepository sales;
    private final UserRepository users;
    private final ReviewRepository reviews;
    private final Random random = new Random(20261018L);

    DemoHistory(AppointmentRepository appointments, ProductSaleRepository sales, UserRepository users,
                ReviewRepository reviews) {
        this.reviews = reviews;
        this.appointments = appointments;
        this.sales = sales;
        this.users = users;
    }

    void seed(Center center, List<User> workers, List<Treatment> treatments, List<Product> products,
              List<User> existingClients, LocalDateTime today) {
        List<User> clients = new ArrayList<>(existingClients);
        for (int i = 0; i < NAMES.length; i++) {
            String phone = "6119" + String.format("%05d", 100 + i);
            clients.add(User.builder()
                    .name(NAMES[i])
                    .email("cliente_" + phone + User.PLACEHOLDER_EMAIL_DOMAIN)
                    .phone(phone)
                    .role(Role.CLIENT)
                    .build());
        }
        users.saveAll(clients.subList(existingClients.size(), clients.size()));

        // Primer mes con Cuquora: enero del año anterior
        LocalDate start = LocalDate.of(today.getYear() - 1, 1, 8);
        LocalDate lastDay = today.toLocalDate().minusDays(1);
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(start, lastDay);
        List<Appointment> history = new ArrayList<>();
        List<ProductSale> productSales = new ArrayList<>();

        for (int i = 0; i < clients.size(); i++) {
            User client = clients.get(i);
            // Cada cliente llega en un momento del año (el centro va ganando clientes) y vuelve con su propio ritmo
            LocalDate firstVisit = start.plusDays(i < REGULARS_FROM_START ? random.nextInt(25) : random.nextInt(days - 20));
            int everyDays = 18 + random.nextInt(40);
            // Uno de cada cinco deja de venir hace unos meses: son los «clientes a recuperar»
            LocalDate stopsAt = i % 5 == 3 ? lastDay.minusDays(95 + random.nextInt(80)) : lastDay;
            Treatment usual = treatments.get(random.nextInt(treatments.size()));

            for (LocalDate day = firstVisit; !day.isAfter(stopsAt); day = day.plusDays(everyDays - 4 + random.nextInt(9))) {
                LocalDate visitDay = day.getDayOfWeek() == DayOfWeek.SUNDAY ? day.plusDays(1) : day;
                if (visitDay.isAfter(lastDay)) {
                    break;
                }
                LocalDateTime at = visitDay.atTime(10 + random.nextInt(9), random.nextBoolean() ? 0 : 30);
                Treatment treatment = random.nextInt(10) < 6 ? usual : treatments.get(random.nextInt(treatments.size()));
                User worker = workers.get(random.nextInt(workers.size()));
                boolean cancelled = random.nextInt(100) < 7
                        && visitDay.isBefore(lastDay.minusDays(NO_CANCELLATIONS_LAST_DAYS));
                // Alguna vez el cliente no se presenta (para «No vinieron» del análisis)
                boolean noShow = !cancelled && random.nextInt(100) < 3;

                Appointment appointment = Appointment.builder()
                        .dateTime(at).treatment(treatment).center(center).client(client).worker(worker)
                        .status(cancelled ? AppointmentStatus.CANCELLED : noShow ? AppointmentStatus.NO_SHOW : AppointmentStatus.COMPLETED)
                        // Alguna visita con descuento del 10 %, como cuando se cobra menos que en la carta
                        .price(random.nextInt(10) == 0 ? Math.round(treatment.getPrice() * 90) / 100.0 : treatment.getPrice())
                        .createdAt(at.minusDays(3 + random.nextInt(10)))
                        .build();
                if (cancelled) {
                    appointment.recordChange(AppointmentChange.CANCELLED_BY_CLIENT, at.minusDays(1));
                }
                history.add(appointment);

                if (!cancelled && !noShow && random.nextInt(100) < 30) {
                    Product product = products.get(random.nextInt(products.size()));
                    productSales.add(ProductSale.builder()
                            .center(center).client(client).product(product).worker(worker)
                            .quantity(random.nextInt(4) == 0 ? 2 : 1)
                            .unitPrice(product.getPrice())
                            .soldAt(at.plusMinutes(treatment.getDuration()))
                            .build());
                }
            }
        }
        appointments.saveAll(history);
        sales.saveAll(productSales);
        new DemoReviews(reviews).seed(history,
                existingClients.stream().map(User::getId).collect(Collectors.toSet()), today);
    }
}
