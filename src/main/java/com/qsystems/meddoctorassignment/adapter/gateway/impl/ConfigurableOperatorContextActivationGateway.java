package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.OperatorContextActivationGateway;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.domain.exception.MutationContextException;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурируемый activation-step перед mutating REST-операциями.
 *
 * <p>Точный контракт Orchestra для старта operator/service-point session пока не подтвержден,
 * поэтому путь, метод и payload полностью вынесены в конфигурацию. Этот адаптер предназначен
 * как каркас для быстрого включения нужного REST-вызова без переписывания доменного workflow.</p>
 */
@Singleton
public class ConfigurableOperatorContextActivationGateway implements OperatorContextActivationGateway {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableOperatorContextActivationGateway.class);

    private final HttpClient httpClient;
    private final AssignmentProperties assignmentProperties;
    private final OrchestraProperties orchestraProperties;

    public ConfigurableOperatorContextActivationGateway(@Client("${application.orchestra.url}") HttpClient httpClient,
                                                        AssignmentProperties assignmentProperties,
                                                        OrchestraProperties orchestraProperties) {
        this.httpClient = httpClient;
        this.assignmentProperties = assignmentProperties;
        this.orchestraProperties = orchestraProperties;
    }

    @Override
    public void activate(DoctorContext doctorContext) {
        AssignmentProperties.Activation activation = assignmentProperties.getActivation();
        if (activation == null || !activation.isEnabled()) {
            return;
        }

        String pathTemplate = activation.getPath();
        if (pathTemplate == null || pathTemplate.trim().isEmpty()) {
            String message = "Activation step is enabled, but application.assignment.activation.path is not configured";
            log.error(message);
            if (activation.isFailCycleOnError()) {
                throw new MutationContextException(message);
            }
            return;
        }

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", Integer.valueOf(doctorContext.getBranchId()));
        variables.put("servicePointId", Long.valueOf(doctorContext.getServicePointId()));
        variables.put("staffId", Integer.valueOf(doctorContext.getStaffId()));
        variables.put("workProfileId", Integer.valueOf(doctorContext.getWorkProfileId()));
        variables.put("servicePointName", doctorContext.getServicePointName() != null ? doctorContext.getServicePointName() : "");
        variables.put("workProfileName", doctorContext.getWorkProfileName() != null ? doctorContext.getWorkProfileName() : "");
        variables.put("userName", doctorContext.getUserName() != null ? doctorContext.getUserName() : "");

        String expandedPath = expand(pathTemplate, variables);
        String payload = expand(activation.getPayloadTemplate(), variables);
        String methodName = activation.getMethod() != null ? activation.getMethod().trim().toUpperCase() : "POST";
        HttpMethod method = resolveMethod(methodName);

        log.info("Activation request branchId={} servicePointId={} staffId={} workProfileId={} method={} path={} payload={}",
                doctorContext.getBranchId(),
                doctorContext.getServicePointId(),
                doctorContext.getStaffId(),
                doctorContext.getWorkProfileId(),
                method,
                expandedPath,
                payload == null || payload.trim().isEmpty() ? "<empty>" : payload);

        MutableHttpRequest<?> request = buildRequest(method, expandedPath, payload);
        request = applyAuth(request).accept(MediaType.APPLICATION_JSON_TYPE);
        if (method != HttpMethod.GET && method != HttpMethod.DELETE) {
            request.contentType(MediaType.APPLICATION_JSON_TYPE);
        }

        try {
            HttpResponse<byte[]> response = httpClient.toBlocking().exchange(request, byte[].class);
            String responseBody = toLoggableBody(response.getBody(byte[].class).orElse(null));
            log.info("Activation response branchId={} servicePointId={} status={} responseBody={}",
                    doctorContext.getBranchId(),
                    doctorContext.getServicePointId(),
                    response.getStatus().getCode(),
                    responseBody);

            String invalidUserState = detectInvalidUserState(responseBody);
            if (invalidUserState != null) {
                String message = "Activation step returned userState=" + invalidUserState
                        + " for branch " + doctorContext.getBranchId()
                        + ", servicePoint " + doctorContext.getServicePointId()
                        + ". Mutating EntryPoint context is still not ready.";
                log.error(message + " ResponseBody={}", responseBody);
                if (activation.isFailCycleOnError()) {
                    throw new MutationContextException(message);
                }
            }
        } catch (HttpClientResponseException exception) {
            String responseBody = exception.getResponse().getBody(String.class).orElse("<empty>");
            String message = "Activation step failed for branch " + doctorContext.getBranchId()
                    + ", servicePoint " + doctorContext.getServicePointId()
                    + ": status=" + exception.getStatus()
                    + ", responseBody=" + responseBody;
            log.error(message, exception);
            if (activation.isFailCycleOnError()) {
                throw new MutationContextException(message, exception);
            }
        }
    }

    static HttpMethod resolveMethod(String methodName) {
        if ("GET".equals(methodName)) {
            return HttpMethod.GET;
        }
        if ("PUT".equals(methodName)) {
            return HttpMethod.PUT;
        }
        if ("PATCH".equals(methodName)) {
            return HttpMethod.PATCH;
        }
        if ("DELETE".equals(methodName)) {
            return HttpMethod.DELETE;
        }
        return HttpMethod.POST;
    }

    static String expand(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private MutableHttpRequest<?> buildRequest(HttpMethod method, String path, String payload) {
        if (method == HttpMethod.GET) {
            return HttpRequest.GET(path);
        }
        if (method == HttpMethod.DELETE) {
            return HttpRequest.DELETE(path);
        }
        String body = payload != null ? payload : "";
        if (method == HttpMethod.PUT) {
            return HttpRequest.PUT(path, body);
        }
        if (method == HttpMethod.PATCH) {
            return HttpRequest.PATCH(path, body);
        }
        return HttpRequest.POST(path, body);
    }

    private String detectInvalidUserState(String responseBody) {
        String userState = ConfigurableVisitWorkflowGateway.extractUserState(responseBody);
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

    private static String toLoggableBody(byte[] responseBodyBytes) {
        if (responseBodyBytes == null || responseBodyBytes.length == 0) {
            return "<empty>";
        }
        String responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
        return responseBody.trim().isEmpty() ? "<empty>" : responseBody;
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
