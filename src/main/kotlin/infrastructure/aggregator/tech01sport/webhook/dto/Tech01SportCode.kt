package infrastructure.aggregator.tech01sport.webhook.dto

/**
 * Response codes of the 01.tech partner API contract. Every webhook answers HTTP 200
 * with the status carried in the body `code`.
 */
object Tech01SportCode {
    const val SUCCESS = 0

    const val WRONG_TOKEN = 1

    const val NOT_ENOUGH_BALANCE = 2

    const val INTERNAL_ERROR = 3

    const val WRONG_SIGNATURE = 4

    const val VALIDATION_FAILED = 5

    const val NOT_FOUND_TRANSACTION = 7

    /** Opt-in `/get-user` code: the requested user does not exist. */
    const val USER_NOT_FOUND = 12
}
