package com.rackspace.ceres.app.model;

import java.time.Instant;
import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class TimeQueryData {
	@NotNull
	Instant start;

	Instant end;

	@NotEmpty
	List<TimeQuery> queries;

	public Instant getStart() {
		return start;
	}

	public void setStart(Instant start) {
		this.start = start;
	}

	public Instant getEnd() {
		return end;
	}

	public void setEnd(Instant end) {
		this.end = end;
	}

	public List<TimeQuery> getQueries() {
		return queries;
	}

	public void setQueries(List<TimeQuery> queries) {
		this.queries = queries;
	}
}
