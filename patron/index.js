const logger = require('./logger');
const async = require('async');
const _ = require('lodash');
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

// ==============================
// gRPC client implementation
// ==============================

function staff(id, callback) {
  client.staffOrder({ mealId: id }, (e, meal) => { logger.logSingleMeal(meal) });
  callback();
}

function easternHostOrder(callback) {
  console.log('Ordering meals #1, #10, #12');
  const call = client.easternHostOrder({ mealIds: [1, 10, 12] });

  call.on('data', (meal) => logger.logSingleMeal(meal));
  call.on('end', callback);
}

function westernHostOrder(callback) {
  const call = client.westernHostOrder((error, batchMeal) => {
    if (error) {
      callback(error);
      return;
    }
    logger.logBatchMeal(batchMeal);
    callback();
  });

  function foodSender(mealId) {
    return function (callback) {
      console.log("SENDING MEAL");
      call.write({
        mealId
      });
      _.delay(callback, _.random(500, 1500));
    }
  }

  const asyncRequests = [1, 10, 12].map(order => foodSender(order));
  async.series(asyncRequests, () => call.end());
}

function dimSumOrder(callback) {
  const call = client.dimSumOrder();
  call.on('data', function (batchMeal) {
    logger.logBatchMeal(batchMeal);
  });

  call.on('end', callback);

  function foodSender(mealIds) {
    return function (callback) {
      console.log('Sending mealRequest with following combos: ' + mealIds);
      call.write({
        mealIds
      });
      _.delay(callback, _.random(500, 3000));
    }
  }

  const mealRequests = [
    { mealIds: [12] },
    { mealIds: [1, 12] },
    { mealIds: [10, 12] },
    { mealIds: [1, 10, 12] }
  ];

  const asyncRequests = mealRequests.map(mealRequest => foodSender(mealRequest.mealIds));

  async.series(asyncRequests, () => call.end());
}

// CLI piece
const processName = process.argv.shift();
const scriptName = process.argv.shift();
const command = process.argv.shift();

logger.logInitiation(command);
if (command == 'staff') {
  staff(process.argv.shift(), () => logger.logCompletion(command));
} else if (command == 'western') {
  westernHostOrder(() => logger.logCompletion(command));
} else if (command == 'eastern') {
  easternHostOrder(() => logger.logCompletion(command));
} else if (command == 'dimsum') {
  dimSumOrder(() => logger.logCompletion(command));
}