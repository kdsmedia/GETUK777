package domain.exception.badrequest

class UnspecifiedRtpTypeException : BadRequestException("RTP type must be HOT or COLD")
