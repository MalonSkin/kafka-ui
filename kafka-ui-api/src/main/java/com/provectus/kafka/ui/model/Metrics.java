package com.provectus.kafka.ui.model;

import static java.util.stream.Collectors.toMap;

import com.provectus.kafka.ui.service.metrics.RawMetric;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;


@Builder
@Value
public class Metrics {

  Map<Integer, BigDecimal> brokerBytesInPerSec;
  Map<Integer, BigDecimal> brokerBytesOutPerSec;
  Map<String, BigDecimal> topicBytesInPerSec;
  Map<String, BigDecimal> topicBytesOutPerSec;
  Map<Integer, List<RawMetric>> perBrokerMetrics;

  public static Metrics empty() {
    return Metrics.builder()
        .brokerBytesInPerSec(Map.of())
        .brokerBytesOutPerSec(Map.of())
        .topicBytesInPerSec(Map.of())
        .topicBytesOutPerSec(Map.of())
        .perBrokerMetrics(Map.of())
        .build();
  }

  // 汇总所有 broker 的 metrics，当 perBrokerMetrics 为空时返回空流，避免 NPE
  public Stream<RawMetric> getSummarizedMetrics() {
    if (perBrokerMetrics == null || perBrokerMetrics.isEmpty()) {
      return Stream.of();
    }
    return perBrokerMetrics.values().stream()
        .flatMap(Collection::stream)
        .collect(toMap(RawMetric::identityKey, m -> m, (m1, m2) -> m1.copyWithValue(m1.value().add(m2.value()))))
        .values()
        .stream();
  }

}
