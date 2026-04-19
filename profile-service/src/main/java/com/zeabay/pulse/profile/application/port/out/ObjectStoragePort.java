package com.zeabay.pulse.profile.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Output port for object storage operations (avatar upload/delete).
 *
 * <p>Isolates the application layer from the concrete storage SDK. Implemented by {@link
 * com.zeabay.pulse.profile.infrastructure.adapter.MinioStorageAdapter}.
 */
public interface ObjectStoragePort {

  /**
   * Generates a presigned PUT URL for direct client-to-storage upload.
   *
   * @param objectKey the S3 object key (e.g. "avatars/{keycloakId}/{uuid}.jpg")
   * @param contentType MIME type of the file
   * @return the presigned URL as a string
   */
  Mono<String> generatePresignedUploadUrl(String objectKey, String contentType);

  /**
   * Deletes an object from storage.
   *
   * @param objectKey the S3 object key to delete
   */
  Mono<Void> deleteObject(String objectKey);

  /**
   * Builds the public URL for an object.
   *
   * @param objectKey the S3 object key
   * @return the publicly accessible URL
   */
  String buildPublicUrl(String objectKey);
}
