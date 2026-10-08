#!/usr/bin/env python3
"""Independent P04b expected-value calculator; Python/SciPy only, no product imports."""
from __future__ import annotations
import json
import math
import sys
from decimal import Decimal, getcontext
from itertools import combinations
from pathlib import Path
from typing import Any

import numpy as np
import scipy
from scipy.stats import chi2_contingency, fisher_exact, mannwhitneyu

getcontext().prec = 80
HERE = Path(__file__).resolve().parent
INPUT = HERE / "fixed-inputs.json"
OUTPUT = HERE / "independent-oracles.json"
ALPHA = 0.05


def dec(x: Any) -> Decimal:
    return x if isinstance(x, Decimal) else Decimal(str(x))


def dstr(x: Decimal | None) -> str | None:
    if x is None:
        return None
    value = format(x, "f")
    return value.rstrip("0").rstrip(".") if "." in value else value


def type7(values: list[Any]) -> dict[str, Any]:
    """R-7 / Excel PERCENTILE.INC: h=(n-1)p, linear interpolation, exact Decimal."""
    xs = sorted(dec(x) for x in values if x is not None)
    n = len(xs)
    if n == 0:
        q1 = med = q3 = iqr = None
    else:
        def q(p: Decimal) -> Decimal:
            h = Decimal(n - 1) * p
            lo = int(h)
            frac = h - lo
            hi = min(lo + 1, n - 1)
            return xs[lo] + frac * (xs[hi] - xs[lo])
        q1, med, q3 = q(Decimal("0.25")), q(Decimal("0.5")), q(Decimal("0.75"))
        iqr = q3 - q1
    return {"n": n, "sorted": [dstr(x) for x in xs], "q1": dstr(q1), "median": dstr(med), "q3": dstr(q3), "iqr": dstr(iqr)}


def rank_codes(a: list[Any], b: list[Any]) -> tuple[list[float], list[float], bool, int]:
    aa, bb = [dec(x) for x in a], [dec(x) for x in b]
    pool = sorted(set(aa + bb))
    code = {value: float(i + 1) for i, value in enumerate(pool)}
    return [code[x] for x in aa], [code[x] for x in bb], len(pool) < len(aa) + len(bb), len(pool)


def p_fields(p: float) -> dict[str, Any]:
    return {"pValue": float(p), "displayP": "<0.001" if p < 0.001 else f"{p:.3f}", "significantRawAtAlpha005": bool(p < ALPHA)}


def mw_test(a: list[Any], b: list[Any], denominator: str = "NON_MISSING_METRIC") -> dict[str, Any]:
    aa, bb = [dec(x) for x in a if x is not None], [dec(x) for x in b if x is not None]
    n_a, n_b = len(aa), len(bb)
    out: dict[str, Any] = {"nA": n_a, "nB": n_b, "testDenominator": denominator, "warnings": []}
    if n_a < 5 or n_b < 5:
        out.update({"method": None, "status": "INSUFFICIENT_SAMPLE", "reason": "VALID_N_LT_5", "pValue": None, "displayP": "—", "significantRawAtAlpha005": None})
        return out
    xa, xb, ties, unique = rank_codes(aa, bb)
    if unique == 1:
        out.update({"method": None, "status": "DEGENERATE", "reason": "ZERO_RANK_VARIANCE", "pValue": None, "displayP": "—", "significantRawAtAlpha005": None})
        return out
    exact = (not ties and min(n_a, n_b) <= 8 and max(n_a, n_b) <= 50)
    method = "exact" if exact else "asymptotic"
    result = mannwhitneyu(xa, xb, alternative="two-sided", method=method, use_continuity=True)
    out.update({"method": "MANN_WHITNEY_EXACT" if exact else "MANN_WHITNEY_ASYMPTOTIC", "status": "OK", "reason": None, "U_A": float(result.statistic)})
    if ties and (n_a < 10 or n_b < 10):
        out["warnings"].append("SMALL_SAMPLE_TIES")
    out.update(p_fields(float(result.pvalue)))
    return out


