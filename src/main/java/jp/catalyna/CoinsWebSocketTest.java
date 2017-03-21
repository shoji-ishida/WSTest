package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.apache.commons.cli.*;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.asynchttpclient.*;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private static final AtomicInteger concurrent_count = new AtomicInteger(0);
    private static final AtomicInteger close_count = new AtomicInteger(0);

    private static final AtomicInteger push_count = new AtomicInteger(0);

    private static final Object lock = new Object();
    private static final Object concurrencyLock = new Object();

    private static long startTime = 0;

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        // enable trace log for netty and asynchhttpclient
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "io.netty", "trace");
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "org.asynchttpclient", "trace");
        Options options = new Options();
        options.addOption(Option.builder("c").hasArg().required(false).argName("concurrency").desc("Number of multiple sessions to establish at a time").build());
        options.addOption(Option.builder("n").hasArg().required(false).argName("requests").desc("Number of request to perform").build());

        options.addOption(Option.builder("r").hasArg(false).required(false).desc("Generate random UUID for URL parameter").build());
        options.addOption(Option.builder("closeOnOpen").hasArg(false).required(false).desc("Close websocket connection when onOpen is called").build());
        options.addOption(Option.builder("click").hasArg(false).required(false).desc("Send click message on onMassage").build());
        CommandLineParser parser = new DefaultParser();

        int c = NUM_OF_SESSIONS;
        int n = c;
        boolean isRandomUuid = false;

        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder().setConnectionTtl(-1).setReadTimeout(-1).setRequestTimeout(-1).setConnectTimeout(-1).setKeepAlive(true).build();

        try (AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(config)) {
            CommandLine cmd = parser.parse( options, args);
            if (cmd.hasOption("c")) {
                c = Integer.parseInt(cmd.getOptionValue("c"));
            }
            if (cmd.hasOption("n")) {
                n = Integer.parseInt(cmd.getOptionValue("n"));
                if (n < c) {
                    System.err.println("c can not be bigger than n");
                    System.exit(-1);
                }
            }
            if (cmd.hasOption("r")) {
                isRandomUuid = true;
            }
            final boolean isCloseOnOpen = cmd.hasOption("closeOnOpen");

            String[] leftArgs = cmd.getArgs();
            String endpoint = leftArgs[0];
            int concurrent = c;
            int requests = n;

            List<String> endpoints = null;
            if (isRandomUuid) {
                String ref = leftArgs[1];
                String pre = endpoint;
                endpoints = Stream.generate(() -> {
                    return pre + UUID.randomUUID().toString() + ref;
                }).limit(requests).collect(Collectors.toList());
            }

            startTime = System.currentTimeMillis();
            for (int i = 0, j = 0; i < requests; i++, j++) { // looping with Stream API causes abnormal behavior of AsyncHttpClient thus using  for loop
                if (isRandomUuid) {
                    endpoint = endpoints.get(i);
                }
                if (j == concurrent) {
                    j = 0;
                    //log.log(Level.INFO, "wait on concurrencyLock");
                    synchronized (concurrencyLock) {
                        concurrencyLock.wait();
                    }
                    startTime = System.currentTimeMillis();
                }

                final CompletableFuture<WebSocket> future = asyncHttpClient.prepareGet(endpoint)
                        .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
                                new WebSocketTextListener() {
                                    WebSocket ws;

                                    @Override
                                    public void onOpen(WebSocket webSocket) {
                                        //log.log(Level.INFO, "onOpen");
                                        ws = webSocket;
                                        int sessionCount = session_count.incrementAndGet();
                                        int concurrentCount = concurrent_count.incrementAndGet();
                                        if (isCloseOnOpen) {
                                            try {
                                                webSocket.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        if (concurrentCount == concurrent) {
                                            concurrent_count.set(0);
                                            long endTime = System.currentTimeMillis();
                                            String duration = DurationFormatUtils.formatPeriod(startTime, endTime, "mm:ss.SSS");
                                            log.log(Level.INFO, "Completed " + sessionCount + " requests (" + duration +")");
                                            synchronized (concurrencyLock) {
                                                concurrencyLock.notify();
                                            }
                                        }
                                        if (sessionCount == requests) {
                                            log.log(Level.INFO, requests + " sessions(s) established. Ready to push");
                                        }
                                    }

                                    @Override
                                    public void onClose(WebSocket webSocket) {
                                        //log.log(Level.INFO, "onClose");
                                        int count = close_count.incrementAndGet();
                                        if (count == requests) {
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
                                        //log.log(Level.INFO, message);
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
                //log.log(Level.INFO, "wait on lock");
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