package com.example.micro_user.Repository;

import com.example.micro_user.Entity.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginHistoryRepository
        extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findByUsernameOrderByLoginTimeDesc(String username);
    @Query(value = """
SELECT DATE(login_time) as day, COUNT(*) as count
FROM login_history
GROUP BY DATE(login_time)
ORDER BY day
""", nativeQuery = true)
    List<Object[]> countConnectionsPerDay();

    boolean existsByUsernameAndLoginTimeBetween(String username, LocalDateTime startOfDay, LocalDateTime endOfDay);
    boolean existsByUsernameAndLoginTimeAfter(
            String username,
            LocalDateTime time
    );
}