package domain.exception.conflict

/**
 * A spin with the same external id is already committed. Raised by the unique constraint on
 * `spins.external_id`, which is the only place a concurrent redelivery of one provider
 * transaction can be caught — a read-then-insert check loses that race.
 */
class SpinAlreadyExistsException : ConflictException("Spin already exists")
