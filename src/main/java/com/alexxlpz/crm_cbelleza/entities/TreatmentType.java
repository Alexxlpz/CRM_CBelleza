package com.alexxlpz.crm_cbelleza.entities;

import lombok.Getter;

@Getter
public enum TreatmentType {
    MANICURA_PEDICURA("Manicura y Pedicura"),
    FACIAL("Tratamientos Faciales"),
    CORPORAL("Tratamientos Corporales"),
    MASAJES("Masajes"),
    PELUQUERIA("Peluquería"),
    DEPILACION("Depilación"),
    OTRO("Otro");

    private final String displayName;

    TreatmentType(String displayName) {
        this.displayName = displayName;
    }

}
