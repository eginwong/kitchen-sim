const inquirer = require('inquirer');

/** gRPC client imports */
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
  console.log('SENDING SINGLE ORDER!\n');
  client.staffOrder({ mealId: id }, (e, meal) => {
    errorHandler(e);
    logger.logSingleMeal(meal);
    callback();
  });
}

function easternHostOrder(callback) {
  console.log('SENDING MEAL REQUEST!\n');
  const call = client.easternHostOrder({ mealIds: [getRandomFood(), getRandomFood(), getRandomFood()] });
  call.on('data', (meal) => logger.logSingleMeal(meal));
  call.on('error', errorHandler);
  call.on('end', callback);
}

function westernHostOrder(callback) {
  const call = client.westernHostOrder((e, batchMeal) => {
    errorHandler(e);
    logger.logBatchMeal(batchMeal);
    callback();
  });

  function foodSender(mealId) {
    return function (callback) {
      console.log('SENDING SINGLE ORDER #' + mealId + '!\n');
      call.write({
        mealId
      });
      _.delay(callback, _.random(500, 1500));
    }
  }

  const asyncRequests = [getRandomFood(), getRandomFood(), getRandomFood()].map(order => foodSender(order));
  async.series(asyncRequests, () => call.end());
}

function dimSumOrder(callback) {
  const call = client.dimSumOrder();
  call.on('data', function (batchMeal) {
    logger.logBatchMeal(batchMeal);
  });
  call.on('error', errorHandler);
  call.on('end', callback);

  function foodSender(mealIds) {
    return function (callback) {
      console.log('SENDING MEALREQUEST WITH FOLLOWING COMBOS: ' + mealIds + '\n');
      call.write({
        mealIds
      });
      _.delay(callback, _.random(500, 4000));
    }
  }

  const mealRequests = [
    { mealIds: [getRandomFood()] },
    { mealIds: [getRandomFood(), getRandomFood()] },
    { mealIds: [getRandomFood(), getRandomFood()] },
    { mealIds: [getRandomFood(), getRandomFood(), getRandomFood()] }
  ];
  const asyncRequests = mealRequests.map(mealRequest => foodSender(mealRequest.mealIds));

  async.series(asyncRequests, () => call.end());
}

function getRandomFood() {
  return _.random(1, 12);
}

function errorHandler(e) {
  if (e) {
    console.error('Error encountered. Please make sure Java gRPC server is running correctly.\n')
    process.exit(1);
  }
}

/** DEMO SECTION */
inquirer
  .prompt([
    {
      type: 'list',
      name: 'option',
      message: 'Which demo would you like to see?',
      choices: [
        { name: 'Unary: direct order to kitchen', value: 'unary' },
        { name: 'Client-side streaming: several orders and served all at once', value: 'css' },
        { name: 'Server-side streaming: single batch order and served immediately', value: 'sss' },
        { name: 'Client/Server-side streaming: several orders and served whenever ready', value: 'csss' },
        new inquirer.Separator(),
      ]
    },
  ])
  .then(choice => {
    const option = choice.option;
    logger.logInitiation();

    if (option == 'unary') {
      staff(10, logger.logCompletion);
    } else if (option == 'css') {
      westernHostOrder(logger.logCompletion);
    } else if (option == 'sss') {
      easternHostOrder(logger.logCompletion);
    } else if (option == 'csss') {
      dimSumOrder(logger.logCompletion);
    }
  });
