package com.zeabay.pulse.profile.infrastructure.adapter;

import com.zeabay.common.logging.Loggable;
import com.zeabay.common.s3.ZeabayS3Properties;
import com.zeabay.pulse.profile.application.port.out.ObjectStoragePort;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Infrastructure adapter that implements {@link ObjectStoragePort} using MinIO via the AWS S3 SDK.
 *
 * <p>Handles presigned URL generation for direct client uploads, object deletion, and public URL
 * construction. Automatically creates the configured bucket on startup if it does not exist.
 *
 * <p>S3 client beans ({@link S3AsyncClient}, {@link S3Presigner}) and connection properties ({@link
 * ZeabayS3Properties}) are provided by the centralized {@code zeabay-s3} module.
 */
@Slf4j
@Loggable
@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements ObjectStoragePort {

  private final S3AsyncClient s3AsyncClient;
  private final S3Presigner s3Presigner;
  private final ZeabayS3Properties properties;

  /** Creates the avatar bucket on startup if it does not exist. */
  @PostConstruct
  void ensureBucketExists() {
    String bucket = properties.getBucket();
    try {
      s3AsyncClient.headBucket(HeadBucketRequest.builder().bucket(bucket).build()).join();
      log.info("[MinIO] Bucket '{}' already exists", bucket);
      applyPublicReadPolicy(bucket);
    } catch (Exception e) {
      if (hasCause(e, NoSuchBucketException.class)) {
        s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).join();
        log.info("[MinIO] Created bucket '{}'", bucket);
        applyPublicReadPolicy(bucket);
      } else {
        log.warn("[MinIO] Could not verify bucket '{}': {}", bucket, e.getMessage());
      }
    }
  }

  private void applyPublicReadPolicy(String bucket) {
    String policy =
        "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
            + bucket
            + "/*\"]}]}";
    try {
      s3AsyncClient
          .putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy(policy).build())
          .join();
      log.info("[MinIO] Successfully applied public-read policy to bucket '{}'", bucket);
    } catch (Exception e) {
      log.warn(
          "[MinIO] Failed to apply public-read policy to bucket '{}': {}", bucket, e.getMessage());
    }
  }

  @Override
  public Mono<String> generatePresignedUploadUrl(String objectKey, String contentType) {
    return Mono.fromCallable(
        () -> {
          PutObjectRequest putRequest =
              PutObjectRequest.builder()
                  .bucket(properties.getBucket())
                  .key(objectKey)
                  .contentType(contentType)
                  .build();

          PutObjectPresignRequest presignRequest =
              PutObjectPresignRequest.builder()
                  .putObjectRequest(putRequest)
                  .signatureDuration(Duration.ofSeconds(properties.getPresignedUrlExpirySeconds()))
                  .build();

          PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
          return presigned.url().toString();
        });
  }

  @Override
  public Mono<Void> deleteObject(String objectKey) {
    return Mono.fromFuture(
            () ->
                s3AsyncClient.deleteObject(
                    DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build()))
        .doOnSuccess(_ -> log.info("[MinIO] Deleted object: {}", objectKey))
        .then();
  }

  @Override
  public String buildPublicUrl(String objectKey) {
    return properties.getEndpoint() + "/" + properties.getBucket() + "/" + objectKey;
  }

  private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
    Throwable current = t;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
