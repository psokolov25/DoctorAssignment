package com.qsystems.meddoctorassignment.adapter.orchestra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TinyQueue {

    private int id;
    private String name;
    private String queueType;
    private int customersWaiting;
    private int waitingTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQueueType() {
        return queueType;
    }

    public void setQueueType(String queueType) {
        this.queueType = queueType;
    }

    public int getCustomersWaiting() {
        return customersWaiting;
    }

    public void setCustomersWaiting(int customersWaiting) {
        this.customersWaiting = customersWaiting;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public void setWaitingTime(int waitingTime) {
        this.waitingTime = waitingTime;
    }
}
