const grpc = require('grpc');
const protoLoader = require('@grpc/proto-loader');
const packageDefinition = protoLoader.loadSync(
  '../protos/kitchen.proto',
  {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
  });
const kitchen_sim_proto = grpc.loadPackageDefinition(packageDefinition).com.eginwong.kitchensim;


const client = new kitchen_sim_proto.Waiter('127.0.0.1:50051',
  grpc.credentials.createInsecure());

function printResponse(error, response) {
  if (error)
    console.log('Error: ', error);
  else
    console.log(response);
}

function staff(id) {
  client.staffOrder({ mealId: id }, function (error, meal) {
    printResponse(error, meal);
  });
}

function easternHostOrder(callback) {
  console.log('Ordering meals #1, #10, #12');
  const call = client.easternHostOrder({ mealIds: [1, 10, 12] });

  call.on('data', function (meal) {
    console.log('Served meal: "' + meal.name + '" with ingredients:  ' +
      meal.ingredients + ', for id: ' +
      meal.id + ". Serving time required: " + meal.servingTime);
  });
  call.on('end', callback);
}

function westernHostOrder() {
}

// CLI piece
const processName = process.argv.shift();
const scriptName = process.argv.shift();
const command = process.argv.shift();

if (command == 'staff') {
  staff(process.argv.shift());
} else if (command == 'western') {
  westernHostOrder();
} else if (command == 'eastern') {
  easternHostOrder(() => {});
}