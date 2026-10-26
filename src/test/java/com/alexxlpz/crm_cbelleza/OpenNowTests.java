package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.Closure;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.OpenState;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.Period;
import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterSearchService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * «Abierto ahora» en el buscador de centros: cuándo está abierto un centro, el texto de su tarjeta y el filtro
 * con la base de datos de ejemplo. Cada test se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OpenNowTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private AgendaBlockRepository blocks;
    @Autowired
    private Clock clock;

    // Lunes 20 de octubre de 2025
    private static final LocalDateTime MONDAY = LocalDateTime.of(2025, 10, 20, 0, 0);

    /** Lunes a viernes 10:00-14:00 y 16:30-20:30, como Plaza Norte; fines de semana cerrado. */
    private static final OpeningHours SPLIT = new OpeningHours(30, Stream.of(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            .flatMap(day -> Stream.of(
                    new Period(day, LocalTime.of(10, 0), LocalTime.of(14, 0)),
                    new Period(day, LocalTime.of(16, 30), LocalTime.of(20, 30))))
            .toList());

    @Test
    void openCentersSayWhenTheyClose() {
        assertEquals(new OpenState(true, MONDAY.withHour(14)), SPLIT.stateAt(MONDAY.withHour(12), List.of()));
        assertEquals(new OpenState(true, MONDAY.withHour(14)), SPLIT.stateAt(MONDAY.withHour(10), List.of()));
        assertEquals(new OpenState(true, MONDAY.withHour(20).withMinute(30)), SPLIT.stateAt(MONDAY.withHour(17), List.of()));
    }

    @Test
    void closedCentersSayWhenTheyOpenNext() {
        // A mediodía, por la mañana temprano, por la noche y el fin de semana
        assertEquals(new OpenState(false, MONDAY.withHour(16).withMinute(30)), SPLIT.stateAt(MONDAY.withHour(14), List.of()));
        assertEquals(new OpenState(false, MONDAY.withHour(10)), SPLIT.stateAt(MONDAY.withHour(8), List.of()));
        assertEquals(new OpenState(false, MONDAY.plusDays(1).withHour(10)), SPLIT.stateAt(MONDAY.withHour(21), List.of()));
        assertEquals(new OpenState(false, MONDAY.plusDays(7).withHour(10)), SPLIT.stateAt(MONDAY.plusDays(5).withHour(11), List.of()));
        assertEquals(new OpenState(false, null), new OpeningHours(30, List.of()).stateAt(MONDAY, List.of()));
    }

    @Test
    void closuresOfTheWholeCenterCountAsClosed() {
        // Cerrado el lunes hasta las 12:00 y de martes a jueves enteros
        List<Closure> closures = List.of(
                new Closure(MONDAY.withHour(9), MONDAY.withHour(12)),
                new Closure(MONDAY.plusDays(1), MONDAY.plusDays(4)));
        assertEquals(new OpenState(false, MONDAY.withHour(12)), SPLIT.stateAt(MONDAY.withHour(10).withMinute(30), closures));
        assertEquals(new OpenState(true, MONDAY.withHour(14)), SPLIT.stateAt(MONDAY.withHour(12), closures));
        assertEquals(new OpenState(false, MONDAY.plusDays(4).withHour(10)), SPLIT.stateAt(MONDAY.withHour(21), closures));
    }

    @Test
    void periodsThatTouchCountAsOne() {
        OpeningHours continuous = new OpeningHours(30, List.of(
                new Period(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)),
                new Period(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(20, 0))));
        assertEquals(new OpenState(true, MONDAY.withHour(20)), continuous.stateAt(MONDAY.withHour(13), List.of()));
    }

    @Test
    void cardLabels() {
        LocalDateTime now = MONDAY.withHour(12);
        assertEquals("Abierto · cierra a las 14:00", CenterSearchService.hoursLabel(new OpenState(true, MONDAY.withHour(14)), now));
        assertEquals("Cerrado · abre a las 16:30", CenterSearchService.hoursLabel(new OpenState(false, MONDAY.withHour(16).withMinute(30)), now));
        assertEquals("Cerrado · abre mañana a las 9:00", CenterSearchService.hoursLabel(new OpenState(false, MONDAY.plusDays(1).withHour(9)), now));
        assertEquals("Cerrado · abre el sábado a las 10:00", CenterSearchService.hoursLabel(new OpenState(false, MONDAY.plusDays(5).withHour(10)), now));
        assertEquals("Cerrado · abre el 3 de noviembre a las 9:00", CenterSearchService.hoursLabel(new OpenState(false, MONDAY.plusDays(14).withHour(9)), now));
        assertEquals("Cerrado", CenterSearchService.hoursLabel(new OpenState(false, null), now));
    }

    @Test
    void everyCenterSaysWhetherItIsOpenAndTheFilterKeepsOnlyOpenOnes() throws Exception {
        // Centro Histórico cierra ahora mismo por un imprevisto
        Center closed = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter();
        LocalDateTime now = CenterClock.now(clock, centers.findById(closed.getId()).orElseThrow());
        blocks.save(AgendaBlock.builder().center(closed).startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .reason("Avería").build());

        List<Map<String, Object>> all = search("/api/centers?size=50");
        assertFalse(all.isEmpty());
        for (Map<String, Object> center : all) {
            boolean open = (Boolean) center.get("openNow");
            assertEquals(open, ((String) center.get("hoursLabel")).startsWith("Abierto"), center.toString());
        }
        Map<String, Object> closedCard = all.stream()
                .filter(c -> closed.getId().equals(((Number) c.get("id")).longValue())).findFirst().orElseThrow();
        assertEquals(false, closedCard.get("openNow"));
        assertTrue(((String) closedCard.get("hoursLabel")).startsWith("Cerrado"));

        List<Map<String, Object>> open = search("/api/centers?size=50&openNow=true");
        assertTrue(open.stream().allMatch(c -> (Boolean) c.get("openNow")));
        assertEquals(all.stream().filter(c -> (Boolean) c.get("openNow")).count(), open.size());
        assertTrue(open.stream().noneMatch(c -> closed.getId().equals(((Number) c.get("id")).longValue())));
    }

    private List<Map<String, Object>> search(String url) throws Exception {
        return JsonPath.read(mvc.perform(get(url)).andReturn().getResponse().getContentAsString(), "$");
    }
}
