package com.alexxlpz.crm_cbelleza.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * Dispositivos recordados («Recordar mi sesión») en la tabla persistent_logins (migración V4).
 * Hace lo mismo que {@code JdbcTokenRepositoryImpl} de Spring Security, que hereda de la clase obsoleta
 * {@code JdbcDaoSupport}; aquí se usa directamente un {@link JdbcTemplate}.
 */
public class JdbcRememberMeTokenRepository implements PersistentTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRememberMeTokenRepository.class);

    private static final String SELECT_BY_SERIES =
            "SELECT username, series, token, last_used FROM persistent_logins WHERE series = ?";
    private static final String INSERT =
            "INSERT INTO persistent_logins (username, series, token, last_used) VALUES (?, ?, ?, ?)";
    private static final String UPDATE =
            "UPDATE persistent_logins SET token = ?, last_used = ? WHERE series = ?";
    private static final String DELETE_BY_USERNAME =
            "DELETE FROM persistent_logins WHERE username = ?";

    private final JdbcTemplate jdbc;

    public JdbcRememberMeTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createNewToken(PersistentRememberMeToken token) {
        jdbc.update(INSERT, token.getUsername(), token.getSeries(), token.getTokenValue(), new Timestamp(token.getDate().getTime()));
    }

    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        jdbc.update(UPDATE, tokenValue, new Timestamp(lastUsed.getTime()), series);
    }

    /** El dispositivo recordado de esa serie, o {@code null} si no existe (la cookie deja de valer). */
    @Override
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        try {
            List<PersistentRememberMeToken> tokens = jdbc.query(SELECT_BY_SERIES, (rs, row) -> new PersistentRememberMeToken(
                    rs.getString("username"), rs.getString("series"), rs.getString("token"), rs.getTimestamp("last_used")), seriesId);
            if (tokens.size() > 1) {
                log.error("Hay más de un dispositivo recordado con la serie {}", seriesId);
                return null;
            }
            return tokens.isEmpty() ? null : tokens.getFirst();
        } catch (DataAccessException e) {
            log.error("No se ha podido leer el dispositivo recordado de la serie {}", seriesId, e);
            return null;
        }
    }

    @Override
    public void removeUserTokens(String username) {
        jdbc.update(DELETE_BY_USERNAME, username);
    }
}
