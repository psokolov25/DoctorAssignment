package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.adapter.gateway.MedRobotOptimalServiceGateway;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.MedRobotErrorHandlingMode;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.MedRobotRequestBodyMode;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorServiceMatcher;
import com.qsystems.meddoctorassignment.domain.service.impl.MedRobotAwareDoctorServiceSelectionService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Проверяет граничные режимы выбора услуги через med-robot без поднятия Micronaut-контекста. */
public class MedRobotAwareDoctorServiceSelectionServiceTest {

  @Test
  void usesLocalSelectionAndDoesNotCallMedRobotWhenIntegrationDisabled() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(false);
    fixture.gateway.response = response(302, 902);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
    Assertions.assertEquals(0, fixture.gateway.callCount);
  }

  @Test
  void sendsSortedResolvableUnservedServicesAndUsesRobotResponse() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.gateway.response = response(302, 902);
    VisitDetails visit =
        new VisitDetails(
            1001L,
            900,
            Arrays.asList(
                new VisitUnservedService(303, null, 3),
                new VisitUnservedService(null, "external-301", 1),
                new VisitUnservedService(302, null, 2)));

    Optional<SelectedDoctorService> selected = fixture.select(visit);

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(302, selected.get().getServiceId());
    Assertions.assertEquals(902, selected.get().getTargetQueueId());
    Assertions.assertEquals(Integer.valueOf(2), selected.get().getRouteOrder());
    Assertions.assertEquals(1, fixture.gateway.callCount);
    Assertions.assertEquals(7, fixture.gateway.lastBranchId);
    Assertions.assertEquals(301, fixture.gateway.lastCurrentServiceId);
    Assertions.assertEquals(Arrays.asList(301, 302, 303), fixture.gateway.lastUnservedServiceIds);
  }

  @Test
  void sendsTicketNumberAsPlainTextWhenConfigured() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequestBodyMode(MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT);
    fixture.properties.setPlainTextPolicy("priority");
    fixture.gateway.response = response(302, 902);
    VisitDetails visit = defaultVisit();
    visit.setTicketNumber(" A-1001 ");

    Optional<SelectedDoctorService> selected = fixture.select(visit);

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(302, selected.get().getServiceId());
    Assertions.assertEquals(902, selected.get().getTargetQueueId());
    Assertions.assertEquals(1, fixture.gateway.callCount);
    Assertions.assertEquals(7, fixture.gateway.lastBranchId);
    Assertions.assertEquals(301, fixture.gateway.lastCurrentServiceId);
    Assertions.assertEquals("A-1001", fixture.gateway.lastPlainTextBody);
    Assertions.assertEquals("priority", fixture.gateway.lastPolicy);
    Assertions.assertEquals("plain-text", fixture.gateway.lastMode);
  }


  @Test
  void plainTextModeCallsMedRobotWhenLocalPreselectionIsAbsent() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequestBodyMode(MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT);
    fixture.gateway.response = response(302, 902);
    VisitDetails visit =
        new VisitDetails(
            1002L,
            900,
            Arrays.asList(
                new VisitUnservedService(302, null, 1)));
    visit.setTicketNumber(" Щ010 ");

    Optional<SelectedDoctorService> selected = fixture.select(visit);

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(302, selected.get().getServiceId());
    Assertions.assertEquals(902, selected.get().getTargetQueueId());
    Assertions.assertEquals(1, fixture.gateway.callCount);
    Assertions.assertEquals(301, fixture.gateway.lastCurrentServiceId);
    Assertions.assertEquals("Щ010", fixture.gateway.lastPlainTextBody);
    Assertions.assertEquals("plain-text", fixture.gateway.lastMode);
  }

  @Test
  void plainTextModeUsesVisitCurrentServiceWhenLocalPreselectionIsAbsentAndCurrentServiceIsDoctorAvailable() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequestBodyMode(MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT);
    fixture.doctorAvailableServices.add(Integer.valueOf(117));
    fixture.gateway.response = response(302, 902);
    VisitDetails visit =
        new VisitDetails(
            1003L,
            900,
            Arrays.asList(
                new VisitUnservedService(999, null, 1)));
    visit.setCurrentServiceId(Integer.valueOf(117));
    visit.setTicketNumber("Щ011");

    Optional<SelectedDoctorService> selected = fixture.select(visit);

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(302, selected.get().getServiceId());
    Assertions.assertEquals(902, selected.get().getTargetQueueId());
    Assertions.assertEquals(117, fixture.gateway.lastCurrentServiceId);
    Assertions.assertEquals("Щ011", fixture.gateway.lastPlainTextBody);
  }

  @Test
  void plainTextModeCallsMedRobotWhenVisitHasNoLocalUnservedServices() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequestBodyMode(MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT);
    fixture.gateway.response = response(302, 902);
    VisitDetails visit = new VisitDetails(1004L, 900, new ArrayList<VisitUnservedService>());
    visit.setTicketNumber("Р004");

    Optional<SelectedDoctorService> selected = fixture.select(visit);

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(302, selected.get().getServiceId());
    Assertions.assertEquals(902, selected.get().getTargetQueueId());
    Assertions.assertNull(selected.get().getRouteOrder());
    Assertions.assertEquals(1, fixture.gateway.callCount);
    Assertions.assertEquals("Р004", fixture.gateway.lastPlainTextBody);
  }

  @Test
  void fallsBackToLocalSelectionWhenPlainTextModeHasNoTicketNumberAndFallbackIsEnabled() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequestBodyMode(MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT);
    fixture.gateway.response = response(302, 902);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
    Assertions.assertEquals(0, fixture.gateway.callCount);
  }

  @Test
  void fallsBackToLocalSelectionWhenRobotReturnsNullPair() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.gateway.response = new MedRobotOptimalServiceResponse();

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
  }

  @Test
  void returnsEmptyWhenRobotReturnsEmptyPairAndFallbackIsDisabled() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setFallbackToLocalOnEmptyResponse(false);
    fixture.gateway.response = response(0, 0);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertFalse(selected.isPresent());
  }

  @Test
  void fallsBackToLocalSelectionWhenRobotThrowsAndFallbackIsEnabled() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.gateway.failure = new IllegalStateException("robot unavailable");

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
  }

  @Test
  void skipsVisitWhenRobotThrowsAndErrorHandlingModeIsSkipVisit() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setErrorHandlingMode(MedRobotErrorHandlingMode.SKIP_VISIT);
    fixture.gateway.failure = new IllegalStateException("robot unavailable");

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertFalse(selected.isPresent());
  }

  @Test
  void fallbackToLocalOnErrorBooleanAliasStillMapsToSkipVisitWhenDisabled() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setFallbackToLocalOnError(false);
    fixture.gateway.failure = new IllegalStateException("robot unavailable");

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertFalse(selected.isPresent());
    Assertions.assertEquals(MedRobotErrorHandlingMode.SKIP_VISIT, fixture.properties.getErrorHandlingMode());
  }

  @Test
  void fallsBackWhenRobotReturnsServiceOutsideUnservedRoute() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.gateway.response = response(999, 902);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
  }

  @Test
  void fallsBackWhenRobotReturnsUnknownQueueAndQueueValidationIsEnabled() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequireKnownQueue(true);
    fixture.gateway.response = response(302, 999);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
  }

  @Test
  void acceptsRobotQueueOutsideLocalServiceMappingWhenQueueExistsInCache() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequireKnownQueue(true);
    fixture.cache.getQueueMap().put(Integer.valueOf(950), queue(950, "robot-special-queue"));
    fixture.gateway.response = response(302, 950);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(302, selected.get().getServiceId());
    Assertions.assertEquals(950, selected.get().getTargetQueueId());
  }

  @Test
  void fallsBackWhenRobotReturnsDoctorUnavailableServiceInStrictMode() {
    Fixture fixture = new Fixture();
    fixture.properties.setEnabled(true);
    fixture.properties.setRequireDoctorAvailableService(true);
    fixture.gateway.response = response(302, 902);

    Optional<SelectedDoctorService> selected = fixture.select(defaultVisit());

    Assertions.assertTrue(selected.isPresent());
    Assertions.assertEquals(301, selected.get().getServiceId());
    Assertions.assertEquals(901, selected.get().getTargetQueueId());
  }

  private static VisitDetails defaultVisit() {
    return
        new VisitDetails(
            1001L,
            900,
            Arrays.asList(
                new VisitUnservedService(301, null, 1),
                new VisitUnservedService(302, null, 2)));
  }

  private static MedRobotOptimalServiceResponse response(Integer serviceId, Integer queueId) {
    MedRobotOptimalServiceResponse response = new MedRobotOptimalServiceResponse();
    response.setServiceId(serviceId);
    response.setQueueId(queueId);
    return response;
  }

  private static TinyQueue queue(int id, String name) {
    TinyQueue queue = new TinyQueue();
    queue.setId(id);
    queue.setName(name);
    return queue;
  }

  private static final class Fixture {
    private final MedRobotProperties properties = new MedRobotProperties();
    private final CapturingMedRobotGateway gateway = new CapturingMedRobotGateway();
    private final BranchAssignmentCache cache = new BranchAssignmentCache(7);
    private final MedRobotAwareDoctorServiceSelectionService selectionService;
    private final Set<Integer> doctorAvailableServices = new HashSet<Integer>();

    private Fixture() {
      AssignmentProperties assignmentProperties = new AssignmentProperties();
      selectionService =
          new MedRobotAwareDoctorServiceSelectionService(
              new DefaultDoctorServiceMatcher(assignmentProperties), gateway, properties);
      cache.getQueueMap().put(Integer.valueOf(901), queue(901, "local-doctor-queue"));
      cache.getQueueMap().put(Integer.valueOf(902), queue(902, "robot-queue"));
      cache.getServiceIdToQueueId().put(Integer.valueOf(301), Integer.valueOf(901));
      cache.getServiceIdToQueueId().put(Integer.valueOf(302), Integer.valueOf(902));
      cache.getServiceExternalKeyToId().put("external-301", Integer.valueOf(301));
      doctorAvailableServices.add(Integer.valueOf(301));
    }

    private Optional<SelectedDoctorService> select(VisitDetails visit) {
      return selectionService.select(visit, doctorAvailableServices, cache);
    }
  }

  private static final class CapturingMedRobotGateway implements MedRobotOptimalServiceGateway {
    private MedRobotOptimalServiceResponse response;
    private RuntimeException failure;
    private int callCount;
    private int lastBranchId;
    private int lastCurrentServiceId;
    private List<Integer> lastUnservedServiceIds = new ArrayList<Integer>();
    private String lastPlainTextBody;
    private String lastPolicy;
    private String lastMode;

    @Override
    public MedRobotOptimalServiceResponse selectOptimalService(
        int branchId, int currentServiceId, Set<Integer> unservedServiceIds) {
      callCount++;
      lastBranchId = branchId;
      lastCurrentServiceId = currentServiceId;
      lastUnservedServiceIds = new ArrayList<Integer>(unservedServiceIds);
      lastMode = "json-array";
      if (failure != null) {
        throw failure;
      }
      return response;
    }

    @Override
    public MedRobotOptimalServiceResponse selectOptimalServicePlainText(
        int branchId, int currentServiceId, String plainTextBody, String policy) {
      callCount++;
      lastBranchId = branchId;
      lastCurrentServiceId = currentServiceId;
      lastPlainTextBody = plainTextBody;
      lastPolicy = policy;
      lastMode = "plain-text";
      if (failure != null) {
        throw failure;
      }
      return response;
    }
  }
}
