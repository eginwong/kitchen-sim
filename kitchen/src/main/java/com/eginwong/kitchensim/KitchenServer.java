package com.eginwong.kitchensim;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.eginwong.kitchensim.Kitchen.*;

/**
 * Server that manages startup/shutdown of a {@code KitchenServer} server.
 */
public class KitchenServer {
    private static final Logger logger = Logger.getLogger(KitchenServer.class.getName());

    private final int port;
    private final Server server;

    private KitchenServer(int port) throws IOException {
        this(port, KitchenServerUtil.getDefaultMealsFile());
    }

    /**
     * Create a Kitchen server listening on {@code port} using {@code mealsFile} database.
     */
    private KitchenServer(int port, URL mealsFile) throws IOException {
        this(ServerBuilder.forPort(port), port, KitchenServerUtil.parseKitchenMeals(mealsFile));
    }

    /**
     * Create a Kitchen server using serverBuilder as a base and meals as data.
     */
    KitchenServer(ServerBuilder<?> serverBuilder, int port, Map<Integer, Meal> meals) {
        this.port = port;
        server = serverBuilder.addService(new WaiterService(meals))
                .build();
    }

    /**
     * Start serving requests.
     */
    void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            KitchenServer.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    /**
     * Stop serving requests.
     */
    void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final KitchenServer server = new KitchenServer(50051);
        server.start();
        server.blockUntilShutdown();
    }

    /**
     * Our implementation of Waiter service.
     *
     * <p>See kitchen.proto for details of the methods.
     */
    private static class WaiterService extends WaiterGrpc.WaiterImplBase {
        private final Map<Integer, Meal> meals;

        WaiterService(Map<Integer, Meal> mealsDatabase) {
            this.meals = mealsDatabase;
        }

        /**
         * Gets the {@link Meal} with the requested id. If no meal with that id
         * exists, a default meal is returned at the provided location.
         *
         * @param req              the requested meal.
         * @param responseObserver the observer that will receive the meal at the requested point.
         */
        public void staffOrder(SingleOrder req, StreamObserver<Meal> responseObserver) {
            if (meals.containsKey(req.getMealId())) {
                Meal orderedMeal = meals.get(req.getMealId());

                try {
                    TimeUnit.MILLISECONDS.sleep(orderedMeal.getServingTime());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                responseObserver.onNext(orderedMeal);
            } else {
                responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND));
            }

            responseObserver.onCompleted();
        }

        /**
         * Gets all {@link Meal}s contained within the {@link MealRequest}.
         *
         * @param request          the ids for the requested meals.
         * @param responseObserver the observer that will receive the meals.
         */
        @Override
        public void easternHostOrder(MealRequest request, StreamObserver<Meal> responseObserver) {

            request.getMealIdsList().stream()
                    .filter(meals::containsKey)
                    .map(meals::get)
                    .forEach(meal -> {
                        try {
                            TimeUnit.MILLISECONDS.sleep(meal.getServingTime());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        responseObserver.onNext(meal);
                    });
            responseObserver.onCompleted();
        }

        /**
         * Gets a stream of {@link SingleOrder}s, and responds with an aggregation of all meals as {@link BatchMeals}
         * served at once from kitchen.
         *
         * @param responseObserver an observer to receive the response summary.
         * @return an observer to receive the requested food orders.
         */
        @Override
        public StreamObserver<SingleOrder> westernHostOrder(final StreamObserver<BatchMeals> responseObserver) {
            return new StreamObserver<SingleOrder>() {
                List<Meal> orderedMeals = new ArrayList<>();

                @Override
                public void onNext(SingleOrder order) {
                    if (meals.containsKey(order.getMealId())) {
                        try {
                            Meal orderedMeal = meals.get(order.getMealId());
                            TimeUnit.MILLISECONDS.sleep(orderedMeal.getServingTime());
                            orderedMeals.add(orderedMeal);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "western host order cancelled");
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(BatchMeals.newBuilder().addAllMeals(orderedMeals).build());
                    responseObserver.onCompleted();
                }
            };
        }

        /**
         * Receives a stream of {@link MealRequest}s, and responds with a stream of {@link BatchMeals} as they become
         * immediately available to the patrons.
         *
         * @param responseObserver an observer to receive the stream of batch meals output from the kitchen.
         * @return an observer to handle requested meal requests.
         */
        @Override
        public StreamObserver<MealRequest> dimSumOrder(final StreamObserver<BatchMeals> responseObserver) {
            return new StreamObserver<MealRequest>() {
                @Override
                public void onNext(MealRequest mealReq) {
                    responseObserver.onNext(BatchMeals.newBuilder()
                            .addAllMeals(
                                    mealReq.getMealIdsList()
                                            .parallelStream()
                                            .filter(meals::containsKey)
                                            .map(meals::get)
                                            .peek(meal -> {
                                                try {
                                                    TimeUnit.MILLISECONDS.sleep(meal.getServingTime());
                                                } catch (InterruptedException e) {
                                                    e.printStackTrace();
                                                }
                                            })
                                            .collect(Collectors.toList()))
                            .build());
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "dimSumOrder cancelled");
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
    }
}