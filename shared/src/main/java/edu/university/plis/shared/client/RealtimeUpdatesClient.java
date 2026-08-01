package edu.university.plis.shared.client;

import edu.university.plis.shared.dto.ExamParticipantSnapshot;
import edu.university.plis.shared.dto.StudentActivitySnapshot;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class RealtimeUpdatesClient implements AutoCloseable {
    private final URI webSocketUri;
    private final String accessToken;
    private final WebSocketStompClient stompClient;
    private volatile StompSession session;

    public RealtimeUpdatesClient(URI webSocketUri, String accessToken) {
        this.webSocketUri = Objects.requireNonNull(webSocketUri);
        this.accessToken = Objects.requireNonNull(accessToken);
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.getObjectMapper().findAndRegisterModules();
        stompClient.setMessageConverter(converter);
    }

    public CompletableFuture<Void> connect() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        return stompClient.connectAsync(webSocketUri.toString(), new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() { })
                .thenAccept(connectedSession -> session = connectedSession);
    }

    public void subscribeToLab(long labSessionId, Consumer<StudentActivitySnapshot> listener) {
        requireSession().subscribe("/topic/labs/" + labSessionId, frameHandler(StudentActivitySnapshot.class, listener));
    }

    public void subscribeToExam(long examSessionId, Consumer<ExamParticipantSnapshot> listener) {
        requireSession().subscribe("/topic/exams/" + examSessionId, frameHandler(ExamParticipantSnapshot.class, listener));
    }

    private StompSession requireSession() {
        StompSession currentSession = session;
        if (currentSession == null || !currentSession.isConnected()) {
            throw new IllegalStateException("The real-time connection is not established");
        }
        return currentSession;
    }

    private <T> StompFrameHandler frameHandler(Class<T> payloadType, Consumer<T> listener) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                listener.accept(payloadType.cast(payload));
            }
        };
    }

    @Override
    public void close() {
        StompSession currentSession = session;
        if (currentSession != null && currentSession.isConnected()) {
            currentSession.disconnect();
        }
        stompClient.stop();
    }
}
