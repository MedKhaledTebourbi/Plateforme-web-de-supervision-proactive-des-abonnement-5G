package com.example.micro_user.Configuration;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditorProvider")
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {

        var authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || authentication.getPrincipal().equals("anonymousUser")) {

            return Optional.of("SYSTEM");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return Optional.of(userDetails.getUsername());
        }

        return Optional.of(authentication.getName());
    }
}
