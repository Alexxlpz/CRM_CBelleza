package com.alexxlpz.crm_cbelleza.forms;

/** Oferta de un tratamiento: precio rebajado y, si se pone, último día (yyyy-MM-dd). */
public record TreatmentOfferForm(Double offerPrice, String endsOn) {
}
