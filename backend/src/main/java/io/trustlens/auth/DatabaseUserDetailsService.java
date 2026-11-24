package io.trustlens.auth;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public DatabaseUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var rows = jdbc.query(
            "SELECT username, password_hash, enabled FROM app_users WHERE username = ?",
            (rs, rowNum) -> new Object[] { rs.getString("username"), rs.getString("password_hash"), rs.getBoolean("enabled") },
            username
        );
        if (rows.isEmpty()) throw new UsernameNotFoundException("Invalid username or password");
        Object[] row = rows.get(0);
        List<String> roles = jdbc.query(
            "SELECT r.code FROM app_roles r JOIN user_roles ur ON ur.role_id = r.id JOIN app_users u ON u.id = ur.user_id WHERE u.username = ?",
            (rs, rowNum) -> rs.getString(1), username
        );
        String[] authorities = roles.stream().map(r -> "ROLE_" + r).toArray(String[]::new);
        return User.withUsername((String) row[0])
            .password((String) row[1])
            .disabled(!(Boolean) row[2])
            .authorities(authorities)
            .build();
    }
}
