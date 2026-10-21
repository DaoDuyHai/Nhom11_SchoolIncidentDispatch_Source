package com.incidentreport.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quản lý kết nối cơ sở dữ liệu SQLite đa luồng an toàn (WAL Mode)
 */
public class DatabaseConnection {

    public static File getDatabaseFile() {
        File dbFile = new File("database/incident_report.db");
        if (dbFile.exists()) return dbFile;
        dbFile = new File("../database/incident_report.db");
        if (dbFile.exists()) return dbFile;
        dbFile = new File("../../database/incident_report.db");
        if (dbFile.exists()) return dbFile;
        return new File("database/incident_report.db");
    }

    public static Connection getConnection() {
        File dbFile = getDatabaseFile();
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        String dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        config.setBusyTimeout(30000); // 30s timeout
        config.enforceForeignKeys(true);
        config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);

        int retries = 15;
        while (retries > 0) {
            try {
                return DriverManager.getConnection(dbUrl, config.toProperties());
            } catch (SQLException e) {
                if (e.getMessage() != null && (e.getMessage().contains("BUSY") || e.getMessage().contains("locked"))) {
                    retries--;
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {}
                } else {
                    System.err.println("Lỗi kết nối Database: " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }
}
