package infrastructure.s3

import application.port.external.FileAdapter
import application.port.external.MediaFile
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import java.util.UUID

class S3FileAdapter(
    private val config: S3Config
) : FileAdapter {

    private val client = S3Client {
        region = config.region
        endpointUrl = Url.parse(config.endpoint)
        forcePathStyle = true
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = config.accessKey
            secretAccessKey = config.secretKey
        }
    }

    override suspend fun upload(folder: String, fileName: String, file: MediaFile): Result<String> = runCatching {
        val key = buildKey(folder, fileName, file.ext)

        client.putObject(PutObjectRequest {
            bucket = config.bucket
            this.key = key
            body = ByteStream.fromBytes(file.bytes)
            contentType = contentTypeFor(file.ext)
            cacheControl = CACHE_CONTROL
        })

        // The object KEY is what callers store — the public host that serves the bucket
        // (cdn.<domain>) is a deployment concern the consumer prepends, not ours.
        key
    }

    override suspend fun delete(path: String): Result<Boolean> = runCatching {
        client.deleteObject(DeleteObjectRequest {
            bucket = config.bucket
            key = path
        })
        true
    }

    /**
     * Objects are served straight from the bucket over cdn.<domain>, so the stored
     * Content-Type is what a browser sees. Without it S3 falls back to
     * application/octet-stream and clients get an image they may refuse to render.
     */
    private fun contentTypeFor(ext: String): String = when (ext.lowercase()) {
        "webp" -> "image/webp"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "avif" -> "image/avif"
        else -> "application/octet-stream"
    }

    private fun buildKey(folder: String, fileName: String, ext: String): String {
        val name = fileName.ifBlank { UUID.randomUUID().toString() }
        return if (ext.isNotBlank()) "$folder/$name.$ext" else "$folder/$name"
    }

    private companion object {
        /**
         * Objects live under a stable key (`<folder>/<identity>/<image key>`), so a long
         * browser cache is safe; replacing one means purging the CDN edge for that key.
         */
        const val CACHE_CONTROL = "public, max-age=86400"
    }
}
