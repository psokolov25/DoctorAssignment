package com.qsystems.meddoctorassignment.diagnostics;

import com.qsystems.meddoctorassignment.adapter.gateway.OperatorContextActivationGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.adapter.orchestra.OrchestraSessionCookieStore;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.config.WebsocketProperties;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Стартовый аудит эффективной runtime-конфигурации.
 *
 * <p>Нужен для ситуаций, когда архив исходников и реально запущенный артефакт расходятся. На старте
 * пишет ключевые флаги и кодовый источник gateway-реализации, чтобы по логам можно было сразу
 * проверить: подхватились ли нужные настройки и тот ли артефакт реально запущен.
 *
 * <p>В лог сознательно выводятся именно "операционные" признаки: какие trigger-ы активны, как
 * интерпретируется server-side user state, включен ли replay cookie для GET/PUT, как настроены
 * websocket/polling fallback и какая задержка используется для повторного подключения к Orchestra.
 */
@Singleton
public class RuntimeConfigurationLogger implements ApplicationEventListener<StartupEvent> {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationLogger.class);
  private static final String BUILD_MARKER = "2026-04-28-route-step-dedup-fix";

  private final AssignmentProperties assignmentProperties;
  private final MedRobotProperties medRobotProperties;
  private final OrchestraSessionCookieStore orchestraSessionCookieStore;
  private final VisitWorkflowGateway visitWorkflowGateway;
  private final OperatorContextActivationGateway operatorContextActivationGateway;
  private final WebsocketProperties websocketProperties;
  private final OrchestraProperties orchestraProperties;

  public RuntimeConfigurationLogger(
      AssignmentProperties assignmentProperties,
      MedRobotProperties medRobotProperties,
      OrchestraSessionCookieStore orchestraSessionCookieStore,
      VisitWorkflowGateway visitWorkflowGateway,
      OperatorContextActivationGateway operatorContextActivationGateway,
      WebsocketProperties websocketProperties,
      OrchestraProperties orchestraProperties) {
    this.assignmentProperties = assignmentProperties;
    this.medRobotProperties = medRobotProperties;
    this.orchestraSessionCookieStore = orchestraSessionCookieStore;
    this.visitWorkflowGateway = visitWorkflowGateway;
    this.operatorContextActivationGateway = operatorContextActivationGateway;
    this.websocketProperties = websocketProperties;
    this.orchestraProperties = orchestraProperties;
  }

  @Override
  public void onApplicationEvent(StartupEvent event) {
    log.info(
        "Runtime configuration marker={} gatewayClass={} gatewayCodeSource={} activationGatewayClass={} activationGatewayCodeSource={} activationEnabled={} activationMethod={} activationPath={} activationFailCycleOnError={} userServicePointSessionStartTriggerEnabled={} workProfileExpandedTriggerEnabled={} setWorkProfileTriggerEnabled={} servicePointOpenTriggerEnabled={} userSessionSettleWindowMs={} abortCycleOnForbiddenMutation={} treatInactiveUserStateAsFailure={} treatNoStartedServicePointSessionAsFailure={} replayMutationCookiesForPut={} replayReadCookiesForGet={} orchestraUrl={} orchestraBranchesForCache={} orchestraReconnectDelayMs={} websocketEnabled={} websocketReconnectDelayMs={} websocketSendCookiesInHandshake={} pollingEnabled={} pollingCron={} medRobotEnabled={} medRobotUrl={} medRobotOptimalServicePath={} medRobotRequestBodyMode={} medRobotPlainTextPolicy={} medRobotErrorHandlingMode={} medRobotFallbackOnError={} medRobotFallbackOnEmpty={} medRobotRequireDoctorAvailableService={} medRobotRequireKnownQueue={}",
        BUILD_MARKER,
        visitWorkflowGateway.getClass().getName(),
        resolveCodeSource(visitWorkflowGateway.getClass()),
        operatorContextActivationGateway.getClass().getName(),
        resolveCodeSource(operatorContextActivationGateway.getClass()),
        assignmentProperties.getActivation() != null
            && assignmentProperties.getActivation().isEnabled(),
        assignmentProperties.getActivation() != null
            ? assignmentProperties.getActivation().getMethod()
            : null,
        assignmentProperties.getActivation() != null
            ? assignmentProperties.getActivation().getPath()
            : null,
        assignmentProperties.getActivation() != null
            && assignmentProperties.getActivation().isFailCycleOnError(),
        assignmentProperties.isUserServicePointSessionStartTriggerEnabled(),
        assignmentProperties.isWorkProfileExpandedTriggerEnabled(),
        assignmentProperties.isSetWorkProfileTriggerEnabled(),
        assignmentProperties.isServicePointOpenTriggerEnabled(),
        assignmentProperties.getUserSessionSettleWindowMs(),
        assignmentProperties.isAbortCycleOnForbiddenMutation(),
        assignmentProperties.isTreatInactiveUserStateAsFailure(),
        assignmentProperties.isTreatNoStartedServicePointSessionAsFailure(),
        orchestraSessionCookieStore.isCookieReplayEnabledForMethod("PUT"),
        orchestraSessionCookieStore.isCookieReplayEnabledForMethod("GET"),
        orchestraProperties.getUrl(),
        orchestraProperties.getBranchesForCache(),
        Long.valueOf(orchestraProperties.getReconnectDelayMs()),
        websocketProperties.isEnabled(),
        Long.valueOf(websocketProperties.getDelayBeforeReconnectInMilliseconds()),
        websocketProperties.isSendCookiesInHandshake(),
        assignmentProperties.isPollingEnabled(),
        assignmentProperties.getPollingCron(),
        medRobotProperties.isEnabled(),
        medRobotProperties.getUrl(),
        medRobotProperties.getOptimalServicePath(),
        medRobotProperties.getRequestBodyMode(),
        medRobotProperties.getPlainTextPolicy(),
        medRobotProperties.getErrorHandlingMode(),
        medRobotProperties.isFallbackToLocalOnError(),
        medRobotProperties.isFallbackToLocalOnEmptyResponse(),
        medRobotProperties.isRequireDoctorAvailableService(),
        medRobotProperties.isRequireKnownQueue());
  }

  private String resolveCodeSource(Class<?> type) {
    if (type == null
        || type.getProtectionDomain() == null
        || type.getProtectionDomain().getCodeSource() == null) {
      return "<unknown>";
    }
    return String.valueOf(type.getProtectionDomain().getCodeSource().getLocation());
  }
}
