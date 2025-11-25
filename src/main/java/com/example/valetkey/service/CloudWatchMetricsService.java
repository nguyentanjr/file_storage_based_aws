package com.example.valetkey.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CloudWatchMetricsService {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchMetricsService.class);

    @Autowired
    private CloudWatchClient cloudWatchClient;

    @Value("${aws.cloudwatch.namespace:ValetKey/Backup}")
    private String namespace;

    public void recordBackupLatency(String objectKey, double latencySeconds) {
        try {
            MetricDatum datum = MetricDatum.builder()
                    .metricName("BackupLatency")
                    .value(latencySeconds)
                    .unit(StandardUnit.SECONDS)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("ObjectKey")
                                    .value(objectKey)
                                    .build()
                    )
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.info("Recorded backup latency: {} seconds for {}", latencySeconds, objectKey);
        } catch (Exception e) {
            log.error("Failed to record backup latency metric: {}", e.getMessage(), e);
        }
    }

    public void recordBackupResult(String status, String objectKey) {
        try {
            double value = "COMPLETED".equals(status) ? 1.0 : 0.0;

            MetricDatum datum = MetricDatum.builder()
                    .metricName("BackupSuccess")
                    .value(value)
                    .unit(StandardUnit.NONE)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("Status")
                                    .value(status)
                                    .build()
                    )
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.info("Recorded backup result: {} for {}", status, objectKey);
        } catch (Exception e) {
            log.error("Failed to record backup result metric: {}", e.getMessage(), e);
        }
    }

    public void recordBackupThroughput(String objectKey, double megabytesPerSecond) {
        try {
            double bytesPerSecond = megabytesPerSecond * 1024.0 * 1024.0;
            
            MetricDatum datum = MetricDatum.builder()
                    .metricName("BackupThroughput")
                    .value(bytesPerSecond)
                    .unit(StandardUnit.BYTES_SECOND)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("ObjectKey")
                                    .value(objectKey)
                                    .build()
                    )
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.info("Recorded backup throughput: {} MB/s ({} bytes/s) for {}", 
                    megabytesPerSecond, bytesPerSecond, objectKey);
        } catch (Exception e) {
            log.error("Failed to record backup throughput metric: {}", e.getMessage(), e);
        }
    }

    public void recordCustomMetric(String metricName, double value, List<Dimension> dimensions) {
        try {
            MetricDatum datum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(value)
                    .unit(StandardUnit.NONE)
                    .timestamp(Instant.now())
                    .dimensions(dimensions)
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.debug("Recorded custom metric: {} = {}", metricName, value);
        } catch (Exception e) {
            log.error("Failed to record custom metric {}: {}", metricName, e.getMessage());
        }
    }
}

