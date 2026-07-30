package com.ksh.features.discovery.ingestion;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class NewsIngestionLease {

    private static final String LOCK_NAME = "korea-discovery-ingestion";
    private final DataSource dataSource;

    public NewsIngestionLease(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Lease tryAcquire(Duration duration) {
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, LOCK_NAME);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getInt(1) == 1) {
                        return new Lease(connection, true);
                    }
                }
            }
            connection.close();
            return new Lease(null, false);
        } catch (SQLException exception) {
            throw new IllegalStateException("Không thể lấy khóa cào tin", exception);
        }
    }

    public void release(Lease lease) {
        if (lease == null || !lease.acquired()) {
            return;
        }
        try {
            try (PreparedStatement statement = lease.connection().prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, LOCK_NAME);
                statement.execute();
            } finally {
                lease.connection().close();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Không thể nhả khóa cào tin", exception);
        }
    }

    public record Lease(Connection connection, boolean acquired) {
    }
}
