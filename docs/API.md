# gRPC API reference

Proto package: `game.v1`
Java package: `com.nekgamebling.game.v1`

The engine exposes 5 gRPC services. A pre-built client JAR is published
on tag pushes — see [Client JAR](#client-jar) at the bottom.

## Services

- [`GameService`](#gameservice) — game catalogue and session launch
- [`ProviderService`](#providerservice) — game provider management
- [`CollectionService`](#collectionservice) — game collections / categories
- [`AggregatorService`](#aggregatorservice) — aggregator configuration
- [`FreespinService`](#freespinservice) — freespin bonuses

---

## GameService

```protobuf
service GameService {
  rpc Save(SaveGameCommand) returns (Empty);
  rpc Find(FindGameQuery) returns (FindGameQuery.Result);
  rpc FindAll(FindAllGameQuery) returns (FindAllGameQuery.Result);
  rpc Batch(BatchGameQuery) returns (BatchGameQuery.Result);
  rpc UpdateImage(UpdateGameImageCommand) returns (Empty);
  rpc Play(PlayGameCommand) returns (PlayGameCommand.Result);
  rpc OpenDemo(OpenDemoQuery) returns (OpenDemoQuery.Result);
  rpc AddFavourite(GameFavouriteCommand) returns (Empty);
  rpc RemoveFavourite(GameFavouriteCommand) returns (Empty);
}
```

### Play (open real-money session)

```protobuf
message PlayGameCommand {
  string identity = 1;                       // Game identifier
  string player_id = 2;                      // Your player ID
  string locale = 3;                         // Locale (e.g., "en", "de")
  PlatformDto platform = 4;                  // DESKTOP, MOBILE, DOWNLOAD
  string currency = 5;                       // Currency code (e.g., "EUR")
  optional int64 max_spin_place_amount = 6;  // Max bet limit for player

  message Result {
    string launch_url = 1;                   // URL to launch the game
  }
}
```

### OpenDemo (demo game launch)

```protobuf
message OpenDemoQuery {
  string identity = 1;       // Game identifier
  string currency = 2;       // Currency code
  string locale = 3;         // Locale
  PlatformDto platform = 4;  // Platform type
  string lobby_url = 5;      // Return URL after game exit

  message Result {
    string launch_url = 1;   // Demo game URL
  }
}
```

### FindAll (list with filter and pagination)

```protobuf
message GameFilter {
  string query = 1;
  optional bool active = 2;
  optional string provider_identity = 3;
  repeated string tags = 4;
  optional bool bonus_bet_enable = 5;
  optional bool bonus_wagering_enable = 6;
  optional bool free_spin_enable = 7;
  optional bool free_chip_enable = 8;
  optional bool jackpot_enable = 9;
  optional bool demo_enable = 10;
  optional bool bonus_buy_enable = 11;
}

message FindAllGameQuery {
  GameFilter filter = 1;
  int32 page_num = 2;
  int32 page_size = 3;

  message Result {
    repeated Item items = 1;                 // Games with provider info
    repeated ProviderDto providers = 2;
    repeated AggregatorDto aggregators = 3;
    repeated CollectionDto collections = 4;
    int32 total_items = 5;
  }
}
```

### UpdateImage

```protobuf
message UpdateGameImageCommand {
  string identity = 1;    // Game identifier
  string key = 2;         // Image key (e.g., "thumbnail", "banner")
  bytes file = 3;         // Image binary data
  string extension = 4;   // File extension (e.g., "png", "jpg")
}
```

---

## ProviderService

```protobuf
service ProviderService {
  rpc Save(ProviderDto) returns (Empty);
  rpc Find(FindProviderQuery) returns (FindProviderQuery.Result);
  rpc FindAll(FindAllProviderQuery) returns (FindAllProviderQuery.Result);
  rpc UpdateImage(UpdateProviderImageCommand) returns (Empty);
}

message FindProviderQuery {
  string identity = 1;
  message Result {
    ProviderDto item = 1;
    AggregatorDto aggregator = 2;
    int32 active_game_count = 3;
    int32 deactivate_game_count = 4;
  }
}

message FindAllProviderQuery {
  string query = 1;
  optional bool active = 2;
  optional string aggregator_identity = 3;
  int32 page_num = 4;
  int32 page_size = 5;
}
```

---

## CollectionService

```protobuf
service CollectionService {
  rpc Save(CollectionDto) returns (Empty);
  rpc Find(FindCollectionQuery) returns (FindCollectionQuery.Result);
  rpc FindAll(FindAllCollectionQuery) returns (FindAllCollectionQuery.Result);
  rpc UpdateGames(UpdateCollectionGamesCommand) returns (Empty);
  rpc UpdateImage(UpdateCollectionImageCommand) returns (Empty);
}

message UpdateCollectionGamesCommand {
  string identity = 1;
  repeated string add_games = 2;
  repeated string remove_games = 3;
}

message CollectionDto {
  string identity = 1;
  map<string, string> name = 2;     // {"en": "Popular", "de": "Beliebt"}
  map<string, string> images = 3;
  bool active = 4;
  int32 order = 5;
}
```

---

## AggregatorService

```protobuf
service AggregatorService {
  rpc Save(AggregatorDto) returns (Empty);
  rpc Find(FindAggregatorQuery) returns (AggregatorDto);
  rpc FindAll(FindAllAggregatorQuery) returns (FindAllAggregatorResult);
}

message AggregatorDto {
  string identity = 1;
  string integration = 2;            // ONEGAMEHUB, PRAGMATIC, PATEPLAY
  google.protobuf.Struct config = 3;
  bool active = 4;
}

message FindAllAggregatorQuery {
  string query = 1;
  optional bool active = 2;
  optional string integration = 3;
  int32 page_num = 4;
  int32 page_size = 5;
}
```

---

## FreespinService

```protobuf
service FreespinService {
  rpc GetPreset(GetFreespinPresetQuery) returns (GetFreespinPresetQuery.Result);
  rpc Create(CreateFreespinCommand) returns (Empty);
  rpc Cancel(CancelFreespinCommand) returns (Empty);
}

message CreateFreespinCommand {
  string game_identity = 1;
  string player_id = 2;
  string reference_id = 3;       // Your reference ID
  string currency = 4;
  string start_at = 5;           // ISO datetime
  string end_at = 6;             // ISO datetime
  google.protobuf.Struct preset_values = 7;
}

message GetFreespinPresetQuery {
  string game_identity = 1;
  message Result {
    google.protobuf.Struct preset = 1;
  }
}
```

---

## Shared DTOs

### GameDto

```protobuf
message GameDto {
  string identity = 1;
  string name = 2;
  string provider_identity = 3;
  repeated string collection_identities = 4;
  bool bonus_bet_enable = 5;
  bool bonus_wagering_enable = 6;
  repeated string tags = 7;
  bool active = 8;
  map<string, string> images = 9;
  int32 order = 10;
  string symbol = 11;                 // Aggregator game symbol
  string integration = 12;
  bool free_spin_enable = 14;
  bool free_chip_enable = 15;
  bool jackpot_enable = 16;
  bool demo_enable = 17;
  bool bonus_buy_enable = 18;
  repeated string locales = 19;
  repeated PlatformDto platforms = 20;
  int32 play_lines = 21;
}
```

### ProviderDto

```protobuf
message ProviderDto {
  string identity = 1;
  string name = 2;
  map<string, string> images = 3;
  int32 order = 4;
  bool active = 5;
  string aggregator_identity = 6;
}
```

### PlatformDto

```protobuf
enum PlatformDto {
  PLATFORM_UNSPECIFIED = 0;
  PLATFORM_DESKTOP = 1;
  PLATFORM_MOBILE = 2;
  PLATFORM_DOWNLOAD = 3;
}
```

---

## Client JAR

A gRPC client JAR is published to GitHub Packages on tag pushes (`v*`):

```bash
./gradlew grpcClientJar grpcClientSourcesJar \
  -PgrpcClientVersion=1.0.0
```

Maven coordinates:

```
com.nekgamebling:game-grpc-client:1.0.0
```

CI workflow: `.github/workflows/publish-grpc-client.yml`.
