package edu.university.plis.server.config;

import edu.university.plis.server.security.AuthTokenService;
import edu.university.plis.server.security.DatabaseUserDetailsService;
import edu.university.plis.server.repository.AppUserRepository;
import edu.university.plis.server.repository.ExamSessionRepository;
import edu.university.plis.server.repository.LabSessionRepository;
import edu.university.plis.shared.model.RoleName;
import io.jsonwebtoken.JwtException;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Pattern LAB_TOPIC = Pattern.compile("^/topic/labs/(\\d+)$");
    private static final Pattern EXAM_TOPIC = Pattern.compile("^/topic/exams/(\\d+)$");
    private final AuthTokenService tokenService;
    private final DatabaseUserDetailsService userDetailsService;
    private final AppUserRepository userRepository;
    private final LabSessionRepository labSessionRepository;
    private final ExamSessionRepository examSessionRepository;

    public WebSocketConfig(
            AuthTokenService tokenService,
            DatabaseUserDetailsService userDetailsService,
            AppUserRepository userRepository,
            LabSessionRepository labSessionRepository,
            ExamSessionRepository examSessionRepository) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.labSessionRepository = labSessionRepository;
        this.examSessionRepository = examSessionRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor.getNativeHeader("Authorization")));
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    requireDashboardAccess(accessor.getUser(), accessor.getDestination());
                }
                return message;
            }
        });
    }

    private Authentication authenticate(List<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()
                || !authorizationHeaders.get(0).startsWith("Bearer ")) {
            throw new AccessDeniedException("A bearer token is required for WebSocket connections");
        }
        String token = authorizationHeaders.get(0).substring(7);
        try {
            UserDetails user = userDetailsService.loadUserByUsername(tokenService.extractUsername(token));
            if (!tokenService.isValid(token, user.getUsername())) {
                throw new AccessDeniedException("The WebSocket token is invalid");
            }
            return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("The WebSocket token is invalid", exception);
        }
    }

    private void requireDashboardAccess(java.security.Principal principal, String destination) {
        if (!(principal instanceof Authentication authentication)) {
            throw new AccessDeniedException("The WebSocket subscription is not authenticated");
        }
        var actor = userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("The WebSocket user no longer exists"));
        if (actor.getRole() != RoleName.ROLE_TEACHER && actor.getRole() != RoleName.ROLE_ADMIN) {
            throw new AccessDeniedException("Only teachers and administrators can subscribe to dashboards");
        }
        if (actor.getRole() == RoleName.ROLE_ADMIN) {
            return;
        }
        Matcher lab = LAB_TOPIC.matcher(destination == null ? "" : destination);
        if (lab.matches()) {
            boolean ownsLab = labSessionRepository.findById(Long.parseLong(lab.group(1)))
                    .map(session -> session.getTeacher().getUser().getUsername()
                            .equalsIgnoreCase(authentication.getName()))
                    .orElse(false);
            if (ownsLab) {
                return;
            }
        }
        Matcher exam = EXAM_TOPIC.matcher(destination == null ? "" : destination);
        if (exam.matches()) {
            boolean ownsExam = examSessionRepository.findById(Long.parseLong(exam.group(1)))
                    .map(session -> session.getCreatedBy().getUser().getUsername()
                            .equalsIgnoreCase(authentication.getName()))
                    .orElse(false);
            if (ownsExam) {
                return;
            }
        }
        throw new AccessDeniedException("Teachers can subscribe only to their own session dashboards");
    }
}
