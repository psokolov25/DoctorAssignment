package com.qsystems.meddoctorassignment.websocket;

import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.config.WebsocketProperties;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class WebSocketService implements ApplicationEventListener<StartupEvent> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketService.class);

    private final String connectUrl;
    private final long reconnectDelayMs;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final StompSessionHandler sessionHandler;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean heartbeatScheduled = new AtomicBoolean(false);

    private volatile StompSession stompSession;
    private volatile WebSocketStompClient stompClient;

    public WebSocketService(OrchestraProperties orchestraProperties,
                            WebsocketProperties websocketProperties,
                            StompSessionHandler sessionHandler) {
        this.connectUrl = String.valueOf(UriBuilder.of(orchestraProperties.getUrl()).path("qpevents/events").build());
        this.reconnectDelayMs = websocketProperties.getDelayBeforeReconnectInMilliseconds();
        this.enabled = websocketProperties.isEnabled();
        this.username = orchestraProperties.getUsername();
        this.password = orchestraProperties.getPassword();
        this.sessionHandler = sessionHandler;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        if (!enabled) {
            log.info("Websocket integration disabled by configuration. Polling fallback remains active.");
            return;
        }
        connect();
    }

    private void connect() {
        try {
            WebSocketStompClient client = getOrCreateClient();
            WebSocketHttpHeaders webSocketHeaders = createWebSocketHeaders();
            StompHeaders stompHeaders = createStompHeaders();
            log.info("Connecting to Orchestra websocket {}", connectUrl);
            this.stompSession = client.connect(connectUrl, webSocketHeaders, stompHeaders, sessionHandler).get();
            reconnectScheduled.set(false);
            if (client.getTaskScheduler() != null && heartbeatScheduled.compareAndSet(false, true)) {
                client.getTaskScheduler().scheduleWithFixedDelay(createHeartbeatTask(), 30000L);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while connecting websocket", interruptedException);
            reconnect();
        } catch (ExecutionException executionException) {
            log.error("Cannot connect websocket: {}", executionException.getMessage(), executionException);
            reconnect();
        } catch (Throwable throwable) {
            log.error("Websocket transport unavailable. Service will continue with polling fallback. Cause: {}", throwable.getMessage(), throwable);
            reconnect();
        }
    }

    private synchronized WebSocketStompClient getOrCreateClient() {
        if (this.stompClient == null) {
            List<Transport> transports = new ArrayList<Transport>();
            transports.add(new RestTemplateXhrTransport());

            SockJsClient sockJsClient = new SockJsClient(transports);
            WebSocketStompClient client = new WebSocketStompClient(sockJsClient);
            client.setTaskScheduler(createHeartbeatScheduler());
            client.setDefaultHeartbeat(new long[]{30000L, 30000L});
            this.stompClient = client;
        }
        return this.stompClient;
    }

    private ThreadPoolTaskScheduler createHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }

    private WebSocketHttpHeaders createWebSocketHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (username != null && !username.trim().isEmpty()) {
            String rawValue = username + ":" + (password != null ? password : "");
            String encodedValue = Base64.getEncoder().encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
            headers.add("Authorization", "Basic " + encodedValue);
        }
        return headers;
    }

    private StompHeaders createStompHeaders() {
        StompHeaders headers = new StompHeaders();
        if (username != null && !username.trim().isEmpty()) {
            headers.setLogin(username);
            headers.setPasscode(password != null ? password : "");
            String rawValue = username + ":" + (password != null ? password : "");
            String encodedValue = Base64.getEncoder().encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
            headers.add("Authorization", "Basic " + encodedValue);
        }
        return headers;
    }

    private TimerTask createHeartbeatTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    if (stompSession == null || !stompSession.isConnected()) {
                        reconnect();
                        return;
                    }
                    stompSession.send("/", "ping".getBytes(StandardCharsets.UTF_8));
                } catch (Exception exception) {
                    log.error("Heartbeat failed: {}", exception.getMessage(), exception);
                    reconnect();
                }
            }
        };
    }

    private void reconnect() {
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        log.info("Schedule websocket reconnect in {} ms", reconnectDelayMs);
        reconnectScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                reconnectScheduled.set(false);
                connect();
            }
        }, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }
}
