package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Teléfonos con prefijo de país, guardados siempre igual: «+34 612 345 678». El prefijo va aparte y el resto en
 * grupos para que se lea bien (en España, de tres en tres). {@code static/js/phone-input.js} hace lo mismo en el
 * navegador: si cambia una regla aquí, hay que cambiarla allí.
 */
public final class PhoneNumbers {

    /** España: el prefijo que sale elegido si no se indica otro. */
    public static final String DEFAULT_COUNTRY = "34";

    /** Países que salen en la lista del prefijo (otros se pueden escribir con «+» delante del número). */
    public static final List<Country> COUNTRIES = List.of(
            new Country("34", "España"),
            new Country("351", "Portugal"),
            new Country("376", "Andorra"),
            new Country("33", "Francia"),
            new Country("39", "Italia"),
            new Country("49", "Alemania"),
            new Country("44", "Reino Unido"),
            new Country("353", "Irlanda"),
            new Country("31", "Países Bajos"),
            new Country("32", "Bélgica"),
            new Country("41", "Suiza"),
            new Country("40", "Rumanía"),
            new Country("43", "Austria"),
            new Country("48", "Polonia"),
            new Country("46", "Suecia"),
            new Country("47", "Noruega"),
            new Country("45", "Dinamarca"),
            new Country("30", "Grecia"),
            new Country("240", "Guinea Ecuatorial"),
            new Country("212", "Marruecos"),
            new Country("52", "México"),
            new Country("57", "Colombia"),
            new Country("51", "Perú"),
            new Country("56", "Chile"),
            new Country("54", "Argentina"),
            new Country("58", "Venezuela"),
            new Country("593", "Ecuador"),
            new Country("591", "Bolivia"),
            new Country("595", "Paraguay"),
            new Country("598", "Uruguay"),
            new Country("53", "Cuba"),
            new Country("55", "Brasil"),
            new Country("502", "Guatemala"),
            new Country("503", "El Salvador"),
            new Country("504", "Honduras"),
            new Country("505", "Nicaragua"),
            new Country("506", "Costa Rica"),
            new Country("507", "Panamá"),
            new Country("1", "EE. UU., Canadá y Caribe"));

    /** Prefijos de país de la UIT. Ninguno empieza como otro, así que se pueden separar de un número sin ambigüedad. */
    public static final Set<String> CALLING_CODES = callingCodes();

    /** Países en los que el 0 inicial de uso nacional no se marca con el prefijo (+44 07700… → +44 7700…). */
    private static final Set<String> TRUNK_ZERO = Set.of("33", "44", "49", "31", "32", "41", "43", "353", "54", "40", "212");

    /** Agrupación de las cifras cuando el número tiene la longitud habitual del país. */
    private static final Map<String, int[]> GROUPS = Map.of(
            "34", new int[]{3, 3, 3},
            "351", new int[]{3, 3, 3},
            "33", new int[]{1, 2, 2, 2, 2},
            "1", new int[]{3, 3, 4},
            "56", new int[]{1, 4, 4},
            "44", new int[]{4, 6});

    /** Prefijo según la zona horaria del centro (para los teléfonos guardados sin prefijo). */
    private static final Map<String, String> BY_TIME_ZONE = Map.of(
            "Europe/Madrid", "34",
            "Atlantic/Canary", "34",
            "Europe/Lisbon", "351",
            "America/Mexico_City", "52",
            "America/Bogota", "57",
            "America/Lima", "51",
            "America/Santiago", "56",
            "America/Argentina/Buenos_Aires", "54");

    private static final int MAX_DIGITS = 15;

    private PhoneNumbers() {
    }

    public record Country(String code, String name) {

        /** En la lista desplegada: «+34 (España)». */
        public String label() {
            return "+" + code + " (" + name + ")";
        }

        /** Ya elegido: solo «+34» (phone-input.js cambia el texto al abrir y cerrar la lista). */
        public String shortLabel() {
            return "+" + code;
        }
    }

    /** Un teléfono guardado, separado en prefijo (sin «+») y resto, para volver a pintarlo en el formulario. */
    public record Parts(String country, String number) {
    }

    /** Separa «+34 612 345 678» en «34» y «612 345 678»; si no lleva prefijo, el de {@code defaultCountry} y el texto tal cual. */
    public static Parts split(String stored, String defaultCountry) {
        if (FormText.isBlank(stored)) {
            return new Parts(defaultCountry, "");
        }
        String text = stored.trim();
        if (text.startsWith("+")) {
            Optional<String> code = callingCodeOf(text.substring(1).replaceAll("\\D", ""));
            if (code.isPresent() && text.startsWith("+" + code.get())) {
                return new Parts(code.get(), text.substring(1 + code.get().length()).trim());
            }
        }
        return new Parts(defaultCountry, text);
    }

