package domain.exception.badrequest

class CasinoNotSupportedException(integration: String) :
    BadRequestException("Aggregator does not support casino: $integration")
