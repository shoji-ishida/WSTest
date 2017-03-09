package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class HttpMultipleRequests {
    private static final String ENDPOINT = "http://yanase-W331AU.local:8080/SpringTest_war/spring";

    private static final int NUM_OF_REQUESTS = 100;
    private static final Logger log = Logger.getLogger(HttpMultipleRequests.class.getName());

    public static void main(String[] args) throws IOException {

        try(AsyncHttpClient client = asyncHttpClient()) {
            // Generates NUM_OF_REQUESTS of CompletableFuture from AsyncHttpClient
            List<CompletableFuture<Response>> futures = Stream.generate(() -> client.prepareGet(ENDPOINT).execute().toCompletableFuture()).parallel().limit(NUM_OF_REQUESTS).collect(Collectors.toList());

            // wait on all of Futures to finish
            CompletableFuture<Void> allOfFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
            allOfFutures.join();

            // Process Response from each Future
            futures.parallelStream().forEach(future -> {
                future.thenApplyAsync(Response::getStatusText).thenAcceptAsync(System.out::println);
            });
        }
    }
}