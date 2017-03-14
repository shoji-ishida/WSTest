package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.apache.commons.cli.*;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.asynchttpclient.Dsl.asyncHttpClient;

// Make multiple Http requests concurrently and wait for response asynchronously
public class HttpMultipleRequests {
    private static final String ENDPOINT = "http://yanase-W331AU.local:8080/SpringTest_war/spring";

    private static final int NUM_OF_REQUESTS = 1000;
    private static final Logger log = Logger.getLogger(HttpMultipleRequests.class.getName());
    private static final AtomicInteger success = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        Options options = new Options();
        options.addOption(Option.builder("c").hasArg().required(false).argName("concurrency").desc("Number of multiple requests to make at a time").build());
        CommandLineParser parser = new DefaultParser();

        int requests = NUM_OF_REQUESTS;
        try {
            CommandLine cmd = parser.parse( options, args);
            if (cmd.hasOption("c")) {
                requests = Integer.parseInt(cmd.getOptionValue("c"));
            }
            String[] leftArgs = cmd.getArgs();
            String endpoint = leftArgs[0];



            long startTime;
            try(AsyncHttpClient client = asyncHttpClient()) {
                startTime = System.currentTimeMillis();
                // Generates NUM_OF_REQUESTS of CompletableFuture from AsyncHttpClient
                List<CompletableFuture<Response>> futures = Stream.generate(() -> client.prepareGet(endpoint).execute().toCompletableFuture()).parallel().limit(requests).collect(Collectors.toList());

                // wait on all of Futures to finish
                CompletableFuture<Void> allOfFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
                allOfFutures.join();

                // Process Response from each Future
                futures.parallelStream().forEach(future -> {
                    //future.thenApplyAsync(Response::getStatusText).thenAcceptAsync(System.out::println);
                    future.thenApplyAsync(Response::getStatusCode).thenAcceptAsync(HttpMultipleRequests::count);
                });
            }
            long endTime = System.currentTimeMillis();
            System.out.println("Took: " + DurationFormatUtils.formatPeriod(startTime, endTime, "mm:ss.SSS"));
            System.out.println("Ok: " + success + " Err: " + Integer.toString(requests - success.get()));
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("wrong number of arguments");
        }
    }

    private static void count(Integer status) {
        if (status == 200) {
            success.incrementAndGet();
        }
    }
}