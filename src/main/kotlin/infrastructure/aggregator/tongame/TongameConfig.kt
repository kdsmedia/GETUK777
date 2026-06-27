package infrastructure.aggregator.tongame

class TongameConfig(config: Map<String, Any>) {

    /** Provider REST base URL, e.g. `https://provider.example.com`. Endpoints live under
     *  `<apiUrl>/api/v1/...`. */
    val apiUrl = (config["apiUrl"]?.toString() ?: "").trimEnd('/')

    /** Operator identity registered with the provider — sent as the `X-Operator` header. */
    val operatorIdentity = config["operatorIdentity"]?.toString() ?: ""

    /** Shared secret — sent as `X-Secret-Key` on outbound calls and verified on inbound webhooks. */
    val apiKey = config["apiKey"]?.toString() ?: ""

    /** Base host for game frontends. Each game is served at `<gameSymbol>.<gameHost>`. */
    val gameHost = config["gameHost"]?.toString() ?: ""

    /** Provider WebSocket base, e.g. `wss://provider.example.com`. The public jackpot is consumed at
     *  `<wsUrl>/ws/jackpot`. Defaults to [apiUrl] with the scheme swapped to ws/wss. */
    val wsUrl = config["wsUrl"]?.toString()?.trimEnd('/')
        ?: apiUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
}
