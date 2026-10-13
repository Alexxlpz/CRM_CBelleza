package com.alexxlpz.crm_cbelleza.forms;

/** Tarjeta de puntos del centro en «Mi Centro». mode: OFF, PER_EURO o PER_ITEM. */
public record LoyaltySettingsForm(String mode, Integer pointsPerUnit, Integer rewardPoints, String rewardText) {
}
