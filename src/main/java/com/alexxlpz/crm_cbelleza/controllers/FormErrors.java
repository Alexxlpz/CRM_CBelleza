package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.FieldException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error de un formulario que vuelve a su página: el mensaje arriba y, si es de un campo, ese campo en rojo con el
 * mensaje debajo (fragments/alerts :: flash y app.js). Lo escrito se vuelve a poner en el formulario para no perderlo.
 */
final class FormErrors {

    private FormErrors() {
    }

    /**
     * @param fieldIds  campo de {@link FieldException} → id del input en la página
     * @param submitted id del input → lo que se había escrito (nunca contraseñas)
     */
    static void flash(RedirectAttributes redirect, BusinessRuleException e, Map<String, String> fieldIds,
                      Map<String, String> submitted) {
        redirect.addFlashAttribute("errorMessage", e.getMessage());
        if (e instanceof FieldException field && fieldIds.containsKey(field.getField())) {
            redirect.addFlashAttribute("errorField", fieldIds.get(field.getField()));
        }
        Map<String, String> values = new LinkedHashMap<>();
        submitted.forEach((id, value) -> {
            if (value != null) {
                values.put(id, value);
            }
        });
        redirect.addFlashAttribute("submittedValues", values);
    }

    /** Mapa a partir de pares clave, valor (admite valores nulos, a diferencia de Map.of). */
    static Map<String, String> pairs(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
