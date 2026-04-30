package com.example.micro_user.Service.auth;

import com.example.micro_user.Entity.ResetCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetService {

    @Autowired
    private JavaMailSender mailSender;

    // stockage temporaire en mémoire
    private Map<String, ResetCode> resetCodes = new ConcurrentHashMap<>();

    // 🔥 Générer code à 6 chiffres
    public String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    // 🔥 Générer + envoyer code
    public void sendResetCode(String email) {

        String code = generateCode();

        // expiration 10 minutes
        LocalDateTime expiration = LocalDateTime.now().plusMinutes(10);

        resetCodes.put(email, new ResetCode(code, expiration));

        // Email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Code de réinitialisation mot de passe");
        message.setText(
                "Votre code de vérification est : " + code +
                        "\nCe code expire dans 10 minutes."
        );

        mailSender.send(message);
    }

    // 🔥 Vérifier code
    public boolean verifyCode(String email, String code) {

        ResetCode resetCode = resetCodes.get(email);

        if (resetCode == null) {
            return false;
        }

        if (resetCode.getExpirationTime().isBefore(LocalDateTime.now())) {
            resetCodes.remove(email);
            return false;
        }

        if (!resetCode.getCode().equals(code)) {
            return false;
        }

        // Supprimer après validation
        resetCodes.remove(email);
        return true;
    }
}
