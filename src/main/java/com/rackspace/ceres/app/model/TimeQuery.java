package com.rackspace.ceres.app.model;

import java.util.List;
import java.util.Map;

public class TimeQuery {
	String metric;
	String downsample;
	List<Map<String, String>> filters;

	public String getMetric() {
		return metric;
	}

	public void setMetric(String metric) {
		this.metric = metric;
	}

	public String getDownsample() {
		return downsample;
	}

	public void setDownsample(String downsample) {
		this.downsample = downsample;
	}

	public List<Map<String, String>> getFilters() {
		return filters;
	}

	public void setFilters(List<Map<String, String>> filters) {
		this.filters = filters;
	}
}
