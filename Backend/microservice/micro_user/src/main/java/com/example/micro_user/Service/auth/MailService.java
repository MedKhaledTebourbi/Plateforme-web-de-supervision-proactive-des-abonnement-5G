package com.example.micro_user.Service.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String body) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        mailSender.send(message);
        System.out.println("EMAIL ENVOYE À : " + to);
    }
    public void sendRegistrationEmail(String toEmail, String username, String rawPassword) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Bienvenue - Votre compte a été créé");

        message.setText(
                "Bonjour " + username + ",\n\n" +
                        "Votre compte a été créé avec succès.\n\n" +
                        "Username : " + username + "\n" +
                        "Mot de passe : " + rawPassword + "\n\n" +
                        "Merci."
        );

        mailSender.send(message);
    }
}