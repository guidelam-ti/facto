package com.guidelam.facto.migration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One-shot migrator H2 -> PostgreSQL. Activé via profil "migrate-h2"
 * (en plus du profil "postgres" qui fournit la DataSource cible).
 *
 * Exécution :
 *   SPRING_PROFILES_ACTIVE=postgres,migrate-h2 java -jar facto-*.jar
 *
 * Override de la source H2 (par défaut ~/.facto/db/facto) :
 *   -Dfacto.migrate.h2.url=jdbc:h2:file:C:/facto/db/facto;...
 */
@Component
@Profile("migrate-h2")
@Slf4j
public class H2ToPostgresMigrator implements ApplicationRunner {

    private static final List<String> TABLE_ORDER = List.of(
            "supplier",
            "app_setting",
            "supplier_mapping",
            "processed_invoice",
            "processing_job"
    );

    private static final List<String> IDENTITY_TABLES = List.of(
            "supplier",
            "supplier_mapping",
            "processed_invoice",
            "processing_job"
    );

    private final DataSource pgDataSource;
    private final ConfigurableApplicationContext context;

    @Value("${facto.migrate.h2.url:jdbc:h2:file:${user.home}/.facto/db/facto;ACCESS_MODE_DATA=r;IFEXISTS=TRUE;AUTO_SERVER=FALSE}")
    private String h2Url;

    @Value("${facto.migrate.h2.username:sa}")
    private String h2Username;

    @Value("${facto.migrate.h2.password:}")
    private String h2Password;

    public H2ToPostgresMigrator(DataSource pgDataSource, ConfigurableApplicationContext context) {
        this.pgDataSource = pgDataSource;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Migration H2 -> PostgreSQL ===");
        log.info("Source H2 : {}", h2Url);

        int exitCode = 0;
        try (HikariDataSource h2Ds = buildH2DataSource()) {
            int total = 0;
            for (String table : TABLE_ORDER) {
                int n = copyTable(h2Ds, pgDataSource, table);
                total += n;
                log.info("Migration table {} : {} lignes copiees", table, n);
            }
            log.info("--- Total : {} lignes copiees ---", total);

            for (String table : IDENTITY_TABLES) {
                resetSequence(pgDataSource, table, "id");
            }
            log.info("Sequences PG remises a jour pour {}", IDENTITY_TABLES);
            log.info("=== Migration terminee avec succes ===");
        } catch (Exception e) {
            log.error("Migration ECHOUEE : {}", e.getMessage(), e);
            exitCode = 1;
        }

        final int code = exitCode;
        SpringApplication.exit(context, () -> code);
    }

    private HikariDataSource buildH2DataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(h2Url);
        cfg.setUsername(h2Username);
        cfg.setPassword(h2Password);
        cfg.setDriverClassName("org.h2.Driver");
        cfg.setMaximumPoolSize(2);
        cfg.setReadOnly(true);
        return new HikariDataSource(cfg);
    }

    private int copyTable(DataSource source, DataSource target, String table) throws SQLException {
        try (Connection srcConn = source.getConnection();
             Connection tgtConn = target.getConnection()) {

            try (Statement chk = tgtConn.createStatement();
                 ResultSet rsChk = chk.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rsChk.next();
                int existing = rsChk.getInt(1);
                if (existing > 0) {
                    log.warn("Table PG '{}' contient deja {} lignes, suppression avant import.", table, existing);
                    try (Statement del = tgtConn.createStatement()) {
                        del.executeUpdate("DELETE FROM " + table);
                    }
                }
            }

            try (Statement srcStmt = srcConn.createStatement();
                 ResultSet rs = srcStmt.executeQuery("SELECT * FROM " + table)) {

                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                List<String> cols = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    cols.add(md.getColumnName(i).toLowerCase());
                }
                String colList = String.join(", ", cols);
                String paramList = String.join(", ", Collections.nCopies(colCount, "?"));
                String sql = "INSERT INTO " + table + " (" + colList + ") VALUES (" + paramList + ")";

                int count = 0;
                try (PreparedStatement ps = tgtConn.prepareStatement(sql)) {
                    while (rs.next()) {
                        for (int i = 1; i <= colCount; i++) {
                            ps.setObject(i, rs.getObject(i));
                        }
                        ps.addBatch();
                        count++;
                        if (count % 100 == 0) {
                            ps.executeBatch();
                        }
                    }
                    ps.executeBatch();
                }
                return count;
            }
        }
    }

    private void resetSequence(DataSource ds, String table, String idCol) throws SQLException {
        String sql = "SELECT setval(pg_get_serial_sequence(?, ?), "
                + "COALESCE((SELECT MAX(" + idCol + ") FROM " + table + "), 1))";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, idCol);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                log.info("Sequence {} -> setval = {}", table, rs.getLong(1));
            }
        }
    }
}
