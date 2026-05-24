package infrastructure.aggregator.tongame

class TongameConfig(config: Map<String, Any>) {

    /** Provider gRPC server address (`host:port`) — SessionService/GameService live here. */
    val address = config["address"]?.toString() ?: ""

    /** Operator identity registered with the provider — sent as the `x-identity` header. */
    val operatorIdentity = config["operatorIdentity"]?.toString() ?: ""

    /** Operator API key — sent as the `x-api-key` header. slot.v1 has no separate secret. */
    val apiKey = config["apiKey"]?.toString() ?: ""

    val gameLaunchUrl = config["gameLaunchUrl"]?.toString() ?: ""

    val gameDemoLaunchUrl = config["gameDemoLaunchUrl"]?.toString() ?: ""
}