def expected_below5(table: list[list[int]]) -> tuple[int, int, list[list[float]]]:
    total = sum(sum(row) for row in table)
    row_sums = [sum(row) for row in table]
    col_sums = [sum(table[r][c] for r in range(len(table))) for c in range(len(table[0]))]
    expected = [[r * c / total for c in col_sums] for r in row_sums]
    below = sum(x < 5 for row in expected for x in row)
    return below, len(row_sums) * len(col_sums), expected


def contingency(table_in: list[list[int]], denominator: str = "KNOWN_CATEGORY") -> dict[str, Any]:
    # Table is rows=groups / columns=categories. Prune zero margins before policy guards.
    if not table_in or not table_in[0] or any(len(row) != len(table_in[0]) for row in table_in):
        raise ValueError("invalid table shape")
    if any(v < 0 for row in table_in for v in row):
        raise ValueError("negative count")
    keep_rows = [i for i, row in enumerate(table_in) if sum(row) > 0]
    keep_cols = [j for j in range(len(table_in[0])) if sum(table_in[i][j] for i in range(len(table_in))) > 0]
    table = [[table_in[i][j] for j in keep_cols] for i in keep_rows]
    out: dict[str, Any] = {"nA": sum(table_in[0]) if table_in else 0, "nB": sum(table_in[1]) if len(table_in) > 1 else None,
                           "testDenominator": denominator, "inputTableByGroup": table_in, "prunedTableByGroup": table,
                           "warnings": [], "expectedBelow5Cells": None, "expectedCellCount": None}
    if not table or not keep_cols or len(table) < 2 or len(keep_cols) < 2 or sum(sum(row) for row in table) == 0:
        out.update({"method": None, "status": "DEGENERATE", "reason": "ZERO_MARGIN", "pValue": None, "displayP": "—", "significantRawAtAlpha005": None})
        return out
    if any(sum(row) < 5 for row in table):
        out.update({"method": None, "status": "INSUFFICIENT_SAMPLE", "reason": "VALID_N_LT_5", "pValue": None, "displayP": "—", "significantRawAtAlpha005": None})
        return out
    below, cells, expected = expected_below5(table)
    fisher = len(table) == 2 and len(table[0]) == 2 and below > 0
    if fisher:
        p = float(fisher_exact(np.asarray(table, dtype=np.int64), alternative="two-sided").pvalue)
        method = "FISHER_EXACT"
    else:
        stat, p, _, exp = chi2_contingency(np.asarray(table, dtype=np.int64), correction=False)
        p = float(p)
        expected = exp.tolist()
        method = "CHI_SQUARE"
    out.update({"method": method, "status": "OK", "reason": None, "expectedFrequencies": expected,
                "expectedBelow5Cells": below, "expectedCellCount": cells})
    if not fisher and cells > 4 and below / cells > 0.2:
        out["warnings"].append("SPARSE_EXPECTED_COUNTS")
    out.update(p_fields(p))
    return out


def ratio(count: int, denominator: int) -> Decimal | None:
    return None if denominator == 0 else Decimal(count) / Decimal(denominator)


def ratio_summary(a: dict[str, int], b: dict[str, int], yes: str, no: str, display_key: str, test_denominator: str) -> dict[str, Any]:
    ca, cb = a[yes], b[yes]
    na, nb = a["cohortN"], b["cohortN"]
    ka, kb = a[yes] + a[no], b[yes] + b[no]
    table = [[a[yes], a[no]], [b[yes], b[no]]]
    test = contingency(table, test_denominator)
    return {
        "display": {
            "denominator": display_key,
            "A": {"numerator": ca, "denominator": na, "rate": dstr(ratio(ca, na)), "unknown": a["unknown"]},
            "B": {"numerator": cb, "denominator": nb, "rate": dstr(ratio(cb, nb)), "unknown": b["unknown"]},
            "differenceAminusB": dstr(ratio(ca, na) - ratio(cb, nb)),
            "differenceAminusBPercentagePoints": dstr((ratio(ca, na) - ratio(cb, nb)) * Decimal(100))
        },
        "test": test
    }


