package domain.exception.badrequest

class BetCurrencyMismatchException : BadRequestException("Bet operation currency does not match the bet currency")
