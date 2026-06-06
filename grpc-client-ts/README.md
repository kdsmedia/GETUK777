# @nekzabirov/game-grpc-client

Typed TypeScript gRPC client generated from this engine's `src/main/proto`
with [`buf`](https://buf.build) + [`protobuf-es`](https://github.com/bufbuild/protobuf-es) v2.
In v2 a single codegen plugin (`protoc-gen-es`) emits both message types and
service descriptors. This package ships only the generated artifact; the
transport (`@connectrpc/connect-node` `createGrpcTransport`, HTTP/2) and
`createClient` live on the consumer.

## Install (GitHub Packages)

```
@nekzabirov:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

```bash
npm install @nekzabirov/game-grpc-client @connectrpc/connect @connectrpc/connect-node
```

## Usage (Node ESM)

```typescript
import { createClient } from "@connectrpc/connect";
import { createGrpcTransport } from "@connectrpc/connect-node";
// import the service descriptors you need from the barrel:
import * as proto from "@nekzabirov/game-grpc-client";

const transport = createGrpcTransport({ baseUrl: process.env.GRPC_BASE_URL! });
const client = createClient(proto.SomeService, transport);
```

## Develop / regenerate

```bash
npm install
npm run build   # buf generate (-> src/gen) + barrel + tsc (-> dist)
```

After editing the engine's protos, rerun `npm run build`. Publishing happens on
the same tag push as the Java client.