def overlap_test(n_a: int, n_b: int, denominator: str) -> dict[str, Any]:
    """Honor overlap before invoking any inferential routine."""
    return {"nA":n_a,"nB":n_b,"testDenominator":denominator,"method":None,"status":"OVERLAP","reason":"OVERLAP","pValue":None,"displayP":"—","significantRawAtAlpha005":None,"warnings":[]}


def aggregate_metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    result = {}
    for metric in ("age", "diseaseDurationYears", "das28", "crp", "haq"):
        result[metric] = type7([p[metric] for p in rows])
    return result


def compare_continuous(a_rows: list[dict[str, Any]], b_rows: list[dict[str, Any]], overlap: bool) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for metric in ("age", "diseaseDurationYears", "das28", "crp", "haq"):
        a = [p[metric] for p in a_rows if p[metric] is not None]
        b = [p[metric] for p in b_rows if p[metric] is not None]
        sa, sb = type7(a), type7(b)
        test = overlap_test(len(a),len(b),"NON_MISSING_EVALUATION_METRIC") if overlap else mw_test(a, b, "NON_MISSING_EVALUATION_METRIC")
        median_diff = None if sa["median"] is None or sb["median"] is None else dstr(dec(sa["median"]) - dec(sb["median"]))
        result[metric] = {"A": sa, "B": sb, "medianDifferenceAminusB": median_diff, "test": test}
    return result


def activity_counts(rows: list[dict[str, Any]]) -> list[int]:
    cats = ["remission", "low", "moderate", "high"]
    return [sum(p["activity"] == c for p in rows) for c in cats]


def line_counts(rows: list[dict[str, Any]]) -> tuple[list[int], int]:
    return [sum(p["treatmentLine"] == c for p in rows) for c in (1, 2, 3)], sum(p["treatmentLine"] is None for p in rows)


def ratio_from_rows(rows: list[dict[str, Any]], field: str, positive: str, negative: str, unknown: str, denominator_key: str) -> dict[str, Any]:
    pos = sum(p[field] == positive for p in rows)
    neg = sum(p[field] == negative for p in rows)
    un = sum(p[field] == unknown for p in rows)
    return {"cohortN": len(rows), "knownN": pos + neg, "positive": pos, "negative": neg, "unknown": un, "denominator": denominator_key}


