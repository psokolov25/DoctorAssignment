package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Реализация {@link com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway},
 * работающая через конфигурируемые REST endpoint-ы Orchestra.
 *
 * <p>Класс не делает предположений о приватных API Orchestra: все пути задаются в
 * {@code application.assignment.experimental-endpoints}. Это позволяет адаптировать сервис
 * под конкретную инсталляцию без переписывания доменной логики.</p>
 */
@Singleton
public class ConfigurableVisitWorkflowGateway implements VisitWorkflowGateway {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableVisitWorkflowGateway.class);

    private final HttpClient httpClient;
    private final AssignmentProperties assignmentProperties;
    private final OrchestraProperties orchestraProperties;

    public ConfigurableVisitWorkflowGateway(@Client("${application.orchestra.url}") HttpClient httpClient,
                                            AssignmentProperties assignmentProperties,
                                            OrchestraProperties orchestraProperties) {
        this.httpClient = httpClient;
        this.assignmentProperties = assignmentProperties;
        this.orchestraProperties = orchestraProperties;
    }

    @Override
    public List<VisitSummary> getWaitingVisits(int branchId, int queueId) {
        String path = assignmentProperties.getExperimentalEndpoints().getQueueVisitsPath();
        ensureConfigured(path, "queue-visits-path");

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("queueId", queueId);

        HttpRequest<Object> request = applyAuth(HttpRequest.GET(expand(path, variables)));
        return httpClient.toBlocking().retrieve(request, Argument.listOf(VisitSummary.class));
    }

    @Override
    public VisitDetails getVisitDetails(int branchId, long visitId) {
        String path = assignmentProperties.getExperimentalEndpoints().getVisitDetailsPath();
        ensureConfigured(path, "visit-details-path");

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("visitId", visitId);

        HttpRequest<Object> request = applyAuth(HttpRequest.GET(expand(path, variables)));
        return httpClient.toBlocking().retrieve(request, VisitDetails.class);
    }

    @Override
    public void assignServiceToVisit(int branchId, long visitId, int serviceId, int staffId, long servicePointId) {
        String path = assignmentProperties.getExperimentalEndpoints().getAssignServicePath();
        ensureConfigured(path, "assign-service-path");

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("visitId", visitId);
        variables.put("serviceId", serviceId);

        String expandedPath = expand(path, variables);
        log.info("Assign service {} to visit {} in branch {} using PUT {}", serviceId, visitId, branchId, expandedPath);

        // На части инсталляций Orchestra назначение услуги работает именно через PUT по URL ресурса,
        // а дополнительные поля staffId/servicePointId либо игнорируются, либо приводят к ошибкам.
        MutableHttpRequest<String> request = applyAuth(HttpRequest.PUT(expandedPath, ""))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE);

        try {
            httpClient.toBlocking().exchange(request, Object.class);
        } catch (HttpClientResponseException exception) {
            String responseBody = exception.getResponse().getBody(String.class).orElse("<empty>");
            log.error("Failed to assign service {} to visit {} in branch {}: status={} responseBody={}",
                    serviceId,
                    visitId,
                    branchId,
                    exception.getStatus(),
                    responseBody,
                    exception);
            throw exception;
        }
    }

    @Override
    public void transferVisitToQueue(int branchId, long visitId, int sourceQueueId, int targetQueueId) {
        String path = assignmentProperties.getExperimentalEndpoints().getTransferVisitPath();
        ensureConfigured(path, "transfer-visit-path");

        Integer sourceEntryPointId = assignmentProperties.resolveSourceEntryPointId(branchId);
        if (sourceEntryPointId == null) {
            throw new IllegalStateException("Missing source entry point ID for branch " + branchId
                    + ". Configure application.assignment.source-entry-point-id-by-branch." + branchId
                    + " or application.assignment.default-source-entry-point-id");
        }

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("queueId", targetQueueId);
        variables.put("targetQueueId", targetQueueId);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("fromBranchId", branchId);
        payload.put("fromId", sourceEntryPointId);
        payload.put("visitId", visitId);

        // В этой интеграции поле fromId трактуется как entry point id исходного потока,
        // а не как идентификатор очереди. Именно поэтому sourceEntryPoint задается отдельно в конфиге.
        String expandedPath = expand(path, variables);
        log.info("Transfer visit {} from queue {} to queue {} in branch {} using entryPointId={} and PUT {}",
                visitId,
                sourceQueueId,
                targetQueueId,
                branchId,
                sourceEntryPointId,
                expandedPath);

        MutableHttpRequest<Map<String, Object>> request = applyAuth(HttpRequest.PUT(expandedPath, payload))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE);

        try {
            httpClient.toBlocking().exchange(request, Object.class);
        } catch (HttpClientResponseException exception) {
            String responseBody = exception.getResponse().getBody(String.class).orElse("<empty>");
            log.error("Failed to transfer visit {} from queue {} to queue {} in branch {} using entryPointId={}: status={} responseBody={}",
                    visitId,
                    sourceQueueId,
                    targetQueueId,
                    branchId,
                    sourceEntryPointId,
                    exception.getStatus(),
                    responseBody,
                    exception);
            throw exception;
        }
    }

    @Override
    public Optional<VisitSummary> findVisit(int branchId, long visitId) {
        String path = assignmentProperties.getExperimentalEndpoints().getVisitByIdPath();
        if (path == null || path.trim().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("visitId", visitId);

        HttpRequest<Object> request = applyAuth(HttpRequest.GET(expand(path, variables)));
        VisitSummary summary = httpClient.toBlocking().retrieve(request, VisitSummary.class);
        return Optional.ofNullable(summary);
    }

    private void ensureConfigured(String path, String property) {
        if (!assignmentProperties.getExperimentalEndpoints().isEnabled()) {
            throw new UnsupportedOperationException("Visit workflow adapter is disabled. Enable application.assignment.experimental-endpoints.enabled and configure " + property);
        }
        if (path == null || path.trim().isEmpty()) {
            throw new UnsupportedOperationException("Missing experimental endpoint property: application.assignment.experimental-endpoints." + property);
        }
    }

    private String expand(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
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
