package com.example.valetkey.config;

import com.example.valetkey.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

@Configuration
public class AWSConfig {

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();
        } else {
            // Use default credentials provider (from environment, IAM role, etc.)
            return S3Client.builder()
                    .region(Region.of(region))
                    .build();
        }
    }

    @Bean
    public SqsClient sqsClient() {
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
            return SqsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();
        } else {
            return SqsClient.builder()
                    .region(Region.of(region))
                    .build();
        }
    }

    @Bean
    public CloudWatchClient cloudWatchClient() {
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
            return CloudWatchClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();
        } else {
            return CloudWatchClient.builder()
                    .region(Region.of(region))
                    .build();
        }
    }

    @Bean
    public CommandLineRunner initData(@Autowired UserService userService) {
        return args -> {
            // Create demo users for testing
            userService.createDemoUsers();

            System.out.println("=".repeat(50));
            System.out.println("Valet Key Demo Application Started");
            System.out.println("=".repeat(50));
            System.out.println("Demo users:");
            System.out.println("- Username: demo, Password: demo123");
            System.out.println("- Username: admin, Password: admin123");
            System.out.println("=".repeat(50));
            System.out.println("Access the application at: http://localhost:8080/");
            System.out.println("=".repeat(50));
        };
    }
}

