package com.example.valetkey.service;

import com.example.valetkey.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AWSS3Service {

    private static final Logger log = LoggerFactory.getLogger(AWSS3Service.class);

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name:valet-demo}")
    private String bucketName;

    @Value("${aws.s3.region:ap-southeast-1}")
    private String region;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    public String generatePresignedUploadUrl(String objectKey, int expiryMinutes, User user) {
        if (!user.isCreate() && !user.isWrite()) {
            throw new RuntimeException("User does not have permission to upload files");
        }

        try (S3Presigner presigner = createPresigner();
             S3Client s3 = S3Client.builder()
                     .region(Region.AP_SOUTHEAST_1)
                     .build()) {



            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(null)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);

            log.info("Generated presigned URL for key: {}", objectKey);
            log.debug("URL: {}", presignedRequest.url());

            return presignedRequest.url().toString();

        } catch (Exception e) {
            log.error("Error generating presigned upload URL for key: {}", objectKey, e);
            throw new RuntimeException("Failed to generate upload URL: " + e.getMessage(), e);
        }
    }

    public String generatePresignedDownloadUrl(String objectKey, int expiryMinutes, User user) {
        if (!user.isRead()) {
            throw new RuntimeException("User does not have permission to download files");
        }

        if (!objectExists(objectKey)) {
            throw new RuntimeException("File not found: " + objectKey);
        }

        try (S3Presigner presigner = createPresigner()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned download URL for key: {}", objectKey, e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    public void deleteObject(String objectKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.debug("Deleted object from S3: {}", objectKey);
        } catch (Exception e) {
            log.error("Error deleting object from S3: {}", objectKey, e);
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    public boolean objectExists(String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking object existence: {}", objectKey, e);
            return false;
        }
    }

    public List<String> listObjects(String prefix) {
        List<String> result = new ArrayList<>();
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            for (S3Object s3Object : listResponse.contents()) {
                result.add(s3Object.key());
            }
        } catch (Exception e) {
            log.error("Error listing objects with prefix: {}", prefix, e);
        }
        return result;
    }

    public InputStream getObjectInputStream(String objectKey) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            return s3Client.getObject(getRequest);
        } catch (Exception e) {
            log.error("Error getting object input stream: {}", objectKey, e);
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    public long getObjectContentLength(String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            HeadObjectResponse head = s3Client.headObject(headRequest);
            return head.contentLength();
        } catch (Exception e) {
            log.error("Error getting object content length: {}", objectKey, e);
            throw new RuntimeException("Failed to get content length: " + e.getMessage(), e);
        }
    }

    public void uploadObject(String objectKey, byte[] data, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
                    new ByteArrayInputStream(data), data.length));
            
            log.debug("Uploaded object to S3: {}", objectKey);
        } catch (Exception e) {
            log.error("Error uploading object to S3: {}", objectKey, e);
            throw new RuntimeException("Failed to upload object: " + e.getMessage(), e);
        }
    }

    public void uploadObject(String objectKey, Path sourcePath, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, sourcePath);
            log.debug("Uploaded object from path to S3: {}", objectKey);
        } catch (Exception e) {
            log.error("Error uploading object from path to S3: {}", objectKey, e);
            throw new RuntimeException("Failed to upload object: " + e.getMessage(), e);
        }
    }

    private S3Presigner createPresigner() {
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials awsCreds =
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey);
            return S3Presigner.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(awsCreds))
                    .build();
        } else {
            return S3Presigner.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .build();
        }
    }
}

