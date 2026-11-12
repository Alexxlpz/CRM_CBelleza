package com.alexxlpz.productapi;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTests {

    @Test
    void downWhenTheDatabaseDoesNotAnswer() throws Exception {
        DataSource unreachable = mock(DataSource.class);
        when(unreachable.getConnection()).thenThrow(new SQLException("Connection refused"));
        var response = new HealthController(unreachable).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().get("status"));

        DataSource stuck = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(stuck.getConnection()).thenReturn(connection);
        when(connection.isValid(HealthController.TIMEOUT_SECONDS)).thenReturn(false);
        assertEquals(503, new HealthController(stuck).health().getStatusCode().value());
    }
}
