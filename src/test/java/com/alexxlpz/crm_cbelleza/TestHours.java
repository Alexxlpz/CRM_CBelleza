package com.alexxlpz.crm_cbelleza;

import java.util.List;

/** Horario de lunes a viernes de 9:00 a 20:00 tal como lo envía el editor del alta y de «Mi Centro». */
final class TestHours {

    static final List<Integer> DAYS = List.of(1, 2, 3, 4, 5);
    static final List<String> FROM = List.of("09:00", "09:00", "09:00", "09:00", "09:00");
    static final List<String> TO = List.of("20:00", "20:00", "20:00", "20:00", "20:00");

    private TestHours() {
    }
}
