package com.eginwong.kitchensim;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.eginwong.kitchensim.Kitchen.*;

/**
 * Server that manages startup/shutdown of a {@code KitchenServer} server.
 */
public class KitchenServer {
    private static final Logger logger = Logger.getLogger(KitchenServer.class.getName());

    private final int port;
    private final Server server;

    public KitchenServer(int port) throws IOException {
        this(port, KitchenServerUtil.getDefaultMealsFile());
    }

    /**
     * Create a Kitchen server listening on {@code port} using {@code mealsFile} database.
     */
    public KitchenServer(int port, URL mealsFile) throws IOException {
        this(ServerBuilder.forPort(port), port, KitchenServerUtil.parseKitchenMeals(mealsFile));
    }

    /**
     * Create a Kitchen server using serverBuilder as a base and meals as data.
     */
    public KitchenServer(ServerBuilder<?> serverBuilder, int port, Map<Integer, Meal> meals) {
        this.port = port;
        server = serverBuilder.addService(new WaiterService(meals))
                .build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
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
    public void stop() {
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
            logger.info("RECEIVED STAFF ORDER REQUEST");
            if (meals.containsKey(req.getMealId())) {
                Meal orderedMeal = meals.get(req.getMealId());

                try {
                    TimeUnit.MILLISECONDS.sleep(orderedMeal.getServingTime());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                responseObserver.onNext(orderedMeal);

                try {
                    logger.info(JsonFormat.printer().print(req));
                    logger.info("SERVED BY: " + JsonFormat.printer().print(orderedMeal));
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            } else {
                responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND));
            }

            responseObserver.onCompleted();
        }

        /**
         * Gets all meals contained within the meal request.
         *
         * @param request          the ids for the requested meals.
         * @param responseObserver the observer that will receive the meals.
         */
        @Override
        public void easternHostOrder(MealRequest request, StreamObserver<Meal> responseObserver) {
            for (Integer id : request.getMealIdsList()) {
                if (!meals.containsKey(id)) {
                    continue;
                }
                Meal orderedMeal = meals.get(id);

                try {
                    TimeUnit.MILLISECONDS.sleep(orderedMeal.getServingTime());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                responseObserver.onNext(orderedMeal);
            }
            responseObserver.onCompleted();
        }
}