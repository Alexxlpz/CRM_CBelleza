package com.alexxlpz.crm_cbelleza.forms;

import java.util.ArrayList;
import java.util.List;

/**
 * Alta pública de un centro (/alta-centro): datos del centro, horario de apertura (hoursDay, hoursFrom y hoursTo,
 * como en «Mi Centro»), ubicación en el mapa, responsable y equipo, y la tarjeta de puntos opcional (mode, pointsPerUnit, rewardPoints y rewardText, como en «Mi Centro»).
 * La validación está en {@code CenterRegistrationService}.
 */
public record CenterRegistrationForm(String centerName, String cif, String phone, String address,
                                     Double latitude, Double longitude, String timeZone, Integer cabins,
                                     List<Integer> hoursDay, List<String> hoursFrom, List<String> hoursTo,
                                     String contactName, String email,
                                     List<String> workerNames, List<String> workerEmails,
                                     String mode, Integer pointsPerUnit, Integer rewardPoints, String rewardText,
                                     List<String> specialties, String notes, Boolean terms) {

    public List<String> specialtiesOrEmpty() {
        return specialties == null ? List.of() : specialties;
    }

    public OpeningHoursForm openingHours() {
        return new OpeningHoursForm(hoursDay, hoursFrom, hoursTo);
    }

    public LoyaltySettingsForm loyalty() {
        return new LoyaltySettingsForm(mode, pointsPerUnit, rewardPoints, rewardText);
    }

    /** Trabajadores además del responsable, tal como llegan (las filas vacías se descartan). */
    public List<Member> extraWorkers() {
        List<String> names = workerNames == null ? List.of() : workerNames;
        List<String> emails = workerEmails == null ? List.of() : workerEmails;
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < Math.max(names.size(), emails.size()); i++) {
            String name = i < names.size() ? FormText.trimToNull(names.get(i)) : null;
            String email = i < emails.size() ? FormText.trimToNull(emails.get(i)) : null;
            if (name != null || email != null) {
                members.add(new Member(name, email));
            }
        }
        return members;
    }

    /** Filas que se vuelven a pintar si el formulario tiene un error (al menos una, vacía). */
    public List<Member> workerRowsForView() {
        List<Member> rows = extraWorkers();
        return rows.isEmpty() ? List.of(new Member(null, null)) : rows;
    }

    public record Member(String name, String email) {
    }

    /** Formulario vacío para la primera visita. */
    public static CenterRegistrationForm empty() {
        return new CenterRegistrationForm(null, null, null, null, null, null, null, 1, null, null, null, null, null,
                List.of(), List.of(), "OFF", 1, 100, null, List.of(), null, null);
    }
}
