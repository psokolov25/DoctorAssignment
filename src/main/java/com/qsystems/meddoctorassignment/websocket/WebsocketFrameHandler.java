package com.qsystems.meddoctorassignment.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsystems.meddoctorassignment.event.DoctorAssignmentEventHandler;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import io.micronaut.context.annotation.Context;

/** Разбирает сырой websocket frame и передает его в доменный обработчик событий. */
@Context
public class WebsocketFrameHandler {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DoctorAssignmentEventHandler eventHandler;

  public WebsocketFrameHandler(DoctorAssignmentEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  public void handleFrame(String payload) throws Exception {
    OrchestraEvent orchestraEvent = objectMapper.readValue(payload, OrchestraEvent.class);
    eventHandler.handle(orchestraEvent);
  }
}
