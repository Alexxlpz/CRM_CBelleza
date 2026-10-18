package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductSaleRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Cifras de la pantalla «Análisis» del trabajador. Todo se calcula en memoria sobre las citas y ventas del
 * centro (una consulta de cada), porque la primera visita de un cliente depende de todo su historial.
 * <p>
 * Una visita es una cita completada. Los ingresos son lo cobrado en cada visita ({@code appointments.price})
 * más las ventas de productos a su precio de venta.
 * <p>
 * Dos vistas: los últimos meses (el mes en curso frente al anterior) y un año entero frente al anterior.
 * Nunca se compara con meses en los que el centro aún no usaba Cuquora: saldrían a cero y parecería que crece.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    public static final List<Integer> PERIOD_OPTIONS = List.of(3, 6, 12);
    public static final int DEFAULT_MONTHS = 12;

    /** Sin visita en este tiempo (o en el doble de lo que suele tardar en volver) un cliente habitual se da por perdido. */
    static final int MIN_DAYS_AWAY = 60;
    static final int RANKING_SIZE = 6;
    static final int CLIENT_LIST_SIZE = 8;
    static final int CHANGE_LIST_SIZE = 3;

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private final AppointmentRepository appointments;
    private final ProductSaleRepository sales;
    private final UserRepository users;
    private final CenterService centerService;
    private final Clock clock;

    public AnalyticsService(AppointmentRepository appointments, ProductSaleRepository sales, UserRepository users,
                            CenterService centerService, Clock clock) {
        this.appointments = appointments;
        this.sales = sales;
        this.users = users;
        this.centerService = centerService;
        this.clock = clock;
    }

    public static int normalizeMonths(Integer months) {
        return months != null && PERIOD_OPTIONS.contains(months) ? months : DEFAULT_MONTHS;
    }

    /** Últimos {@code months} meses; las cifras de arriba son el mes en curso frente al mismo tramo del anterior. */
    public AnalyticsReport reportFor(Long centerId, int months) {
        CenterData data = load(centerId);
        LocalDateTime now = data.now();
        YearMonth currentMonth = YearMonth.from(now);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        Window period = new Window(firstMonth.atDay(1).atStartOfDay(), now);

        // Este mes hasta hoy frente al mismo tramo del mes anterior (no frente al mes entero, que aún no ha acabado)
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        Window current = new Window(monthStart, now);
        Window previous = currentMonth.minusMonths(1).isBefore(data.start())
                ? null
                : new Window(monthStart.minusMonths(1), now.minusMonths(1));

        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
        List<Appointment> bookedRestOfMonth = data.all().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> a.getDateTime().isAfter(now) && !a.getDateTime().isAfter(monthEnd))
                .toList();

        return build(data, new View(
                months, null, null, null,
                monthName(currentMonth) + " hasta hoy",
                previous == null ? null : "Comparado con el " + dayRange(previous),
                null,
                "Primer mes con Cuquora: aún no hay con qué comparar",
                "Últimos " + months + " meses",
                null, null,
                period, current, previous, null,
                firstMonth, currentMonth, false,
                bookedRestOfMonth.size(), bookedRestOfMonth.stream().mapToDouble(AnalyticsService::price).sum(),
                true));
    }

    /**
     * Un año entero (o hasta hoy, si es el actual) frente al anterior. Solo se puede ir hacia atrás mientras haya
     * datos: el primer año es el del primer mes con Cuquora. Si el año anterior empezó a medias, se comparan solo
     * los meses que hay en los dos.
     */
    public AnalyticsReport yearReportFor(Long centerId, Integer requestedYear) {
        CenterData data = load(centerId);
        LocalDateTime now = data.now();
        int firstYear = data.start().getYear();
        int currentYear = now.getYear();
        int year = requestedYear == null ? currentYear : Math.max(firstYear, Math.min(currentYear, requestedYear));

        LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = year == currentYear ? now : LocalDate.of(year, 12, 31).atTime(LocalTime.MAX);
        Window period = new Window(yearStart, yearEnd);

        Window current = period;
        Window previous = null;
        String comparisonNote = null;
        if (year - 1 >= firstYear) {
            LocalDateTime startOfData = data.start().atDay(1).atStartOfDay();
            LocalDateTime previousFrom = max(yearStart.minusYears(1), startOfData);
            LocalDateTime previousTo = yearEnd.minusYears(1);
            if (!previousFrom.isAfter(previousTo)) {
                previous = new Window(previousFrom, previousTo);
                current = new Window(previousFrom.plusYears(1), yearEnd);
                if (previousFrom.isAfter(yearStart.minusYears(1))) {
                    comparisonNote = "Solo se comparan los meses que hay en los dos años: el centro empezó con Cuquora en "
                            + monthName(data.start()).toLowerCase(ES) + " de " + data.start().getYear() + ".";
                }
            }
        }

        return build(data, new View(
                0, year,
                year - 1 >= firstYear ? year - 1 : null,
                year + 1 <= currentYear ? year + 1 : null,
                year == currentYear ? year + " hasta hoy" : "Año " + year,
                previous == null ? null : "Comparado con " + spanLabel(previous),
                comparisonNote,
                "Primer año con Cuquora: aún no hay con qué comparar",
                year == currentYear ? "Año " + year + " hasta hoy" : "Año " + year,
                String.valueOf(year), String.valueOf(year - 1),
                period, current, previous, previous == null ? null : current,
                YearMonth.of(year, 1), YearMonth.of(year, 12), true,
                0, 0,
                year == currentYear));
    }

    private AnalyticsReport build(CenterData data, View view) {
        Window period = view.period();
        PeriodFigures shown = figures(data, view.current());
        PeriodFigures before = view.previous() == null ? null : figures(data, view.previous());
        List<Kpi> kpis = kpis(shown, before);

        List<Appointment> periodVisits = visitsIn(data, period);
        List<ProductSale> periodSales = salesIn(data, period);
        // Las diferencias de los listados se calculan sobre los tramos comparables (en el caso normal, el mismo periodo)
        List<Appointment> comparedVisits = view.previous() == null ? null : visitsIn(data, view.current());
        List<Appointment> previousVisits = view.previous() == null ? null : visitsIn(data, view.previous());
        List<ProductSale> comparedSales = view.previous() == null ? null : salesIn(data, view.current());
        List<ProductSale> previousSales = view.previous() == null ? null : salesIn(data, view.previous());

        List<MonthPoint> monthly = monthly(data, view.firstMonth(), view.lastMonth(), view.withPreviousYear());
        double maxRevenue = monthly.stream()
                .flatMap(p -> Stream.of(p.revenue(), p.previousRevenue() == null ? 0 : p.previousRevenue()))
                .mapToDouble(Double::doubleValue).max().orElse(0);
        int maxVisits = monthly.stream()
                .flatMap(p -> Stream.of(p.visits(), p.previousVisits() == null ? 0 : p.previousVisits()))
                .mapToInt(Integer::intValue).max().orElse(0);
        double periodRevenue = periodVisits.stream().mapToDouble(AnalyticsService::price).sum()
                + periodSales.stream().mapToDouble(AnalyticsService::amount).sum();

        ChangeBreakdown changes = view.changeWindow() == null || before == null ? null
                : changes(shown, before, comparedVisits, previousVisits, data);

        return new AnalyticsReport(
                view.months(), view.year() != null, view.year(), view.previousYear(), view.nextYear(),
                view.headline(), view.comparisonLabel(), view.comparisonNote(), view.noComparisonText(),
                view.periodLabel(), view.currentSeriesLabel(), view.previousSeriesLabel(),
                monthName(data.start()).toLowerCase(ES) + " de " + data.start().getYear(),
                kpis,
                view.bookedRestOfMonth(), view.bookedRevenueRestOfMonth(),
                monthly, maxRevenue, maxVisits,
                periodVisits.size(), periodRevenue,
                (int) periodVisits.stream().map(a -> a.getClient().getId()).distinct().count(),
                (int) data.firstVisit().values().stream().filter(period::contains).count(),
                changes,
                topClients(periodVisits, periodSales),
                view.showWinBack() ? clientsToWinBack(data) : List.of(),
                view.showWinBack(),
                treatmentRanking(periodVisits, comparedVisits, previousVisits),
                productRanking(periodSales, comparedSales, previousSales),
                team(data.centerId(), periodVisits, comparedVisits, previousVisits),
                weekdays(periodVisits),
                busiestHour(periodVisits),
                !data.visits().isEmpty() || !data.sales().isEmpty());
    }

    private CenterData load(Long centerId) {
        Center center = centerService.getCenterById(centerId);
        LocalDateTime now = CenterClock.now(clock, center);
        List<Appointment> all = appointments.findByCenterIdOrderByDateTimeDesc(centerId);
        List<ProductSale> centerSales = sales.findByCenterId(centerId);
        List<Appointment> visits = all.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED && a.getClient() != null)
                .sorted(Comparator.comparing(Appointment::getDateTime))
                .toList();
        Map<Long, LocalDateTime> firstVisit = new HashMap<>();
        for (Appointment visit : visits) {
            firstVisit.putIfAbsent(visit.getClient().getId(), visit.getDateTime());
        }

        // Primer mes con Cuquora: el alta del centro o, si es anterior (centros demo o migrados), su primer dato
        LocalDateTime first = center.getRegisteredAt() != null ? center.getRegisteredAt() : now;
        for (Appointment appointment : all) {
            first = min(first, appointment.getDateTime());
        }
        for (ProductSale sale : centerSales) {
            first = min(first, sale.getSoldAt());
        }
        return new CenterData(centerId, now, all, visits, centerSales, firstVisit, YearMonth.from(first));
    }

    private static List<Kpi> kpis(PeriodFigures shown, PeriodFigures before) {
        PeriodFigures base = before != null ? before : shown;
        boolean compare = before != null;
        return List.of(
                Kpi.of("Ingresos", "lucide-wallet", shown.revenue(), base.revenue(), Format.MONEY, true, compare),
                Kpi.of("Visitas", "lucide-calendar-check-2", shown.visits(), base.visits(), Format.NUMBER, true, compare),
                Kpi.of("Clientes nuevos", "lucide-user-plus", shown.newClients(), base.newClients(), Format.NUMBER, true, compare),
                Kpi.of("Ticket medio", "lucide-receipt", shown.averageTicket(), base.averageTicket(), Format.MONEY, true, compare),
                Kpi.of("Visitas de clientes que repiten", "lucide-repeat", shown.returningShare(), base.returningShare(), Format.PERCENT, true, compare),
                Kpi.of("Citas anuladas o rechazadas", "lucide-calendar-x-2", shown.cancelledShare(), base.cancelledShare(), Format.PERCENT, false, compare));
    }

    private static PeriodFigures figures(CenterData data, Window window) {
        List<Appointment> inWindow = visitsIn(data, window);
        double revenue = inWindow.stream().mapToDouble(AnalyticsService::price).sum()
                + salesIn(data, window).stream().mapToDouble(AnalyticsService::amount).sum();
        int newClients = (int) data.firstVisit().values().stream().filter(window::contains).count();
        int returningVisits = (int) inWindow.stream().filter(a -> !isFirstVisit(data, a)).count();

        // Solo cuentan las citas que llegaron a decidirse; las solicitudes y propuestas abiertas aún no son nada
        int decided = 0;
        int lost = 0;
        for (Appointment appointment : data.all()) {
            if (!window.contains(appointment.getDateTime()) || appointment.getStatus().isOpen()) {
                continue;
            }
            decided++;
            if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.REJECTED) {
                lost++;
            }
        }
        return new PeriodFigures(revenue, inWindow.size(), newClients,
                inWindow.isEmpty() ? 0 : revenue / inWindow.size(),
                inWindow.isEmpty() ? 0 : 100.0 * returningVisits / inWindow.size(),
                decided == 0 ? 0 : 100.0 * lost / decided);
    }

    /**
     * Un punto por mes. Los meses anteriores al primer mes con Cuquora y los que aún no han llegado no tienen datos
     * (no son meses a cero). Con {@code withPreviousYear}, cada mes lleva también el mismo mes del año anterior.
     */
    private static List<MonthPoint> monthly(CenterData data, YearMonth first, YearMonth last, boolean withPreviousYear) {
        Map<YearMonth, MonthTotals> totals = new HashMap<>();
        for (Appointment visit : data.visits()) {
            MonthTotals month = totals.computeIfAbsent(YearMonth.from(visit.getDateTime()), m -> new MonthTotals());
            month.treatments += price(visit);
            if (isFirstVisit(data, visit)) {
                month.newVisits++;
            } else {
                month.returningVisits++;
            }
        }
        for (ProductSale sale : data.sales()) {
            totals.computeIfAbsent(YearMonth.from(sale.getSoldAt()), m -> new MonthTotals()).products += amount(sale);
        }

        YearMonth currentMonth = YearMonth.from(data.now());
        List<MonthPoint> points = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            String emptyNote = null;
            if (month.isBefore(data.start())) {
                emptyNote = "Sin datos: el centro aún no usaba Cuquora";
            } else if (month.isAfter(currentMonth)) {
                emptyNote = "Todavía no ha llegado";
            }
            MonthTotals values = emptyNote == null ? totals.getOrDefault(month, new MonthTotals()) : new MonthTotals();
            YearMonth sameMonthBefore = month.minusYears(1);
            MonthTotals previous = withPreviousYear && !sameMonthBefore.isBefore(data.start())
                    ? totals.getOrDefault(sameMonthBefore, new MonthTotals())
                    : null;
            points.add(new MonthPoint(shortMonthName(month), monthName(month) + " " + month.getYear(),
                    values.treatments + values.products, values.treatments, values.products,
                    values.newVisits + values.returningVisits, values.newVisits, values.returningVisits,
                    emptyNote, month.equals(data.start()),
                    previous == null ? null : previous.treatments + previous.products,
                    previous == null ? null : previous.newVisits + previous.returningVisits));
        }
        return points;
    }

    /**
     * Por qué cambian los ingresos: visitas por ticket medio, qué clientes han dejado de venir y qué tratamientos
     * suben o bajan. Siempre sobre los dos tramos comparables.
     */
    private static ChangeBreakdown changes(PeriodFigures now, PeriodFigures before, List<Appointment> currentVisits,
                                           List<Appointment> previousVisits, CenterData data) {
        Integer revenueChange = percentChange(now.revenue(), before.revenue());
        Integer visitsChange = percentChange(now.visits(), before.visits());
        Integer ticketChange = percentChange(now.averageTicket(), before.averageTicket());

        Set<Long> currentClients = new HashSet<>();
        currentVisits.forEach(v -> currentClients.add(v.getClient().getId()));
        Map<Long, ClientAccumulator> previousClients = new LinkedHashMap<>();
        for (Appointment visit : previousVisits) {
            ClientAccumulator acc = previousClients.computeIfAbsent(visit.getClient().getId(), id -> new ClientAccumulator(visit.getClient()));
            acc.visits++;
            acc.spent += price(visit);
            acc.lastVisit = max(acc.lastVisit, visit.getDateTime());
        }
        int returning = (int) previousClients.keySet().stream().filter(currentClients::contains).count();
        List<ClientRow> lost = previousClients.values().stream()
                .filter(c -> !currentClients.contains(c.client.getId()))
                .sorted(Comparator.comparingDouble((ClientAccumulator c) -> c.spent).reversed())
                .map(c -> new ClientRow(c.client.getId(), c.client.getName(), c.visits, c.spent,
                        c.lastVisit.toLocalDate(), null, null))
                .toList();
        int gained = (int) currentClients.stream().filter(id -> !previousClients.containsKey(id)).count();

        Map<String, Integer> treatmentsNow = countByTreatment(currentVisits);
        Map<String, Integer> treatmentsBefore = countByTreatment(previousVisits);
        Set<String> names = new HashSet<>(treatmentsNow.keySet());
        names.addAll(treatmentsBefore.keySet());
        List<DiffRow> diffs = names.stream()
                .map(name -> new DiffRow(name, treatmentsBefore.getOrDefault(name, 0), treatmentsNow.getOrDefault(name, 0)))
                .filter(row -> row.diff() != 0)
                .toList();
        List<DiffRow> drops = diffs.stream().filter(r -> r.diff() < 0)
                .sorted(Comparator.comparingInt(DiffRow::diff).thenComparing(DiffRow::name)).limit(CHANGE_LIST_SIZE).toList();
        List<DiffRow> gains = diffs.stream().filter(r -> r.diff() > 0)
                .sorted(Comparator.comparingInt(DiffRow::diff).reversed().thenComparing(DiffRow::name)).limit(CHANGE_LIST_SIZE).toList();

        return new ChangeBreakdown(revenueChange, visitsChange, ticketChange,
                verdict(revenueChange, visitsChange, ticketChange),
                returning, lost.size(), gained, lost.stream().limit(CHANGE_LIST_SIZE + 2).toList(), drops, gains);
    }

    /** Una frase que dice qué pesa más en el cambio de ingresos: cuánta gente viene o cuánto deja cada visita. */
    public static String verdict(Integer revenue, Integer visits, Integer ticket) {
        if (revenue == null) {
            return null;
        }
        if (revenue == 0) {
            return "Los ingresos están igual que en el mismo tramo del año anterior.";
        }
        String direction = revenue > 0 ? "suben" : "bajan";
        int v = visits == null ? 0 : visits;
        int t = ticket == null ? 0 : ticket;
        String driver = Math.abs(v) >= Math.abs(t)
                ? (v > 0 ? "viene más gente" : v < 0 ? "viene menos gente" : "cada visita deja " + (t > 0 ? "más" : "menos"))
                : (t > 0 ? "cada visita deja más dinero" : "cada visita deja menos dinero");
        return "Los ingresos " + direction + " un " + Math.abs(revenue) + " %, sobre todo porque " + driver + ".";
    }

    private static Map<String, Integer> countByTreatment(List<Appointment> visits) {
        Map<String, Integer> counts = new HashMap<>();
        visits.forEach(v -> counts.merge(v.getTreatment().getName(), 1, Integer::sum));
        return counts;
    }

    private static List<ClientRow> topClients(List<Appointment> periodVisits, List<ProductSale> periodSales) {
        Map<Long, ClientAccumulator> byClient = new HashMap<>();
        for (Appointment visit : periodVisits) {
            ClientAccumulator acc = byClient.computeIfAbsent(visit.getClient().getId(), id -> new ClientAccumulator(visit.getClient()));
            acc.visits++;
            acc.spent += price(visit);
            acc.lastVisit = max(acc.lastVisit, visit.getDateTime());
        }
        for (ProductSale sale : periodSales) {
            ClientAccumulator acc = byClient.get(sale.getClient().getId());
            if (acc != null) {
                acc.spent += amount(sale);
            }
        }
        return byClient.values().stream()
                .sorted(Comparator.comparingInt((ClientAccumulator c) -> c.visits).reversed()
                        .thenComparing(Comparator.comparingDouble((ClientAccumulator c) -> c.spent).reversed()))
                .limit(CLIENT_LIST_SIZE)
                .map(c -> new ClientRow(c.client.getId(), c.client.getName(), c.visits, c.spent,
                        c.lastVisit.toLocalDate(), null, null))
                .toList();
    }

    /**
     * Clientes que venían a menudo (dos visitas o más) y llevan mucho sin venir ni tener cita: los que conviene llamar.
     * «Mucho» es el doble de lo que suele tardar en volver, y nunca menos de {@link #MIN_DAYS_AWAY} días.
     */
    private static List<ClientRow> clientsToWinBack(CenterData data) {
        LocalDateTime now = data.now();
        Set<Long> withUpcoming = new HashSet<>();
        for (Appointment appointment : data.all()) {
            AppointmentStatus status = appointment.getStatus();
            if (appointment.getClient() != null && appointment.getDateTime().isAfter(now)
                    && (status == AppointmentStatus.CONFIRMED || status.isOpen())) {
                withUpcoming.add(appointment.getClient().getId());
            }
        }
        Map<Long, List<Appointment>> byClient = new LinkedHashMap<>();
        for (Appointment visit : data.visits()) {
            byClient.computeIfAbsent(visit.getClient().getId(), id -> new ArrayList<>()).add(visit);
        }
        List<ClientRow> rows = new ArrayList<>();
        byClient.forEach((clientId, clientVisits) -> {
            if (clientVisits.size() < 2 || withUpcoming.contains(clientId)) {
                return;
            }
            LocalDateTime first = clientVisits.getFirst().getDateTime();
            LocalDateTime last = clientVisits.getLast().getDateTime();
            long usualGap = Duration.between(first, last).toDays() / (clientVisits.size() - 1);
            long daysAway = ChronoUnit.DAYS.between(last.toLocalDate(), now.toLocalDate());
            if (daysAway >= Math.max(MIN_DAYS_AWAY, 2 * usualGap)) {
                double spent = clientVisits.stream().mapToDouble(AnalyticsService::price).sum();
                User client = clientVisits.getFirst().getClient();
                rows.add(new ClientRow(clientId, client.getName(), clientVisits.size(), spent,
                        last.toLocalDate(), daysAway, Math.max(1, usualGap)));
            }
        });
        return rows.stream()
                .sorted(Comparator.comparingDouble(ClientRow::spent).reversed())
                .limit(CLIENT_LIST_SIZE)
                .toList();
    }

    private static List<RankRow> treatmentRanking(List<Appointment> periodVisits, List<Appointment> compared,
                                                  List<Appointment> previous) {
        return ranking(treatmentTotals(periodVisits),
                compared == null ? null : treatmentTotals(compared),
                previous == null ? null : treatmentTotals(previous));
    }

    private static Map<String, double[]> treatmentTotals(List<Appointment> visits) {
        Map<String, double[]> byName = new HashMap<>();
        for (Appointment visit : visits) {
            double[] values = byName.computeIfAbsent(visit.getTreatment().getName(), name -> new double[2]);
            values[0]++;
            values[1] += price(visit);
        }
        return byName;
    }

    private static List<RankRow> productRanking(List<ProductSale> periodSales, List<ProductSale> compared,
                                                List<ProductSale> previous) {
        return ranking(productTotals(periodSales),
                compared == null ? null : productTotals(compared),
                previous == null ? null : productTotals(previous));
    }

    private static Map<String, double[]> productTotals(List<ProductSale> productSales) {
        Map<String, double[]> byName = new HashMap<>();
        for (ProductSale sale : productSales) {
            double[] values = byName.computeIfAbsent(sale.getProduct().getName(), name -> new double[2]);
            values[0] += sale.getQuantity();
            values[1] += amount(sale);
        }
        return byName;
    }

    /** {@code compared}/{@code previous}: totales de los tramos comparables, para la diferencia (null = sin comparar). */
    private static List<RankRow> ranking(Map<String, double[]> byName, Map<String, double[]> compared,
                                         Map<String, double[]> previous) {
        double top = byName.values().stream().mapToDouble(v -> v[0]).max().orElse(0);
        return byName.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[0]).reversed()
                        .thenComparing(e -> -e.getValue()[1]))
                .limit(RANKING_SIZE)
                .map(e -> new RankRow(e.getKey(), (int) e.getValue()[0], e.getValue()[1], percent(e.getValue()[0], top),
                        previous == null ? null
                                : (int) (compared.getOrDefault(e.getKey(), new double[2])[0]
                                        - previous.getOrDefault(e.getKey(), new double[2])[0])))
                .toList();
    }

    private List<TeamRow> team(Long centerId, List<Appointment> periodVisits, List<Appointment> compared,
                               List<Appointment> previous) {
        Map<Long, Integer> visitsByWorker = visitsByWorker(periodVisits);
        Map<Long, Integer> comparedByWorker = compared == null ? null : visitsByWorker(compared);
        Map<Long, Integer> previousByWorker = previous == null ? null : visitsByWorker(previous);
        Map<Long, Double> revenueByWorker = new HashMap<>();
        Map<Long, Set<Long>> clientsByWorker = new HashMap<>();
        for (Appointment visit : periodVisits) {
            if (visit.getWorker() != null) {
                revenueByWorker.merge(visit.getWorker().getId(), price(visit), Double::sum);
                clientsByWorker.computeIfAbsent(visit.getWorker().getId(), id -> new HashSet<>()).add(visit.getClient().getId());
            }
        }
        double top = revenueByWorker.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return users.findByCenterIdAndRole(centerId, Role.WORKER).stream()
                .map(worker -> {
                    double revenue = revenueByWorker.getOrDefault(worker.getId(), 0.0);
                    return new TeamRow(worker.getName(), visitsByWorker.getOrDefault(worker.getId(), 0),
                            clientsByWorker.getOrDefault(worker.getId(), Set.of()).size(), revenue, percent(revenue, top),
                            previousByWorker == null ? null
                                    : comparedByWorker.getOrDefault(worker.getId(), 0) - previousByWorker.getOrDefault(worker.getId(), 0));
                })
                .sorted(Comparator.comparingDouble(TeamRow::revenue).reversed().thenComparing(TeamRow::name))
                .toList();
    }

    private static Map<Long, Integer> visitsByWorker(List<Appointment> visits) {
        Map<Long, Integer> counts = new HashMap<>();
        visits.stream().filter(v -> v.getWorker() != null).forEach(v -> counts.merge(v.getWorker().getId(), 1, Integer::sum));
        return counts;
    }

    private static List<WeekdayRow> weekdays(List<Appointment> periodVisits) {
        Map<DayOfWeek, Integer> counts = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            counts.put(day, 0);
        }
        periodVisits.forEach(visit -> counts.merge(visit.getDateTime().getDayOfWeek(), 1, Integer::sum));
        int top = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return counts.entrySet().stream()
                .map(e -> new WeekdayRow(capitalize(e.getKey().getDisplayName(TextStyle.FULL, ES)),
                        capitalize(e.getKey().getDisplayName(TextStyle.SHORT, ES)).replace(".", ""),
                        e.getValue(), percent(e.getValue(), top), top > 0 && e.getValue() == top))
                .toList();
    }

    /** Hora en punto en la que empiezan más visitas (null si no hay ninguna). */
    private static Integer busiestHour(List<Appointment> periodVisits) {
        Map<Integer, Integer> byHour = new HashMap<>();
        periodVisits.forEach(visit -> byHour.merge(visit.getDateTime().getHour(), 1, Integer::sum));
        return byHour.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static List<Appointment> visitsIn(CenterData data, Window window) {
        return data.visits().stream().filter(a -> window.contains(a.getDateTime())).toList();
    }

    private static List<ProductSale> salesIn(CenterData data, Window window) {
        return data.sales().stream().filter(s -> window.contains(s.getSoldAt())).toList();
    }

    private static boolean isFirstVisit(CenterData data, Appointment visit) {
        return !data.firstVisit().get(visit.getClient().getId()).isBefore(visit.getDateTime());
    }

    private static double price(Appointment appointment) {
        return appointment.getPrice() != null ? appointment.getPrice() : 0;
    }

    private static double amount(ProductSale sale) {
        return sale.getQuantity() * sale.getUnitPrice();
    }

    private static int percent(double value, double top) {
        return top <= 0 ? 0 : (int) Math.round(100 * value / top);
    }

    private static Integer percentChange(double value, double previous) {
        return previous == 0 ? null : (int) Math.round(100 * (value - previous) / previous);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a == null || b.isAfter(a) ? b : a;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return b == null || a.isBefore(b) ? a : b;
    }

    /** «1-18 sept», o «1 sept» el primer día del mes. */
    private static String dayRange(Window window) {
        int from = window.from().getDayOfMonth();
        int to = window.to().getDayOfMonth();
        return (from == to ? String.valueOf(from) : from + "-" + to) + " " + shortMonthName(YearMonth.from(window.from()));
    }

    /** «2025» si es el año entero; si no, «el 1 ago-18 oct de 2025». */
    private static String spanLabel(Window window) {
        LocalDate from = window.from().toLocalDate();
        LocalDate to = window.to().toLocalDate();
        if (from.getDayOfYear() == 1 && to.getMonthValue() == 12 && to.getDayOfMonth() == 31) {
            return String.valueOf(from.getYear());
        }
        return "el " + from.getDayOfMonth() + " " + shortMonthName(YearMonth.from(from)) + "-"
                + to.getDayOfMonth() + " " + shortMonthName(YearMonth.from(to)) + " de " + to.getYear();
    }

    private static String monthName(YearMonth month) {
        return capitalize(month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, ES));
    }

    private static String shortMonthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, ES).replace(".", "");
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Intervalo cerrado [from, to] en hora del centro. */
    private record Window(LocalDateTime from, LocalDateTime to) {
        boolean contains(LocalDateTime moment) {
            return moment != null && !moment.isBefore(from) && !moment.isAfter(to);
        }
    }

    /** Todo lo del centro que hace falta para cualquier vista; {@code start}: primer mes con Cuquora. */
    private record CenterData(Long centerId, LocalDateTime now, List<Appointment> all, List<Appointment> visits,
                              List<ProductSale> sales, Map<Long, LocalDateTime> firstVisit, YearMonth start) {
    }

    /**
     * Qué enseña cada vista. {@code current}/{@code previous}: tramos de las cifras de arriba y de las diferencias
     * ({@code previous} null = sin comparación); {@code changeWindow} no nulo = se enseña «Qué ha cambiado».
     */
    private record View(int months, Integer year, Integer previousYear, Integer nextYear,
                        String headline, String comparisonLabel, String comparisonNote, String noComparisonText,
                        String periodLabel, String currentSeriesLabel, String previousSeriesLabel,
                        Window period, Window current, Window previous, Window changeWindow,
                        YearMonth firstMonth, YearMonth lastMonth, boolean withPreviousYear,
                        int bookedRestOfMonth, double bookedRevenueRestOfMonth, boolean showWinBack) {
    }

    private record PeriodFigures(double revenue, int visits, int newClients, double averageTicket,
                                 double returningShare, double cancelledShare) {
    }

    private static final class MonthTotals {
        private double treatments;
        private double products;
        private int newVisits;
        private int returningVisits;
    }

    private static final class ClientAccumulator {
        private final User client;
        private int visits;
        private double spent;
        private LocalDateTime lastVisit;

        private ClientAccumulator(User client) {
            this.client = client;
        }
    }

    public enum Format { MONEY, NUMBER, PERCENT }

    /**
     * Cifra del periodo frente al tramo comparable. {@code change}: variación en % (o en puntos si la cifra ya es un
     * porcentaje); null si no hay con qué comparar.
     */
    public record Kpi(String label, String icon, double value, double previous, Format format, Integer change,
                      boolean higherIsBetter) {

        static Kpi of(String label, String icon, double value, double previous, Format format, boolean higherIsBetter,
                      boolean compare) {
            Integer change = null;
            if (compare && format == Format.PERCENT) {
                change = (int) Math.round(value - previous);
            } else if (compare) {
                change = percentChange(value, previous);
            }
            return new Kpi(label, icon, value, previous, format, change, higherIsBetter);
        }

        /** El cambio es una mejora (sube lo que conviene que suba o baja lo que conviene que baje). */
        public boolean isImprovement() {
            return change != null && change != 0 && (change > 0) == higherIsBetter;
        }

        public boolean isWorsening() {
            return change != null && change != 0 && (change > 0) != higherIsBetter;
        }

        public String displayValue() {
            return display(value);
        }

        public String displayPrevious() {
            return display(previous);
        }

        private String display(double amount) {
            return switch (format) {
                case MONEY -> money(amount, amount > 0 && amount < 100 ? 2 : 0);
                case NUMBER -> String.format(ES, "%,d", Math.round(amount));
                case PERCENT -> Math.round(amount) + " %";
            };
        }

        /** «+12 %», «-3 pts» o «Igual»; null si no hay con qué comparar. */
        public String changeLabel() {
            if (change == null) {
                return null;
            }
            if (change == 0) {
                return "Igual";
            }
            String sign = change > 0 ? "+" : "−";
            return sign + Math.abs(change) + (format == Format.PERCENT ? " pts" : " %");
        }
    }

    /** Importe en euros al estilo español: «1.234 €» o «32,50 €». */
    public static String money(double amount, int decimals) {
        return String.format(ES, "%,." + decimals + "f €", amount);
    }

    /** «+5», «−3» o «=»: diferencia de una cantidad frente al año anterior. */
    public static String signed(int diff) {
        return diff == 0 ? "=" : (diff > 0 ? "+" : "−") + Math.abs(diff);
    }

    /**
     * Un mes del gráfico. {@code emptyNote}: por qué no tiene datos (antes de Cuquora o aún no ha llegado), null si los
     * tiene. {@code cuquoraStart}: primer mes con Cuquora. {@code previous*}: el mismo mes del año anterior (null si
     * no se compara o entonces aún no se usaba Cuquora).
     */
    public record MonthPoint(String shortLabel, String label, double revenue, double treatmentRevenue,
                             double productRevenue, int visits, int newVisits, int returningVisits,
                             String emptyNote, boolean cuquoraStart, Double previousRevenue, Integer previousVisits) {

        public boolean hasData() {
            return emptyNote == null;
        }

        /** Inicial del mes («E», «F»…): en móvil no caben los doce nombres. */
        public String initial() {
            return shortLabel.substring(0, 1).toUpperCase(ES);
        }

        /** «−12 %» frente al mismo mes del año anterior; null si entonces no había datos o estaba a cero. */
        public String revenueChangeLabel() {
            return previousRevenue == null || previousRevenue == 0 || !hasData() ? null
                    : ChangeBreakdown.changeText(percentChange(revenue, previousRevenue));
        }

        public String visitsChangeLabel() {
            return previousVisits == null || previousVisits == 0 || !hasData() ? null
                    : ChangeBreakdown.changeText(percentChange(visits, previousVisits));
        }
    }

    /** {@code daysAway} y {@code usualGapDays} solo se rellenan en «clientes a recuperar». */
    public record ClientRow(Long id, String name, int visits, double spent, LocalDate lastVisit,
                            Long daysAway, Long usualGapDays) {

        public String awayLabel() {
            return daysAway == null ? null : "Hace " + spanOf(daysAway);
        }

        public String usualGapLabel() {
            return usualGapDays == null ? null : "Venía cada " + spanOf(usualGapDays);
        }

        private static String spanOf(long days) {
            if (days < 14) {
                return days == 1 ? "1 día" : days + " días";
            }
            if (days < 60) {
                return Math.round(days / 7.0) + " semanas";
            }
            long months = Math.round(days / 30.0);
            return months == 1 ? "1 mes" : months + " meses";
        }
    }

    /** {@code share}: % frente al primero de la lista, para el largo de la barra. {@code diff}: frente al año anterior. */
    public record RankRow(String name, int count, double revenue, int share, Integer diff) {
        public String diffLabel() {
            return diff == null ? null : signed(diff);
        }
    }

    public record TeamRow(String name, int visits, int clients, double revenue, int share, Integer diff) {
        public String diffLabel() {
            return diff == null ? null : signed(diff);
        }
    }

    public record WeekdayRow(String name, String shortName, int visits, int share, boolean busiest) {
    }

    /** Visitas de un tratamiento en el tramo anterior ({@code before}) y en el actual ({@code after}). */
    public record DiffRow(String name, int before, int after) {
        public int diff() {
            return after - before;
        }

        public String diffLabel() {
            return signed(diff());
        }
    }

    /** «Qué ha cambiado» en la vista por año. Variaciones en %, null si el tramo anterior estaba a cero. */
    public record ChangeBreakdown(Integer revenueChange, Integer visitsChange, Integer ticketChange, String verdict,
                                  int returningClients, int lostClients, int gainedClients, List<ClientRow> lostTopClients,
                                  List<DiffRow> treatmentDrops, List<DiffRow> treatmentGains) {

        public String revenueLabel() {
            return changeText(revenueChange);
        }

        public String visitsLabel() {
            return changeText(visitsChange);
        }

        public String ticketLabel() {
            return changeText(ticketChange);
        }

        static String changeText(Integer change) {
            if (change == null) {
                return "Sin datos";
            }
            return change == 0 ? "Igual" : (change > 0 ? "+" : "−") + Math.abs(change) + " %";
        }
    }

    public record AnalyticsReport(
            int months,
            boolean yearView,
            Integer year,
            Integer previousYear,
            Integer nextYear,
            String headline,
            String comparisonLabel,
            String comparisonNote,
            String noComparisonText,
            String periodLabel,
            String currentSeriesLabel,
            String previousSeriesLabel,
            String cuquoraStartLabel,
            List<Kpi> kpis,
            int bookedRestOfMonth,
            double bookedRevenueRestOfMonth,
            List<MonthPoint> monthly,
            double maxMonthlyRevenue,
            int maxMonthlyVisits,
            int periodVisits,
            double periodRevenue,
            int periodClients,
            int periodNewClients,
            ChangeBreakdown changes,
            List<ClientRow> topClients,
            List<ClientRow> clientsToWinBack,
            boolean showWinBack,
            List<RankRow> treatments,
            List<RankRow> products,
            List<TeamRow> team,
            List<WeekdayRow> weekdays,
            Integer busiestHour,
            boolean hasData) {

        /** Alto de cada barra del gráfico mensual, en % del mes más alto (de los dos años si se comparan). */
        public int revenueHeight(MonthPoint point) {
            return height(point.revenue(), maxMonthlyRevenue);
        }

        public int previousRevenueHeight(MonthPoint point) {
            return point.previousRevenue() == null ? 0 : height(point.previousRevenue(), maxMonthlyRevenue);
        }

        public int visitsHeight(MonthPoint point) {
            return height(point.visits(), maxMonthlyVisits);
        }

        public int previousVisitsHeight(MonthPoint point) {
            return point.previousVisits() == null ? 0 : height(point.previousVisits(), maxMonthlyVisits);
        }

        public int newVisitsHeight(MonthPoint point) {
            return height(point.newVisits(), maxMonthlyVisits);
        }

        public int returningVisitsHeight(MonthPoint point) {
            return height(point.returningVisits(), maxMonthlyVisits);
        }

        public double periodAverageTicket() {
            return periodVisits == 0 ? 0 : periodRevenue / periodVisits;
        }

        /** Algún mes visible es el primero con Cuquora: se explica la bolita en la leyenda. */
        public boolean showsCuquoraStart() {
            return monthly.stream().anyMatch(MonthPoint::cuquoraStart);
        }

        private static int height(double value, double max) {
            return max <= 0 ? 0 : (int) Math.round(100 * value / max);
        }
    }
}
