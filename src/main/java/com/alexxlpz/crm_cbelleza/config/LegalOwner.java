package com.alexxlpz.crm_cbelleza.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Titular de la web (LSSI art. 10 y responsable del tratamiento en la política de privacidad):
 * LEGAL_OWNER_NAME, LEGAL_OWNER_NIF y LEGAL_OWNER_ADDRESS. Las páginas legales los muestran cuando están puestos.
 */
@Component
public class LegalOwner {

    private final String name;
    private final String nif;
    private final String address;

    public LegalOwner(@Value("${crm.legal.owner-name:}") String name,
                      @Value("${crm.legal.owner-nif:}") String nif,
                      @Value("${crm.legal.owner-address:}") String address) {
        this.name = name.strip();
        this.nif = nif.strip();
        this.address = address.strip();
    }

    /** Nombre o razón social; «Cuquora» mientras no esté configurado. */
    public String getName() {
        return name.isEmpty() ? "Cuquora" : name;
    }

    public String getNif() {
        return nif;
    }

    public String getAddress() {
        return address;
    }
}
