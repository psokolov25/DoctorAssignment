package com.qsystems.meddoctorassignment.diagnostics;

import com.qsystems.meddoctorassignment.adapter.gateway.OperatorContextActivationGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.adapter.orchestra.OrchestraSessionCookieStore;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
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
 * интерпретируется server-side user state, включен ли replay cookie для GET/PUT и какие
 * gateway-реализации реально были подняты в текущем процессе.
 */
@Singleton
public class RuntimeConfigurationLogger implements ApplicationEventListener<StartupEvent> {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationLogger.class);
  private static final String BUILD_MARKER = "2026-04-16-run1328-r9-profile-expansion";

  private final AssignmentProperties assignmentProperties;
  private final OrchestraSessionCookieStore orchestraSessionCookieStore;
  private final VisitWorkflowGateway visitWorkflowGateway;
  private final OperatorContextActivationGateway operatorContextActivationGateway;
  private final WebsocketProperties websocketProperties;

  public RuntimeConfigurationLogger(
      AssignmentProperties assignmentProperties,
      OrchestraSessionCookieStore orchestraSessionCookieStore,
      VisitWorkflowGateway visitWorkflowGateway,
      OperatorContextActivationGateway operatorContextActivationGateway,
      WebsocketProperties websocketProperties) {
    this.assignmentProperties = assignmentProperties;
    this.orchestraSessionCookieStore = orchestraSessionCookieStore;
    this.visitWorkflowGateway = visitWorkflowGateway;
    this.operatorContextActivationGateway = operatorContextActivationGateway;
    this.websocketProperties = websocketProperties;
  }

  @Override
  public void onApplicationEvent(StartupEvent event) {
    log.info(
        "Runtime configuration marker={} gatewayClass={} gatewayCodeSource={} activationGatewayClass={} activationGatewayCodeSource={} activationEnabled={} activationMethod={} activationPath={} activationFailCycleOnError={} userServicePointSessionStartTriggerEnabled={} workProfileExpandedTriggerEnabled={} setWorkProfileTriggerEnabled={} servicePointOpenTriggerEnabled={} userSessionSettleWindowMs={} abortCycleOnForbiddenMutation={} treatInactiveUserStateAsFailure={} treatNoStartedServicePointSessionAsFailure={} replayMutationCookiesForPut={} replayReadCookiesForGet={} websocketSendCookiesInHandshake={}",
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
        websocketProperties.isSendCookiesInHandshake());
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
