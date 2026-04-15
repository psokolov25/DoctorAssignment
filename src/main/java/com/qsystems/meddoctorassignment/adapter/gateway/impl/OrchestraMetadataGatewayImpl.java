package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.OrchestraMetadataGateway;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.SmallBranch;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST-реализация {@link com.qsystems.meddoctorassignment.adapter.gateway.OrchestraMetadataGateway}.
 *
 * <p>Слой инкапсулирует конкретные подтвержденные endpoint-ы Orchestra и возвращает доменно
 * полезные коллекции, удобные для дальнейшего построения branch cache.</p>
 */
@Singleton
public class OrchestraMetadataGatewayImpl implements OrchestraMetadataGateway {

    private final HttpClient httpClient;
    private final OrchestraProperties orchestraProperties;

    public OrchestraMetadataGatewayImpl(@Client("${application.orchestra.url}") HttpClient httpClient,
                                        OrchestraProperties orchestraProperties) {
        this.httpClient = httpClient;
        this.orchestraProperties = orchestraProperties;
    }

    @Override
    public List<SmallBranch> getAllBranches() {
        return getList(orchestraProperties.getConfigurationRestPath() + "/branches", SmallBranch.class);
    }

    @Override
    public List<ServiceData> getServicesFromBranch(int branchId) {
        return getList(orchestraProperties.getCommonRestPath() + "/servicepoint/branches/" + branchId + "/services", ServiceData.class);
    }

    @Override
    public TinyQueue getQueueForServiceInBranch(int branchId, int serviceId) {
        return getObject(orchestraProperties.getCommonRestPath() + "/entrypoint/branches/" + branchId + "/services/" + serviceId + "/queue", TinyQueue.class);
    }

    @Override
    public List<ServicePointData> getServicePointsFromBranch(int branchId) {
        return getList(orchestraProperties.getCommonRestPath() + "/managementinformation/v2/branches/" + branchId + "/servicePoints", ServicePointData.class);
    }

    @Override
    public List<WorkProfileData> getWorkProfilesFromBranch(int branchId) {
        return getList(orchestraProperties.getCommonRestPath() + "/servicepoint/branches/" + branchId + "/workProfiles", WorkProfileData.class);
    }

    @Override
    public List<TinyQueue> getQueuesForWorkProfileInBranch(int branchId, int workProfileId) {
        return getList(orchestraProperties.getCommonRestPath() + "/servicepoint/branches/" + branchId + "/workProfiles/" + workProfileId + "/queues", TinyQueue.class);
    }

    @Override
    public List<TinyQueue> getAllQueuesInBranch(int branchId) {
        return getList(orchestraProperties.getCommonRestPath() + "/servicepoint/branches/" + branchId + "/queues/", TinyQueue.class);
    }

    private <T> List<T> getList(String path, Class<T> bodyType) {
        List<T> data = httpClient.toBlocking().retrieve(applyAuth(HttpRequest.GET(path)), Argument.listOf(bodyType));
        if (data == null) {
            return Collections.emptyList();
        }
        return new ArrayList<T>(data);
    }

    private <T> T getObject(String path, Class<T> bodyType) {
        return httpClient.toBlocking().retrieve(applyAuth(HttpRequest.GET(path)), bodyType);
    }

    private <T> MutableHttpRequest<T> applyAuth(MutableHttpRequest<T> request) {
        String username = orchestraProperties.getUsername();
        String password = orchestraProperties.getPassword();
        if (username != null && !username.trim().isEmpty()) {
            request.basicAuth(username, password != null ? password : "");
        }
        return request;
    }
}
