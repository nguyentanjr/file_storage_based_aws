package com.example.valetkey.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


public class BackupLambdaHandler implements RequestHandler<SQSEvent, String> {

    private static final Logger log = LoggerFactory.getLogger(BackupLambdaHandler.class);

    private static final String AWS_S3_BUCKET = "aws-cloud-int3319x";

    
    private static final String IBM_COS_BUCKET = "cloud-object-storage-cos-standard-p44";
    
    private static final String API_ENDPOINT = System.getenv("API_ENDPOINT") != null
        ? System.getenv("API_ENDPOINT") 
        : "http://localhost:8080";
    private static final String API_KEY = "";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private S3Client awsS3Client;
    private CloudWatchClient cloudWatchClient;
    private static final String CLOUDWATCH_NAMESPACE = "ValetKey/Backup";

    public BackupLambdaHandler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        initializeClients();
    }

    private void initializeClients() {
        String awsRegion = System.getenv("AWS_REGION");
        awsS3Client = S3Client.builder()
                .region(Region.of(awsRegion != null ? awsRegion : "ap-southeast-1"))
                .build();


        cloudWatchClient = CloudWatchClient.builder()
                .region(Region.of(awsRegion != null ? awsRegion : "ap-southeast-1"))
                .build();
        
        log.info("Initialized clients. AWS Region: {}, S3 Bucket: {}, IBM COS Bucket: {}", 
                awsRegion, AWS_S3_BUCKET, IBM_COS_BUCKET);
    }

    @Override
    public String handleRequest(SQSEvent sqsEvent, Context context) {
        log.info(" Received {} messages from SQS", sqsEvent.getRecords().size());
        
        int successCount = 0;
        int failureCount = 0;

        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            try {
                String messageBody = message.getBody();
                log.info("Processing message: {}", messageBody);

                Map<String, Object> backupJob = objectMapper.readValue(messageBody, Map.class);
                Long resourceId = Long.valueOf(backupJob.get("resourceId").toString());
                String objectKey = (String) backupJob.get("objectKey");
                Long fileSize = Long.valueOf(backupJob.get("fileSize").toString());

                log.info("Processing backup job - ResourceId: {}, ObjectKey: {}, FileSize: {} bytes", 
                        resourceId, objectKey, fileSize);

                long startTime = System.currentTimeMillis();
                boolean success = processBackupMetadata(resourceId, objectKey, fileSize);
                long duration = System.currentTimeMillis() - startTime;
                double latencySeconds = duration / 1000.0;

                if (success) {
                    successCount++;
                    log.info("Backup metadata processed successfully for {} in {} ms", objectKey, duration);
                    
                    recordMetric("BackupSuccess", 1.0, "Status", "PENDING_SYNC");
                    recordMetric("BackupLatency", latencySeconds, "ObjectKey", objectKey);
                    
                    if (fileSize > 0) {
                        double throughputMBps = (fileSize / 1024.0 / 1024.0) / latencySeconds;
                        recordMetric("BackupThroughput", throughputMBps, "ObjectKey", objectKey);
                    }
                    
                    log.info("CloudWatch metrics recorded. Status: PENDING_SYNC, Latency: {}s", latencySeconds);
                } else {
                    failureCount++;
                    log.error("Backup metadata processing failed for {}", objectKey);
                    recordMetric("BackupSuccess", 0.0, "Status", "FAILED");
                }

            } catch (Exception e) {
                failureCount++;
                log.error("Error processing SQS message: {}", e.getMessage(), e);
                recordMetric("BackupSuccess", 0.0, "Status", "FAILED");
            }
        }

        String result = String.format("Processed %d messages: %d success, %d failed", 
                sqsEvent.getRecords().size(), successCount, failureCount);
        log.info(result);
        return result;
    }

    private boolean processBackupMetadata(Long resourceId, String objectKey, Long fileSize) {
        try {
            log.info("Verifying S3 object exists: {}", objectKey);
            
            // Verify file exists in S3 and get metadata
            software.amazon.awssdk.services.s3.model.HeadObjectRequest headRequest = 
                software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(AWS_S3_BUCKET)
                    .key(objectKey)
                    .build();
            
            software.amazon.awssdk.services.s3.model.HeadObjectResponse headResponse = 
                awsS3Client.headObject(headRequest);
            
            long actualSize = headResponse.contentLength();
            String contentType = headResponse.contentType();
            
            log.info("S3 object verified - Size: {} bytes, ContentType: {}", actualSize, contentType);
            
            if (!fileSize.equals(actualSize)) {
                log.warn("File size mismatch. Expected: {}, Actual: {}", fileSize, actualSize);
            }

            boolean dbUpdated = updateDatabaseStatus(resourceId, "PENDING_SYNC", objectKey);
            
            if (dbUpdated) {
                log.info("Database updated: ResourceId={}, Status=PENDING_SYNC", resourceId);
                log.info("EC2 rclone cron will sync {} from S3 to IBM COS", objectKey);
                return true;
            } else {
                log.error("Failed to update database for ResourceId={}", resourceId);
                return false;
            }
            
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            log.error("File not found in S3: {}", objectKey);
            updateDatabaseStatus(resourceId, "FAILED", "File not found in S3");
            return false;
        } catch (Exception e) {
            log.error("Error processing backup metadata for {}: {}", objectKey, e.getMessage(), e);
            updateDatabaseStatus(resourceId, "FAILED", e.getMessage());
            return false;
        }
    }

    private boolean updateDatabaseStatus(Long resourceId, String status, String errorMessage) {
        try {
            // If API endpoint is not configured, skip database update (for testing)
            if (API_ENDPOINT == null || API_ENDPOINT.isEmpty()) {
                log.warn("API_ENDPOINT not configured. Skipping database update for ResourceId={}, Status={}", 
                        resourceId, status);
                return true; // Return true to not block Lambda execution
            }

            // Build request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("status", status);
            if (errorMessage != null && !errorMessage.isEmpty()) {
                requestBody.put("error", errorMessage);
            }

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            String url = API_ENDPOINT + "/api/internal/backup/" + resourceId + "/status";

            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            // Add API key if configured
            if (API_KEY != null && !API_KEY.isEmpty()) {
                requestBuilder.header("X-API-Key", API_KEY);
            }

            HttpRequest request = requestBuilder.build();

            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Database updated successfully - ResourceId: {}, Status: {}", resourceId, status);
                return true;
            } else {
                log.error("Failed to update database - ResourceId: {}, Status: {}, HTTP: {}, Response: {}", 
                        resourceId, status, response.statusCode(), response.body());
                return false;
            }

        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            log.error("Cannot connect to API endpoint {}: {}", API_ENDPOINT, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Failed to update database for ResourceId={}: {}", resourceId, e.getMessage(), e);
            return false;
        }
    }

    private boolean updateDatabaseStatus(Long resourceId, String status) {
        return updateDatabaseStatus(resourceId, status, null);
    }

    private void recordMetric(String metricName, double value, String dimensionName, String dimensionValue) {
        try {
            if (cloudWatchClient == null) {
                return;
            }
            MetricDatum datum = MetricDatum.builder()
                    .metricName(metricName)
                    .value(value)
                    .unit(StandardUnit.NONE)
                    .timestamp(java.time.Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name(dimensionName)
                                    .value(dimensionValue)
                                    .build()
                    )
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(CLOUDWATCH_NAMESPACE)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
        } catch (Exception e) {
            log.warn("Failed to record CloudWatch metric {}: {}", metricName, e.getMessage());
        }
    }
}

