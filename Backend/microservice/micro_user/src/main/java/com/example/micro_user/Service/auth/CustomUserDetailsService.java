package com.example.micro_user.Service.auth;

import com.example.micro_user.Entity.LoginHistory;
import com.example.micro_user.Entity.User;
import com.example.micro_user.Repository.LoginHistoryRepository;
import com.example.micro_user.Repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final LoginHistoryRepository repository;
    private final SessionManager sessionManager;



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        System.out.println("Utilisateur connecté : ID = " + user.getId());
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())




                .build();
    }
    public Long getUserIdByUsername(String username) {
        User user = userRepository.findByUsername(username);  // Récupérer l'utilisateur depuis la base de données
        if (user != null) {
            return user.getId();  // Retourner l'ID de l'utilisateur
        }
        return null;
    }
    public String getUserRoleByUsername(String username) {
        User user = userRepository.findByUsername(username);  // Récupérer l'utilisateur depuis la base de données
        if (user != null) {
            return user.getRole();  // Retourner l'ID de l'utilisateur
        }
        return null;
    }
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getByUsername(String username) {
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByUsername(username));
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        return userOptional.get();
    }
    public void toggleUser(String username) {

        User user = getByUsername(username);

        user.setActive(!user.isActive());  // ✅ isActive()

        if (!user.isActive()) {
            String admin = SecurityContextHolder.getContext()
                    .getAuthentication().getName();
            user.setDisabledBy(admin);
        }

        userRepository.save(user);
    }


    public void saveLogin(String username, HttpServletRequest request) {

        // 🔥 Si déjà en ligne → ne pas recréer connexion
        if (sessionManager.isUserOnline(username)) {
            return;
        }

        LoginHistory history = new LoginHistory();
        history.setUsername(username);
        history.setLoginTime(LocalDateTime.now());
        history.setIpAddress(request.getRemoteAddr());
        history.setUserAgent(request.getHeader("User-Agent"));

        repository.save(history);

        sessionManager.setUserOnline(username, true);
    }

    public List<LoginHistory> getUserHistory(String username) {
        return repository.findByUsernameOrderByLoginTimeDesc(username);
    }
    public List<Map<String, Object>> getConnectionsPerDay() {

        List<Object[]> results = repository.countConnectionsPerDay();

        return results.stream().map(obj -> {
            Map<String, Object> map = new HashMap<>();
            map.put("day", obj[0].toString());
            map.put("count", obj[1]);
            return map;
        }).toList();
    }
    public Map<String, Long> getOnlineStats() {
        // Récupérer tous les utilisateurs
        List<User> allUsers = userRepository.findAll();

        // Compter ceux qui sont en ligne
        long online = allUsers.stream()
                .filter(u -> sessionManager.isUserOnline(u.getUsername()))
                .count();

        long total = allUsers.size();
        long offline = total - online;

        Map<String, Long> map = new HashMap<>();
        map.put("online", online);
        map.put("offline", offline);

        return map;
    }
    public List<User> getTechniciensByRegion(String region) {
        return userRepository.findByRoleAndRegion("TECHNICIEN", region);
    }

    public String getUserRegionByUsername(String username) {
        User user = userRepository.findByUsername(username);  // Récupérer l'utilisateur depuis la base de données
        if (user != null) {
            return user.getRegion();  // Retourner l'ID de l'utilisateur
        }
        return null;
}}
