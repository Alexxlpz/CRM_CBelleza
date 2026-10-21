package com.alexxlpz.crm_cbelleza.forms;

/** Formulario combinado de "Mi centro": datos públicos del centro + cuenta del profesional. */
public record WorkerCenterForm(String centerName, String centerAddress, String centerPhone, String centerEmail,
                               Double latitude, Double longitude, String timeZone, Integer cabins,
                               String workerName, String workerPhone, String newPassword, String confirmPassword) {

    public CenterForm center() {
        return new CenterForm(centerName, centerAddress, centerPhone, centerEmail, latitude, longitude, timeZone, cabins);
    }

    /** ¿Trae algún dato del centro? Solo el dueño los envía (al resto no se le enseñan esos campos). */
    public boolean hasCenterData() {
        return centerName != null || centerAddress != null || centerPhone != null || centerEmail != null
                || latitude != null || longitude != null || timeZone != null || cabins != null;
    }

    public ProfileForm worker() {
        return new ProfileForm(workerName, workerPhone, newPassword, confirmPassword);
    }
}
