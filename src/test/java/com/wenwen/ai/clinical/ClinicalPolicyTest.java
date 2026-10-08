package com.wenwen.ai.clinical;

import com.wenwen.ai.source.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClinicalPolicyTest {
    @Test void exactYearsAndUnknownEvidenceHaveIndependentBoundaryExpectations() {
        LocalDate asOf=LocalDate.of(2026,10,7);
        assertEquals(40,ClinicalPolicy.age("110101198610070011",asOf)); assertEquals(39,ClinicalPolicy.age("110101198610080011",asOf));
        assertEquals(25,ClinicalPolicy.age("110101200002290011",LocalDate.of(2026,2,28)));
        assertEquals(26,ClinicalPolicy.age("110101200002290011",LocalDate.of(2026,3,1)));
        assertNull(ClinicalPolicy.age("invalid",asOf)); assertNull(ClinicalPolicy.age("110101202610080011",asOf));
        assertEquals(9,ClinicalPolicy.duration("2016-10-08",asOf)); assertEquals(10,ClinicalPolicy.duration("2016-10-07",asOf));
        for(String date:new String[]{null,"bad","2026-10-08"}) assertNull(ClinicalPolicy.duration(date,asOf));
        for(String date:new String[]{null,"bad","2026-02-30","2026-10-08"}) assertFalse(ClinicalPolicy.datedAt(date,asOf));
        assertEquals("UNKNOWN",ClinicalPolicy.serology(null,null)); assertEquals("FALSE",ClinicalPolicy.serology(null,"negative"));
        assertEquals("TRUE",ClinicalPolicy.serology("FALSE","positive")); assertEquals("TRUE",ClinicalPolicy.serology("TRUE","negative"));
        assertEquals("TRUE",ClinicalPolicy.association(null,asOf)); assertEquals("UNKNOWN",ClinicalPolicy.association(2027,asOf));
        assertEquals("M",ClinicalPolicy.sex(1)); assertEquals("F",ClinicalPolicy.sex(2)); assertNull(ClinicalPolicy.sex(0)); assertNull(ClinicalPolicy.sex(null));
    }
    @Test void sourceValuesDefensivelyCopyNestedCollections() {
        List<Long> ids=new ArrayList<>(Arrays.asList(1L)); List<ScoreVisit> visits=new ArrayList<>(); Map<Long,PatientClinical> patients=new HashMap<>();
        Map<Long,PatientDisplay> displays=new HashMap<>();displays.put(1L,new PatientDisplay(null,"SYNTHETIC-STUDY"));
        SourceBatch batch=new SourceBatch(ids,visits,Instant.EPOCH,patients,Collections.emptyMap(),displays); ids.clear();displays.clear();
        assertEquals("SYNTHETIC-STUDY",batch.getDisplay().get(1L).getStudyNo());assertNull(batch.getDisplay().get(1L).getName());
        assertThrows(UnsupportedOperationException.class,() -> batch.getDisplay().clear());
        assertEquals(1,batch.getPatientIds().size()); assertThrows(UnsupportedOperationException.class,() -> batch.getPatientIds().clear());
        List<String> quality=new ArrayList<>(Arrays.asList("LEGACY_UNVERIFIED")); Map<String,Object> component=new HashMap<>(); component.put("quality",quality);
        Map<String,Map<String,Object>> evaluation=new HashMap<>(); evaluation.put("haq",component);
        ScoreVisit visit=new ScoreVisit(1,1,LocalDate.of(2026,10,7),"3",new java.math.BigDecimal("3"),quality,null,null,evaluation);
        quality.clear(); component.clear(); evaluation.clear();
        assertEquals(1,visit.getQuality().size()); assertEquals(1,((List<?>)visit.getEvaluation().get("haq").get("quality")).size());
        assertThrows(UnsupportedOperationException.class,() -> visit.getEvaluation().get("haq").clear());
        Map<String,Object> provenance=new HashMap<>(); List<String> evidence=new ArrayList<>(Arrays.asList("test")); provenance.put("evidence",evidence);
        PatientClinical patient=new PatientClinical(null,null,null,"UNKNOWN","UNKNOWN","UNKNOWN",provenance); evidence.clear(); provenance.clear();
        assertEquals(1,((List<?>)patient.getProvenance().get("evidence")).size());
        assertThrows(UnsupportedOperationException.class,() -> patient.getProvenance().clear());
    }
}
