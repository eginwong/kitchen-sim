package com.eginwong.kitchensim;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
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
    public KitchenServer(ServerBuilder<?> serverBuilder, int port, Collection<Meal> meals) {
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
        private final Collection<Meal> meals;

        WaiterService(Collection<Meal> mealsDatabase) {
            this.meals = mealsDatabase;
        }

        /**
         * Gets the {@link Meal} with the requested id. If no meal with that id
         * exists, a default meal is returned at the provided location.
         *
         * @param req              the requested meal.
         * @param responseObserver the observer that will receive the meal at the requested point.
         */
        @Override
        public void instantOrder(MealRequest req, StreamObserver<MealResponse> responseObserver) {
            logger.info("RECEIVED INSTANT ORDER REQUEST");
            MealResponse response = MealResponse.newBuilder().addMeals(Meal.newBuilder().setId(req.getMealIds(0))
                    .addIngredients("CHIVES").setName("YEUNG CHOW CHOW FAN").build()).build();
            responseObserver.onNext(response);

            try {
                logger.info(JsonFormat.printer().print(req));
                logger.info("SERVED BY: " + JsonFormat.printer().print(response));
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            responseObserver.onCompleted();
        }
    }
}