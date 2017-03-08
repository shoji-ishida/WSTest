package jp.catalyna;

/**
 * Created by ishida on 2017/03/07.
 */
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;

import java.io.IOException;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class CompletableFutures {
    public static void main(String[] args) throws IOException {
            try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
                asyncHttpClient
                        .prepareGet("http://yanase-W331AU.local:8080/SpringTest_war/spring")
                        .execute()
                        .toCompletableFuture()
                        .thenApply(Response::getResponseBody)
                        .thenAccept(CompletableFutures::dump)
                        .join();
            }
        }

    public static void dump(String string) {
        System.out.println(string);
    }
}