package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.SmallBranch;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData;

import java.util.List;

public interface OrchestraMetadataGateway {

    List<SmallBranch> getAllBranches();

    List<ServiceData> getServicesFromBranch(int branchId);

    TinyQueue getQueueForServiceInBranch(int branchId, int serviceId);

    List<ServicePointData> getServicePointsFromBranch(int branchId);

    List<WorkProfileData> getWorkProfilesFromBranch(int branchId);

    List<TinyQueue> getQueuesForWorkProfileInBranch(int branchId, int workProfileId);

    List<TinyQueue> getAllQueuesInBranch(int branchId);
}
