package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.apache.commons.cli.*;
import org.asynchttpclient.*;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.asynchttpclient.Dsl.asyncHttpClient;

// Connect to echo websocket server and wait for echo
public class CoinsWebSocketTest {
    private static final String ENDPOINT = "wss://soc.webpush.jp/coins_websocket/push/10000001/74c0575d-6166-458b-81e5-89b0f06b150c/aHR0cDovL2NoZWNrLndlYnB1c2guanAveWFuYXNlL2NvaW5zMi9pbmRleC5odG1s";

    private static final int NUM_OF_SESSIONS = 1000;
    private static final Logger log = Logger.getLogger(CoinsWebSocketTest.class.getName());

    private static final AtomicInteger session_count = new AtomicInteger(0);
    private static final AtomicInteger push_count = new AtomicInteger(0);

    private static final Object lock = new Object();

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        // enable trace log for netty and asynchhttpclient
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "io.netty", "trace");
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "org.asynchttpclient", "trace");
        Options options = new Options();
        options.addOption(Option.builder("c").hasArg().required(false).argName("concurrency").desc("Number of multiple sessions to establish at a time").build());
        CommandLineParser parser = new DefaultParser();

        int n = NUM_OF_SESSIONS;

        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder().setConnectionTtl(-1).setReadTimeout(-1).setRequestTimeout(-1).setConnectTimeout(-1).setKeepAlive(true).build();

        try (AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(config)) {
            CommandLine cmd = parser.parse( options, args);
            if (cmd.hasOption("c")) {
                n = Integer.parseInt(cmd.getOptionValue("c"));
            }
            String[] leftArgs = cmd.getArgs();
            String endpoint = leftArgs[0];
            int sessions = n;

            for (int i = 0; i< sessions; i++) { // looping with Stream API causes abnormal behavior of AsyncHttpClient thus using  for loop
                final CompletableFuture<WebSocket> future = asyncHttpClient.prepareGet(endpoint)
                        .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
                                new WebSocketTextListener() {
                                    WebSocket ws;

                                    @Override
                                    public void onOpen(WebSocket webSocket) {
                                        //log.log(Level.INFO, "onOpen");
                                        ws = webSocket;
                                        int count = session_count.incrementAndGet();
                                        if (count == sessions) {
                                            log.log(Level.INFO, sessions + " sessions(s) established.");
                                        }
                                    }

                                    @Override
                                    public void onClose(WebSocket webSocket) {
                                        //log.log(Level.INFO, "onClose");
                                        int count = session_count.decrementAndGet();
                                        if (count == 0) {
                                            log.log(Level.INFO, "all session(s) closed.");
                                            synchronized (lock) {
                                                lock.notify();
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        log.log(Level.WARNING, "onError: " + t.getMessage());
                                    }

                                    @Override
                                    public void onMessage(String message) {
                                        log.log(Level.INFO, message);
                                        int count = push_count.incrementAndGet();
                                        if (count == session_count.get()) {
                                            log.log(Level.INFO, count + " push(es) received.");
                                            synchronized (lock) {
                                                lock.notify();
                                            }
                                        }
                                    }


                                }).build()).toCompletableFuture();
            }
            synchronized(lock) {
                log.log(Level.INFO, "wait");
                lock.wait();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("wrong number of arguments");
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private static void requestPush(String url, String token) {
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
            RequestBuilder builder = new RequestBuilder("GET");
            Request request = builder.setUrl(url)
                    .addHeader("X-Account-Token", token)
                    .setBody("JSON body here")
                    .build();
            asyncHttpClient
                    .executeRequest(request)
                    .toCompletableFuture()
                    .thenApply(Response::getResponseBody)
                    .thenAccept(CompletableFutures::dump)
                    .join();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}