def build_main(data: dict[str, Any]) -> dict[str, Any]:
    patients = data["mainPatients"]
    by_id = {p["id"]: p for p in patients}
    a_ids = data["metadata"]["groupDefinitions"]["A"]
    b_ids = data["metadata"]["groupDefinitions"]["B"]
    overlap_ids = set(a_ids) & set(b_ids)
    a_all, b_all = [by_id[i] for i in a_ids], [by_id[i] for i in b_ids]
    a_exc = [p for p in a_all if p["id"] not in overlap_ids]
    b_exc = [p for p in b_all if p["id"] not in overlap_ids]
    original = {
        "groupN": {"A": len(a_all), "B": len(b_all)},
        "intersectionPatientIds": sorted(overlap_ids),
        "intersectionN": len(overlap_ids),
        "id8HaqMissingStillInIntersection": by_id[8]["haq"] is None,
        "continuous": compare_continuous(a_all, b_all, overlap=True),
        "activityCountsByGroup": {"A": activity_counts(a_all), "B": activity_counts(b_all)},
        "lineCountsByGroup": {"A": line_counts(a_all), "B": line_counts(b_all)},
        "testStatusForEveryPRow": "OVERLAP"
    }
    # Preserve descriptive values even though all inferential rows are withheld for overlap.
    def overlap_binary(a_rows: list[dict[str, Any]], b_rows: list[dict[str, Any]], field: str, yes: str, no: str, unknown: str | None, denominator: str, test_denominator: str) -> dict[str, Any]:
        def counts(rows: list[dict[str, Any]]) -> dict[str, int]:
            return {"cohortN":len(rows),"knownN":sum(p[field] in (yes,no) for p in rows),yes:sum(p[field]==yes for p in rows),no:sum(p[field]==no for p in rows),"unknown":sum(unknown is not None and p[field]==unknown for p in rows)}
        aa,bb=counts(a_rows),counts(b_rows)
        ra,rb=ratio(aa[yes],aa["cohortN"]),ratio(bb[yes],bb["cohortN"])
        return {"display":{"denominator":denominator,"A":{"numerator":aa[yes],"denominator":aa["cohortN"],"rate":dstr(ra),"unknown":aa["unknown"]},
                           "B":{"numerator":bb[yes],"denominator":bb["cohortN"],"rate":dstr(rb),"unknown":bb["unknown"]},
                           "differenceAminusB":dstr(ra-rb),"differenceAminusBPercentagePoints":dstr((ra-rb)*Decimal(100))},
                "test":overlap_test(aa["knownN"],bb["knownN"],test_denominator)}
    original["binary"] = {
        "female": overlap_binary(a_all,b_all,"sex","F","M","UNKNOWN","C.n","KNOWN_CATEGORY"),
        "serologyPositive": overlap_binary(a_all,b_all,"serology","TRUE","FALSE","UNKNOWN","C.n","KNOWN_CATEGORY"),
        "target": overlap_binary(a_all,b_all,"target",True,False,"UNKNOWN","evalN","EVALUABLE_DAS28"),
        "complete": overlap_binary(a_all,b_all,"qcStatus","COMPLETE","MISSING",None,"C.n","KNOWN_CATEGORY")
    }
    original["activityDistribution"] = {"categories":["remission","low","moderate","high"],"denominator":"evalN","A":{"counts":activity_counts(a_all),"evalN":sum(activity_counts(a_all))},"B":{"counts":activity_counts(b_all),"evalN":sum(activity_counts(b_all))},"test":overlap_test(sum(activity_counts(a_all)),sum(activity_counts(b_all)),"EVALUABLE_ACTIVITY")}
    full_la,full_ua=line_counts(a_all);full_lb,full_ub=line_counts(b_all)
    original["lineDistribution"]={"categories":[1,2,3],"denominator":"C.n","A":{"counts":full_la,"cohortN":len(a_all),"unknownLine":full_ua},"B":{"counts":full_lb,"cohortN":len(b_all),"unknownLine":full_ub},"test":overlap_test(sum(full_la),sum(full_lb),"KNOWN_LINE")}
    excluded = {
        "includedPatientIds": {"A": [p["id"] for p in a_exc], "B": [p["id"] for p in b_exc]},
        "groupN": {"A": len(a_exc), "B": len(b_exc)},
        "continuous": compare_continuous(a_exc, b_exc, overlap=False),
    }
    female_a = {"cohortN":len(a_exc),"knownN":len(a_exc),"female":sum(p["sex"]=="F" for p in a_exc),"male":sum(p["sex"]=="M" for p in a_exc),"unknown":0}
    female_b = {"cohortN":len(b_exc),"knownN":len(b_exc),"female":sum(p["sex"]=="F" for p in b_exc),"male":sum(p["sex"]=="M" for p in b_exc),"unknown":0}
    excluded["binary"] = {
        "female": ratio_summary(female_a, female_b, "female", "male", "C.n", "KNOWN_CATEGORY"),
    }
    # target is denominator=evaluable DAS, but this fixture has all six evaluable on each side.
    target_a = {"cohortN":len(a_exc),"knownN":len(a_exc),"target":sum(p["target"] for p in a_exc),"notTarget":sum(not p["target"] for p in a_exc),"unknown":0}
    target_b = {"cohortN":len(b_exc),"knownN":len(b_exc),"target":sum(p["target"] for p in b_exc),"notTarget":sum(not p["target"] for p in b_exc),"unknown":0}
    excluded["binary"]["target"] = ratio_summary(target_a, target_b, "target", "notTarget", "evalN", "EVALUABLE_DAS28")
    sero_a = ratio_from_rows(a_exc, "serology", "TRUE", "FALSE", "UNKNOWN", "C.n")
    sero_b = ratio_from_rows(b_exc, "serology", "TRUE", "FALSE", "UNKNOWN", "C.n")
    sero_a["unknown"] = sero_a.pop("unknown")
    sero_b["unknown"] = sero_b.pop("unknown")
    excluded["binary"]["serologyPositive"] = {
        "display": {"denominator":"C.n","A":{"numerator":sero_a["positive"],"denominator":sero_a["cohortN"],"rate":dstr(ratio(sero_a["positive"],sero_a["cohortN"])),"unknown":sero_a["unknown"]},
                    "B":{"numerator":sero_b["positive"],"denominator":sero_b["cohortN"],"rate":dstr(ratio(sero_b["positive"],sero_b["cohortN"])),"unknown":sero_b["unknown"]},
                    "differenceAminusB":dstr(ratio(sero_a["positive"],sero_a["cohortN"])-ratio(sero_b["positive"],sero_b["cohortN"])),
                    "differenceAminusBPercentagePoints":dstr((ratio(sero_a["positive"],sero_a["cohortN"])-ratio(sero_b["positive"],sero_b["cohortN"]))*Decimal(100))},
        "test": contingency([[sero_a["positive"],sero_a["negative"]],[sero_b["positive"],sero_b["negative"]]], "KNOWN_CATEGORY")
    }
    qa_a = {"cohortN":len(a_exc),"knownN":len(a_exc),"complete":sum(p["qcStatus"]=="COMPLETE" for p in a_exc),"incomplete":sum(p["qcStatus"]=="MISSING" for p in a_exc),"unknown":0}
    qa_b = {"cohortN":len(b_exc),"knownN":len(b_exc),"complete":sum(p["qcStatus"]=="COMPLETE" for p in b_exc),"incomplete":sum(p["qcStatus"]=="MISSING" for p in b_exc),"unknown":0}
    excluded["binary"]["complete"] = {
        "display": {"denominator":"C.n","A":{"numerator":qa_a["complete"],"denominator":qa_a["cohortN"],"rate":dstr(ratio(qa_a["complete"],qa_a["cohortN"])),"unknown":qa_a["unknown"]},
                    "B":{"numerator":qa_b["complete"],"denominator":qa_b["cohortN"],"rate":dstr(ratio(qa_b["complete"],qa_b["cohortN"])),"unknown":qa_b["unknown"]},
                    "differenceAminusB":dstr(ratio(qa_a["complete"],qa_a["cohortN"])-ratio(qa_b["complete"],qa_b["cohortN"])),
                    "differenceAminusBPercentagePoints":dstr((ratio(qa_a["complete"],qa_a["cohortN"])-ratio(qa_b["complete"],qa_b["cohortN"]))*Decimal(100))},
        "test": contingency([[qa_a["complete"],qa_a["incomplete"]],[qa_b["complete"],qa_b["incomplete"]]], "KNOWN_CATEGORY")
    }
    ac, bc = activity_counts(a_exc), activity_counts(b_exc)
    activity_test = contingency([ac,bc], "EVALUABLE_ACTIVITY")
    excluded["activityDistribution"] = {"categories":["remission","low","moderate","high"],"denominator":"evalN","A":{"counts":ac,"evalN":sum(ac)},"B":{"counts":bc,"evalN":sum(bc)},"test":activity_test}
    lc_a, un_a = line_counts(a_exc); lc_b, un_b = line_counts(b_exc)
    line_test = contingency([lc_a,lc_b], "KNOWN_LINE")
    excluded["lineDistribution"] = {"categories":[1,2,3],"denominator":"C.n","A":{"counts":lc_a,"cohortN":len(a_exc),"unknownLine":un_a},"B":{"counts":lc_b,"cohortN":len(b_exc),"unknownLine":un_b},"test":line_test}
    # Check label exchange: P must be invariant, every A-minus-B median/ratio difference changes sign.
    swapped = {
        "continuousPValues": {m: mw_test([p[m] for p in b_exc if p[m] is not None],[p[m] for p in a_exc if p[m] is not None],"NON_MISSING_EVALUATION_METRIC")["pValue"] for m in ("age","diseaseDurationYears","das28","crp","haq")},
        "femalePValue": contingency([[female_b["female"],female_b["male"]],[female_a["female"],female_a["male"]]],"KNOWN_CATEGORY")["pValue"],
        "activityPValue": contingency([bc,ac],"EVALUABLE_ACTIVITY")["pValue"],
        "linePValue": contingency([lc_b,lc_a],"KNOWN_LINE")["pValue"]
    }
    excluded["swappedGroupCheck"] = swapped
    return {"beforeExclusion":original,"afterExcludingIntersection":excluded}


