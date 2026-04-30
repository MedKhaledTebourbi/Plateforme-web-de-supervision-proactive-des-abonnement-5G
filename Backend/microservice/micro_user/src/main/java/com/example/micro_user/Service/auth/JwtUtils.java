package com.example.micro_user.Service.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;

@Component
public class JwtUtils {
    // Définir une clé secrète statique et unique
    private static final String SECRET_KEY = "votre_clé_secrète_uniquevzrbgrbaefbaetntenadsvjsdkvblrBKVSPVOUBZDVP OUBFVPOUV SBDVBSDVÖSDVBSDVUOBSDVBF IUFVBJEFVI"; // Utilisez une chaîne secrète robuste
    private static final Key KEY = new SecretKeySpec(SECRET_KEY.getBytes(), SignatureAlgorithm.HS256.getJcaName());

    private static final long EXPIRATION_TIME = 86400000; // 24 heures (en millisecondes)

    // Génération du token
    public static String generateToken(String username) {


        return Jwts.builder()
                .setSubject(username)
                // Ajoute l'username dans le JWT
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))  // Expiration dans 24 heures
                .signWith(KEY)  // Signature avec la clé secrète
                .compact();  // Génère le token compact
    }

    // Extraction de l'username à partir du token
    public static String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)  // Utilisation de la même clé pour vérifier la signature
                .build()
                .parseClaimsJws(token)  // Parse le token
                .getBody()
                .getSubject();  // Récupère l'username
    }

    // Validation du token
    public static boolean validateToken(String jwtToken, UserDetails userDetails) {
        String username = extractUsername(jwtToken);  // Récupère l'username du token
        return username.equals(userDetails.getUsername());  // Compare avec l'username du UserDetails
    }


}
