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

    private static final int NUM_OF_REQUESTS = 10;
    private static final Logger log = Logger.getLogger(HttpMultipleRequests.class.getName());

    public static void main(String[] args) throws IOException {

        AsyncHttpClient client = asyncHttpClient();
        List<CompletableFuture<Response>> futures = Stream.generate(() -> client.prepareGet(ENDPOINT).execute().toCompletableFuture()).parallel().limit(NUM_OF_REQUESTS).collect(Collectors.toList());

        CompletableFuture<Void> allOfFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));

        futures.parallelStream().forEach(future -> {
            future.thenApplyAsync(Response::getStatusCode).thenAcceptAsync(HttpMultipleRequests::dump);
        });

        try {
            allOfFutures.get();
        } catch (ExecutionException | InterruptedException e) {
            log.log(Level.SEVERE, e.getMessage());
            //e.printStackTrace();
        }
    }


    public static void dump(Integer code) {
        log.log(Level.INFO, code.toString());
    }
}