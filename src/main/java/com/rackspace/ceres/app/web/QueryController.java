/*
 * Copyright 2021 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.ceres.app.web;

import static com.rackspace.ceres.app.web.TagListConverter.convertPairsListToMap;

import com.rackspace.ceres.app.config.DownsampleProperties;
import com.rackspace.ceres.app.downsample.Aggregator;
import com.rackspace.ceres.app.model.QueryResult;
import com.rackspace.ceres.app.model.TimeQuery;
import com.rackspace.ceres.app.model.TimeQueryData;
import com.rackspace.ceres.app.services.QueryService;
import com.rackspace.ceres.app.utils.DateTimeUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Native Ceres query API endpoints.
 */
@RestController
@RequestMapping("/api/query")
@Profile("query")
public class QueryController {

  private final QueryService queryService;
  private final DownsampleProperties downsampleProperties;

  @Autowired
  public QueryController(QueryService queryService, DownsampleProperties downsampleProperties) {
    this.queryService = queryService;
    this.downsampleProperties = downsampleProperties;
  }
  
	@PostMapping
	public Mono<ResponseEntity<?>> postGraf(@RequestBody TimeQueryData timeQueryData) {

//		timeQueryData.flatMap(data -> {
//	        log.debug("timeQueryData {}", data.getStart());
//			return Mono.just(data);
//		});

		System.out.println("Start: " + timeQueryData.getStart().toString());
		System.out.println("End: " + timeQueryData.getEnd().toString());

		List<TimeQuery> queries = timeQueryData.getQueries();
		for (TimeQuery query : queries) {
			System.out.println("Metric:" + query.getMetric());
			System.out.println("downsample:" + query.getDownsample());
			
			List<Map<String, String>> filters = query.getFilters();
			for (Map<String, String> filter : filters) {
				System.out.println("type:" + filter.get("type"));
				System.out.println("tagk:" + filter.get("tagk"));
				System.out.println("filter:" + filter.get("filter"));
			}		
		}

		return Mono.just(ResponseEntity.noContent().build());
	}

  @GetMapping
  public Flux<QueryResult> query(@RequestParam(name = "tenant") String tenantParam,
      @RequestParam String metricName,
      @RequestParam(defaultValue = "raw") Aggregator aggregator,
      @RequestParam(required = false) Duration granularity,
      @RequestParam List<String> tag,
      @RequestParam String start,
      @RequestParam(required = false) String end) {
    Instant startTime = DateTimeUtils.parseInstant(start);
    Instant endTime = DateTimeUtils.parseInstant(end);

    if (aggregator == null || Objects.equals(aggregator, Aggregator.raw)) {
      return queryService.queryRaw(tenantParam, metricName,
          convertPairsListToMap(tag),
          startTime, endTime
      );
    } else {
      if (granularity == null) {
        granularity = DateTimeUtils
            .getGranularity(startTime, endTime, downsampleProperties.getGranularities());
      }
      return queryService.queryDownsampled(tenantParam, metricName,
          aggregator,
          granularity,
          convertPairsListToMap(tag),
          startTime, endTime
      );
    }
  }
}
