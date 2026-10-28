package com.incidentreport.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quản lý kết nối cơ sở dữ liệu SQLite đa luồng an toàn (WAL Mode)
 */
public class DatabaseConnection {

    private static String customDbUrl = null;

    public static void setCustomDbUrl(String url) {
        customDbUrl = url;
    }

    public static String getCustomDbUrl() {
        return customDbUrl;
    }

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
        String dbUrl;
        if (customDbUrl != null && !customDbUrl.trim().isEmpty()) {
            if (customDbUrl.startsWith("jdbc:sqlite:")) {
                String rawPath = customDbUrl.substring("jdbc:sqlite:".length()).trim();
                File f = new File(rawPath);
                if (!f.isAbsolute()) {
                    File resolved = InitDatabase.findFile(rawPath);
                    if (resolved.getParentFile() != null && !resolved.getParentFile().exists()) {
                        resolved.getParentFile().mkdirs();
                    }
                    dbUrl = "jdbc:sqlite:" + resolved.getAbsolutePath();
                } else {
                    dbUrl = customDbUrl;
                }
            } else {
                dbUrl = customDbUrl;
            }
        } else {
            String sysProp = System.getProperty("sqlite.db.url");
            if (sysProp != null && !sysProp.trim().isEmpty()) {
                dbUrl = sysProp.trim();
            } else {
                File dbFile = getDatabaseFile();
                if (!dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            }
        }

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
