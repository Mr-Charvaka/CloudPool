package com.cloudpool.config;

import com.cloudpool.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

@Slf4j
public class TenantAwareDataSourceWrapper implements DataSource {

    private final DataSource delegate;

    public TenantAwareDataSourceWrapper(DataSource delegate) {
        this.delegate = delegate;
        log.info("TenantAwareDataSourceWrapper initialized. RLS session variables will be set on every connection.");
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = delegate.getConnection();
        setTenantContext(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = delegate.getConnection(username, password);
        setTenantContext(conn);
        return conn;
    }

    private void setTenantContext(Connection conn) {
        String tenantId = TenantContextHolder.getTenantId();
        String userId = TenantContextHolder.getUserId();

        try (Statement stmt = conn.createStatement()) {
            String tid = (tenantId != null) ? tenantId.replace("'", "''") : "";
            String uid = (userId != null) ? userId.replace("'", "''") : "";
            stmt.execute("SELECT set_config('app.tenant_id', '" + tid + "', true)");
            stmt.execute("SELECT set_config('app.user_id', '" + uid + "', true)");
        } catch (SQLException e) {
            log.warn("Failed to set tenant context on connection: {}. " +
                     "If not using PostgreSQL RLS, this is expected.", e.getMessage());
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return true;
        }
        return delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
    }
}
