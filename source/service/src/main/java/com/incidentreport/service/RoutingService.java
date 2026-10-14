package com.incidentreport.service;

import com.incidentreport.common.model.IncidentCategory;

public class RoutingService {
    public static void init() {
        CategoryDAO.refreshCache();
    }

    public static IncidentCategory getRoute(String categoryCode) {
        return CategoryDAO.getCategory(categoryCode);
    }
}