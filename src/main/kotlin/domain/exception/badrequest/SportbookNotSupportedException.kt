package domain.exception.badrequest

class SportbookNotSupportedException(integration: String) :
    BadRequestException("Aggregator does not support sportbook: $integration")
