package com.qsystems.meddoctorassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VisitUnservedService {

    @JsonAlias({"serviceId", "serviceOrigId"})
    private Integer serviceId;

    @JsonAlias({"externalKey", "serviceInternalName", "serviceExternalName"})
    private String externalKey;

    @JsonAlias({"routeOrder", "order"})
    private Integer routeOrder;

    public VisitUnservedService() {
    }

    public VisitUnservedService(Integer serviceId, String externalKey, Integer routeOrder) {
        this.serviceId = serviceId;
        this.externalKey = externalKey;
        this.routeOrder = routeOrder;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public Integer getRouteOrder() {
        return routeOrder;
    }

    public void setRouteOrder(Integer routeOrder) {
        this.routeOrder = routeOrder;
    }
}
