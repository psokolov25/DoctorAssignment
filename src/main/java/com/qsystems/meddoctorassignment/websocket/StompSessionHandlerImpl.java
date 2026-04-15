package com.qsystems.meddoctorassignment.websocket;

import com.qsystems.meddoctorassignment.config.WebsocketProperties;
import io.micronaut.context.annotation.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;

import java.lang.reflect.Type;

/**
 * STOMP session handler, подписывающийся на нужные события Orchestra и передающий payload дальше.
 */
@Context
public class StompSessionHandlerImpl implements StompSessionHandler {

    private static final Logger log = LoggerFactory.getLogger(StompSessionHandlerImpl.class);

    private final WebsocketProperties websocketProperties;
    private final WebsocketFrameHandler websocketFrameHandler;

    public StompSessionHandlerImpl(WebsocketProperties websocketProperties,
                                   WebsocketFrameHandler websocketFrameHandler) {
        this.websocketProperties = websocketProperties;
        this.websocketFrameHandler = websocketFrameHandler;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        for (String eventName : websocketProperties.getSubscribedEvents()) {
            // Подписка строится шаблонно: базовый topic + имя события + wildcard по unit.
            String destination = websocketProperties.getTopic() + "/" + eventName + "/*";
            session.subscribe(destination, this);
            log.info("Subscribed to {}", destination);
        }
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        log.error("STOMP exception: {}", exception.getMessage(), exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        log.error("STOMP transport error: {}", exception.getMessage(), exception);
        if (exception instanceof ConnectionLostException) {
            throw (ConnectionLostException) exception;
        }
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        try {
            websocketFrameHandler.handleFrame(new String((byte[]) payload));
        } catch (Exception exception) {
            log.error("Cannot handle websocket payload: {}", exception.getMessage(), exception);
        }
    }
}
