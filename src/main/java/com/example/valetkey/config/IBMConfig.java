package com.example.valetkey.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class IBMConfig {

    @Value("${ibm.cos.endpoint:https://s3.us-south.cloud-object-storage.appdomain.cloud}")
    private String endpoint;

    @Value("${ibm.cos.region:us-south}")
    private String region;

    @Value("${ibm.cos.access-key:}")
    private String accessKey;

    @Value("${ibm.cos.secret-key:}")
    private String secretKey;

    @Bean(name = "ibmS3Client")
    public S3Client ibmS3Client() {
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}

