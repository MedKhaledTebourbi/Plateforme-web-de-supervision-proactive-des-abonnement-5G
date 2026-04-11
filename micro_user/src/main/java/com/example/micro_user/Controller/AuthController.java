package com.example.micro_user.Controller;

import com.example.micro_user.Entity.LoginHistory;
import com.example.micro_user.Entity.User;
import com.example.micro_user.Entity.UserDTO;
import com.example.micro_user.Repository.LoginHistoryRepository;
import com.example.micro_user.Repository.UserRepository;
import com.example.micro_user.Service.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final PasswordResetService passwordResetService;
    @Autowired
    private CustomUserDetailsService userService;


    private AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final LoginHistoryRepository repository;

    private final PasswordResetTokenService tokenService;
    private final MailService mailService;


    @Autowired
    public AuthController(PasswordEncoder passwordEncoder, UserRepository userRepository,
                          AuthenticationManager authenticationManager, CustomUserDetailsService userService , PasswordResetTokenService tokenService, MailService mailService, PasswordResetService passwordResetService, LoginHistoryRepository repository) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.passwordResetService = passwordResetService;

        this.repository = repository;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Username already exists");
        }

        String rawPassword = user.getPassword(); // sauvegarder mot de passe original

        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);


        // envoyer email
        mailService.sendRegistrationEmail(
                user.getEmail(),
                user.getUsername(),
                rawPassword
        );
        System.out.println("AUTH USER = " +
                SecurityContextHolder.getContext().getAuthentication());


        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<UserDTO> authenticate(@RequestBody User user,
                                                HttpServletRequest request) {
        System.out.println("Tentative de connexion pour l'utilisateur : " + user.getUsername());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
        } catch (Exception e) {
            System.out.println("Échec de l'authentification : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
// 🔴 AJOUT ICI : récupérer user depuis la base
        User userFromDb = userRepository.findByUsername(user.getUsername());

        if (!userFromDb.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        userService.saveLogin(user.getUsername(), request);

        String token = JwtUtils.generateToken(user.getUsername());

        // ➕ Injecter dans le contexte Spring Security
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                token, // credentials
                null
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Récupérer l'ID de l'utilisateur depuis la base de données ou le service utilisateur
        Long userId = userService.getUserIdByUsername(user.getUsername());
        String role = userService.getUserRoleByUsername(user.getUsername());
        String region = userService.getUserRegionByUsername(user.getUsername());

        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(user.getUsername());
        userDTO.setToken(token);
        userDTO.setId(userId);
        userDTO.setRole(role);
        userDTO.setRegion(region);


        return ResponseEntity.ok(userDTO);
    }



    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        UserDTO dto = new UserDTO();
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setId(user.getId());
        dto.setRole(user.getRole());
        dto.setRegion(user.getRegion());
        dto.setToken(token);

        return ResponseEntity.ok(dto);
    }




    @GetMapping("/test")
    public String test() {
        return "message from backend successfully";
    }
    // 📌 Endpoint pour demander la réinitialisation du mot de passe
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        if (username == null || username.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username requis"));
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Utilisateur non trouvé"));
        }

        // Générer token JWT pour reset
        String token = JwtUtils.generateToken(user.getEmail());

        // Optionnel : afficher dans console
        System.out.println("TOKEN GENERE : " + token);

        // Retourner le token directement au frontend
        return ResponseEntity.ok(Map.of(
                "username", username,
                "token", token
        ));
    }


    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestParam String token,
            @RequestBody User user) {

        String email = JwtUtils.extractUsername(token);
        if (email == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Token invalide ou expiré"));
        }

        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        if (existingUserOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Utilisateur non trouvé"));
        }

        User existingUser = existingUserOpt.get();
        existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(existingUser);

        return ResponseEntity.ok(
                Map.of("message", "Mot de passe réinitialisé avec succès !")
        );
    }

    @PostMapping("/verify-code")
    public ResponseEntity<Map<String, String>> verifyCode(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        String code = request.get("code");

        Optional<User> userOpt = Optional.ofNullable(userRepository.findByUsername(username));
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Utilisateur non trouvé"));
        }

        String email = userOpt.get().getEmail();
        boolean isValid = passwordResetService.verifyCode(email, code);

        if (!isValid) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Code invalide ou expiré"));
        }

        return ResponseEntity.ok(Map.of("message", "Code valide"));
    }


    @PostMapping("/send-reset-code")
    public ResponseEntity<String> sendResetCode(@RequestParam String username) {

        Optional<User> userOpt = Optional.ofNullable(userRepository.findByUsername(username));

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Utilisateur non trouvé");
        }

        String email = userOpt.get().getEmail();

        passwordResetService.sendResetCode(email);

        return ResponseEntity.ok("Code envoyé avec succès");
    }



    @GetMapping("/get-email")
    public ResponseEntity<Map<String, String>> getEmailByUsername(@RequestParam String username) {
        Optional<User> user = Optional.ofNullable(userRepository.findByUsername(username));
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Utilisateur introuvable"));
        }
        return ResponseEntity.ok(Map.of("email", user.get().getEmail()));
    }
    @PreAuthorize("hasRole('ADMIN')")

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {

        var users = userRepository.findAll()
                .stream()
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setEmail(user.getEmail());
                    dto.setRole(user.getRole());
                    return dto;
                })
                .toList();

        return ResponseEntity.ok(users);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/usersd/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Utilisateur non trouvé");
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok("Utilisateur supprimé avec succès");
    }

    @PatchMapping("/toggle-active/{username}")
    public ResponseEntity<String> toggleUserActive(@PathVariable String username) {
        Optional<User> userOpt = Optional.ofNullable(userRepository.findByUsername(username));
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur introuvable");
        }

        User user = userOpt.get();
        user.setActive(!user.isActive()); // inverse l’état
        userRepository.save(user);

        String status = user.isActive() ? "activé" : "désactivé";
        return ResponseEntity.ok("Compte " + status + " avec succès !");
    }
    @Autowired
    private SessionManager sessionManager;

    @GetMapping("/user-status/{username}")
    public ResponseEntity<?> getUserStatus(@PathVariable String username) {

        boolean online = sessionManager.isUserOnline(username);

        return ResponseEntity.ok(
                Map.of(
                        "username", username,
                        "status", online ? "ONLINE" : "OFFLINE"
                )
        );
    }

    @GetMapping("/last-activity/{username}")
    public ResponseEntity<?> getLastActivity(@PathVariable String username) {

        Long lastActivity = sessionManager.getLastActivity(username);

        if (lastActivity == null) {
            return ResponseEntity.ok(Map.of("status", "OFFLINE"));
        }

        return ResponseEntity.ok(
                Map.of(
                        "username", username,
                        "lastActivity", lastActivity
                )
        );
    }
    @GetMapping("/all-status")
    public ResponseEntity<?> getAllStatus() {
        return ResponseEntity.ok(sessionManager.getAllActiveUsers());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {

        Pageable pageable = PageRequest.of(page, size);

        Page<User> userPage = userRepository.searchUsers(role, active, pageable);

        var result = userPage.getContent().stream().map(user -> {
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setRole(user.getRole());
            dto.setActive(user.isActive());
            return dto;
        }).toList();

        return ResponseEntity.ok(
                Map.of(
                        "content", result,
                        "totalElements", userPage.getTotalElements(),
                        "totalPages", userPage.getTotalPages(),
                        "currentPage", userPage.getNumber()
                )
        );
    }
    @GetMapping("/usersa/{username}")
    public User getUserByUsername(@PathVariable String username) {
        return userService.getByUsername(username);
    }
    @GetMapping
    public List<User> getAll() {
        return userService.getAllUsers();
    }

    @GetMapping("/{username}")
    public User getUser(@PathVariable String username) {
        return userService.getByUsername(username);
    }

    @PutMapping("/toggle/{username}")
    public void toggle(@PathVariable String username) {
        userService.toggleUser(username);
    }

    @GetMapping("/history/{username}")
    public List<LoginHistory> history(@PathVariable String username) {
        return  userService.getUserHistory(username);
    }
    @GetMapping("/stats/connections")
    public List<Map<String, Object>> connectionsPerDay() {
        return userService.getConnectionsPerDay();
    }
    @GetMapping("/stats/online")
    public Map<String, Long> onlineStats() {
        return userService.getOnlineStats();
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {

        String username = body.get("username");

        sessionManager.setUserOnline(username, false);

        return ResponseEntity.ok().build();
    }
    @GetMapping("/profile/{username}")
    public ResponseEntity<?> getProfile(@PathVariable String username) {

        User user = userRepository.findByUsername(username);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Utilisateur introuvable");
        }

        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());

        return ResponseEntity.ok(dto);
    }
    @PutMapping("/update-profile/{username}")
    public ResponseEntity<?> updateProfile(
            @PathVariable String username,
            @RequestBody UserDTO dto) {

        User user = userRepository.findByUsername(username);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Utilisateur introuvable");
        }

        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }

        if (dto.getUsername() != null) {
            user.setUsername(dto.getUsername());
        }

        userRepository.save(user);

        return ResponseEntity.ok("Profil mis à jour avec succès");
    }
    @GetMapping("/techniciens")
    public List<User> getTechniciensByRegion(@RequestParam String region) {
        return userService.getTechniciensByRegion(region);
    }
}