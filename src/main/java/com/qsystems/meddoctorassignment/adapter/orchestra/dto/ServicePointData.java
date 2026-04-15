package com.qsystems.meddoctorassignment.adapter.orchestra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServicePointData {

    private int branchId;
    private int workProfileId;
    private String workProfileName;
    private int staffId;
    private String staffName;
    private String status;
    private String name;
    private long id;

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
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

    public int getStaffId() {
        return staffId;
    }

    public void setStaffId(int staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
