package com.alexxlpz.crm_cbelleza.forms;

import java.util.List;

/** Alta de un tratamiento en la carta de servicios del centro. {@code workerIds}: quién lo hace (vacío = todo el equipo). */
public record TreatmentForm(String name, String description, Double price, Integer duration, String type, List<Long> workerIds) {
}
