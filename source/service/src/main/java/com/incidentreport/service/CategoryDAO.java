package com.incidentreport.service;

import com.incidentreport.common.model.IncidentCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryDAO {
    private static final Map<String, IncidentCategory> cache = new HashMap<>();

    public static synchronized List<IncidentCategory> getAllCategories() {
        if (!cache.isEmpty()) {
            return new ArrayList<>(cache.values());
        }
        refreshCache();
        return new ArrayList<>(cache.values());
    }

    public static synchronized IncidentCategory getCategory(String categoryCode) {
        if (cache.isEmpty()) {
            refreshCache();
        }
        return cache.get(categoryCode);
    }

    public static synchronized void refreshCache() {
        cache.clear();
        String sql = "SELECT * FROM incident_categories";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                IncidentCategory c = new IncidentCategory(
                    rs.getString("category_code"),
                    rs.getString("category_name"),
                    rs.getString("required_specialty"),
                    rs.getInt("default_severity"),
                    rs.getInt("dedup_window_seconds")
                );
                cache.put(c.getCategoryCode(), c);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi CategoryDAO.refreshCache: " + e.getMessage());
        }
    }
}

