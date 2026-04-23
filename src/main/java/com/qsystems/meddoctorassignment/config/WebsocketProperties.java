package com.qsystems.meddoctorassignment.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/** Настройки event-driven интеграции с SockJS/STOMP шиной Orchestra. */
@ConfigurationProperties("application.websocket")
public class WebsocketProperties {

  private boolean enabled = true;
  private String topic = "/topic/event";
  private List<String> subscribedEvents = new ArrayList<String>();
  private long delayBeforeReconnectInMilliseconds = 10000L;
  private boolean sendCookiesInHandshake = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public List<String> getSubscribedEvents() {
    return subscribedEvents;
  }

  public void setSubscribedEvents(List<String> subscribedEvents) {
    this.subscribedEvents = subscribedEvents;
  }

  public long getDelayBeforeReconnectInMilliseconds() {
    return delayBeforeReconnectInMilliseconds;
  }

  public void setDelayBeforeReconnectInMilliseconds(long delayBeforeReconnectInMilliseconds) {
    this.delayBeforeReconnectInMilliseconds = delayBeforeReconnectInMilliseconds;
  }

  public boolean isSendCookiesInHandshake() {
    return sendCookiesInHandshake;
  }

  public void setSendCookiesInHandshake(boolean sendCookiesInHandshake) {
    this.sendCookiesInHandshake = sendCookiesInHandshake;
  }
}
