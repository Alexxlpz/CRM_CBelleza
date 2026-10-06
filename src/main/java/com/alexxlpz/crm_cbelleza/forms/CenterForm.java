package com.alexxlpz.crm_cbelleza.forms;

/** Datos públicos del centro editables desde "Mi centro". */
public record CenterForm(String name, String address, String phone, String email, Double latitude, Double longitude) {
}
