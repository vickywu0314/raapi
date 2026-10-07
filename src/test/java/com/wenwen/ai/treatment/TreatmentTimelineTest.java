package com.wenwen.ai.treatment;

import com.wenwen.ai.source.ScoreVisit;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentTimelineTest {
    final DrugDictionary.Snapshot dictionary=new DevelopmentDrugDictionary().snapshot();
    final LocalDate asOf=LocalDate.of(2025,6,1);
    ScoreVisit visit(long id,String date,String... rows) {
        String source="{\"xyList\":["+String.join(",",rows)+"]}";
        return observation(id,date,source);
    }
    ScoreVisit observation(long id,String date,String source) {
        return new ScoreVisit(id,1,date==null?null:LocalDate.parse(date),null,null,Collections.emptyList(),null,null,Collections.emptyMap(),MedicationObservation.parse(source));
    }
    String drug(String name,String start,String end) {
        return "{\"drugName\":\""+name+"\",\"startTime\":"+(start==null?"null":"\""+start+"\"")+",\"endTime\":"+(end==null?"null":"\""+end+"\"")+"}";
    }
    TreatmentTimeline timeline(ScoreVisit... visits) { return TreatmentTimeline.build(Arrays.asList(visits),asOf,dictionary); }
    @Test void displayLineBucketsKeepActualHistoryAndEveryEpisodeBoundary() {
        List<ScoreVisit> sequence=Arrays.asList(
            visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-02-28")),
            visit(200,"2025-03-01",drug("依那西普","2025-03-01","2025-04-30")),
            visit(300,"2025-05-01",drug("托法替布","2025-05-01","2025-06-30")),
            visit(400,"2025-07-01",drug("阿巴西普","2025-07-01","2025-08-31")),
            visit(500,"2025-09-01",drug("甲氨蝶呤","2025-09-01",null)));
        String[] dates={"2025-02-01","2025-04-01","2025-06-01","2025-08-01","2025-09-01"};
        String[] starts={"2025-01-01","2025-03-01","2025-05-01","2025-07-01","2025-09-01"};
        String[] ends={"2025-02-28","2025-04-30","2025-06-30","2025-08-31",null};
        String[] names={"阿达木单抗","依那西普","托法替布","阿巴西普","甲氨蝶呤"};
        String[] categories={"TNFi","TNFi","JAKi","Abatacept","csDMARD"};
        int[] lines={2,3,3,3,1}, histories={1,2,3,4,4};
        for(int i=0;i<dates.length;i++) {
            LocalDate at=LocalDate.parse(dates[i]);
            TreatmentTimeline value=TreatmentTimeline.build(sequence,at,dictionary);
            assertEquals(i+1,value.episodes.size());
            TreatmentEpisode current=value.current(); assertEquals("ACTIVE",current.state);
            assertEquals(categories[i],current.category); assertEquals(Integer.valueOf(lines[i]),current.line);
            assertEquals(histories[i],current.historyN);
            assertEquals(i==4?0L:1L,current.summary(at).get("schemeDurationMonths"));
            assertEquals(histories[i],current.summary(at).get("targetedDrugHistoryN"));
            for(int j=0;j<=i;j++) {
                TreatmentEpisode episode=value.episodes.get(j);
                assertEquals(Integer.valueOf(lines[j]),episode.line); assertEquals(histories[j],episode.historyN);
                assertEquals(Arrays.asList("dev:"+names[j]),episode.genericIds);
                assertEquals("visit:"+((j+1)*100)+":dev:"+names[j],episode.key);
                assertEquals(LocalDate.parse(starts[j]),episode.start); assertNull(episode.estimatedStart);
                assertEquals(ends[j]==null?null:LocalDate.parse(ends[j]),episode.end);
                assertEquals("EXPLICIT",episode.confidence);
                assertEquals(Integer.toString((j+1)*100),episode.provenance.get("visitId"));
                assertEquals("zlfa.xyList",episode.provenance.get("field"));
                assertEquals(starts[j],episode.provenance.get("observedAt"));
            }
        }
    }

    @Test void nonActiveStatesKeepNullLineAndFourthTargetedHistory() {
        List<ScoreVisit> sequence=Arrays.asList(
            visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-02-28")),
            visit(200,"2025-03-01",drug("依那西普","2025-03-01","2025-04-30")),
            visit(300,"2025-05-01",drug("托法替布","2025-05-01","2025-06-30")),
            visit(400,"2025-07-01",drug("阿巴西普","2025-07-01","2025-08-31")));
        LocalDate at=LocalDate.of(2025,9,1);
        TreatmentEpisode none=TreatmentTimeline.build(sequence,at,dictionary).current();
        assertEquals("NONE",none.state); assertNull(none.line); assertEquals(4,none.historyN);
        List<ScoreVisit> unknown=new ArrayList<>(sequence);
        unknown.add(visit(500,"2025-09-01",drug("unmapped",null,null)));
        TreatmentEpisode uncertain=TreatmentTimeline.build(unknown,at,dictionary).current();
        assertEquals("UNKNOWN",uncertain.state); assertNull(uncertain.line); assertEquals(4,uncertain.historyN);
        List<ScoreVisit> conflict=new ArrayList<>(sequence);
        conflict.add(visit(500,"2025-09-01",drug("阿达木单抗","2025-09-01",null),drug("依那西普","2025-09-01",null)));
        TreatmentEpisode combined=TreatmentTimeline.build(conflict,at,dictionary).current();
        assertEquals("CONFLICT",combined.state); assertNull(combined.line); assertEquals(4,combined.historyN);
        for(TreatmentEpisode current:Arrays.asList(none,uncertain,combined)) {
            assertNull(current.summary(at).get("line")); assertEquals(4,current.summary(at).get("targetedDrugHistoryN"));
            assertFalse(current.matches("bio"));
        }
    }

    @Test void differentTargetedGenericsIncreaseHistoryWithinSameCategory() {
        TreatmentTimeline value=timeline(
            visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-02-28")),
            visit(200,"2025-03-01",drug("依那西普","2025-03-01",null)));
        TreatmentEpisode current=value.current(); assertEquals("TNFi",current.category); assertEquals(Integer.valueOf(3),current.line);
        assertEquals(2,current.historyN); assertEquals(3L,current.summary(asOf).get("schemeDurationMonths"));
        assertEquals(2,value.episodes.size()); assertEquals(LocalDate.of(2025,2,28),value.episodes.get(0).end);
    }
    @Test void brandAndAdjunctChangesDoNotRestartTargetedEpisode() {
        ScoreVisit initial=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01",null));
        ScoreVisit changed=observation(200,"2025-04-01","{\"xyList\":[{\"drugName\":\" 阿达木单抗 \",\"goodsName\":\"changed-brand\",\"company\":\"changed-maker\",\"startTime\":\"2025-01-01\"},"+drug("甲氨蝶呤","2025-03-01",null)+"]}");
        TreatmentTimeline value=timeline(initial,changed); assertEquals(timeline(initial).current().key,value.current().key);
        assertEquals(1,value.episodes.size()); assertEquals(Integer.valueOf(2),value.current().line); assertEquals(1,value.current().historyN);
        assertEquals(LocalDate.of(2025,1,1),value.current().start);
    }

    @Test void onlyExplicitEndStopsSchemeAndEndDayIsInclusive() {
        ScoreVisit stopped=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31"));
        TreatmentEpisode current=timeline(stopped).current(); assertEquals("NONE",current.state);
        assertNull(current.line); assertNull(current.category); assertNull(current.summary(asOf).get("schemeDurationMonths")); assertEquals(1,current.historyN);
        TreatmentEpisode endDay=TreatmentTimeline.build(Arrays.asList(stopped),LocalDate.of(2025,5,31),dictionary).current();
        assertEquals("ACTIVE",endDay.state); assertEquals(Integer.valueOf(2),endDay.line);
        ScoreVisit uiOnly=observation(200,"2025-05-31","{\"finish\":true,\"xyList\":[{\"drugName\":\"阿达木单抗\",\"startTime\":\"2025-01-01\",\"tygc\":\"停药\",\"tyyy\":\"已结束\"}]}");
        assertEquals("ACTIVE",timeline(uiOnly).current().state);
    }

    @Test void restartAfterExplicitStopCreatesEpisodeWithoutNewGenericHistory() {
        ScoreVisit first=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-02-28"));
        ScoreVisit restart=visit(300,"2025-05-01",drug("阿达木单抗","2025-05-01",null));
        TreatmentTimeline direct=timeline(first,restart); assertEquals(2,direct.episodes.size());
        assertNotEquals(direct.episodes.get(0).key,direct.current().key); assertEquals(1,direct.current().historyN); assertEquals(Integer.valueOf(2),direct.current().line);
        assertEquals(1L,direct.current().summary(asOf).get("schemeDurationMonths"));
        TreatmentTimeline switched=timeline(first,visit(200,"2025-03-01",drug("依那西普","2025-03-01","2025-04-30")),restart);
        assertEquals(3,switched.episodes.size()); assertEquals(2,switched.current().historyN); assertEquals(Integer.valueOf(3),switched.current().line);
        assertEquals(LocalDate.of(2025,5,1),switched.current().start);
    }

    @Test void newSchemeBoundsPreviousAndTraditionalSchemeRetainsTargetedHistory() {
        TreatmentTimeline value=timeline(visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01",null)),
            visit(200,"2025-03-01",drug("依那西普","2025-03-01","2025-04-30")),
            visit(300,"2025-05-01",drug("阿达木单抗","2025-05-01","2025-05-19")),
            visit(400,"2025-05-20",drug("甲氨蝶呤","2025-05-20",null)));
        assertEquals(LocalDate.of(2025,2,28),value.episodes.get(0).end);
        assertEquals("csDMARD",value.current().category); assertEquals(Integer.valueOf(1),value.current().line); assertEquals(2,value.current().historyN);
        assertEquals(0L,value.current().summary(asOf).get("schemeDurationMonths"));
        TreatmentTimeline traditional=timeline(visit(500,"2025-01-01",drug("甲氨蝶呤","2025-01-01",null)),
            visit(600,"2025-03-01",drug("甲氨蝶呤","2025-01-01",null),drug("羟氯喹","2025-03-01",null)));
        assertEquals(2,traditional.episodes.size()); assertEquals(LocalDate.of(2025,3,1),traditional.current().start);
        assertEquals(LocalDate.of(2025,2,28),traditional.episodes.get(0).end);
    }

    @Test void emptyModuleCarriesEstimatedSchemeWithoutInventingStopOrReliableStart() {
        ScoreVisit first=visit(100,"2025-01-31",drug("阿达木单抗",null,null));
        TreatmentEpisode current=timeline(first,observation(200,"2025-05-01","{\"finish\":true,\"xyList\":[]}")).current();
        assertEquals("ACTIVE",current.state); assertNull(current.start); assertEquals(LocalDate.of(2025,1,31),current.estimatedStart);
        assertEquals("ESTIMATED_FIRST_OBSERVED",current.confidence); assertEquals(4L,current.summary(asOf).get("schemeDurationMonths"));
        assertTrue(current.provenance.get("quality").toString().contains("CARRIED_FORWARD"));
        assertEquals(timeline(first).current().key,current.key);
        assertEquals("UNKNOWN",timeline(observation(300,"2025-04-01","{}")).current().state);
        assertEquals("NONE",timeline(visit(400,"2025-01-01",drug("阿达木单抗",null,"2025-05-31")),observation(500,"2025-06-01","{}")).current().state);
    }

    @Test void malformedMedicationEvidenceCannotCarryPreviousScheme() {
        ScoreVisit first=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01",null));
        for(String source:Arrays.asList("not-json","\"record\"","[]","{\"xyList\":{}}","{\"xyList\":[1]}","{\"xyList\":[null]}","{\"zcyList\":false}","{} {}","{\"xyList\":[],\"xyList\":[]}")) {
            TreatmentEpisode value=timeline(first,observation(200,"2025-05-01",source)).current();
            assertEquals("UNKNOWN",value.state,source); assertEquals("INVALID_MEDICATION_SOURCE",value.provenance.get("missingReason"));
            assertNull(value.start); assertNull(value.line);
        }
        assertEquals("UNKNOWN",timeline(first,visit(300,"2025-05-01",drug("未映射药",null,null))).current().state);
    }

    @Test void invalidExplicitDatesNeverBecomeReliableOrEstimatedOrigins() {
        for(String[] dates:Arrays.asList(new String[]{"2025-02-30",null},new String[]{"2025-05-01","2025-04-01"},new String[]{"2025-1-01",null},new String[]{"2025-01-01","invalid"})) {
            TreatmentEpisode value=timeline(visit(100,"2025-01-01",drug("阿达木单抗",dates[0],dates[1]))).current();
            assertNull(value.start); assertNull(value.estimatedStart); assertNull(value.summary(asOf).get("schemeDurationMonths"));
            assertTrue(value.provenance.get("quality").toString().contains("INVALID_TREATMENT_DATE"));
        }
    }

    @Test void duplicateEntityConflictingDatesAreOrderIndependentAndUnreliable() {
        String a=drug("阿达木单抗","2025-01-01","2025-05-31"), b=drug("阿达木单抗","2025-02-01",null);
        TreatmentEpisode value=timeline(visit(100,"2025-01-01",a,b)).current();
        assertNull(value.start); assertNull(value.estimatedStart); assertNull(value.end);
        assertTrue(value.provenance.get("quality").toString().contains("DATE_CONFLICT"));
        assertEquals(1,value.historyN); assertEquals(1,value.genericIds.size());
        assertEquals(value.summary(asOf),timeline(visit(100,"2025-01-01",b,a)).current().summary(asOf));
        TreatmentEpisode compatible=timeline(visit(200,"2025-01-01",drug("阿达木单抗",null,null),drug("阿达木单抗","2025-01-01",null))).current();
        assertEquals(LocalDate.of(2025,1,1),compatible.start); assertEquals(Integer.valueOf(2),compatible.line);
    }

    @Test void futureStartsWaitForClockAndCannotTerminateOldSchemeEarly() {
        ScoreVisit old=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01",null));
        ScoreVisit future=visit(200,"2025-05-01",drug("依那西普","2025-07-01",null));
        TreatmentTimeline now=timeline(old,future); assertEquals("dev:阿达木单抗",now.current().genericIds.get(0));
        assertEquals(1,now.current().historyN); assertEquals(1,now.episodes.size());
        TreatmentTimeline later=TreatmentTimeline.build(Arrays.asList(old,future),LocalDate.of(2025,8,1),dictionary);
        assertEquals("dev:依那西普",later.current().genericIds.get(0)); assertEquals(2,later.current().historyN); assertEquals(Integer.valueOf(3),later.current().line);
        assertEquals(LocalDate.of(2025,6,30),later.episodes.get(0).end); assertEquals(1L,later.current().summary(LocalDate.of(2025,8,1)).get("schemeDurationMonths"));
        assertEquals("UNKNOWN",timeline(future).current().state);
    }

    @Test void endedDrugNoLongerConflictsWithActiveDrugButStillCountsHistory() {
        ScoreVisit row=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31"),drug("依那西普","2025-03-01",null));
        TreatmentEpisode current=timeline(row).current(); assertEquals("ACTIVE",current.state); assertEquals("TNFi",current.category);
        assertEquals(Arrays.asList("dev:依那西普"),current.genericIds); assertEquals(Integer.valueOf(3),current.line); assertEquals(2,current.historyN);
        assertNull(current.start); assertEquals(LocalDate.of(2025,6,1),current.estimatedStart); assertEquals("ESTIMATED_EXPLICIT_END",current.confidence);
        assertEquals(0L,current.summary(asOf).get("schemeDurationMonths"));
        TreatmentEpisode onEnd=TreatmentTimeline.build(Arrays.asList(row),LocalDate.of(2025,5,31),dictionary).current(); assertEquals("CONFLICT",onEnd.state);
    }

    @Test void invalidAndFutureVisitDatesCannotCreateCurrentTreatmentFacts() {
        ScoreVisit undated=visit(100,null,drug("阿达木单抗","2025-01-01",null));
        ScoreVisit future=visit(200,"2025-07-01",drug("依那西普","2025-01-01",null));
        TreatmentEpisode none=timeline(undated,future).current(); assertEquals("UNKNOWN",none.state); assertEquals(0,none.historyN);
        assertTrue(none.provenance.get("quality").toString().contains("UNDATED_MEDICATION_OBSERVATION"));
        assertTrue(none.provenance.get("quality").toString().contains("FUTURE_MEDICATION_OBSERVATION"));
        TreatmentEpisode current=timeline(visit(20,"2025-01-01",drug("阿达木单抗","2025-01-01",null)),future).current();
        assertEquals(Arrays.asList("dev:阿达木单抗"),current.genericIds); assertEquals(1,current.historyN);
    }

    @Test void laterMissingDatesCannotEraseExplicitStopEvidence() {
        TreatmentTimeline value=timeline(visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31")),
            visit(200,"2025-04-01",drug("阿达木单抗",null,null)));
        assertEquals("NONE",value.current().state); assertEquals(LocalDate.of(2025,5,31),value.current().end);
        assertEquals(1,value.episodes.size()); assertEquals(LocalDate.of(2025,1,1),value.episodes.get(0).start);
    }

    @Test void laterExplicitStartReplacesEstimateForSameEpisode() {
        ScoreVisit estimated=visit(100,"2025-01-31",drug("阿达木单抗",null,null));
        TreatmentTimeline value=timeline(estimated,visit(200,"2025-04-01",drug("阿达木单抗","2025-01-01",null)));
        assertEquals(LocalDate.of(2025,1,1),value.current().start); assertNull(value.current().estimatedStart);
        assertEquals("EXPLICIT",value.current().confidence); assertEquals(timeline(estimated).current().key,value.current().key);
        assertEquals(5L,value.current().summary(asOf).get("schemeDurationMonths"));
    }

    @Test void contradictoryDatesAcrossObservationsDoNotSilentlyChooseLatest() {
        ScoreVisit first=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01",null));
        TreatmentEpisode value=timeline(first,visit(200,"2025-04-01",drug("阿达木单抗","2025-02-01",null))).current();
        assertNull(value.start); assertNull(value.estimatedStart); assertTrue(value.provenance.get("quality").toString().contains("DATE_CONFLICT"));
        TreatmentEpisode invalid=timeline(first,visit(300,"2025-04-01",drug("阿达木单抗","2025-02-30",null))).current();
        assertNull(invalid.start); assertNull(invalid.estimatedStart); assertEquals("UNKNOWN",invalid.confidence);
    }

    @Test void episodeCopiesNestedProvenanceAndCollections() {
        java.util.List<String> quality=new java.util.ArrayList<>(Arrays.asList("LEGACY_UNVERIFIED"));
        java.util.List<String> ids=new java.util.ArrayList<>(Arrays.asList("dev:阿达木单抗"));
        java.util.Map<String,Object> provenance=new java.util.LinkedHashMap<>(); provenance.put("quality",quality);
        TreatmentEpisode value=new TreatmentEpisode("key","ACTIVE","TNFi",2,1,asOf,null,null,"EXPLICIT",ids,provenance);
        quality.clear(); ids.clear(); provenance.clear();
        assertEquals(Arrays.asList("LEGACY_UNVERIFIED"),value.provenance.get("quality")); assertEquals(1,value.genericIds.size());
        assertThrows(UnsupportedOperationException.class,() -> ((java.util.List<String>)value.provenance.get("quality")).clear());
        assertThrows(UnsupportedOperationException.class,() -> value.genericIds.clear());
        assertThrows(UnsupportedOperationException.class,() -> timeline(visit(1,"2025-01-01",drug("阿达木单抗","2025-01-01",null))).episodes.clear());
    }

    @Test void normalizedOtherDrugStillCannotMatchBioOrFiveCategories() {
        java.util.Map<String,DrugDictionary.Drug> names=new java.util.LinkedHashMap<>(); names.put("other",new DrugDictionary.Drug("synthetic:other","other","OTHER"));
        TreatmentEpisode value=TreatmentTimeline.build(Arrays.asList(visit(100,"2025-01-01",drug("other","2025-01-01",null))),asOf,new DrugDictionary.Snapshot("synthetic-other",names)).current();
        assertEquals("UNKNOWN",value.state); assertNull(value.category); assertNull(value.line); assertEquals(0,value.historyN); assertFalse(value.matches("bio"));
        for(String category:Arrays.asList("csDMARD","TNFi","JAKi","IL-6i","Abatacept")) assertFalse(value.matches(category));
    }

    @Test void observedRestartAfterExplicitStopCanHaveEstimatedNewOrigin() {
        ScoreVisit stopped=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-02-28"));
        TreatmentTimeline value=timeline(stopped,visit(200,"2025-05-01",drug("阿达木单抗",null,null)));
        assertEquals(2,value.episodes.size()); assertEquals("ACTIVE",value.current().state); assertNull(value.current().start);
        assertEquals(LocalDate.of(2025,5,1),value.current().estimatedStart); assertEquals("ESTIMATED_FIRST_OBSERVED",value.current().confidence);
        assertEquals(1,value.current().historyN); assertEquals(Integer.valueOf(2),value.current().line); assertEquals(1L,value.current().summary(asOf).get("schemeDurationMonths"));
    }

    @Test void excludedDatesRetainQualityAlongsideStillActiveOldScheme() {
        ScoreVisit active=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01",null));
        TreatmentEpisode value=timeline(active,visit(200,"2025-05-01",drug("依那西普","2025-07-01",null)),
            visit(300,"2025-07-01",drug("托法替布","2025-01-01",null)),visit(400,null,drug("阿巴西普","2025-01-01",null))).current();
        assertEquals(Arrays.asList("dev:阿达木单抗"),value.genericIds); assertEquals(1,value.historyN);
        String quality=value.provenance.get("quality").toString(); assertTrue(quality.contains("FUTURE_TREATMENT_START"));
        assertTrue(quality.contains("FUTURE_MEDICATION_OBSERVATION")); assertTrue(quality.contains("UNDATED_MEDICATION_OBSERVATION"));
    }

    @Test void allDevelopmentNamesAndStableVisitOrderHaveIndependentExpectations() {
        String[][] groups={{"csDMARD","甲氨蝶呤","来氟米特","柳氮磺吡啶","羟氯喹"},{"TNFi","阿达木单抗","依那西普","英夫利昔单抗","戈利木单抗","赛妥珠单抗"},{"JAKi","托法替布","巴瑞替尼","乌帕替尼"},{"IL-6i","托珠单抗","沙利鲁单抗"},{"Abatacept","阿巴西普"}};
        for(String[] group:groups) for(int i=1;i<group.length;i++) {
            DrugDictionary.Drug drug=dictionary.identify(" "+group[i]+" "); assertNotNull(drug); assertEquals("dev:"+group[i],drug.id); assertEquals(group[0],drug.category);
        }
        for(String name:Arrays.asList("MTX","阿达木","synthetic-brand","TNF","使用阿达木单抗有效")) assertNull(dictionary.identify(name));
        ScoreVisit older=visit(100,"2025-04-01",drug("阿达木单抗","2025-01-01","2025-02-28"));
        ScoreVisit newer=visit(200,"2025-04-01",drug("依那西普","2025-03-01",null));
        TreatmentTimeline value=timeline(newer,older); assertEquals(timeline(older,newer).current().summary(asOf),value.current().summary(asOf));
        assertEquals(Arrays.asList("dev:依那西普"),value.current().genericIds); assertEquals(2,value.current().historyN); assertEquals(Integer.valueOf(3),value.current().line); assertEquals(2,value.episodes.size());
    }

    @Test void duplicateDateConflictCannotHideInvalidExplicitDateQuality() {
        String valid=drug("阿达木单抗","2025-01-01",null),invalid=drug("阿达木单抗","invalid",null);
        TreatmentEpisode value=timeline(visit(100,"2025-01-01",valid,invalid)).current();
        String quality=value.provenance.get("quality").toString(); assertTrue(quality.contains("INVALID_TREATMENT_DATE")); assertTrue(quality.contains("DATE_CONFLICT"));
        assertNull(value.start); assertNull(value.estimatedStart);
        assertEquals(value.summary(asOf),timeline(visit(100,"2025-01-01",invalid,valid)).current().summary(asOf));
        TreatmentEpisode target=timeline(visit(200,"2025-01-01",valid),visit(300,"2025-04-01",valid,drug("甲氨蝶呤","invalid",null))).current();
        assertTrue(target.provenance.get("quality").toString().contains("INVALID_TREATMENT_DATE"));
        assertEquals(LocalDate.of(2025,1,1),target.start); assertEquals("EXPLICIT",target.confidence);

    }

    @Test void explicitMemberEndPreservesHistoricalTraditionalCombinationAndCreatesNewSegment() {
        ScoreVisit jan=visit(100,"2025-01-01",drug("甲氨蝶呤","2025-01-01",null));
        ScoreVisit mar=visit(200,"2025-03-01",drug("甲氨蝶呤","2025-01-01",null),drug("来氟米特","2025-03-01","2025-04-30"));
        ScoreVisit may=visit(300,"2025-05-01",drug("甲氨蝶呤","2025-01-01",null));
        TreatmentTimeline current=timeline(jan,mar,may); assertEquals(3,current.episodes.size());
        assertEquals(LocalDate.of(2025,2,28),current.episodes.get(0).end);
        assertEquals(LocalDate.of(2025,3,1),current.episodes.get(1).start); assertEquals(LocalDate.of(2025,4,30),current.episodes.get(1).end);
        assertEquals(2,current.episodes.get(1).genericIds.size()); assertEquals("200",current.episodes.get(1).provenance.get("visitId"));
        TreatmentEpisode value=current.current(); assertEquals("ACTIVE",value.state); assertEquals("csDMARD",value.category);
        assertEquals(Integer.valueOf(1),value.line); assertEquals(0,value.historyN); assertNull(value.start); assertEquals(LocalDate.of(2025,5,1),value.estimatedStart);
        assertEquals("ESTIMATED_EXPLICIT_END",value.confidence); assertTrue(value.provenance.get("quality").toString().contains("END_DERIVED_SCHEME_START"));
        assertEquals(1L,value.summary(asOf).get("schemeDurationMonths")); assertNotEquals(current.episodes.get(0).key,value.key);
        TreatmentTimeline april=TreatmentTimeline.build(Arrays.asList(jan,mar,may),LocalDate.of(2025,4,1),dictionary); assertEquals(2,april.episodes.size());
        assertEquals(LocalDate.of(2025,3,1),april.current().start);
    }

    @Test void explicitEndTransitionsWithoutNewVisitAndRetainsOriginalDrugFacts() {
        ScoreVisit jan=visit(100,"2025-01-01",drug("甲氨蝶呤","2025-01-01",null));
        ScoreVisit mar=visit(200,"2025-03-01",drug("甲氨蝶呤","2025-01-01",null),drug("来氟米特","2025-03-01","2025-04-30"));
        TreatmentTimeline value=timeline(jan,mar); assertEquals(3,value.episodes.size()); assertEquals(1L,value.current().summary(asOf).get("schemeDurationMonths"));
        assertNull(value.current().start); assertEquals(LocalDate.of(2025,5,1),value.current().estimatedStart);
        java.util.List<java.util.Map<String,Object>> facts=(java.util.List<java.util.Map<String,Object>>)value.episodes.get(1).provenance.get("drugFacts");
        assertEquals(2,facts.size());
        for(java.util.Map<String,Object> fact:facts) {
            assertEquals("200",fact.get("visitId")); assertEquals("zlfa.xyList",fact.get("field"));
            if("dev:甲氨蝶呤".equals(fact.get("genericDrugId"))) assertEquals("2025-01-01",fact.get("startTime"));
            if("dev:来氟米特".equals(fact.get("genericDrugId"))) { assertEquals("2025-03-01",fact.get("startTime")); assertEquals("2025-04-30",fact.get("endTime")); }
        }
        TreatmentTimeline repeated=timeline(jan,mar,visit(300,"2025-05-20",drug("甲氨蝶呤","2025-01-01",null)),observation(400,"2025-05-25","{}"));
        assertEquals(value.current().key,repeated.current().key); assertEquals(3,repeated.episodes.size()); assertNull(repeated.current().start);
        assertEquals("ESTIMATED_EXPLICIT_END",repeated.current().confidence); assertEquals(1L,repeated.current().summary(asOf).get("schemeDurationMonths"));
        assertTrue(repeated.current().provenance.get("quality").toString().contains("CARRIED_FORWARD"));
        TreatmentTimeline endDay=TreatmentTimeline.build(Arrays.asList(jan,mar),LocalDate.of(2025,4,30),dictionary); assertEquals(2,endDay.episodes.size()); assertEquals(2,endDay.current().genericIds.size());
    }

    @Test void futureStartActivationAndConflictExitUseTheirOwnEffectiveDays() {
        ScoreVisit combined=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31"),drug("依那西普","2025-03-01",null));
        TreatmentTimeline feb=TreatmentTimeline.build(Arrays.asList(combined),LocalDate.of(2025,2,1),dictionary);
        assertEquals(1,feb.episodes.size()); assertEquals(Arrays.asList("dev:阿达木单抗"),feb.current().genericIds); assertEquals(1,feb.current().historyN);
        for(LocalDate time:Arrays.asList(LocalDate.of(2025,4,1),LocalDate.of(2025,5,31))) {
            TreatmentTimeline value=TreatmentTimeline.build(Arrays.asList(combined),time,dictionary); assertEquals(2,value.episodes.size());
            assertEquals("CONFLICT",value.current().state); assertEquals(LocalDate.of(2025,3,1),value.current().start); assertEquals(2,value.current().historyN); assertFalse(value.current().matches("bio"));
        }
        TreatmentTimeline june=timeline(combined); assertEquals(3,june.episodes.size()); assertNull(june.current().start); assertEquals(LocalDate.of(2025,6,1),june.current().estimatedStart);
        assertEquals("ESTIMATED_EXPLICIT_END",june.current().confidence); assertEquals(0L,june.current().summary(asOf).get("schemeDurationMonths")); assertEquals(Integer.valueOf(3),june.current().line);
        ScoreVisit ada=visit(200,"2025-01-01",drug("阿达木单抗","2025-01-01",null));
        ScoreVisit pending=visit(300,"2025-05-01",drug("依那西普","2025-07-01",null));
        assertEquals(1,timeline(ada,pending).episodes.size()); assertNull(timeline(ada,pending).current().end);
        TreatmentTimeline july=TreatmentTimeline.build(Arrays.asList(ada,pending),LocalDate.of(2025,7,1),dictionary);
        assertEquals(2,july.episodes.size()); assertEquals(LocalDate.of(2025,6,30),july.episodes.get(0).end); assertEquals(LocalDate.of(2025,7,1),july.current().start);
        assertEquals(2,july.current().historyN); assertEquals("EXPLICIT",july.current().confidence);
        assertEquals(1L,TreatmentTimeline.build(Arrays.asList(ada,pending),LocalDate.of(2025,8,1),dictionary).current().summary(LocalDate.of(2025,8,1)).get("schemeDurationMonths"));
    }

    @Test void targetedExitLeavesTraditionalSegmentWhileAdjunctChangesKeepTargetedOrigin() {
        ScoreVisit source=visit(100,"2025-01-01",drug("甲氨蝶呤","2025-01-01",null),drug("阿达木单抗","2025-01-01","2025-04-30"));
        TreatmentTimeline april=TreatmentTimeline.build(Arrays.asList(source),LocalDate.of(2025,4,30),dictionary); assertEquals("TNFi",april.current().category);
        TreatmentTimeline june=timeline(source); assertEquals(2,june.episodes.size()); assertEquals("csDMARD",june.current().category); assertEquals(Integer.valueOf(1),june.current().line); assertEquals(1,june.current().historyN);
        assertNull(june.current().start); assertEquals(LocalDate.of(2025,5,1),june.current().estimatedStart); assertEquals(1L,june.current().summary(asOf).get("schemeDurationMonths"));
        ScoreVisit adjunct=visit(200,"2025-01-01",drug("阿达木单抗","2025-01-01",null),drug("甲氨蝶呤","2025-03-01","2025-04-30"));
        TreatmentTimeline remaining=timeline(adjunct); assertEquals(1,remaining.episodes.size()); assertEquals(LocalDate.of(2025,1,1),remaining.current().start);
        assertEquals("EXPLICIT",remaining.current().confidence); assertEquals(5L,remaining.current().summary(asOf).get("schemeDurationMonths"));
        assertEquals(Integer.valueOf(2),remaining.current().line); assertEquals(1,remaining.current().historyN);
    }

    @Test void unknownAndMalformedObservationsBlockOlderScheduledStartsAndStops() {
        ScoreVisit planned=visit(100,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31"),drug("依那西普","2025-07-01",null));
        for(ScoreVisit barrier:Arrays.asList(observation(200,"2025-04-01","not-json"),visit(200,"2025-04-01",drug("unmapped",null,null)))) {
            TreatmentEpisode value=TreatmentTimeline.build(Arrays.asList(planned,barrier),LocalDate.of(2025,8,1),dictionary).current();
            assertEquals("UNKNOWN",value.state); assertNull(value.line); assertNull(value.start); assertNull(value.estimatedStart); assertNull(value.summary(LocalDate.of(2025,8,1)).get("schemeDurationMonths"));
            assertEquals(1,value.historyN); assertFalse(value.matches("bio"));
        }
        ScoreVisit ended=visit(300,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31"));
        TreatmentEpisode empty=timeline(ended,observation(400,"2025-04-01","{}")).current(); assertEquals("NONE",empty.state); assertEquals(1,empty.historyN);
        ScoreVisit contradictory=visit(500,"2025-01-01",drug("阿达木单抗","2025-01-01","2025-05-31"),drug("阿达木单抗","2025-01-01","2025-04-30"));
        TreatmentEpisode uncertain=timeline(contradictory).current(); assertNull(uncertain.start); assertNull(uncertain.estimatedStart); assertNull(uncertain.end);
        assertTrue(uncertain.provenance.get("quality").toString().contains("DATE_CONFLICT"));
    }

}
