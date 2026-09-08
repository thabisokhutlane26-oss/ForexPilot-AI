package com.forexpilot.ai;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TwelveDataClient {

    public interface Callback {

        void price(double price);

        void candles(List<Candle> candles);

        void error(String error);
    }

    private final OkHttpClient client;

    private WebSocket webSocket;

    private final Callback callback;

    private volatile double latestPrice = 0;

    private volatile boolean connected = false;

    private String currentSymbol = "";

    private String currentApiKey = "";

    public TwelveDataClient(Callback callback) {

        this.callback = callback;

        client = new OkHttpClient.Builder()
                .connectTimeout(
                        15,
                        TimeUnit.SECONDS
                )
                .writeTimeout(
                        15,
                        TimeUnit.SECONDS
                )
                .readTimeout(
                        0,
                        TimeUnit.MILLISECONDS
                )
                .build();
    }

    /*
     * ========================================================
     * LIVE PRICE CONNECTION
     * ========================================================
     */

    public synchronized void connect(
            String symbol,
            String apiKey) {

        if (symbol == null
                || symbol.trim().isEmpty()) {

            callback.error(
                    "Market symbol is missing."
            );

            return;
        }

        if (apiKey == null
                || apiKey.trim().isEmpty()) {

            callback.error(
                    "Twelve Data API key is missing."
            );

            return;
        }

        currentSymbol =
                symbol.trim();

        currentApiKey =
                apiKey.trim();

        latestPrice = 0;

        connected = false;

        /*
         * Close an old connection first.
         */

        if (webSocket != null) {

            webSocket.close(
                    1000,
                    "Reconnecting"
            );

            webSocket = null;
        }

        String encodedKey;

        try {

            encodedKey =
                    URLEncoder.encode(
                            currentApiKey,
                            StandardCharsets.UTF_8.name()
                    );

        } catch (Exception exception) {

            callback.error(
                    "Unable to prepare API connection."
            );

            return;
        }

        String url =
                "wss://ws.twelvedata.com/v1/quotes/price?apikey="
                        + encodedKey;

        Request request =
                new Request.Builder()
                        .url(url)
                        .build();

        webSocket =
                client.newWebSocket(
                        request,
                        new WebSocketListener() {

                            @Override
                            public void onOpen(
                                    WebSocket socket,
                                    Response response) {

                                connected = true;

                                try {

                                    JSONObject message =
                                            new JSONObject();

                                    message.put(
                                            "action",
                                            "subscribe"
                                    );

                                    JSONObject params =
                                            new JSONObject();

                                    params.put(
                                            "symbols",
                                            currentSymbol
                                    );

                                    message.put(
                                            "params",
                                            params
                                    );

                                    socket.send(
                                            message.toString()
                                    );

                                } catch (Exception exception) {

                                    callback.error(
                                            safeMessage(
                                                    exception,
                                                    "Unable to subscribe to market."
                                            )
                                    );
                                }
                            }

                            @Override
                            public void onMessage(
                                    WebSocket socket,
                                    String text) {

                                if (text == null
                                        || text.trim().isEmpty()) {

                                    return;
                                }

                                try {

                                    JSONObject object =
                                            new JSONObject(text);

                                    /*
                                     * Twelve Data may send
                                     * heartbeat/status messages.
                                     */

                                    String event =
                                            object.optString(
                                                    "event",
                                                    ""
                                            );

                                    if ("error".equalsIgnoreCase(
                                            event
                                    )) {

                                        callback.error(
                                                object.optString(
                                                        "message",
                                                        "Twelve Data error."
                                                )
                                        );

                                        return;
                                    }

                                    /*
                                     * Live price.
                                     */

                                    if (object.has("price")) {

                                        double price =
                                                object.optDouble(
                                                        "price",
                                                        0
                                                );

                                        if (price > 0) {

                                            latestPrice =
                                                    price;

                                            callback.price(
                                                    price
                                            );
                                        }
                                    }

                                } catch (Exception ignored) {

                                    /*
                                     * Ignore malformed or
                                     * non-price WebSocket
                                     * messages.
                                     */
                                }
                            }

                            @Override
                            public void onClosing(
                                    WebSocket socket,
                                    int code,
                                    String reason) {

                                connected = false;
                            }

                            @Override
                            public void onClosed(
                                    WebSocket socket,
                                    int code,
                                    String reason) {

                                connected = false;
                            }

                            @Override
                            public void onFailure(
                                    WebSocket socket,
                                    Throwable throwable,
                                    Response response) {

                                connected = false;

                                callback.error(
                                        safeMessage(
                                                throwable,
                                                "WebSocket connection failed."
                                        )
                                );
                            }
                        }
                );
    }

    /*
     * ========================================================
     * GET LAST LIVE PRICE
     * ========================================================
     */

    public double getLatestPrice() {

        return latestPrice;
    }

    /*
     * ========================================================
     * CONNECTION STATUS
     * ========================================================
     */

    public boolean isConnected() {

        return connected;
    }

    /*
     * ========================================================
     * GET CURRENT SYMBOL
     * ========================================================
     */

    public String getCurrentSymbol() {

        return currentSymbol;
    }

    /*
     * ========================================================
     * GET CANDLES
     * ========================================================
     */

    public void candles(
            String symbol,
            String interval,
            String apiKey) {

        if (symbol == null
                || symbol.trim().isEmpty()) {

            callback.error(
                    "Market symbol is missing."
            );

            return;
        }

        if (interval == null
                || interval.trim().isEmpty()) {

            callback.error(
                    "Timeframe is missing."
            );

            return;
        }

        if (apiKey == null
                || apiKey.trim().isEmpty()) {

            callback.error(
                    "Twelve Data API key is missing."
            );

            return;
        }

        String encodedSymbol;

        String encodedInterval;

        String encodedKey;

        try {

            encodedSymbol =
                    URLEncoder.encode(
                            symbol.trim(),
                            StandardCharsets.UTF_8.name()
                    );

            encodedInterval =
                    URLEncoder.encode(
                            interval.trim(),
                            StandardCharsets.UTF_8.name()
                    );

            encodedKey =
                    URLEncoder.encode(
                            apiKey.trim(),
                            StandardCharsets.UTF_8.name()
                    );

        } catch (Exception exception) {

            callback.error(
                    "Unable to prepare candle request."
            );

            return;
        }

        String url =
                "https://api.twelvedata.com/time_series"
                        + "?symbol=" + encodedSymbol
                        + "&interval=" + encodedInterval
                        + "&outputsize=100"
                        + "&apikey=" + encodedKey;

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .build();

        client.newCall(request).enqueue(
                new okhttp3.Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException exception) {

                        callback.error(
                                safeMessage(
                                        exception,
                                        "Candle request failed."
                                )
                        );
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response) {

                        try {

                            if (response.body() == null) {

                                callback.error(
                                        "Empty response from Twelve Data."
                                );

                                return;
                            }

                            String body =
                                    response.body().string();

                            if (body == null
                                    || body.trim().isEmpty()) {

                                callback.error(
                                        "Empty response from Twelve Data."
                                );

                                return;
                            }

                            JSONObject object =
                                    new JSONObject(body);

                            /*
                             * API errors do not contain
                             * a values array.
                             */

                            if (!object.has("values")) {

                                String message =
                                        object.optString(
                                                "message",
                                                ""
                                        );

                                if (message.isEmpty()) {

                                    message =
                                            "No candle data received.";
                                }

                                callback.error(
                                        message
                                );

                                return;
                            }

                            JSONArray values =
                                    object.getJSONArray(
                                            "values"
                                    );

                            if (values.length() == 0) {

                                callback.error(
                                        "Twelve Data returned no candles."
                                );

                                return;
                            }

                            List<Candle> list =
                                    new ArrayList<>();

                            /*
                             * Twelve Data normally returns
                             * newest candle first.
                             *
                             * Reverse it so our app has:
                             *
                             * oldest → newest
                             */

                            for (
                                    int i =
                                            values.length() - 1;
                                    i >= 0;
                                    i--
                            ) {

                                JSONObject candle =
                                        values.getJSONObject(i);

                                double open =
                                        candle.optDouble(
                                                "open",
                                                0
                                        );

                                double high =
                                        candle.optDouble(
                                                "high",
                                                0
                                        );

                                double low =
                                        candle.optDouble(
                                                "low",
                                                0
                                        );

                                double close =
                                        candle.optDouble(
                                                "close",
                                                0
                                        );

                                if (open <= 0
                                        || high <= 0
                                        || low <= 0
                                        || close <= 0) {

                                    continue;
                                }

                                list.add(
                                        new Candle(
                                                open,
                                                high,
                                                low,
                                                close
                                        )
                                );
                            }

                            if (list.isEmpty()) {

                                callback.error(
                                        "No valid candle data received."
                                );

                                return;
                            }

                            callback.candles(
                                    list
                            );

                        } catch (Exception exception) {

                            callback.error(
                                    safeMessage(
                                            exception,
                                            "Unable to read candle data."
                                    )
                            );

                        } finally {

                            response.close();
                        }
                    }
                }
        );
    }

    /*
     * ========================================================
     * CLOSE CONNECTION
     * ========================================================
     */

    public synchronized void close() {

        connected = false;

        latestPrice = 0;

        if (webSocket != null) {

            webSocket.close(
                    1000,
                    "Closed"
            );

            webSocket = null;
        }

        /*
         * Do not permanently shut down the OkHttp
         * executor here.
         *
         * The same client may be reused later
         * when the user changes market/timeframe.
         */
    }

    /*
     * ========================================================
     * SAFE ERROR MESSAGE
     * ========================================================
     */

    private static String safeMessage(
            Throwable throwable,
            String fallback) {

        if (throwable == null) {

            return fallback;
        }

        String message =
                throwable.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return fallback;
        }

        return message;
    }
}
