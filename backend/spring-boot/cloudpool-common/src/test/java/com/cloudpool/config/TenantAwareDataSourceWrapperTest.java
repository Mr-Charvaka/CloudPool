package com.cloudpool.config;

import com.cloudpool.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAwareDataSourceWrapperTest {

    @Mock private DataSource delegate;
    @Mock private Connection connection;
    @Mock private Statement statement;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should execute SET LOCAL for both tenant_id and user_id when context is set")
    void testGetConnectionSetsTenantContext() throws SQLException {
        TenantContextHolder.setTenantId("tenant-abc");
        TenantContextHolder.setUserId("user-xyz");
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        wrapper.getConnection();

        verify(statement).execute("SELECT set_config('app.tenant_id', 'tenant-abc', true)");
        verify(statement).execute("SELECT set_config('app.user_id', 'user-xyz', true)");
    }

    @Test
    @DisplayName("Should execute SET LOCAL with empty strings when context is null")
    void testGetConnectionWithNullContext() throws SQLException {
        TenantContextHolder.clear();
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        wrapper.getConnection();

        verify(statement).execute("SELECT set_config('app.tenant_id', '', true)");
        verify(statement).execute("SELECT set_config('app.user_id', '', true)");
    }

    @Test
    @DisplayName("Should set only tenant_id when userId is null")
    void testGetConnectionWithOnlyTenantId() throws SQLException {
        TenantContextHolder.setTenantId("tenant-123");
        TenantContextHolder.setUserId(null);
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        wrapper.getConnection();

        verify(statement).execute("SELECT set_config('app.tenant_id', 'tenant-123', true)");
        verify(statement).execute("SELECT set_config('app.user_id', '', true)");
    }

    @Test
    @DisplayName("Should set only user_id when tenantId is null")
    void testGetConnectionWithOnlyUserId() throws SQLException {
        TenantContextHolder.setTenantId(null);
        TenantContextHolder.setUserId("user-789");
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        wrapper.getConnection();

        verify(statement).execute("SELECT set_config('app.tenant_id', '', true)");
        verify(statement).execute("SELECT set_config('app.user_id', 'user-789', true)");
    }

    @Test
    @DisplayName("Should escape single quotes in tenant_id and user_id")
    void testGetConnectionEscapesQuotes() throws SQLException {
        TenantContextHolder.setTenantId("tenant'o'brien");
        TenantContextHolder.setUserId("user'name");
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        wrapper.getConnection();

        verify(statement).execute("SELECT set_config('app.tenant_id', 'tenant''o''brien', true)");
        verify(statement).execute("SELECT set_config('app.user_id', 'user''name', true)");
    }

    @Test
    @DisplayName("Should use overloaded getConnection with username and password")
    void testGetConnectionWithUsernamePassword() throws SQLException {
        TenantContextHolder.setTenantId("tenant-abc");
        TenantContextHolder.setUserId("user-xyz");
        when(delegate.getConnection("dbuser", "dbpass")).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        wrapper.getConnection("dbuser", "dbpass");

        verify(statement).execute("SELECT set_config('app.tenant_id', 'tenant-abc', true)");
        verify(statement).execute("SELECT set_config('app.user_id', 'user-xyz', true)");
    }

    @Test
    @DisplayName("Should not throw when SQLException occurs during SET LOCAL")
    void testGetConnectionSqlExceptionIsLogged() throws SQLException {
        TenantContextHolder.setTenantId("tenant-abc");
        TenantContextHolder.setUserId("user-xyz");
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenThrow(new SQLException("Not a PostgreSQL database"));

        TenantAwareDataSourceWrapper wrapper = new TenantAwareDataSourceWrapper(delegate);
        Connection result = wrapper.getConnection();

        assertNotNull(result);
        verify(delegate).getConnection();
    }
}