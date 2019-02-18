import com.eginwong.kitchensim.WaiterGrpc;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.logging.Logger;

import static com.eginwong.kitchensim.Kitchen.*;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class KitchenServer {
    private static final Logger logger = Logger.getLogger(KitchenServer.class.getName());

    private Server server;

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new WaiterImpl())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            KitchenServer.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    private void stop() {
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
        final KitchenServer server = new KitchenServer();
        server.start();
        server.blockUntilShutdown();
    }

    static class WaiterImpl extends WaiterGrpc.WaiterImplBase {

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