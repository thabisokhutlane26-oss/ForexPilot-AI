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

    public TwelveDataClient(Callback callback) {

        this.callback = callback;

        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void connect(String symbol, String apiKey) {

        if (apiKey == null || apiKey.trim().isEmpty()) {

            callback.error(
                    "Twelve Data API key is missing."
            );

            return;
        }

        String url =
                "wss://ws.twelvedata.com/v1/quotes/price?apikey="
                        + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = client.newWebSocket(
                request,
                new WebSocketListener() {

                    @Override
                    public void onOpen(
                            WebSocket socket,
                            Response response) {

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
                                    symbol
                            );

                            message.put(
                                    "params",
                                    params
                            );

                            socket.send(
                                    message.toString()
                            );

                        } catch (Exception e) {

                            callback.error(
                                    e.getMessage()
                            );
                        }
                    }

                    @Override
                    public void onMessage(
                            WebSocket socket,
                            String text) {

                        try {

                            JSONObject object =
                                    new JSONObject(text);

                            if (object.has("price")) {

                                double price =
                                        object.optDouble(
                                                "price",
                                                0
                                        );

                                if (price > 0) {
                                    callback.price(price);
                                }
                            }

                            if ("error".equals(
                                    object.optString("event")
                            )) {

                                callback.error(
                                        object.optString(
                                                "message",
                                                "Twelve Data error"
                                        )
                                );
                            }

                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onFailure(
                            WebSocket socket,
                            Throwable throwable,
                            Response response) {

                        String message =
                                throwable.getMessage();

                        if (message == null) {
                            message = "WebSocket connection failed.";
                        }

                        callback.error(message);
                    }
                }
        );
    }

    public void candles(
            String symbol,
            String interval,
            String apiKey) {

        if (apiKey == null || apiKey.trim().isEmpty()) {

            callback.error(
                    "Twelve Data API key is missing."
            );

            return;
        }

        String url =
                "https://api.twelvedata.com/time_series"
                        + "?symbol=" + symbol
                        + "&interval=" + interval
                        + "&outputsize=100"
                        + "&apikey=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(
                new okhttp3.Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException exception) {

                        callback.error(
                                exception.getMessage()
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

                            JSONObject object =
                                    new JSONObject(body);

                            if (!object.has("values")) {

                                callback.error(
                                        object.optString(
                                                "message",
                                                "No candle data received."
                                        )
                                );

                                return;
                            }

                            JSONArray values =
                                    object.getJSONArray(
                                            "values"
                                    );

                            List<Candle> list =
                                    new ArrayList<>();

                            for (
                                    int i = values.length() - 1;
                                    i >= 0;
                                    i--
                            ) {

                                JSONObject candle =
                                        values.getJSONObject(i);

                                double open =
                                        candle.getDouble("open");

                                double high =
                                        candle.getDouble("high");

                                double low =
                                        candle.getDouble("low");

                                double close =
                                        candle.getDouble("close");

                                list.add(
                                        new Candle(
                                                open,
                                                high,
                                                low,
                                                close
                                        )
                                );
                            }

                            callback.candles(list);

                        } catch (Exception exception) {

                            callback.error(
                                    exception.getMessage()
                            );
                        }
                    }
                }
        );
    }

    public void close() {

        if (webSocket != null) {

            webSocket.close(
                    1000,
                    "Closed"
            );
        }

        client.dispatcher()
                .executorService()
                .shutdown();
    }
}
