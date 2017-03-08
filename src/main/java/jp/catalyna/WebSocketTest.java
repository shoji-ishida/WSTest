package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class WebSocketTest {
    private static final String ENDPOINT = "wss://soc.webpush.jp/coins_websocket/push/10000001/74c0575d-6166-458b-81e5-89b0f06b150c/aHR0cDovL2NoZWNrLndlYnB1c2guanAveWFuYXNlL2NvaW5zMi9pbmRleC5odG1s";
    private static final Logger log = Logger.getLogger(WebSocketTest.class.getName());
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {

            WebSocket websocket = asyncHttpClient.prepareGet(ENDPOINT)
                    .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
                            new WebSocketTextListener() {

                                @Override
                                public void onMessage(String message) {
                                    log.log(Level.INFO, message);
                                }


                                @Override
                                public void onOpen(WebSocket webSocket) {
                                    log.log(Level.INFO, "onOpen");
                                }

                                @Override
                                public void onClose(WebSocket webSocket) {
                                    log.log(Level.INFO, "onClose");
                                }

                                @Override
                                public void onError(Throwable t) {
                                }
                            }).build())
                    .get();
        }
    }
}