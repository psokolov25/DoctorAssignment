package com.qsystems.meddoctorassignment.model.event;

import java.util.LinkedHashMap;
import java.util.Map;

public class DoctorContext {

    private int branchId;
    private long servicePointId;
    private int staffId;
    private int workProfileId;
    private String workProfileName;
    private String servicePointName;
    private String userName;
    private TriggerSource triggerSource;
    private final Map<String, String> fieldSources = new LinkedHashMap<String, String>();

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public long getServicePointId() {
        return servicePointId;
    }

    public void setServicePointId(long servicePointId) {
        this.servicePointId = servicePointId;
    }

    public int getStaffId() {
        return staffId;
    }

    public void setStaffId(int staffId) {
        this.staffId = staffId;
    }

    public int getWorkProfileId() {
        return workProfileId;
    }

    public void setWorkProfileId(int workProfileId) {
        this.workProfileId = workProfileId;
    }

    public String getWorkProfileName() {
        return workProfileName;
    }

    public void setWorkProfileName(String workProfileName) {
        this.workProfileName = workProfileName;
    }

    public String getServicePointName() {
        return servicePointName;
    }

    public void setServicePointName(String servicePointName) {
        this.servicePointName = servicePointName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(TriggerSource triggerSource) {
        this.triggerSource = triggerSource;
    }

    public Map<String, String> getFieldSources() {
        return fieldSources;
    }

    public void recordSource(String field, String source) {
        fieldSources.put(field, source);
    }

    public String describeSources() {
        return fieldSources.toString();
    }
}
