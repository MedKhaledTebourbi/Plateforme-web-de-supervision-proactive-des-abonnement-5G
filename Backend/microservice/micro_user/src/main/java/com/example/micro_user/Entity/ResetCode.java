package com.example.micro_user.Entity;

import java.time.LocalDateTime;

public class ResetCode {

    private String code;
    private LocalDateTime expirationTime;

    public ResetCode(String code, LocalDateTime expirationTime) {
        this.code = code;
        this.expirationTime = expirationTime;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpirationTime() {
        return expirationTime;
    }
}
