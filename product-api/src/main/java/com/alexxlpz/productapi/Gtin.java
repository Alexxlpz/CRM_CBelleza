package com.alexxlpz.productapi;

/** Códigos de barras de producto: EAN-8, EAN-13 o GTIN-14, solo dígitos y con su dígito de control. */
public final class Gtin {

    private Gtin() {
    }

    /** El código limpio (un UPC-A de 12 dígitos pasa a EAN-13 con un 0 delante); lanza si no es válido. */
    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.strip();
        if (!digits.matches("\\d{8,14}")) {
            throw new InvalidRequestException("El código tiene que ser un EAN-8, EAN-13 o GTIN-14 (solo dígitos).");
        }
        if (digits.length() == 14 && digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() == 12) digits = "0" + digits;
        if (!(digits.length() == 8 || digits.length() == 13 || digits.length() == 14) || !validCheckDigit(digits)) {
            throw new InvalidRequestException("El código " + digits + " no es válido.");
        }
        return digits;
    }

    static boolean validCheckDigit(String digits) {
        int sum = 0;
        for (int i = digits.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            sum += (digits.charAt(i) - '0') * weight;
        }
        return (10 - sum % 10) % 10 == digits.charAt(digits.length() - 1) - '0';
    }
}
