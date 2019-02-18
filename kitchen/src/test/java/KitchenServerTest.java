import com.eginwong.kitchensim.Kitchen;
import com.eginwong.kitchensim.WaiterGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

    /**
     * To test the server, make calls with a real stub using the in-process channel, and verify
     * behaviors or state changes from the client side.
     */
    @Test
    public void waiterImpl_instantOrder() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(new KitchenServer.WaiterImpl()).build().start());

        WaiterGrpc.WaiterBlockingStub blockingStub = WaiterGrpc.newBlockingStub(
                // Create a client channel and register for automatic graceful shutdown.
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        MealResponse expected = MealResponse.newBuilder().addMeals(Kitchen.Meal.newBuilder().setId(1)
                .addIngredients("CHIVES").setName("YEUNG CHOW CHOW FAN").build()).build();

        MealResponse actual =
                blockingStub.instantOrder(MealRequest.newBuilder().addMealIds(1).build());

        assertThat(expected, is(actual));
    }

}