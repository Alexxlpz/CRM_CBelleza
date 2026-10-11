package com.alexxlpz.crm_cbelleza.booking;

import com.alexxlpz.crm_cbelleza.entities.Center;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** «Ahora» en la hora local de un centro: las citas se guardan en hora local del centro. */
public final class CenterClock {

    private CenterClock() {
    }

    public static LocalDateTime now(Clock clock, Center center) {
        String zone = center != null && center.getTimeZone() != null ? center.getTimeZone() : CenterTimeZones.DEFAULT;
        return LocalDateTime.now(clock.withZone(ZoneId.of(zone)));
    }
}
