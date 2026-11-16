package com.alexxlpz.crm_cbelleza.exceptions;

import java.io.Serial;

/**
 * El enlace «Publicar centro» del correo a administración ha caducado. Para quien no lo trate aparte es un 404 más;
 * la página de revisión ofrece pedir otro (que llega solo al correo de administración).
 */
public class ApprovalLinkExpiredException extends ResourceNotFoundException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String centerName;

    public ApprovalLinkExpiredException(String centerName) {
        super("El enlace para revisar " + centerName + " ha caducado.");
        this.centerName = centerName;
    }

    public String getCenterName() {
        return centerName;
    }
}
