package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

// Connect to echo websocket server and wait for echo
public class EchoWebSocketTest {
    //private static final String ENDPOINT = "wss://soc.webpush.jp/coins_websocket/push/10000001/74c0575d-6166-458b-81e5-89b0f06b150c/aHR0cDovL2NoZWNrLndlYnB1c2guanAveWFuYXNlL2NvaW5zMi9pbmRleC5odG1s";
    private static final String ENDPOINT = "ws://echo.websocket.org";
    //private static final String ENDPOINT = "ws://localhost:8080/websocket";

    private static final Logger log = Logger.getLogger(EchoWebSocketTest.class.getName());

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        // enable trace log for netty and asynchhttpclient
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "io.netty", "trace");
        //System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "org.asynchttpclient", "trace");

        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder().setConnectionTtl(-1).setReadTimeout(-1).setRequestTimeout(-1).setConnectTimeout(-1).setKeepAlive(true).build();
        final Object lock = new Object();

        try (AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(config)) {
            final CompletableFuture<WebSocket> future  = asyncHttpClient.prepareGet(ENDPOINT)
                    .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
                            new WebSocketTextListener() {
                                WebSocket ws;
                                int count;

                                @Override
                                public void onOpen(WebSocket webSocket) {
                                    log.log(Level.INFO, "onOpen");
                                    webSocket.sendMessage("Hello");
                                    ws = webSocket;
                                }

                                @Override
                                public void onClose(WebSocket webSocket) {
                                    log.log(Level.INFO, "onClose");
                                }

                                @Override
                                public void onError(Throwable t) {
                                    log.log(Level.WARNING, "onError: " + t.getMessage());
                                }

                                @Override
                                public void onMessage(String message) {
                                    log.log(Level.INFO, message);
                                    if (count++ >= 10) {
                                        synchronized(lock) {
                                            lock.notify();
                                        }
                                    } else {
                                        ws.sendMessage("Hi:"+count);
                                    }
                                }


                            }).build()).toCompletableFuture();
            WebSocket websocket = future.join();
            //websocket.sendMessage("Hey");
            synchronized(lock) {
                lock.wait();
            }
        }
    }
}