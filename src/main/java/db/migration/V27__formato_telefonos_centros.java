package db.migration;

import com.alexxlpz.crm_cbelleza.forms.PhoneNumbers;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Teléfonos de los centros ya guardados con el formato del alta: prefijo aparte y el resto en grupos
 * («912345678» → «+34 912 345 678»). Si no llevan prefijo, se pone el del país de la zona horaria del centro.
 * Los que no se entienden como teléfono se dejan como están (el dueño los corregirá en «Mi Centro»).
 * En Java y no en SQL porque las reglas son las de {@link PhoneNumbers}, igual en H2 y en PostgreSQL.
 */
@SuppressWarnings("java:S101") // Flyway exige este nombre de clase
public class V27__formato_telefonos_centros extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Map<Long, String> changes = new LinkedHashMap<>();
        try (Statement select = context.getConnection().createStatement();
             ResultSet rows = select.executeQuery("SELECT id, phone, time_zone FROM centers WHERE phone IS NOT NULL")) {
            while (rows.next()) {
                String phone = rows.getString("phone");
                String country = PhoneNumbers.countryForTimeZone(rows.getString("time_zone"));
                long id = rows.getLong("id");
                PhoneNumbers.tryNormalize(country, phone)
                        .filter(normalized -> !normalized.equals(phone))
                        .ifPresent(normalized -> changes.put(id, normalized));
            }
        }
        try (PreparedStatement update = context.getConnection().prepareStatement("UPDATE centers SET phone = ? WHERE id = ?")) {
            for (Map.Entry<Long, String> change : changes.entrySet()) {
                update.setString(1, change.getValue());
                update.setLong(2, change.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
