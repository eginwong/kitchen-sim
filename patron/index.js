var grpc = require('grpc');
var protoLoader = require('@grpc/proto-loader');
var packageDefinition = protoLoader.loadSync(
  '../protos/kitchen.proto',
    {keepCase: true,
     longs: String,
     enums: String,
     defaults: true,
     oneofs: true
    });
var kitchen_sim_proto = grpc.loadPackageDefinition(packageDefinition).com.eginwong.kitchensim;


var client = new kitchen_sim_proto.Waiter('127.0.0.1:50051',
  grpc.credentials.createInsecure());

function printResponse(error, response) {
  if (error)
    console.log('Error: ', error);
  else
    console.log(response);
}

function instant() {
  client.instantOrder({ mealIds: [1] }, function (error, meals) {
    printResponse(error, meals);
  });
}

// CLI piece
var processName = process.argv.shift();
var scriptName = process.argv.shift();
var command = process.argv.shift();

if (command == 'instant')
  instant();
