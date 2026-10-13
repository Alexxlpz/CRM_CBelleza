/*
 * Tarjeta de puntos en «Mi Centro»: oculta los campos si está desactivada, cambia la etiqueta
 * según el modo y escribe un ejemplo con los números elegidos.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('loyaltyForm');
    if (!form) return;

    const mode = document.getElementById('loyaltyMode');
    const perUnit = document.getElementById('loyaltyPointsPerUnit');
    const reward = document.getElementById('loyaltyRewardPoints');
    const rewardText = document.getElementById('loyaltyRewardText');
    const label = document.getElementById('loyaltyPointsLabel');
    const example = document.getElementById('loyaltyExample');
    const euros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 2 });

    function update() {
        const off = mode.value === 'OFF';
        form.querySelectorAll('[data-loyalty-field]').forEach(field => { field.hidden = off; });
        if (off) return;

        const perItem = mode.value === 'PER_ITEM';
        label.textContent = perItem ? 'Puntos por tratamiento o producto' : 'Puntos por cada euro';
        const points = Math.max(Number(perUnit.value) || 0, 0);
        const goal = Math.max(Number(reward.value) || 0, 0);
        const prize = rewardText.value.trim() || 'la recompensa';
        if (!points || !goal) {
            example.textContent = '';
            return;
        }
        const needed = Math.ceil(goal / points);
        example.textContent = perItem
            ? `Un cliente consigue «${prize}» tras ${needed} ${needed === 1 ? 'tratamiento o producto' : 'tratamientos o productos'}.`
            : `Un cliente consigue «${prize}» al gastar ${euros.format(needed)}.`;
    }

    [mode, perUnit, reward, rewardText].forEach(input => input.addEventListener('input', update));
    mode.addEventListener('change', update);
    update();
});
