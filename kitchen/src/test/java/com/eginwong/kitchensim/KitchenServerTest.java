package com.eginwong.kitchensim;

import com.eginwong.kitchensim.Kitchen.Meal;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;

import static com.eginwong.kitchensim.Kitchen.MealRequest;
import static com.eginwong.kitchensim.Kitchen.MealResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


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
    private Collection<Meal> mealsDatabase;


    @Before
    public void setUp() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();
        mealsDatabase = new ArrayList<>();
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

        MealResponse expected = MealResponse.newBuilder().addMeals(Kitchen.Meal.newBuilder().setId(1)
                .addIngredients("CHIVES").setName("YEUNG CHOW CHOW FAN").build()).build();

        MealResponse actual =
                blockingStub.instantOrder(MealRequest.newBuilder().addMealIds(1).build());

        assertThat(expected, is(actual));
    }
}