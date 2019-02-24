Patron - gRPC Client in Node.js
===============================

PREREQUISITES
-------------

- `node`: This requires Node 0.12.x or greater.

GETTING STARTED
---------------
For the demo, make sure the Java server is running in kitchen.
In your terminal:
```sh
npm i
cd patron
node patron/index.js
```

OBJECTIVE
---------

1. Write a sample gRPC client in Node.js.
2. Demonstrate all ways of gRPC communication:
  - point-to-point
  - client-side streaming to server-point
  - server-side streaming to client-point
  - client and server-side streaming  
3. Include testing
4. (optional) write node CLI to launch samples

CAVEATS
-------

Unfortunately, the protobuf.js way of dynamically loading proto files does not lend itself very well to writing tests. 
For more serious codebases, I recommend using the static generation of proto classes via the `protoc` compiler. For a reference of the statically generated tests, please refer [here](https://github.com/grpc/grpc-node/blob/master/packages/grpc-native-core/test/math_client_test.js).