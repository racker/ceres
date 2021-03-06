package com.rackspace.ceres.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rackspace.ceres.app.config.AppProperties;
import com.rackspace.ceres.app.config.DownsampleProperties;
import com.rackspace.ceres.app.downsample.Aggregator;
import com.rackspace.ceres.app.model.Metadata;
import com.rackspace.ceres.app.model.QueryData;
import com.rackspace.ceres.app.model.QueryResult;
import com.rackspace.ceres.app.services.QueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebFlux;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ActiveProfiles(profiles = {"test", "query"})
@SpringBootTest(classes = {QueryController.class, AppProperties.class, RestWebExceptionHandler.class,
    DownsampleProperties.class})
@AutoConfigureWebTestClient
@AutoConfigureWebFlux
public class QueryControllerTest {

  @MockBean
  QueryService queryService;

  @Autowired
  private WebTestClient webTestClient;

  @Test
  public void testQueryApi() {

    Map<String, String> queryTags = Map.of("os", "linux", "deployment", "dev", "host", "h-1");

    Map<Instant, Double> values = Map.of(Instant.now(), 111.0);

    List<QueryResult> queryResults = List
        .of(new QueryResult().setData(new QueryData().setMetricName("cpu-idle").setTags(queryTags).setTenant("t-1")
            .setValues(values)).setMetadata(new Metadata().setAggregator(Aggregator.raw)));

    when(queryService.queryRaw(anyString(), anyString(), any(), any(), any()))
        .thenReturn(Flux.fromIterable(queryResults));

    Flux<QueryResult> result = webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/query")
            .queryParam("metricName", "cpu-idle")
            .queryParam("tag", "os=linux")
            .queryParam("start", "1d-ago")
            .queryParam("tenant", "t-1")
            .build())
        .exchange().expectStatus().isOk()
        .returnResult(QueryResult.class).getResponseBody();

    StepVerifier.create(result).assertNext(queryResult -> {
      assertThat(queryResult.getData()).isEqualTo(queryResults.get(0).getData());
      assertThat(queryResult.getMetadata().getAggregator()).isEqualTo(Aggregator.raw);
    }).verifyComplete();
  }

  @Test
  public void testQueryApiWithAggregator() {

    Map<String, String> queryTags = Map.of("os", "linux", "deployment", "dev", "host", "h-1");

    Map<Instant, Double> values = Map.of(Instant.now(), 111.0);

    List<QueryResult> queryResults = List
        .of(new QueryResult()
            .setData(
                new QueryData()
                    .setMetricName("cpu-idle")
                    .setTags(queryTags)
                    .setTenant("t-1")
                    .setValues(values))
            .setMetadata(
                new Metadata()
                    .setAggregator(Aggregator.min)
                    .setGranularity(Duration.ofMinutes(1))
                    .setStartTime(Instant.ofEpochSecond(1605611015))
                    .setEndTime(Instant.ofEpochSecond(1605697439))));

    when(queryService.queryDownsampled(anyString(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(Flux.fromIterable(queryResults));

    Flux<QueryResult> result = webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/query")
            .queryParam("tenant", "t-1")
            .queryParam("metricName", "cpu-idle")
            .queryParam("tag", "os=linux,deployment=dev,host=h-1,")
            .queryParam("start", "1605611015")
            .queryParam("end", "1605697439")
            .queryParam("aggregator", "min")
            .queryParam("granularity", "pt1m")
            .build())
        .exchange().expectStatus().isOk()
        .returnResult(QueryResult.class).getResponseBody();

    StepVerifier.create(result).assertNext(queryResult -> {
      assertThat(queryResult.getData()).isEqualTo(queryResults.get(0).getData());
      assertThat(queryResult.getMetadata()).isEqualTo(queryResults.get(0).getMetadata());
    }).verifyComplete();

    verify(queryService)
        .queryDownsampled("t-1", "cpu-idle", Aggregator.min, Duration.ofMinutes(1), queryTags,
            Instant.ofEpochSecond(1605611015), Instant.ofEpochSecond(1605697439));

    verifyNoMoreInteractions(queryService);
  }

  @Test
  public void testQueryApiWithAggregatorAndMissingGranularity() {

    Map<String, String> queryTags = Map.of("os", "linux", "deployment", "dev", "host", "h-1");

    Map<Instant, Double> values = Map.of(Instant.now(), 111.0);

    List<QueryResult> queryResults = List
        .of(new QueryResult()
            .setData(
                new QueryData()
                    .setMetricName("cpu-idle")
                    .setTags(queryTags)
                    .setTenant("t-1")
                    .setValues(values))
            .setMetadata(
                new Metadata()
                    .setAggregator(Aggregator.min)
                    .setGranularity(Duration.ofMinutes(1))
                    .setStartTime(Instant.ofEpochSecond(1605611015))
                    .setEndTime(Instant.ofEpochSecond(1605697439))));

    when(queryService.queryDownsampled(anyString(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(Flux.fromIterable(queryResults));

    Flux<QueryResult> result = webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/query")
            .queryParam("tenant", "t-1")
            .queryParam("metricName", "cpu-idle")
            .queryParam("tag", "os=linux,deployment=dev,host=h-1,")
            .queryParam("start", "1605611015")
            .queryParam("end", "1605697439")
            .queryParam("aggregator", "max")
            .build())
        .exchange().expectStatus().isOk()
        .returnResult(QueryResult.class).getResponseBody();

    StepVerifier.create(result).assertNext(queryResult -> {
      assertThat(queryResult.getData()).isEqualTo(queryResults.get(0).getData());
      assertThat(queryResult.getMetadata()).isEqualTo(queryResults.get(0).getMetadata());
    }).verifyComplete();

    verify(queryService)
        .queryDownsampled("t-1", "cpu-idle", Aggregator.max, Duration.ofMinutes(2), queryTags,
            Instant.ofEpochSecond(1605611015), Instant.ofEpochSecond(1605697439));

    verifyNoMoreInteractions(queryService);
  }

  @Test
  public void testQueryApiWithNoTenantInHeaderAndParam() {

    webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/query")
            .queryParam("metricName", "cpu-idle")
            .queryParam("tag", "os=linux")
            .queryParam("start", "1d-ago")
            .build())
        .exchange().expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.status").isEqualTo(400);

    verifyNoInteractions(queryService);
  }
}
