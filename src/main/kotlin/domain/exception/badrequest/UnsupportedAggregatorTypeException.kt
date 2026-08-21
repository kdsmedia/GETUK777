package domain.exception.badrequest

import domain.model.AggregatorType

class UnsupportedAggregatorTypeException(type: AggregatorType) :
    BadRequestException("CasinoProvider aggregator must be of type CASINO, got: $type")
