package com.qsystems.meddoctorassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VisitSummary {

    private long id;
    private Integer queueId;
    private String state;

    @JsonAlias({"ticketNumber", "ticketId"})
    private String ticketNumber;

    public VisitSummary() {
    }

    public VisitSummary(long id, Integer queueId, String state, String ticketNumber) {
        this.id = id;
        this.queueId = queueId;
        this.state = state;
        this.ticketNumber = ticketNumber;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    @JsonSetter("parameterMap")
    public void setParameterMap(Map<String, Object> parameterMap) {
        if (this.queueId != null || parameterMap == null) {
            return;
        }
        Integer resolvedQueueId = readInteger(parameterMap.get("currentQueueOrigId"));
        if (resolvedQueueId == null) {
            resolvedQueueId = readInteger(parameterMap.get("startQueueOrigId"));
        }
        if (resolvedQueueId != null) {
            this.queueId = resolvedQueueId;
        }
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
