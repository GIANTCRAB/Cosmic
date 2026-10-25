package testutil;

import server.ItemInformationProvider;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class ItemInformationProviderMocks {
    private ItemInformationProviderMocks() {
    }

    /** Initialize the eager singleton without requiring the monster-card database. */
    public static ItemInformationProvider mockProvider() {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet cards = mock(ResultSet.class);
        try (var database = mockStatic(DatabaseConnection.class)) {
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(cards);
            database.when(DatabaseConnection::getConnection).thenReturn(connection);
            return mock(ItemInformationProvider.class);
        } catch (SQLException e) {
            throw new AssertionError("Could not prepare the item-provider fixture", e);
        }
    }
}
