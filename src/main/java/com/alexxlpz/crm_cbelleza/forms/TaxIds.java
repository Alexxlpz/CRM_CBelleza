package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CIF, NIF y NIE: se guardan en mayúsculas, sin espacios, puntos ni guiones (B12345674). En España se comprueba
 * además la letra o el dígito de control, que detecta casi cualquier error al copiarlo. Para centros de fuera
 * (RUT, RFC, NIT…) basta con letras y números. {@code static/js/center-registration.js} hace lo mismo en el navegador.
 */
public final class TaxIds {

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String CIF_LETTERS = "JABCDEFGHI";
    private static final Pattern ANY = Pattern.compile("^[A-Z0-9]{5,20}$");
    private static final Pattern DNI = Pattern.compile("^\\d{8}[A-Z]$");
    private static final Pattern NIE = Pattern.compile("^[XYZ]\\d{7}[A-Z]$");
    private static final Pattern NIF_KLM = Pattern.compile("^[KLM]\\d{7}[A-Z]$");
    private static final Pattern CIF = Pattern.compile("^[ABCDEFGHJNPQRSUVW]\\d{7}[0-9A-J]$");

    private TaxIds() {
    }

    /** Sin espacios, puntos ni guiones y en mayúsculas. */
    public static String compact(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s.\\-/]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Identificador fiscal listo para guardar.
     *
     * @param spanish si el centro está en España (se exige un CIF, NIF o NIE español válido)
     * @throws BusinessRuleException con el motivo, si no es válido
     */
    public static String normalize(String raw, boolean spanish) {
        String id = compact(raw);
        if (id.isEmpty()) {
            throw new BusinessRuleException("Indica el CIF o NIF de la empresa o autónomo.");
        }
        if (!ANY.matcher(id).matches()) {
            throw new BusinessRuleException("Revisa el CIF o NIF: solo letras y números, por ejemplo B12345674.");
        }
        if (!spanish) {
            // Fuera de España cada país tiene el suyo (un RUT chileno como 76123456K parece un NIF y no lo es)
            return id;
        }
        boolean spanishShape = DNI.matcher(id).matches() || NIE.matcher(id).matches()
                || NIF_KLM.matcher(id).matches() || CIF.matcher(id).matches();
        if (!spanishShape) {
            throw new BusinessRuleException("Revisa el CIF o NIF: un NIF son 8 números y una letra (12345678Z), "
                    + "un NIE empieza por X, Y o Z (X1234567L) y un CIF es una letra y 8 caracteres (B12345674).");
        }
        if (!validSpanish(id)) {
            throw new BusinessRuleException("La letra o el último dígito del CIF o NIF " + id
                    + " no cuadra: revisa que esté bien copiado.");
        }
        return id;
    }

    /** ¿Cuadra la letra o el dígito de control? {@code id} ya compacto y con forma de identificador español. */
    static boolean validSpanish(String id) {
        char first = id.charAt(0);
        char last = id.charAt(id.length() - 1);
        if (Character.isDigit(first)) {
            return dniLetter(id.substring(0, 8)) == last;
        }
        if ("XYZ".indexOf(first) >= 0) {
            return dniLetter("XYZ".indexOf(first) + id.substring(1, 8)) == last;
        }
        if ("KLM".indexOf(first) >= 0) {
            return dniLetter(id.substring(1, 8)) == last;
        }
        int control = cifControl(id.substring(1, 8));
        char digit = (char) ('0' + control);
        char letter = CIF_LETTERS.charAt(control);
        if ("PQRSNW".indexOf(first) >= 0) return last == letter;       // siempre con letra
        if ("ABEH".indexOf(first) >= 0) return last == digit;          // siempre con número
        return last == digit || last == letter;
    }

    private static char dniLetter(String digits) {
        return DNI_LETTERS.charAt(Integer.parseInt(digits) % 23);
    }

    private static int cifControl(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(i) - '0';
            if (i % 2 == 0) {
                int doubled = d * 2;
                sum += doubled / 10 + doubled % 10;
            } else {
                sum += d;
            }
        }
        return (10 - sum % 10) % 10;
    }
}