    /** Los países de la lista y, si {@code selected} es otro prefijo válido, también ese (para que salga elegido). */
    public static List<Country> countriesWith(String selected) {
        if (selected == null || !CALLING_CODES.contains(selected)
                || COUNTRIES.stream().anyMatch(c -> c.code().equals(selected))) {
            return COUNTRIES;
        }
        List<Country> countries = new ArrayList<>(COUNTRIES);
        countries.add(new Country(selected, "Otro país"));
        return countries;
    }

    /** País (código ISO de dos letras) → prefijo, para elegirlo solo según desde dónde se abre el formulario. */
    private static final Map<String, String> BY_ISO_COUNTRY = Map.ofEntries(
            Map.entry("ES", "34"), Map.entry("PT", "351"), Map.entry("AD", "376"), Map.entry("FR", "33"),
            Map.entry("IT", "39"), Map.entry("DE", "49"), Map.entry("GB", "44"), Map.entry("IE", "353"),
            Map.entry("NL", "31"), Map.entry("BE", "32"), Map.entry("CH", "41"), Map.entry("RO", "40"),
            Map.entry("MA", "212"), Map.entry("MX", "52"), Map.entry("CO", "57"), Map.entry("PE", "51"),
            Map.entry("CL", "56"), Map.entry("AR", "54"), Map.entry("VE", "58"), Map.entry("EC", "593"),
            Map.entry("BO", "591"), Map.entry("PY", "595"), Map.entry("UY", "598"), Map.entry("CU", "53"),
            Map.entry("US", "1"), Map.entry("CA", "1"), Map.entry("DO", "1"), Map.entry("PR", "1"),
            Map.entry("AT", "43"), Map.entry("PL", "48"), Map.entry("SE", "46"), Map.entry("NO", "47"),
            Map.entry("DK", "45"), Map.entry("GR", "30"), Map.entry("BR", "55"), Map.entry("GT", "502"),
            Map.entry("HN", "504"), Map.entry("SV", "503"), Map.entry("NI", "505"), Map.entry("CR", "506"),
            Map.entry("PA", "507"), Map.entry("GQ", "240"));

    /**
     * Prefijo que sale elegido al abrir un formulario: el del país desde el que se conecta la persona
     * ({@code CF-IPCountry}, que añade Cloudflare) o, si no se sabe, el del idioma del navegador (es-MX → México).
     * España si no hay ninguna pista o el país no se conoce.
     */
    public static String countryForRequest(String ipCountry, Iterable<java.util.Locale> languages) {
        if (ipCountry != null) {
            String code = BY_ISO_COUNTRY.get(ipCountry.trim().toUpperCase(java.util.Locale.ROOT));
            if (code != null) return code;
        }
        if (languages != null) {
            for (java.util.Locale language : languages) {
                String code = BY_ISO_COUNTRY.get(language.getCountry());
                if (code != null) return code;
            }
        }
        return DEFAULT_COUNTRY;
    }

    /** Igual que {@link #countryForRequest(String, Iterable)}, a partir de la petición. */
    public static String countryForRequest(jakarta.servlet.http.HttpServletRequest request) {
        return countryForRequest(request.getHeader("CF-IPCountry"),
                java.util.Collections.list(request.getLocales()));
    }

    /** Prefijo del país de una zona horaria de centro; España si no se conoce. */
    public static String countryForTimeZone(String timeZone) {
        return timeZone == null ? DEFAULT_COUNTRY : BY_TIME_ZONE.getOrDefault(timeZone, DEFAULT_COUNTRY);
    }

    /** Los prefijos separados por espacios, para el navegador. */
    public static String callingCodesText() {
        return String.join(" ", CALLING_CODES);
    }

