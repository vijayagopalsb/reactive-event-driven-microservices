 package com.reactiveevent.platform.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateHashes {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        String adminHash = encoder.encode("admin123");
        String userHash = encoder.encode("user123");

        System.out.println("Admin Hash: " + adminHash);
        System.out.println("User Hash:  " + userHash);
    }
}
