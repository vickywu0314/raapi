package com.wenwen.ai.analysis;

import com.wenwen.ai.clinical.PatientClinical;
import com.wenwen.ai.qc.MissingDataStatus;
import com.wenwen.ai.source.ScoreVisit;
import com.wenwen.ai.treatment.TreatmentEpisode;

/** 一次U派生的必要事实；描述统计与名单共用相同评估选择。 */
public final class DerivedPatient {
    public final long id;
    public final PatientClinical clinical;
    public final MissingDataStatus qc;
    public final TreatmentEpisode treatment;
    public final ScoreVisit now,six,baseline,evaluation;
    public DerivedPatient(long id,PatientClinical clinical,MissingDataStatus qc,TreatmentEpisode treatment,
            ScoreVisit now,ScoreVisit six,ScoreVisit baseline,ScoreVisit evaluation) {
        this.id=id;this.clinical=java.util.Objects.requireNonNull(clinical);this.qc=java.util.Objects.requireNonNull(qc);
        this.treatment=java.util.Objects.requireNonNull(treatment);this.now=now;this.six=six;this.baseline=baseline;this.evaluation=evaluation;
    }
}