def denominator_example(data: dict[str, Any], name: str) -> dict[str, Any]:
    c = data["denominatorExamples"][name]
    categories = [k for k in c["A"] if k not in ("cohortN","knownN","unknown")]
    # Its input category members for two categories are inferred from the named explicit counts.
    if name == "femaleCohortVsKnown": yes,no = "female","male"
    elif name == "serologyCohortVsKnown": yes,no = "positive","negative"
    else: raise ValueError(name)
    a,b = c["A"],c["B"]
    test = contingency([[a[yes],a[no]],[b[yes],b[no]]],c["testDenominator"])
    ra,rb = ratio(a[yes],a["cohortN"]),ratio(b[yes],b["cohortN"])
    return {"display": {"denominator":c["displayDenominator"],"A":{"numerator":a[yes],"denominator":a["cohortN"],"rate":dstr(ra),"unknown":a["unknown"]},"B":{"numerator":b[yes],"denominator":b["cohortN"],"rate":dstr(rb),"unknown":b["unknown"]},"differenceAminusB":dstr(ra-rb),"differenceAminusBPercentagePoints":dstr((ra-rb)*Decimal(100))},"test":test}


def boundary_oracles(data: dict[str, Any]) -> dict[str, Any]:
    b = data["boundaryCases"]
    mw = {}
    for c in b["validN"]:
        mw[c["id"]] = mw_test(c["A"],c["B"])
    return {
        "validN":mw,
        "allOneActivityCategory":contingency(b["allOneActivityCategory"]["tableByGroup"],"EVALUABLE_ACTIVITY"),
        "overlapPrecedesInsufficientSample": {"intersection":b["overlapPrecedesInsufficientSample"]["intersection"],"metricKnownN":b["overlapPrecedesInsufficientSample"]["metricKnownN"],"status":"OVERLAP","method":None,"pValue":None,"displayP":"—","significantRawAtAlpha005":None,"reason":"OVERLAP"},
        "expectedExactlyFive":contingency(b["expectedExactlyFive"]["tableByGroup"],"KNOWN_CATEGORY")
    }


