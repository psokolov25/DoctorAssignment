package com.qsystems.meddoctorassignment.cache.model;

import java.time.Instant;

/**
 * Runtime-снимок состояния точки обслуживания.
 *
 * <p>Используется как локальный источник правды во время event-driven обработки и polling fallback.
 */
public class ServicePointRuntimeState {

  private long servicePointId;
  private int branchId;
  private int staffId;
  private int workProfileId;
  private String status;
  private Instant updatedAt = Instant.now();

  public ServicePointRuntimeState() {}

  public ServicePointRuntimeState(
      long servicePointId, int branchId, int staffId, int workProfileId, String status) {
    this.servicePointId = servicePointId;
    this.branchId = branchId;
    this.staffId = staffId;
    this.workProfileId = workProfileId;
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public long getServicePointId() {
    return servicePointId;
  }

  public void setServicePointId(long servicePointId) {
    this.servicePointId = servicePointId;
  }

  public int getBranchId() {
    return branchId;
  }

  public void setBranchId(int branchId) {
    this.branchId = branchId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }
}
