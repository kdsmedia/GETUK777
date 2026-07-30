package infrastructure.s3

data class S3Config(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,

    /** Public host the bucket is served from. Blank → callers get the raw object key. */
    val cdnHost: String = ""
)