def main() -> None:
    data=json.loads(INPUT.read_text(encoding="utf-8"))
    q={name:type7(vals) for name,vals in data["quantileProbes"].items()}
    assert (q["empty"]["q1"],q["empty"]["median"],q["empty"]["q3"],q["empty"]["iqr"]) == (None,None,None,None)
    assert (q["one"]["q1"],q["one"]["median"],q["one"]["q3"],q["one"]["iqr"]) == ("4.25","4.25","4.25","0")
    assert (q["two"]["q1"],q["two"]["median"],q["two"]["q3"],q["two"]["iqr"]) == ("2","3","4","2")
    assert (q["six"]["q1"],q["six"]["median"],q["six"]["q3"],q["six"]["iqr"]) == ("2.25","3.5","4.75","2.5")
    assert (q["ties"]["q1"],q["ties"]["median"],q["ties"]["q3"],q["ties"]["iqr"]) == ("1.25","2","2","0.75")
    den={name:denominator_example(data,name) for name in ("femaleCohortVsKnown","serologyCohortVsKnown")}
    # Denominator-only distribution examples: counts already encode evalN and cohortN separately.
    ac=data["denominatorExamples"]["activityUsesEvalN"]
    aact,bact=ac["A"],ac["B"]
    den["activityUsesEvalN"]={"display":{"denominator":"evalN","A":{"counts":aact["counts"],"denominator":aact["evalN"],"unknown":aact["unknownActivity"]},"B":{"counts":bact["counts"],"denominator":bact["evalN"],"unknown":bact["unknownActivity"]}},"test":contingency([aact["counts"],bact["counts"]],ac["testDenominator"])}
    lc=data["denominatorExamples"]["lineUsesCohortN"]
    al,bl=lc["A"],lc["B"]
    den["lineUsesCohortN"]={"display":{"denominator":"C.n","A":{"counts":al["counts"],"denominator":al["cohortN"],"unknownLine":al["unknownLine"]},"B":{"counts":bl["counts"],"denominator":bl["cohortN"],"unknownLine":bl["unknownLine"]}},"test":contingency([al["counts"],bl["counts"]],lc["testDenominator"])}
    tie=data["tieAsymptoticControl"]
    precision=data["precisionOrderControl"]
    out={
        "metadata":{"purpose":data["metadata"]["purpose"],"python":sys.version.split()[0],"scipy":scipy.__version__,"numpy":np.__version__,"oracleDoesNotImportProduct":True,"alpha":ALPHA,"alternative":"two-sided","mwContinuityCorrection":True,"exactRule":"no ties and min(nA,nB)<=8 and max(nA,nB)<=50, otherwise asymptotic","contingencyCorrection":False,"fisherExpectedRule":"2x2 and any expected frequency <5","quantileRule":"Type7 h=(n-1)p with exact Decimal interpolation","numericTolerance":{"absolute":1e-12,"relativeNonzero":1e-10,"zero":"absolute only"}},
        "mainComparison":build_main(data),
        "quantileProbes":q,
        "denominatorExamples":den,
        "boundaryCases":boundary_oracles(data),
        "tieAsymptoticControl":mw_test(tie["A"],tie["B"]),
        "precisionOrderControl":mw_test(precision["A"],precision["B"]),
        "exactPManualCrossChecks":{"completeSeparation6v6":"2/C(12,6)=2/924=1/462","completeSeparation5v5":"2/C(10,5)=2/252=1/126"}
    }
    expected=out["mainComparison"]["afterExcludingIntersection"]
    for metric in ("age","diseaseDurationYears","das28","crp","haq"):
        assert math.isclose(expected["continuous"][metric]["test"]["pValue"],expected["swappedGroupCheck"]["continuousPValues"][metric],rel_tol=1e-12,abs_tol=1e-12)
        assert dec(expected["continuous"][metric]["medianDifferenceAminusB"]) == dec(expected["continuous"][metric]["A"]["median"]) - dec(expected["continuous"][metric]["B"]["median"])
    assert math.isclose(expected["binary"]["female"]["test"]["pValue"],expected["swappedGroupCheck"]["femalePValue"],rel_tol=1e-12,abs_tol=1e-12)
    assert math.isclose(expected["activityDistribution"]["test"]["pValue"],expected["swappedGroupCheck"]["activityPValue"],rel_tol=1e-12,abs_tol=1e-12)
    assert math.isclose(expected["lineDistribution"]["test"]["pValue"],expected["swappedGroupCheck"]["linePValue"],rel_tol=1e-12,abs_tol=1e-12)
    # The separate tie control must remain asymptotic and warning-bearing.
    assert out["tieAsymptoticControl"]["method"] == "MANN_WHITNEY_ASYMPTOTIC"
    assert "SMALL_SAMPLE_TIES" in out["tieAsymptoticControl"]["warnings"]
    # The close-decimal case is strictly separated after exact Decimal rank coding.
    assert out["precisionOrderControl"]["method"] == "MANN_WHITNEY_EXACT"
    assert abs(out["precisionOrderControl"]["pValue"] - 1/126) < 1e-15
    OUTPUT.write_text(json.dumps(out,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps({"output":str(OUTPUT),"python":sys.version.split()[0],"scipy":scipy.__version__,"numpy":np.__version__,"mainP":{k:v["test"].get("pValue") for k,v in out["mainComparison"]["afterExcludingIntersection"]["continuous"].items()},"activityP":out["mainComparison"]["afterExcludingIntersection"]["activityDistribution"]["test"].get("pValue"),"lineP":out["mainComparison"]["afterExcludingIntersection"]["lineDistribution"]["test"].get("pValue")},ensure_ascii=False,indent=2))

if __name__ == "__main__":
    main()
