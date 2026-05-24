package infrastructure.aggregator.tongame

class TongameConfig(config: Map<String, Any>) {

    /** Provider REST base URL, e.g. `https://operator-api.djmgame.com`. Operator endpoints
     *  live under `<apiUrl>/api/v1/operator/...`. */
    val apiUrl = (config["apiUrl"]?.toString() ?: "").trimEnd('/')

    /** Operator identity registered with the provider — sent as the `X-Identity` header. */
    val operatorIdentity = config["operatorIdentity"]?.toString() ?: ""

    /** Operator API key — sent as the `X-Api-Key` header. */
    val apiKey = config["apiKey"]?.toString() ?: ""

    /** Base host for game frontends. Each game is served at `<gameSymbol>.<gameHost>`. */
    val gameHost = config["gameHost"]?.toString() ?: ""
}
