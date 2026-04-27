package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.domain.exception.MutationContextException;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Реализация {@link com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway},
 * работающая через конфигурируемые REST endpoint-ы Orchestra.
 *
 * <p>Класс не делает предположений о приватных API Orchestra: все пути задаются в
 * {@code application.assignment.experimental-endpoints}. Это позволяет адаптировать сервис
 * под конкретную инсталляцию без переписывания доменной логики.
 *
 * <p>Дополнительно класс инкапсулирует несколько подтвержденных практикой интеграционных
 * нюансов: transfer-ответ Orchestra может быть штатным {@code 204 No Content}; поле
 * {@code fromId} для transfer остается отдельным конфигурируемым entry point id; а ответ
 * assign оценивается не только по {@code userState}, но и по фактическому состоянию
 * {@code currentVisitService} в body ответа.</p>
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

    private static String toLoggableBody(byte[] responseBodyBytes) {
        if (responseBodyBytes == null || responseBodyBytes.length == 0) {
            return "<empty>";
        }
        String responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
        return responseBody.trim().isEmpty() ? "<empty>" : responseBody;
    }

    static Integer extractCurrentVisitServiceId(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }
        String normalized = responseBody.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        String objectMarker = "\"currentVisitService\":{";
        int objectIndex = normalized.indexOf(objectMarker);
        if (objectIndex < 0) {
            return null;
        }
        int objectStart = objectIndex + objectMarker.length();
        int objectEnd = normalized.indexOf('}', objectStart);
        if (objectEnd <= objectStart) {
            return null;
        }
        String currentVisitServiceJson = normalized.substring(objectStart, objectEnd);
        Integer resolvedServiceId = extractIntegerField(currentVisitServiceJson, "serviceId");
        if (resolvedServiceId != null) {
            return resolvedServiceId;
        }
        return extractIntegerField(currentVisitServiceJson, "id");
    }

    static boolean isAssignEffectivelyApplied(Integer currentVisitServiceId, int requestedServiceId) {
        return currentVisitServiceId != null && currentVisitServiceId.intValue() == requestedServiceId;
    }

    static String extractUserState(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }
        String normalized = responseBody.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        String marker = "\"userState\":\"";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int valueStart = markerIndex + marker.length();
        int valueEnd = normalized.indexOf('\"', valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }
        return normalized.substring(valueStart, valueEnd);
    }

    private static Integer extractIntegerField(String jsonFragment, String fieldName) {
        if (jsonFragment == null || jsonFragment.isEmpty()) {
            return null;
        }
        String marker = "\"" + fieldName + "\":";
        int markerIndex = jsonFragment.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int valueStart = markerIndex + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < jsonFragment.length()) {
            char symbol = jsonFragment.charAt(valueEnd);
            if (symbol == ',' || symbol == '}') {
                break;
            }
            valueEnd++;
        }
        if (valueEnd <= valueStart) {
            return null;
        }
        String rawValue = jsonFragment.substring(valueStart, valueEnd).replace("\"", "");
        try {
            return Integer.valueOf(rawValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    /**
     * Добавляет услугу в маршрут визита через подтвержденный REST endpoint Orchestra.
     *
     * <p>Этот шаг нужен для режима med-robot text/plain: робот выбирает услугу по номеру
     * талона и может вернуть serviceId, которого не было в локально прочитанном
     * {@code unservedVisitServices}. В этом случае сначала расширяем маршрут визита,
     * а уже затем выполняем штатный assign/transfer workflow.</p>
     */
    @Override
    public void addServiceToVisit(int branchId, long visitId, int serviceId) {
        String path = assignmentProperties.getExperimentalEndpoints().getAddServicePath();
        if (path == null || path.trim().isEmpty()) {
            path = assignmentProperties.getExperimentalEndpoints().getAssignServicePath();
        }
        ensureConfigured(path, "add-service-path");

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("visitId", visitId);
        variables.put("serviceId", serviceId);
        variables.put("serviceOrigId", serviceId);

        String expandedPath = expand(path, variables);
        log.info("Add service to visit request branchId={} visitId={} serviceId={} path={} payload=<empty>",
                branchId,
                visitId,
                serviceId,
                expandedPath);

        MutableHttpRequest<String> request = applyAuth(HttpRequest.POST(expandedPath, ""))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE);

        try {
            HttpResponse<byte[]> response = httpClient.toBlocking().exchange(request, byte[].class);
            String responseBody = toLoggableBody(response.getBody(byte[].class).orElse(null));
            log.info("Add service to visit response branchId={} visitId={} serviceId={} status={} responseBody={}",
                    branchId,
                    visitId,
                    serviceId,
                    response.getStatus().getCode(),
                    responseBody);
        } catch (HttpClientResponseException exception) {
            String responseBody = exception.getResponse().getBody(String.class).orElse("<empty>");
            log.error("Failed to add service {} to visit {} in branch {}: status={} responseBody={}",
                    serviceId,
                    visitId,
                    branchId,
                    exception.getStatus(),
                    responseBody,
                    exception);
            throw exception;
        }
    }

    /**
     * Выполняет assign-service через конфигурируемый endpoint и интерпретирует ответ Orchestra
     * в терминах доменного workflow, а не только HTTP-статуса.
     *
     * <p>Если Orchestra вернула неидеальный {@code userState}, но в body уже видно, что
     * {@code currentVisitService.serviceId} совпал с запрошенной услугой, assign считается
     * эффективно примененным и цикл может продолжить transfer.</p>
     */
    @Override
    public void assignServiceToVisit(int branchId, long visitId, int serviceId, int staffId, long servicePointId) {
        String path = assignmentProperties.getExperimentalEndpoints().getAssignServicePath();
        ensureConfigured(path, "assign-service-path");

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", branchId);
        variables.put("visitId", visitId);
        variables.put("serviceId", serviceId);
        variables.put("serviceOrigId", serviceId);

        String expandedPath = expand(path, variables);
        log.info("Assign service request branchId={} visitId={} serviceId={} staffId={} servicePointId={} path={} payload=<empty>",
                branchId,
                visitId,
                serviceId,
                staffId,
                servicePointId,
                expandedPath);
        log.warn("Experimental assign-service adapter does not include staffId/servicePointId in payload yet. Context is logged for incident analysis.");

        // На части инсталляций Orchestra назначение услуги работает именно через PUT по URL ресурса,
        // а дополнительные поля staffId/servicePointId либо игнорируются, либо приводят к ошибкам.
        MutableHttpRequest<String> request = applyAuth(HttpRequest.PUT(expandedPath, ""))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE);

        try {
            String responseBody = httpClient.toBlocking().retrieve(request, String.class);
            log.info("Assign service response branchId={} visitId={} serviceId={} status=200 responseBody={}",
                    branchId,
                    visitId,
                    serviceId,
                    responseBody != null && !responseBody.trim().isEmpty() ? responseBody : "<empty>");

            String invalidUserState = detectInvalidUserState(responseBody);
            if (invalidUserState != null) {
                Integer currentVisitServiceId = extractCurrentVisitServiceId(responseBody);
                if (isAssignEffectivelyApplied(currentVisitServiceId, serviceId)) {
                    log.warn("Assign service returned userState={} for visit {} in branch {}, but currentVisitService.serviceId={} already matches requested serviceId={}. Treating assign as effectively successful and continuing with transfer. ResponseBody={}",
                            invalidUserState,
                            visitId,
                            branchId,
                            currentVisitServiceId,
                            serviceId,
                            responseBody != null && !responseBody.trim().isEmpty() ? responseBody : "<empty>");
                } else {
                    String message = "Assign service returned userState=" + invalidUserState
                            + " for visit " + visitId
                            + " in branch " + branchId
                            + ". Orchestra accepted the request formally, but mutating EntryPoint context is not ready yet and currentVisitService.serviceId did not confirm the requested service.";
                    log.error(message + " requestedServiceId={} currentVisitServiceId={} ResponseBody={}",
                            serviceId,
                            currentVisitServiceId,
                            responseBody != null && !responseBody.trim().isEmpty() ? responseBody : "<empty>");
                    throw new MutationContextException(message);
                }
            }
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

    /**
     * Выполняет transfer-visit через конфигурируемый endpoint.
     *
     * <p>На текущей интеграции Orchestra нередко отвечает {@code 204 No Content}, поэтому
     * клиент читает полный {@link HttpResponse} через {@code byte[]} и не требует обязательного
     * строкового тела ответа.</p>
     */
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
        log.info("Transfer visit request branchId={} visitId={} sourceQueueId={} targetQueueId={} sourceEntryPointId={} path={} payload={}",
                branchId,
                visitId,
                sourceQueueId,
                targetQueueId,
                sourceEntryPointId,
                expandedPath,
                payload);
        log.warn("Experimental transfer-visit adapter assumes fromId={} is source entry point for branch {}. Validate this against штатный Orchestra UI trace.",
                sourceEntryPointId,
                branchId);

        MutableHttpRequest<Map<String, Object>> request = applyAuth(HttpRequest.PUT(expandedPath, payload))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE);

        try {
            HttpResponse<byte[]> response = httpClient.toBlocking().exchange(request, byte[].class);
            String responseBody = toLoggableBody(response.getBody(byte[].class).orElse(null));
            log.info("Transfer visit response branchId={} visitId={} targetQueueId={} sourceEntryPointId={} status={} bodyReadMode=exchange-byte-array responseBody={}",
                    branchId,
                    visitId,
                    targetQueueId,
                    sourceEntryPointId,
                    response.getStatus().getCode(),
                    responseBody);
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

    private String detectInvalidUserState(String responseBody) {
        String userState = extractUserState(responseBody);
        if (userState == null) {
            return null;
        }
        if (assignmentProperties.isTreatInactiveUserStateAsFailure() && "INACTIVE".equals(userState)) {
            return userState;
        }
        if (assignmentProperties.isTreatNoStartedServicePointSessionAsFailure()
                && "NO_STARTED_SERVICE_POINT_SESSION".equals(userState)) {
            return userState;
        }
        return null;
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
