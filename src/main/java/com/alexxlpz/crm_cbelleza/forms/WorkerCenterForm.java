package com.alexxlpz.crm_cbelleza.forms;

/** Formularios de "Mi centro": datos públicos del centro (apartado Centro) o cuenta del profesional (Mi cuenta). */
public record WorkerCenterForm(String centerName, String centerAddress, String centerPhoneCountry, String centerPhone,
                               String centerEmail,
                               Double latitude, Double longitude, String timeZone, Integer cabins,
                               String workerName, String workerPhone, String currentPassword, String newPassword,
                               String confirmPassword) {

    public CenterForm center() {
        return new CenterForm(centerName, centerAddress, PhoneNumbers.withCountry(centerPhoneCountry, centerPhone), centerEmail,
                latitude, longitude, timeZone, cabins);
    }

    public ProfileForm worker() {
        return new ProfileForm(workerName, workerPhone, currentPassword, newPassword, confirmPassword);
    }
}
