package com.seatbook.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

@Repository
@RequiredArgsConstructor
public class HealthRepository {

    private final DataSource dataSource;

    public boolean isDatabaseUp() {

        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        }
        catch (Exception e) {
            return false;
        }
    }
}
