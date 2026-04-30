package com.example.micro_user.Service.auth;



import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PasswordResetTokenService {
    private final Map<String, String> tokens = new HashMap<>();

    public String generateToken(String email) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, email);
        return token;
    }

    public String getEmailByToken(String token) {
        return tokens.get(token);
    }

    public void removeToken(String token) {
        tokens.remove(token);
    }
}
