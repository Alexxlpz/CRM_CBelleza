package com.alexxlpz.crm_cbelleza.forms;

/** Formulario combinado de "Mi centro": datos públicos del centro + cuenta del profesional. */
public record WorkerCenterForm(String centerName, String centerAddress, String centerPhone, String centerEmail,
                               Double latitude, Double longitude,
                               String workerName, String workerPhone, String newPassword, String confirmPassword) {

    public CenterForm center() {
        return new CenterForm(centerName, centerAddress, centerPhone, centerEmail, latitude, longitude);
    }

    public ProfileForm worker() {
        return new ProfileForm(workerName, workerPhone, newPassword, confirmPassword);
    }
}
