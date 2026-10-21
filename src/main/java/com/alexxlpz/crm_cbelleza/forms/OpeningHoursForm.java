package com.alexxlpz.crm_cbelleza.forms;

import java.util.List;

/**
 * Horario del centro tal como llega del editor (fragments/opening-hours.html): un trío por tramo con el día
 * (1 = lunes ... 7 = domingo), la hora de apertura y la de cierre ("09:00"). Los días cerrados no envían nada.
 * La validación está en {@code OpeningHoursService}.
 */
public record OpeningHoursForm(List<Integer> hoursDay, List<String> hoursFrom, List<String> hoursTo) {

    public boolean isEmpty() {
        return hoursDay == null || hoursDay.isEmpty();
    }
}
