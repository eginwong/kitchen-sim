package com.eginwong.kitchensim;

import com.eginwong.kitchensim.Kitchen.BatchMeals;
import com.eginwong.kitchensim.Kitchen.Meal;
import com.eginwong.kitchensim.Kitchen.MealRequest;
import com.eginwong.kitchensim.Kitchen.SingleOrder;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
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
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


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
        mealsDatabase = KitchenServerUtil.parseKitchenMeals(KitchenServerUtil.getDefaultMealsFile());
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

    @Test(expected = StatusRuntimeException.class)
    public void waiterImpl_instantOrder_fake_order() {
        WaiterGrpc.WaiterBlockingStub blockingStub = WaiterGrpc.newBlockingStub(inProcessChannel);
        blockingStub.staffOrder(SingleOrder.newBuilder().setMealId(1000).build());
    }

    /**
     * To test the server, make calls with a real stub using the in-process channel, and verify
     * behaviors or state changes from the client side.
     */
    @Test
    public void waiterImpl_instantOrder_real_order() {
        WaiterGrpc.WaiterBlockingStub blockingStub = WaiterGrpc.newBlockingStub(inProcessChannel);

        Meal expected = mealsDatabase.get(1);
        Meal actual = blockingStub.staffOrder(SingleOrder.newBuilder().setMealId(1).build());
        assertThat(expected, is(actual));
    }

    @Test
    public void waiterImpl_easternHostOrder() throws InterruptedException {
        // setup
        MealRequest request = MealRequest.newBuilder()
                .addAllMealIds(Arrays.asList(1, 10, 12))
                .build();
        Map<Integer, Meal> expected = new HashMap<>();
        expected.put(1, mealsDatabase.get(1));
        expected.put(10, mealsDatabase.get(10));
        expected.put(12, mealsDatabase.get(12));

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
        assertEquals(expected, result);
    }

    @Test
    public void waiterImpl_westernHostOrder() {
        BatchMeals expected = BatchMeals.newBuilder()
                .addMeals(mealsDatabase.get(1))
                .addMeals(mealsDatabase.get(10))
                .addMeals(mealsDatabase.get(12))
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<BatchMeals> responseObserver =
                (StreamObserver<BatchMeals>) mock(StreamObserver.class);
        WaiterGrpc.WaiterStub stub = WaiterGrpc.newStub(inProcessChannel);
        ArgumentCaptor<BatchMeals> batchMealsCaptor = ArgumentCaptor.forClass(BatchMeals.class);

        StreamObserver<SingleOrder> requestObserver = stub.westernHostOrder(responseObserver);

        requestObserver.onNext(SingleOrder.newBuilder().setMealId(1).build());
        requestObserver.onNext(SingleOrder.newBuilder().setMealId(10).build());
        requestObserver.onNext(SingleOrder.newBuilder().setMealId(12).build());
        verify(responseObserver, never()).onNext(any(BatchMeals.class));

        requestObserver.onCompleted();

        // allow some ms to let client receive the response. Similar usage later on.
        verify(responseObserver, timeout(100)).onNext(batchMealsCaptor.capture());
        assertEquals(expected, batchMealsCaptor.getValue());
        verify(responseObserver, timeout(100)).onCompleted();
        verify(responseObserver, never()).onError(any(Throwable.class));
    }

    @Test
    public void waiterImpl_dimSumOrder() {
        BatchMeals expected1 = BatchMeals.newBuilder()
                .addMeals(mealsDatabase.get(1))
                .addMeals(mealsDatabase.get(10))
                .build();
        BatchMeals expected2 = BatchMeals.newBuilder()
                .addMeals(mealsDatabase.get(12))
                .build();
        BatchMeals expected3 = BatchMeals.newBuilder()
                .addMeals(mealsDatabase.get(1))
                .addMeals(mealsDatabase.get(10))
                .addMeals(mealsDatabase.get(12))
                .build();

        MealRequest request1 = MealRequest.newBuilder()
                .addAllMealIds(Arrays.asList(1, 10))
                .build();
        MealRequest request2 = MealRequest.newBuilder()
                .addAllMealIds(Collections.singletonList(12))
                .build();
        MealRequest request3 = MealRequest.newBuilder()
                .addAllMealIds(Arrays.asList(1, 10, 12))
                .build();

        @SuppressWarnings("unchecked")
        StreamObserver<BatchMeals> responseObserver =
                (StreamObserver<BatchMeals>) mock(StreamObserver.class);
        WaiterGrpc.WaiterStub stub = WaiterGrpc.newStub(inProcessChannel);

        StreamObserver<MealRequest> requestObserver = stub.dimSumOrder(responseObserver);
        verify(responseObserver, never()).onNext(any(BatchMeals.class));

        requestObserver.onNext(request1);
        ArgumentCaptor<BatchMeals> batchMealsCaptor = ArgumentCaptor.forClass(BatchMeals.class);
        verify(responseObserver, timeout(100)).onNext(batchMealsCaptor.capture());
        assertEquals(expected1, batchMealsCaptor.getValue());

        requestObserver.onNext(request2);
        batchMealsCaptor = ArgumentCaptor.forClass(BatchMeals.class);
        verify(responseObserver, timeout(100).times(2)).onNext(batchMealsCaptor.capture());
        assertEquals(expected2, batchMealsCaptor.getValue());

        requestObserver.onNext(request3);
        batchMealsCaptor = ArgumentCaptor.forClass(BatchMeals.class);
        verify(responseObserver, timeout(100).times(3)).onNext(batchMealsCaptor.capture());
        assertEquals(expected3, batchMealsCaptor.getValue());

        requestObserver.onCompleted();
        verify(responseObserver, timeout(100)).onCompleted();
        verify(responseObserver, never()).onError(any(Throwable.class));
    }
}