    /**
     * Teléfono listo para guardar: «+34 612 345 678». {@code country} es el prefijo elegido en la lista (sin «+»);
     * no cuenta si el número ya empieza por «+» o «00», que traen su propio prefijo.
     *
     * @throws BusinessRuleException con el motivo, si el número no es válido
     */
    public static String normalize(String country, String raw) {
        if (FormText.isBlank(raw)) {
            throw new BusinessRuleException("Escribe el teléfono.");
        }
        String text = raw.trim();
        if (!text.matches("[+0-9 ()./-]+") || text.lastIndexOf('+') > 0) {
            throw new BusinessRuleException("El teléfono solo puede llevar números y espacios (y el prefijo con «+» delante).");
        }
        String digits = text.replaceAll("\\D", "");
        String code;
        if (text.startsWith("+") || text.startsWith("00")) {
            String international = text.startsWith("+") ? digits : digits.substring(2);
            code = callingCodeOf(international).orElseThrow(() -> new BusinessRuleException(
                    "No reconocemos el prefijo del país de ese teléfono. Escríbelo así: +34 612 345 678."));
            digits = international.substring(code.length());
        } else {
            code = FormText.isBlank(country) ? DEFAULT_COUNTRY : country.trim().replaceFirst("^\\+", "");
            if (!CALLING_CODES.contains(code)) {
                throw new BusinessRuleException("Elige el prefijo del país en la lista.");
            }
            // Escrito con el prefijo pero sin «+» (34612345678): se quita si lo que queda tiene la longitud del país
            int[] groups = GROUPS.get(code);
            if (groups != null && digits.startsWith(code) && digits.length() == code.length() + IntStream.of(groups).sum()) {
                digits = digits.substring(code.length());
            }
        }
        if (TRUNK_ZERO.contains(code) && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        String problem = problem(code, digits);
        if (problem != null) {
            throw new BusinessRuleException(problem);
        }
        return "+" + code + " " + group(code, digits);
    }

    /**
     * El número con el prefijo elegido delante («+34 612345678»), para pasarlo a {@link #normalize} sin la lista.
     * Si ya trae su prefijo o está vacío, se devuelve igual.
     */
    public static String withCountry(String country, String raw) {
        if (FormText.isBlank(raw) || FormText.isBlank(country)) return raw;
        String text = raw.trim();
        return text.startsWith("+") || text.startsWith("00") ? text : "+" + country.trim().replaceFirst("^\\+", "") + " " + text;
    }

    /** Como {@link #normalize}, pero vacío si el número no es válido (para datos ya guardados). */
    public static Optional<String> tryNormalize(String country, String raw) {
        try {
            return Optional.of(normalize(country, raw));
        } catch (BusinessRuleException e) {
            return Optional.empty();
        }
    }

    /** Prefijo con el que empiezan unas cifras internacionales (sin «+»). */
    static Optional<String> callingCodeOf(String digits) {
        for (int length = 1; length <= 3 && length <= digits.length(); length++) {
            String candidate = digits.substring(0, length);
            if (CALLING_CODES.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String problem(String code, String digits) {
        return switch (code) {
            case "34" -> digits.matches("[6789]\\d{8}") ? null
                    : "Un teléfono de España tiene 9 cifras y empieza por 6, 7, 8 o 9 (ej.: 612 345 678).";
            case "351" -> digits.matches("[29]\\d{8}") ? null
                    : "Un teléfono de Portugal tiene 9 cifras y empieza por 2 o 9 (ej.: 912 345 678).";
            case "33" -> digits.matches("[1-9]\\d{8}") ? null
                    : "Un teléfono de Francia tiene 9 cifras sin el 0 inicial (ej.: 6 12 34 56 78).";
            case "1" -> digits.matches("[2-9]\\d{9}") ? null
                    : "Un teléfono de EE. UU., Canadá o el Caribe tiene 10 cifras (ej.: 212 555 0123).";
            default -> {
                int max = MAX_DIGITS - code.length();
                yield digits.length() >= 4 && digits.length() <= max ? null
                        : "Revisa el teléfono: sin el prefijo tiene que tener entre 4 y " + max + " cifras.";
            }
        };
    }

    /** Cifras en grupos: los del país si tiene la longitud habitual; si no, de tres en tres sin dejar una suelta. */
    static String group(String code, String digits) {
        int[] sizes = GROUPS.get(code);
        if (sizes == null || IntStream.of(sizes).sum() != digits.length()) {
            sizes = defaultGroups(digits.length());
        }
        List<String> parts = new ArrayList<>();
        int at = 0;
        for (int size : sizes) {
            parts.add(digits.substring(at, at + size));
            at += size;
        }
        return String.join(" ", parts);
    }

    private static int[] defaultGroups(int n) {
        if (n <= 4) return new int[]{n};
        if (n == 5) return new int[]{2, 3};
        List<Integer> sizes = new ArrayList<>();
        int rest = n;
        int tail = switch (n % 3) {
            case 1 -> 4;      // 3 3 4
            case 2 -> 8;      // 3 4 4
            default -> 0;     // 3 3 3
        };
        while (rest > tail) {
            sizes.add(3);
            rest -= 3;
        }
        if (tail == 4) sizes.add(4);
        if (tail == 8) {
            sizes.add(4);
            sizes.add(4);
        }
        return sizes.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Set<String> callingCodes() {
        Stream<String> fixed = Stream.of(
                "1", "7",
                "20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44", "45", "46", "47", "48",
                "49", "51", "52", "53", "54", "55", "56", "57", "58", "60", "61", "62", "63", "64", "65", "66", "81",
                "82", "84", "86", "90", "91", "92", "93", "94", "95", "98",
                "211", "212", "213", "216", "218", "290", "291", "297", "298", "299", "420", "421", "423", "670",
                "850", "852", "853", "855", "856", "880", "886", "992", "993", "994", "995", "996", "998");
        Stream<String> ranges = Stream.of(
                        range(220, 258), range(260, 269), range(350, 359), range(370, 383), range(385, 387),
                        range(389, 389), range(500, 509), range(590, 599), range(672, 683), range(685, 692),
                        range(960, 968), range(970, 977))
                .flatMap(s -> s);
        return Collections.unmodifiableSet(Stream.concat(fixed, ranges).collect(Collectors.toCollection(
                () -> new TreeSet<>(Comparator.comparingInt(String::length).thenComparing(String::compareTo)))));
    }

    private static Stream<String> range(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(String::valueOf);
    }
}
