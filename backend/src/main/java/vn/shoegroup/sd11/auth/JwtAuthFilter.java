package vn.shoegroup.sd11.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    public JwtAuthFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims c = jwtService.parse(header.substring(7).trim());
                long id = Long.parseLong(c.getSubject());
                String role = c.get("role", String.class);
                int roleId = c.get("roleId", Integer.class) == null ? 2 : c.get("roleId", Integer.class);
                String email = c.get("email", String.class);
                String name = c.get("name", String.class);
                JwtPrincipal principal = new JwtPrincipal(id, email, role, roleId, name);
                String authority = "ROLE_" + (role == null ? "CUSTOMER" : role.toUpperCase().replace(' ', '_'));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(authority))));
            } catch (Exception ignored) {
                // Invalid/expired JWT remains anonymous; Spring Security returns 401 for protected routes.
            }
        }
        chain.doFilter(request, response);
    }
}
