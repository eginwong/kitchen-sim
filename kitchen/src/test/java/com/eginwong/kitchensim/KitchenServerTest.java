package com.eginwong.kitchensim;

import com.eginwong.kitchensim.Kitchen.Meal;
import com.eginwong.kitchensim.Kitchen.MealRequest;
import com.eginwong.kitchensim.Kitchen.SingleOrder;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


@RunWith(JUnit4.class)
public class KitchenServerTest {

    /**
     * This rule manages automatic graceful shutdown for the registered servers and channels at the
     * end of test.
     */
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private KitchenServer server;
    private ManagedChannel inProcessChannel;
    private Map<Integer, Meal> mealsDatabase;


    @Before
    public void setUp() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();
        mealsDatabase = new HashMap<>();
        // Use directExecutor for both InProcessServerBuilder and InProcessChannelBuilder can reduce the
        // usage timeouts and latches in test. But we still add timeout and latches where they would be
        // needed if no directExecutor were used, just for demo purpose.
        server = new KitchenServer(
                InProcessServerBuilder.forName(serverName).directExecutor(), 0, mealsDatabase);
        server.start();
        // Create a client channel and register for automatic graceful shutdown.
        inProcessChannel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());
    }

    @After
    public void tearDown() {
        server.stop();
    }

    /**
     * To test the server, make calls with a real stub using the in-process channel, and verify
     * behaviors or state changes from the client side.
     */
    @Test
    public void waiterImpl_instantOrder() {
        WaiterGrpc.WaiterBlockingStub blockingStub = WaiterGrpc.newBlockingStub(inProcessChannel);

        // assert found and not found cases

        Meal expected = Meal.newBuilder().setId(1).addIngredients("CHIVES").setName("YEUNG CHOW CHOW FAN").build();

        Meal actual =
                blockingStub.staffOrder(SingleOrder.newBuilder().setMealId(1).build());

        assertThat(expected, is(actual));
    }

    @Test
    public void waiterImpl_easternHostOrder() throws InterruptedException {
        // setup
        MealRequest request = MealRequest.newBuilder()
                .addAllMealIds(Arrays.asList(1, 10, 12))
                .build();
        Meal m1 = Meal.newBuilder().setId(1)
                .addIngredients("CHIVES").setName("YEUNG CHOW CHOW FAN").build();
        Meal m2 = Meal.newBuilder().setId(10)
                .addIngredients("BEEF").setName("SUPERSTAR PHO").build();
        Meal m3 = Meal.newBuilder().setId(12)
                .addIngredients("CHICKEN").setName("PHOENIX CLAW").build();
        mealsDatabase.put(m1.getId(), m1);
        mealsDatabase.put(m2.getId(), m2);
        mealsDatabase.put(m3.getId(), m3);

        final Map<Integer, Meal> result = new HashMap<>();
        final CountDownLatch latch = new CountDownLatch(1);
        StreamObserver<Meal> responseObserver =
                new StreamObserver<Meal>() {
                    @Override
                    public void onNext(Meal value) {
                        result.put(value.getId(), value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                };
        WaiterGrpc.WaiterStub stub = WaiterGrpc.newStub(inProcessChannel);

        // run
        stub.easternHostOrder(request, responseObserver);
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // verify
        assertEquals(mealsDatabase, result);
    }
}