package domain.event

import domain.model.Round
import domain.model.Session
import domain.model.Spin
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/**
 * The casino aggregates nest the game deep (`round.gameVariant.game`, currency under the session).
 * External consumers (the miniapp CRM bridge) read flat scalar fields, so these serializers add
 * `gameIdentity` / `gameProvider` / `currency` / `freespinId` at the TOP level **alongside** the
 * full nested payload — additive only. Internal consumers (e.g. PlaceSpinEventConsumer) keep
 * decoding the nested aggregate; the extra keys are ignored via `appJson { ignoreUnknownKeys }`.
 * `gameIdentity`/`gameProvider` are the `Identity` slugs CRM expects, NOT the aggregator GameSymbol.
 */
private fun JsonObject.withFlatGameFields(
    gameVariant: JsonObject?,
    currency: JsonElement?,
    freespinId: JsonElement?,
): JsonObject {
    val game = gameVariant?.get("game")?.jsonObject
    val extras = buildMap<String, JsonElement> {
        game?.get("identity")?.let { put("gameIdentity", it) }
        game?.get("provider")?.jsonObject?.get("identity")?.let { put("gameProvider", it) }
        currency?.takeIf { it !is JsonNull }?.let { put("currency", it) }
        freespinId?.takeIf { it !is JsonNull }?.let { put("freespinId", it) }
    }
    return JsonObject(this + extras)
}

object SpinWireSerializer : JsonTransformingSerializer<Spin>(Spin.serializer()) {
    public override fun transformSerialize(element: JsonElement): JsonElement {
        val round = element.jsonObject["round"]?.jsonObject
        return element.jsonObject.withFlatGameFields(
            gameVariant = round?.get("gameVariant")?.jsonObject,
            currency = round?.get("session")?.jsonObject?.get("currency"),
            freespinId = round?.get("freespinId"),
        )
    }
}

object RoundWireSerializer : JsonTransformingSerializer<Round>(Round.serializer()) {
    public override fun transformSerialize(element: JsonElement): JsonElement =
        element.jsonObject.withFlatGameFields(
            gameVariant = element.jsonObject["gameVariant"]?.jsonObject,
            currency = element.jsonObject["session"]?.jsonObject?.get("currency"),
            freespinId = element.jsonObject["freespinId"],
        )
}

object SessionWireSerializer : JsonTransformingSerializer<Session>(Session.serializer()) {
    public override fun transformSerialize(element: JsonElement): JsonElement =
        element.jsonObject.withFlatGameFields(
            gameVariant = element.jsonObject["gameVariant"]?.jsonObject,
            currency = element.jsonObject["currency"],
            freespinId = null,
        )
}
