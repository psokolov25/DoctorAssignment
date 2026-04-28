package com.qsystems.meddoctorassignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VisitDetailsJsonMappingTest {

  @Test
  void mapsOrchestraVisitPayloadWithUnservedVisitServices() throws Exception {
    String json =
        "{\"id\":172082,\"unservedVisitServices\":[{\"serviceId\":5,\"serviceInternalName\":\"Терапевт\"},{\"serviceId\":7,\"serviceInternalName\":\"Оккулист\"},{\"serviceId\":4,\"serviceInternalName\":\"Хирург\"},{\"serviceId\":6,\"serviceInternalName\":\"Стоматолог\"}],\"parameterMap\":{\"currentQueueOrigId\":98}}";

    VisitDetails visitDetails = new ObjectMapper().readValue(json, VisitDetails.class);

    Assertions.assertEquals(172082L, visitDetails.getId());
    Assertions.assertEquals(Integer.valueOf(98), visitDetails.getQueueId());
    Assertions.assertEquals(4, visitDetails.getUnservedServices().size());
    Assertions.assertEquals(
        Integer.valueOf(5), visitDetails.getUnservedServices().get(0).getServiceId());
    Assertions.assertEquals("Терапевт", visitDetails.getUnservedServices().get(0).getExternalKey());
  }

  @Test
  void mapsCurrentVisitServiceIdFromNestedCurrentVisitServiceObject() throws Exception {
    String json =
        "{\"id\":172161,\"currentVisitService\":{\"id\":226626,\"serviceId\":4},\"parameterMap\":{\"currentQueueOrigId\":98}}";

    VisitDetails visitDetails = new ObjectMapper().readValue(json, VisitDetails.class);

    Assertions.assertEquals(172161L, visitDetails.getId());
    Assertions.assertEquals(Integer.valueOf(98), visitDetails.getQueueId());
    Assertions.assertEquals(Integer.valueOf(4), visitDetails.getCurrentServiceId());
    Assertions.assertEquals(Long.valueOf(226626L), visitDetails.getCurrentVisitServiceRecordId());
  }
}
