package com.wenwen.vo;

/**
 * 患者列表的扩展筛选 / 排序条件（都可不传；不传 = 原行为）。取值与校验见 PatientServiceImpl，接口说明见 docs/API.md §2.1。
 * 所有条件都在分页和 total 计数之前生效。
 */
public class PatientListFilter {

	/** 排序：created 建档时间倒序（默认）/ last 最近随访倒序 / das-desc、das-asc 最近 DAS28-CRP / code 研究编号升序 */
	private String sort;
	/** 性别：男 / 女 */
	private String sex;
	/** 年龄下限 / 上限（整周岁，含边界） */
	private Integer ageMin;
	private Integer ageMax;
	/** 年龄段「最小-最大」，如 18-39、70-120（含边界）；与 ageMin/ageMax 同时传时取交集 */
	private String ageBand;
	/** 最近 DAS28-CRP 活动度：remission / low / moderate / high / none（无有效评分） */
	private String activity;
	/** 其他病史：any 有记录 / none 无记录 / 病种编码（FM、AS、SS、RA-ILD、RA-MS） */
	private String comorbidity;
	/** 血清学分型：pos 血清阳性 / neg 血清阴性 / unknown 未提供 */
	private String serology;
	/** 建档时间：30 / 90 / 365 = 最近 N 天内建档；old = 超过 365 天 */
	private String created;
	/** 病情变化：better / stable / worse（首次与最近 DAS28-CRP 比较，±0.6）/ none（有效评分不足 2 次） */
	private String trend;

	public String cacheKey() {
		return sort + "|" + sex + "|" + ageMin + "|" + ageMax + "|" + ageBand + "|" + activity + "|" + comorbidity + "|" + serology + "|" + created + "|" + trend;
	}

	public String getSort() {
		return sort;
	}
	public void setSort(String sort) {
		this.sort = sort;
	}
	public String getSex() {
		return sex;
	}
	public void setSex(String sex) {
		this.sex = sex;
	}
	public Integer getAgeMin() {
		return ageMin;
	}
	public void setAgeMin(Integer ageMin) {
		this.ageMin = ageMin;
	}
	public Integer getAgeMax() {
		return ageMax;
	}
	public void setAgeMax(Integer ageMax) {
		this.ageMax = ageMax;
	}
	public String getAgeBand() {
		return ageBand;
	}
	public void setAgeBand(String ageBand) {
		this.ageBand = ageBand;
	}
	public String getActivity() {
		return activity;
	}
	public void setActivity(String activity) {
		this.activity = activity;
	}
	public String getComorbidity() {
		return comorbidity;
	}
	public void setComorbidity(String comorbidity) {
		this.comorbidity = comorbidity;
	}
	public String getSerology() {
		return serology;
	}
	public void setSerology(String serology) {
		this.serology = serology;
	}
	public String getCreated() {
		return created;
	}
	public void setCreated(String created) {
		this.created = created;
	}
	public String getTrend() {
		return trend;
	}
	public void setTrend(String trend) {
		this.trend = trend;
	}
}
