package com.alexxlpz.crm_cbelleza.forms;

/** Alta de un tratamiento en la carta de servicios del centro. */
public record TreatmentForm(String name, String description, Double price, Integer duration, String type) {
}
