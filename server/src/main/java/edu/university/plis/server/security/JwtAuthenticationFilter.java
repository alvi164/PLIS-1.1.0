package edu.university.plis.server.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import edu.university.plis.server.service.ConnectionApprovalService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthTokenService tokenService;
    private final DatabaseUserDetailsService userDetailsService;
    private final ConnectionApprovalService connectionApprovalService;

    public JwtAuthenticationFilter(
            AuthTokenService tokenService,
            DatabaseUserDetailsService userDetailsService,
            ConnectionApprovalService connectionApprovalService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
        this.connectionApprovalService = connectionApprovalService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authorization.substring(7));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            String username = tokenService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            boolean student = userDetails.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_STUDENT"));
            boolean approvedDevice = !student || connectionApprovalService.isApproved(
                    userDetails.getUsername(), tokenService.extractDeviceId(token));
            if (userDetails.isEnabled() && approvedDevice
                    && tokenService.isValid(token, userDetails.getUsername())) {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                userDetails, null, userDetails.getAuthorities()));
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // Invalid bearer tokens deliberately fall through to Spring Security's 401 response.
        }
    }
}
