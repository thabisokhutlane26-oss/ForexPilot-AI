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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TwelveDataClient {

    public interface Callback {

        void price(double price);

        void candles(List<Candle> candles);

        void error(String error);
    }

    /*
     * ========================================================
     * API / REQUEST SETTINGS
     * ========================================================
     */

    private static final int NORMAL_CANDLE_SIZE = 100;

    /*
     * 5,000 candles can consume unnecessary API resources.
     * 200 is enough for the app's chart/history display.
     */
    private static final int CHART_CANDLE_SIZE = 200;

    /*
     * Prevent identical candle requests from being sent
     * repeatedly within this period.
     */
    private static final long REQUEST_COOLDOWN_MS = 30_000L;

    /*
     * ========================================================
     * NETWORK CLIENT
     * ========================================================
     */

    private final OkHttpClient client;

    private WebSocket webSocket;

    private final Callback callback;

    private volatile double latestPrice = 0;

    private volatile boolean connected = false;

    private String currentSymbol = "";

    private String currentApiKey = "";

    /*
     * Tracks the last successful/requested candle request.
     *
     * Key:
     * SYMBOL|INTERVAL
     */
    private final Map<String, Long> lastRequestTimes =
            new HashMap<>();

    /*
     * Tracks requests that are currently running.
     *
     * This prevents duplicate API calls when refreshes happen
     * close together.
     */
    private final Set<String> requestsInProgress =
            new HashSet<>();

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

        String cleanSymbol = symbol.trim();
        String cleanApiKey = apiKey.trim();

        /*
         * If we are already connected to the exact same
         * symbol using the same API key, do not reconnect.
         *
         * Reconnecting unnecessarily wastes network/API
         * resources.
         */
        if (connected
                && webSocket != null
                && cleanSymbol.equals(currentSymbol)
                && cleanApiKey.equals(currentApiKey)) {

            return;
        }

        currentSymbol = cleanSymbol;
        currentApiKey = cleanApiKey;

        latestPrice = 0;
        connected = false;

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

                                    String event =
                                            object.optString(
                                                    "event",
                                                    ""
                                            );

                                    if ("error".equalsIgnoreCase(event)) {

                                        callback.error(
                                                object.optString(
                                                        "message",
                                                        "Twelve Data error."
                                                )
                                        );

                                        return;
                                    }

                                    if (object.has("price")) {

                                        double price =
                                                object.optDouble(
                                                        "price",
                                                        0
                                                );

                                        if (price > 0) {

                                            latestPrice = price;

                                            callback.price(price);
                                        }
                                    }

                                } catch (Exception ignored) {
                                    /*
                                     * Ignore malformed individual
                                     * WebSocket messages so one bad
                                     * message does not kill the stream.
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

    public double getLatestPrice() {

        return latestPrice;
    }

    public boolean isConnected() {

        return connected;
    }

    public String getCurrentSymbol() {

        return currentSymbol;
    }

    /*
     * ========================================================
     * NORMAL SIGNAL CANDLE REQUEST
     * ========================================================
     */

    public void candles(
            String symbol,
            String interval,
            String apiKey) {

        requestCandles(
                symbol,
                interval,
                apiKey,
                NORMAL_CANDLE_SIZE
        );
    }

    /*
     * ========================================================
     * CHART HISTORY REQUEST
     * ========================================================
     *
     * We intentionally use 200 instead of 5,000.
     *
     * The app does not need thousands of candles just to
     * display a useful mobile chart.
     * ========================================================
     */

    public void chartCandles(
            String symbol,
            String interval,
            String apiKey) {

        requestCandles(
                symbol,
                interval,
                apiKey,
                CHART_CANDLE_SIZE
        );
    }

    /*
     * ========================================================
     * CANDLE REQUEST
     * ========================================================
     */

    private void requestCandles(
            String symbol,
            String interval,
            String apiKey,
            int outputSize) {

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

        String cleanSymbol = symbol.trim();
        String cleanInterval = interval.trim();
        String cleanApiKey = apiKey.trim();

        String requestKey =
                cleanSymbol
                        + "|"
                        + cleanInterval;

        /*
         * ====================================================
         * DUPLICATE REQUEST PROTECTION
         * ====================================================
         */

        synchronized (this) {

            /*
             * If the same request is already running,
             * don't send another one.
             */
            if (requestsInProgress.contains(requestKey)) {

                return;
            }

            long now =
                    System.currentTimeMillis();

            Long lastRequest =
                    lastRequestTimes.get(
                            requestKey
                    );

            /*
             * If the same request was made recently,
             * skip it.
             */
            if (lastRequest != null
                    && now - lastRequest
                    < REQUEST_COOLDOWN_MS) {

                return;
            }

            requestsInProgress.add(requestKey);

            lastRequestTimes.put(
                    requestKey,
                    now
            );
        }

        String encodedSymbol;
        String encodedInterval;
        String encodedKey;

        try {

            encodedSymbol =
                    URLEncoder.encode(
                            cleanSymbol,
                            StandardCharsets.UTF_8.name()
                    );

            encodedInterval =
                    URLEncoder.encode(
                            cleanInterval,
                            StandardCharsets.UTF_8.name()
                    );

            encodedKey =
                    URLEncoder.encode(
                            cleanApiKey,
                            StandardCharsets.UTF_8.name()
                    );

        } catch (Exception exception) {

            synchronized (this) {
                requestsInProgress.remove(requestKey);
            }

            callback.error(
                    cleanSymbol
                            + ": Unable to prepare candle request."
            );

            return;
        }

        String url =
                "https://api.twelvedata.com/time_series"
                        + "?symbol=" + encodedSymbol
                        + "&interval=" + encodedInterval
                        + "&outputsize=" + outputSize
                        + "&order=asc"
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

                        synchronized (TwelveDataClient.this) {
                            requestsInProgress.remove(
                                    requestKey
                            );
                        }

                        callback.error(
                                cleanSymbol
                                        + ": "
                                        + safeMessage(
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
                                        cleanSymbol
                                                + ": Empty response from Twelve Data."
                                );

                                return;
                            }

                            String body =
                                    response.body().string();

                            if (body == null
                                    || body.trim().isEmpty()) {

                                callback.error(
                                        cleanSymbol
                                                + ": Empty response from Twelve Data."
                                );

                                return;
                            }

                            JSONObject object =
                                    new JSONObject(body);

                            /*
                             * Twelve Data may return HTTP 200
                             * while putting an API error inside
                             * the JSON response.
                             */
                            if (!object.has("values")) {

                                String message =
                                        object.optString(
                                                "message",
                                                ""
                                        );

                                if (message.isEmpty()) {

                                    String code =
                                            object.optString(
                                                    "code",
                                                    ""
                                            );

                                    if (!code.isEmpty()) {

                                        message =
                                                "Twelve Data error code "
                                                        + code
                                                        + ".";
                                    } else {

                                        message =
                                                "No candle data received.";
                                    }
                                }

                                callback.error(
                                        cleanSymbol
                                                + ": "
                                                + message
                                );

                                return;
                            }

                            JSONArray values =
                                    object.getJSONArray(
                                            "values"
                                    );

                            if (values.length() == 0) {

                                callback.error(
                                        cleanSymbol
                                                + ": Twelve Data returned no candles."
                                );

                                return;
                            }

                            List<Candle> list =
                                    new ArrayList<>();

                            for (
                                    int i = 0;
                                    i < values.length();
                                    i++
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
                                        cleanSymbol
                                                + ": No valid candle data received."
                                );

                                return;
                            }

                            callback.candles(list);

                        } catch (Exception exception) {

                            callback.error(
                                    cleanSymbol
                                            + ": "
                                            + safeMessage(
                                                    exception,
                                                    "Unable to read candle data."
                                            )
                            );

                        } finally {

                            synchronized (TwelveDataClient.this) {
                                requestsInProgress.remove(
                                        requestKey
                                );
                            }

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
         * Clear request state when this client is closed.
         */
        requestsInProgress.clear();
        lastRequestTimes.clear();
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