/*
 * Copyright 2020 Rackspace US, Inc.
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

package com.rackspace.ceres.app.services;

import static com.rackspace.ceres.app.services.DataTablesStatements.QUERY_RAW;
import static java.util.Objects.requireNonNull;

import com.datastax.oss.driver.api.core.cql.Row;
import com.rackspace.ceres.app.config.AppProperties;
import com.rackspace.ceres.app.downsample.Aggregator;
import com.rackspace.ceres.app.downsample.SingleValueSet;
import com.rackspace.ceres.app.downsample.ValueSet;
import com.rackspace.ceres.app.model.QueryResult;
import com.rackspace.ceres.app.utils.DateTimeUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.cql.ReactiveCqlTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class QueryService {

  private final ReactiveCqlTemplate cqlTemplate;
  private final MetadataService metadataService;
  private final DataTablesStatements dataTablesStatements;
  private final AppProperties appProperties;
  private final DateTimeUtils dateTimeUtils;

  @Autowired
  public QueryService(ReactiveCqlTemplate cqlTemplate,
                      MetadataService metadataService,
                      DataTablesStatements dataTablesStatements,
                      AppProperties appProperties,
                      DateTimeUtils dateTimeUtils) {
    this.cqlTemplate = cqlTemplate;
    this.metadataService = metadataService;
    this.dataTablesStatements = dataTablesStatements;
    this.appProperties = appProperties;
    this.dateTimeUtils = dateTimeUtils;
  }

  public Flux<QueryResult> queryRaw(String tenant, String metricName,
                                    Map<String, String> queryTags,
                                    Instant start, Instant end) {
    return metadataService.locateSeriesSetHashes(tenant, metricName, queryTags)
        .name("queryRaw")
        .metrics()
        .flatMapMany(Flux::fromIterable)
        .flatMap(seriesSet ->
            mapSeriesSetResult(tenant, seriesSet,
                // TODO use repository and projections
                cqlTemplate.queryForRows(
                    QUERY_RAW,
                    tenant, seriesSet, start, end
                )
            )
        );
  }

  public Flux<ValueSet> queryRawWithSeriesSet(String tenant, String seriesSet,
                                              Instant start, Instant end) {
    return cqlTemplate.queryForRows(
        QUERY_RAW,
        tenant, seriesSet, start, end
    )
        .retryWhen(appProperties.getRetryQueryForDownsample().build())
        .map(row ->
            new SingleValueSet().setValue(row.getDouble(1)).setTimestamp(row.getInstant(0))
        )
        .checkpoint();
  }

  public Flux<QueryResult> queryDownsampled(String tenant, String metricName, Aggregator aggregator,
                                            Duration granularity, Map<String, String> queryTags,
                                            Instant start, Instant end) {
    return metadataService.locateSeriesSetHashes(tenant, metricName, queryTags)
        .name("queryDownsampled")
        .metrics()
        .flatMapMany(Flux::fromIterable)
        .flatMap(seriesSet ->
            mapSeriesSetResult(tenant, seriesSet,
                cqlTemplate.queryForRows(
                    dataTablesStatements.queryDownsampled(granularity),
                    tenant, seriesSet, aggregator.name(), start, end
                )
            )
        )
        .checkpoint();
  }

  private Mono<QueryResult> mapSeriesSetResult(String tenant, String seriesSet, Flux<Row> rows) {
    return rows
        .map(row -> Map.entry(
            requireNonNull(row.getInstant(0)),
            row.getDouble(1)
            )
        )
        // collect the ts->value entries into an ordered, LinkedHashMap
        .collectMap(Entry::getKey, Entry::getValue, LinkedHashMap::new)
        .filter(values -> !values.isEmpty())
        .flatMap(values -> buildQueryResult(tenant, seriesSet, values));
  }

  private Mono<QueryResult> buildQueryResult(String tenant, String seriesSet,
                                             Map<Instant, Double> values) {
    return metadataService.resolveSeriesSetHash(tenant, seriesSet)
        .map(metricNameAndTags ->
            new QueryResult()
                .setTenant(tenant)
                .setMetricName(metricNameAndTags.getMetricName())
                .setTags(metricNameAndTags.getTags())
                .setValues(values)
        );
  }

  /**
   * Gets the start time based on the format in startTime.
   *
   * @param startTime
   * @return
   */
  public Instant getStartTime(String startTime) {
    if (dateTimeUtils.isValidInstantInstance(startTime)) {
      return Instant.parse(startTime);
    } else if (dateTimeUtils.isValidEpochMillis(startTime)) {
      return Instant.ofEpochMilli(Long.parseLong(startTime));
    } else if (dateTimeUtils.isValidEpochSeconds(startTime)) {
      return Instant.ofEpochSecond(Long.parseLong(startTime));
    } else {
      return dateTimeUtils.getAbsoluteTimeFromRelativeTime(startTime);
    }
  }

  /**
   * Gets the end time based on the format in endTime.
   *
   * @param endTime
   * @return
   */
  public Instant getEndTime(String endTime) {
    if(endTime==null) {
      return  Instant.now();
    }
    if (dateTimeUtils.isValidInstantInstance(endTime)) {
      return Instant.parse(endTime);
    } else if (dateTimeUtils.isValidEpochMillis(endTime)) {
      return Instant.ofEpochMilli(Long.parseLong(endTime));
    } else if (dateTimeUtils.isValidEpochSeconds(endTime)) {
      return Instant.ofEpochSecond(Long.parseLong(endTime));
    }
    throw new IllegalArgumentException("End time format is invalid");
  }
}
