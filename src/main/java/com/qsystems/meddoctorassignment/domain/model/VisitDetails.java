package com.qsystems.meddoctorassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Детализированное представление визита для анализа его текущего маршрута.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisitDetails {

    private long id;
    private Integer queueId;

    @JsonAlias({"unservedServices", "unservedVisitServices"})
    private List<VisitUnservedService> unservedServices = new ArrayList<VisitUnservedService>();

    public VisitDetails() {
    }

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

    public List<VisitUnservedService> getUnservedServices() {
        return unservedServices;
    }

    public void setUnservedServices(List<VisitUnservedService> unservedServices) {
        this.unservedServices = unservedServices;
    }

    /**
     * Аналогично {@link VisitSummary#setParameterMap(Map)} достраивает queue id из parameterMap,
     * если Orchestra не вернула его отдельным полем верхнего уровня.
     */
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
