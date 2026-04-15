package com.qsystems.meddoctorassignment.adapter.orchestra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceData {

    private int id;
    private String internalName;
    private String externalName;
    private String internalDescription;
    private String externalDescription;
    private int targetTransactionTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public String getExternalName() {
        return externalName;
    }

    public void setExternalName(String externalName) {
        this.externalName = externalName;
    }

    public String getInternalDescription() {
        return internalDescription;
    }

    public void setInternalDescription(String internalDescription) {
        this.internalDescription = internalDescription;
    }

    public String getExternalDescription() {
        return externalDescription;
    }

    public void setExternalDescription(String externalDescription) {
        this.externalDescription = externalDescription;
    }

    public int getTargetTransactionTime() {
        return targetTransactionTime;
    }

    public void setTargetTransactionTime(int targetTransactionTime) {
        this.targetTransactionTime = targetTransactionTime;
    }
}
