package com.alexxlpz.crm_cbelleza.dto;

/**
 * Orden y filtros de la pantalla «Todas las opiniones» del centro. Cualquier valor que no se reconozca
 * se queda en lo de por defecto: las más recientes, sin filtrar.
 */
public record ReviewFilter(Order order, Integer stars, boolean withComment, Long treatmentId) {

    public static final ReviewFilter ALL = new ReviewFilter(Order.RECENT, null, false, null);

    public enum Order {
        RECENT("recientes", "Más recientes"),
        BEST("mejores", "Mejor valoradas"),
        WORST("peores", "Peor valoradas");

        private final String param;
        private final String label;

        Order(String param, String label) {
            this.param = param;
            this.label = label;
        }

        public String getParam() {
            return param;
        }

        public String getLabel() {
            return label;
        }

        static Order fromParam(String value) {
            for (Order order : values()) {
                if (order.param.equalsIgnoreCase(value)) {
                    return order;
                }
            }
            return RECENT;
        }
    }

    /** A partir de los parámetros de la URL ({@code orden}, {@code estrellas}, {@code comentario}, {@code tratamiento}). */
    public static ReviewFilter of(String order, Integer stars, boolean withComment, Long treatmentId) {
        return new ReviewFilter(Order.fromParam(order), stars != null && stars >= 1 && stars <= 5 ? stars : null,
                withComment, treatmentId);
    }

    /** Si hay algún filtro puesto (el orden no cuenta). */
    public boolean isFiltered() {
        return stars != null || withComment || treatmentId != null;
    }
}
