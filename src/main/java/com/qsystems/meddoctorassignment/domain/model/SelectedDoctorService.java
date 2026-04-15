package com.qsystems.meddoctorassignment.domain.model;

public class SelectedDoctorService {

    private final int serviceId;
    private final int targetQueueId;
    private final Integer routeOrder;
    private final String selectionReason;

    public SelectedDoctorService(int serviceId, int targetQueueId, Integer routeOrder, String selectionReason) {
        this.serviceId = serviceId;
        this.targetQueueId = targetQueueId;
        this.routeOrder = routeOrder;
        this.selectionReason = selectionReason;
    }

    public int getServiceId() {
        return serviceId;
    }

    public int getTargetQueueId() {
        return targetQueueId;
    }

    public Integer getRouteOrder() {
        return routeOrder;
    }

    public String getSelectionReason() {
        return selectionReason;
    }
}
