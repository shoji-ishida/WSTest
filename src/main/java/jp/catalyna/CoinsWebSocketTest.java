package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import jp.catalyna.bean.Counter;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.asynchttpclient.*;
import org.asynchttpclient.util.HttpConstants;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.impl.SimpleLogger;

import javax.management.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final String MESSAGE_TYPE = "type";
    private static final String MESSAGE_TYPE_DISPLAY = "display";
    private static final String MESSAGE_TYPE_CLICK = "click";
    private static final String MESSAGE_ID = "messageId";

    private static final String TIME_FORMAT = "mm:ss.SSS";

    private static final String OPTION_N = "n";
    private static final String OPTION_C = "c";
    private static final String OPTION_R = "r";
    private static final String OPTION_L = "l";

    private static final String OPTION_CLOSE_ON_OPEN = "closeOnOpen";
    private static final String OPTION_CLOSE_ON_MESSAGE = "closeOnMessage";
    private static final String OPTION_PUSH_ON_OPEN = "pushOnOpen";
    private static final String OPTION_SEND_DISPLAY = "sendDisplay";
    private static final String OPTION_SEND_CLICK = "sendClick";

    private static final int NUM_OF_SESSIONS = 1000;
    private static final Logger log = Logger.getLogger(CoinsWebSocketTest.class.getName());

    private static final AtomicInteger session_count = new AtomicInteger(0);
    private static final AtomicInteger concurrent_count = new AtomicInteger(0);
    private static final AtomicInteger close_count = new AtomicInteger(0);

    private static final AtomicInteger push_count = new AtomicInteger(0);
    private static final AtomicInteger display_count = new AtomicInteger(0);
    private static final AtomicInteger click_count = new AtomicInteger(0);


    private static final Object lock = new Object();
    private static final Object concurrencyLock = new Object();

    private static long startTime = 0;

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        // enable trace log for netty and asynchhttpclient
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "io.netty", "trace");
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "org.asynchttpclient", "trace");
        Options options = new Options();
        options.addOption(Option.builder(OPTION_C).hasArg().required(false).argName("concurrency").desc("Number of multiple sessions to establish at a time").build());
        options.addOption(Option.builder(OPTION_N).hasArg().required(false).argName("requests").desc("Number of request to perform").build());
        options.addOption(Option.builder(OPTION_R).hasArg(false).required(false).desc("Generate random UUID for URL parameter").build());
        options.addOption(Option.builder(OPTION_L).hasArg().optionalArg(true).required(false).desc("Repeat establishing sessions after all sessions are closed").build());

        options.addOption(Option.builder(OPTION_CLOSE_ON_OPEN).hasArg(false).required(false).desc("Close websocket connection when onOpen is called").build());
        options.addOption(Option.builder(OPTION_CLOSE_ON_MESSAGE).hasArg(false).required(false).desc("Close websocket connection when onMessage is called").build());
        options.addOption(Option.builder(OPTION_PUSH_ON_OPEN).hasArgs().numberOfArgs(3).required(false).desc("Request push when onOpen").build());
        options.addOption(Option.builder(OPTION_SEND_DISPLAY).hasArg(false).required(false).desc("Send display message on onMassage").build());
        options.addOption(Option.builder(OPTION_SEND_CLICK).hasArg(false).required(false).desc("Send click message on onMassage").build());
        CommandLineParser parser = new DefaultParser();

        int c = NUM_OF_SESSIONS;
        int n = c;
        boolean isRandomUuid = false;

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName mbeanName = new ObjectName("jp.catalyna:type=WebSocket");
        Counter counter = new Counter();
        mbs.registerMBean(counter, mbeanName);

        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder().setConnectionTtl(-1).setReadTimeout(-1).setRequestTimeout(-1).setConnectTimeout(-1).setKeepAlive(true).build();

        try (AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(config)) {
            CommandLine cmd = parser.parse( options, args);
            if (cmd.hasOption(OPTION_C)) {
                c = Integer.parseInt(cmd.getOptionValue(OPTION_C));
            }
            if (cmd.hasOption(OPTION_N)) {
                n = Integer.parseInt(cmd.getOptionValue(OPTION_N));
                if (n < c) {
                    System.err.println("c can not be bigger than n");
                    System.exit(-1);
                }
            }
            if (cmd.hasOption(OPTION_R)) {
                isRandomUuid = true;
            }
            final boolean isCloseOnOpen = cmd.hasOption(OPTION_CLOSE_ON_OPEN);
            final boolean isCloseOnMessage = cmd.hasOption(OPTION_CLOSE_ON_MESSAGE);
            final boolean isSendDisplay = cmd.hasOption(OPTION_SEND_DISPLAY);
            final boolean isSendClick = cmd.hasOption(OPTION_SEND_CLICK);
            final boolean isPushOnOpen = cmd.hasOption(OPTION_PUSH_ON_OPEN);
            final boolean isLoop = cmd.hasOption(OPTION_L);
            String lOption = cmd.getOptionValue(OPTION_L);
            final int loopCount = (lOption != null) ? Integer.parseInt(lOption) : -1;

            String pushValues[] = isPushOnOpen ? cmd.getOptionValues(OPTION_PUSH_ON_OPEN) : null;
            if (isPushOnOpen) {
                Path path = FileSystems.getDefault().getPath(pushValues[2]);
                if (Files.notExists(path)) {
                    System.err.println("File does not exist: " + path.toString());
                    System.exit(-1);
                }
            }

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

            int count = 0;
            while (isLoop) {
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
                                            counter.putActiveSession(sessionCount);
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
                                                String duration = DurationFormatUtils.formatPeriod(startTime, endTime, TIME_FORMAT);
                                                log.log(Level.INFO, "Completed " + sessionCount + " requests. (" + duration + ")");
                                                synchronized (concurrencyLock) {
                                                    concurrencyLock.notify();
                                                }
                                            }
                                            if (sessionCount == requests) {
                                                log.log(Level.INFO, requests + " sessions(s) established. Ready to push");
                                                if (isPushOnOpen) {
                                                    log.log(Level.INFO, "Requesting push");
                                                    log.log(Level.INFO, "url=" + pushValues[0] + ", token=" + pushValues[1] + ", json=" + pushValues[2]);
                                                    Path path = FileSystems.getDefault().getPath(pushValues[2]);
                                                    try {
                                                        InputStream is = Files.newInputStream(path);
                                                        requestPush(pushValues[0], pushValues[1], is);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
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
                                            // respond with display/click message
                                            if (isSendClick && isSendDisplay) {
                                                String messageId = parseMessageId(message);
                                                // display
                                                if (isSendDisplay) {
                                                    String jsonString = createJson(MESSAGE_TYPE_DISPLAY, messageId);
                                                    sendMessage(ws, jsonString);
                                                    int count = display_count.incrementAndGet();
                                                    counter.putDisplayCount(count);
                                                }
                                                // click
                                                if (isSendClick) {
                                                    String jsonString = createJson(MESSAGE_TYPE_CLICK, messageId);
                                                    sendMessage(ws, jsonString);
                                                    int count = click_count.incrementAndGet();
                                                    counter.putClickCount(count);
                                                }
                                            }
                                            //log.log(Level.INFO, message);
                                            int count = push_count.incrementAndGet();
                                            counter.putPushCount(count);
                                            if (count == 0) {
                                                startTime = System.currentTimeMillis();
                                            }
                                            if (count == session_count.get()) {
                                                long endTime = System.currentTimeMillis();
                                                String duration = DurationFormatUtils.formatPeriod(startTime, endTime, TIME_FORMAT);
                                                log.log(Level.INFO, count + " push(es) received. (" + duration + ")");
                                                push_count.set(0);
                                                if (isCloseOnMessage) {
                                                    try {
                                                        ws.close();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }


                                    }).build()).toCompletableFuture();
                }

                synchronized (lock) {
                    //log.log(Level.INFO, "wait on lock");
                    lock.wait();
                }
                if (loopCount != -1) {
                    count++;
                    //log.log(Level.INFO, "count = " + count);
                    if (count == loopCount) {
                        break;
                    }
                }
                session_count.set(0);
                close_count.set(0);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("wrong number of arguments");
        } catch (ParseException e) {
            System.err.println(e.getLocalizedMessage());
            //e.printStackTrace();
        }
    }

    private static void requestPush(String url, String token, InputStream is) {
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
            RequestBuilder builder = new RequestBuilder(HttpConstants.Methods.PUT);
            Request request = builder.setUrl(url)
                    .addHeader("X-Account-Token", token)
                    .setBody(is)
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

    private static JsonFactory jsonFactory = new JsonFactory();
    private static String parseMessageId(String message) {
        String messageId = null;
        try {
            JsonParser parser = jsonFactory.createParser(message);
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected data to start with an Object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                if (fieldName.equals(MESSAGE_ID)) {
                    messageId = parser.getValueAsString();
                    break;
                }
            }
            parser.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return messageId;
    }

    private static String createJson(String type, String messageId) {
        String jsonString = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator generator = jsonFactory.createGenerator(bos);
            generator.writeStartObject();
            generator.writeStringField(MESSAGE_TYPE, type);
            generator.writeStringField(MESSAGE_ID, messageId);
            generator.writeEndObject();
            generator.close();
            jsonString = bos.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonString;
    }

    private static void sendMessage(WebSocket webSocket, String message) {
        webSocket.sendMessage(message);
    }

    //private static String testMessage = "{\"type\":\"show\",\"messageId\":\"10000034\",\"messageType\":\"1\"}";
}