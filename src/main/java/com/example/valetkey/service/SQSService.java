package com.example.valetkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.HashMap;
import java.util.Map;

@Service
public class SQSService {

    private static final Logger log = LoggerFactory.getLogger(SQSService.class);

    @Autowired
    private SqsClient sqsClient;

    @Value("${aws.sqs.queue-url:}")
    private String queueUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendBackupMessage(Long resourceId, String objectKey, Long fileSize) {
        if (queueUrl == null || queueUrl.isEmpty()) {
            log.warn("SQS queue URL not configured. Skipping backup message.");
            return;
        }

        try {
            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("resourceId", resourceId);
            messageBody.put("objectKey", objectKey);
            messageBody.put("fileSize", fileSize);
            messageBody.put("timestamp", System.currentTimeMillis());

            String messageBodyJson = objectMapper.writeValueAsString(messageBody);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBodyJson)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);
            log.info("Backup message sent to SQS. MessageId: {}, ResourceId: {}, ObjectKey: {}",
                    response.messageId(), resourceId, objectKey);

        } catch (Exception e) {
            log.error("Failed to send backup message to SQS for resourceId: {}, objectKey: {}",
                    resourceId, objectKey, e);
            throw new RuntimeException("Failed to send backup message: " + e.getMessage(), e);
        }
    }
}

