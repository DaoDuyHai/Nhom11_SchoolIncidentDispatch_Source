package com.incidentreport.service;

import com.incidentreport.common.model.User;

public class AuthService {
    public static User login(String username, String password) {
        return UserDAO.login(username, password);
    }
}