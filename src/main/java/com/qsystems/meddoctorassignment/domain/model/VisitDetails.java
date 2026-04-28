package com.qsystems.meddoctorassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Детализированное представление визита для анализа его текущего маршрута. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisitDetails {

  private long id;
  private Integer queueId;
  private Integer currentServiceId;
  private Long currentVisitServiceRecordId;

  @JsonAlias({"ticketNumber", "ticketId"})
  private String ticketNumber;

  @JsonAlias({"unservedServices", "unservedVisitServices"})
  private List<VisitUnservedService> unservedServices = new ArrayList<VisitUnservedService>();

  public VisitDetails() {}

  public VisitDetails(long id, Integer queueId, List<VisitUnservedService> unservedServices) {
    this.id = id;
    this.queueId = queueId;
    this.unservedServices = unservedServices;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public Integer getQueueId() {
    return queueId;
  }

  public void setQueueId(Integer queueId) {
    this.queueId = queueId;
  }

  public Integer getCurrentServiceId() {
    return currentServiceId;
  }

  public void setCurrentServiceId(Integer currentServiceId) {
    this.currentServiceId = currentServiceId;
  }

  public Long getCurrentVisitServiceRecordId() {
    return currentVisitServiceRecordId;
  }

  public void setCurrentVisitServiceRecordId(Long currentVisitServiceRecordId) {
    this.currentVisitServiceRecordId = currentVisitServiceRecordId;
  }

  public String getTicketNumber() {
    return ticketNumber;
  }

  public void setTicketNumber(String ticketNumber) {
    this.ticketNumber = ticketNumber;
  }

  public List<VisitUnservedService> getUnservedServices() {
    return unservedServices;
  }

  public void setUnservedServices(List<VisitUnservedService> unservedServices) {
    this.unservedServices = unservedServices;
  }

  /**
   * Аналогично {@link VisitSummary#setParameterMap(Map)} достраивает queue id из parameterMap, если
   * Orchestra не вернула его отдельным полем верхнего уровня.
   */
  @JsonSetter("parameterMap")
  public void setParameterMap(Map<String, Object> parameterMap) {
    if (this.queueId == null && parameterMap != null) {
      Integer resolvedQueueId = readInteger(parameterMap.get("currentQueueOrigId"));
      if (resolvedQueueId == null) {
        resolvedQueueId = readInteger(parameterMap.get("startQueueOrigId"));
      }
      if (resolvedQueueId != null) {
        this.queueId = resolvedQueueId;
      }
    }
  }

  /**
   * На живых ответах Orchestra текущая назначенная услуга часто приходит как вложенный объект
   * currentVisitService.serviceId. Поле нужно для ветки transfer-only, когда услуга уже назначена.
   */
  @JsonSetter("currentVisitService")
  public void setCurrentVisitService(Map<String, Object> currentVisitService) {
    if (currentVisitService == null) {
      return;
    }
    Long resolvedRecordId = readLong(currentVisitService.get("id"));
    if (resolvedRecordId != null) {
      this.currentVisitServiceRecordId = resolvedRecordId;
    }
    Integer resolvedServiceId = readInteger(currentVisitService.get("serviceId"));
    if (resolvedServiceId == null) {
      resolvedServiceId = readInteger(currentVisitService.get("id"));
    }
    if (resolvedServiceId != null) {
      this.currentServiceId = resolvedServiceId;
    }
  }

  private Long readLong(Object value) {
    if (value instanceof Number) {
      return Long.valueOf(((Number) value).longValue());
    }
    if (value instanceof String) {
      try {
        return Long.valueOf((String) value);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private Integer readInteger(Object value) {
    if (value instanceof Number) {
      return Integer.valueOf(((Number) value).intValue());
    }
    if (value instanceof String) {
      try {
        return Integer.valueOf((String) value);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
