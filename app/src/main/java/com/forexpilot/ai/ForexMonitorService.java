package com.forexpilot.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ForexMonitorService extends Service {

    private static final String CHANNEL_ID =
            "forexpilot_monitor";

    private static final int FOREGROUND_ID =
            9001;

    /*
     * IMPORTANT:
     *
     * Background scanning is now every 15 minutes.
     *
     * We scan ONLY the timeframe selected
     * inside the ForexPilot AI app.
     */
    private static final long SCAN_INTERVAL_SECONDS =
            15L * 60L;

    private static final String PREFS_NAME =
            "forexpilot_settings";

    private static final String PREF_SELECTED_TIMEFRAME =
            "selected_timeframe";

    /*
     * Markets monitored in the background.
     */
    private static final String[] SYMBOLS = {
            "XAU/USD",
            "EUR/USD",
            "GBP/USD",
            "USD/JPY",
            "NZD/USD"
    };

    private ScheduledExecutorService scheduler;

    private OkHttpClient httpClient;

    /*
     * Prevent repeated notifications for the
     * same symbol + timeframe + signal.
     */
    private final Map<String, String> lastAlertFingerprint =
            new HashMap<>();

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        Notification notification =
                buildMonitoringNotification();

        /*
         * Android 14+ supports SPECIAL_USE.
         */
        if (Build.VERSION.SDK_INT >= 34) {

            startForeground(
                    FOREGROUND_ID,
                    notification,
                    android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );

        } else {

            startForeground(
                    FOREGROUND_ID,
                    notification
            );
        }

        httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(
                                15,
                                TimeUnit.SECONDS
                        )
                        .readTimeout(
                                20,
                                TimeUnit.SECONDS
                        )
                        .build();

        scheduler =
                Executors.newSingleThreadScheduledExecutor();

        /*
         * First scan immediately.
         *
         * After that:
         * one scan every 15 minutes.
         */
        scheduler.scheduleWithFixedDelay(
                this::scanMarkets,
                0,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void scanMarkets() {

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null
                || apiKey.trim().isEmpty()) {

            return;
        }

        /*
         * Read the timeframe currently selected
         * in the ForexPilot AI app.
         */
        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String selectedTimeframe =
                preferences.getString(
                        PREF_SELECTED_TIMEFRAME,
                        "5min"
                );

        /*
         * Safety check.
         *
         * Only allow the six timeframes used
         * by ForexPilot AI.
         */
        if (!isAllowedTimeframe(
                selectedTimeframe)) {

            selectedTimeframe = "5min";
        }

        /*
         * IMPORTANT:
         *
         * We DO NOT loop through six timeframes.
         *
         * Only the selected timeframe is scanned.
         *
         * Example:
         *
         * Selected = 15M
         *
         * Requests:
         * XAU/USD 15M
         * EUR/USD 15M
         * GBP/USD 15M
         * USD/JPY 15M
         * NZD/USD 15M
         *
         * Then wait 15 minutes.
         */
        for (String symbol : SYMBOLS) {

            try {

                List<Candle> candles =
                        getCandles(
                                symbol,
                                selectedTimeframe,
                                apiKey
                        );

                if (candles == null
                        || candles.isEmpty()) {

                    continue;
                }

                /*
                 * Use the newest candle close as
                 * the current background price.
                 */
                double livePrice =
                        candles.get(
                                candles.size() - 1
                        ).close;

                SignalResult result =
                        SignalEngine.analyze(
                                candles,
                                livePrice
                        );

                if (result == null) {
                    continue;
                }

                /*
                 * WAIT does not create a notification.
                 */
                if (!"BUY".equals(result.action)
                        && !"SELL".equals(result.action)) {

                    continue;
                }

                if (result.entry <= 0) {
                    continue;
                }

                notifyIfNew(
                        symbol,
                        selectedTimeframe,
                        result
                );

            } catch (Exception ignored) {

                /*
                 * If one market fails,
                 * continue with the next market.
                 */
            }
        }
    }

    private boolean isAllowedTimeframe(
            String interval) {

        return "5min".equals(interval)
                || "15min".equals(interval)
                || "30min".equals(interval)
                || "1h".equals(interval)
                || "4h".equals(interval)
                || "1day".equals(interval);
    }

    private List<Candle> getCandles(
            String symbol,
            String interval,
            String apiKey)
            throws Exception {

        String encodedSymbol =
                URLEncoder.encode(
                        symbol,
                        StandardCharsets.UTF_8.name()
                );

        String encodedInterval =
                URLEncoder.encode(
                        interval,
                        StandardCharsets.UTF_8.name()
                );

        String encodedKey =
                URLEncoder.encode(
                        apiKey,
                        StandardCharsets.UTF_8.name()
                );

        String url =
                "https://api.twelvedata.com/time_series"
                        + "?symbol="
                        + encodedSymbol
                        + "&interval="
                        + encodedInterval
                        + "&outputsize=100"
                        + "&apikey="
                        + encodedKey;

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .build();

        try (Response response =
                     httpClient
                             .newCall(request)
                             .execute()) {

            if (!response.isSuccessful()
                    || response.body() == null) {

                return null;
            }

            String body =
                    response.body().string();

            if (body == null
                    || body.trim().isEmpty()) {

                return null;
            }

            JSONObject object =
                    new JSONObject(body);

            /*
             * Twelve Data can return an error
             * object instead of values.
             */
            if (!object.has("values")) {
                return null;
            }

            JSONArray values =
                    object.getJSONArray("values");

            List<Candle> candles =
                    new ArrayList<>();

            /*
             * Twelve Data normally returns newest
             * candle first.
             *
             * SignalEngine receives:
             *
             * oldest -> newest
             */
            for (int i = values.length() - 1;
                 i >= 0;
                 i--) {

                JSONObject item =
                        values.getJSONObject(i);

                double open =
                        item.optDouble(
                                "open",
                                0
                        );

                double high =
                        item.optDouble(
                                "high",
                                0
                        );

                double low =
                        item.optDouble(
                                "low",
                                0
                        );

                double close =
                        item.optDouble(
                                "close",
                                0
                        );

                if (open <= 0
                        || high <= 0
                        || low <= 0
                        || close <= 0) {

                    continue;
                }

                candles.add(
                        new Candle(
                                open,
                                high,
                                low,
                                close
                        )
                );
            }

            return candles;
        }
    }

    private void notifyIfNew(
            String symbol,
            String interval,
            SignalResult result) {

        String fingerprint =
                buildFingerprint(
                        symbol,
                        interval,
                        result
                );

        /*
         * Each symbol + timeframe has its own
         * notification memory.
         */
        String alertKey =
                symbol
                        + "|"
                        + interval;

        String previous =
                lastAlertFingerprint.get(
                        alertKey
                );

        /*
         * Same signal = no duplicate notification.
         */
        if (fingerprint.equals(previous)) {
            return;
        }

        lastAlertFingerprint.put(
                alertKey,
                fingerprint
        );

        sendSignalNotification(
                symbol,
                interval,
                result
        );
    }

    private String buildFingerprint(
            String symbol,
            String interval,
            SignalResult result) {

        return symbol
                + "|"
                + interval
                + "|"
                + result.action
                + "|"
                + formatNumber(result.entry)
                + "|"
                + formatNumber(result.sl)
                + "|"
                + formatNumber(result.tp1);
    }

    private String formatNumber(
            double value) {

        return String.format(
                Locale.US,
                "%.5f",
                value
        );
    }

    private String displayTimeframe(
            String interval) {

        if ("5min".equals(interval)) {
            return "5M";
        }

        if ("15min".equals(interval)) {
            return "15M";
        }

        if ("30min".equals(interval)) {
            return "30M";
        }

        if ("1h".equals(interval)) {
            return "1H";
        }

        if ("4h".equals(interval)) {
            return "4H";
        }

        if ("1day".equals(interval)) {
            return "1D";
        }

        return interval;
    }

    private int notificationId(
            String symbol,
            String interval) {

        return Math.abs(
                (symbol + "|" + interval)
                        .hashCode()
        ) + 10000;
    }

    private void sendSignalNotification(
            String symbol,
            String interval,
            SignalResult result) {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (manager == null) {
            return;
        }

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        intent.putExtra(
                "notification_symbol",
                symbol
        );

        intent.putExtra(
                "notification_timeframe",
                displayTimeframe(interval)
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        notificationId(
                                symbol,
                                interval
                        ),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        boolean buy =
                "BUY".equals(result.action);

        String timeframe =
                displayTimeframe(interval);

        String title =
                "ForexPilot AI • "
                        + result.action;

        String message =
                symbol
                        + " • "
                        + timeframe
                        + "\n"
                        + "Confidence: "
                        + result.confidence
                        + "%\n"
                        + "Entry: "
                        + formatPrice(
                                symbol,
                                result.entry
                        )
                        + "\n"
                        + "SL: "
                        + formatPrice(
                                symbol,
                                result.sl
                        )
                        + "\n"
                        + "TP1: "
                        + formatPrice(
                                symbol,
                                result.tp1
                        )
                        + "\n"
                        + "TP2: "
                        + formatPrice(
                                symbol,
                                result.tp2
                        )
                        + "\n"
                        + "TP3: "
                        + formatPrice(
                                symbol,
                                result.tp3
                        );

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable
                                        .ic_popup_sync
                        )
                        .setContentTitle(
                                title
                        )
                        .setContentText(
                                symbol
                                        + " • "
                                        + timeframe
                                        + " • "
                                        + result.confidence
                                        + "% • "
                                        + (buy
                                        ? "BUY setup"
                                        : "SELL setup")
                        )
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(message)
                        )
                        .setContentIntent(
                                pendingIntent
                        )
                        .setAutoCancel(
                                true
                        )
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_HIGH
                        )
                        .setCategory(
                                NotificationCompat
                                        .CATEGORY_ALARM
                        )
                        .setDefaults(
                                NotificationCompat
                                        .DEFAULT_ALL
                        )
                        .build();

        manager.notify(
                notificationId(
                        symbol,
                        interval
                ),
                notification
        );
    }

    private String formatPrice(
            String symbol,
            double value) {

        if (value <= 0) {
            return "--";
        }

        if ("USD/JPY".equals(symbol)) {

            return String.format(
                    Locale.US,
                    "%.3f",
                    value
            );
        }

        if ("XAU/USD".equals(symbol)) {

            return String.format(
                    Locale.US,
                    "%.2f",
                    value
            );
        }

        return String.format(
                Locale.US,
                "%.5f",
                value
        );
    }

    private Notification buildMonitoringNotification() {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * Read the currently selected timeframe
         * for the monitoring notification.
         */
        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String selectedTimeframe =
                preferences.getString(
                        PREF_SELECTED_TIMEFRAME,
                        "5min"
                );

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setSmallIcon(
                        android.R.drawable
                                .ic_popup_sync
                )
                .setContentTitle(
                        "ForexPilot AI"
                )
                .setContentText(
                        "Background monitoring: "
                                + displayTimeframe(
                                selectedTimeframe
                        )
                                + " • checks every 15 minutes"
                )
                .setContentIntent(
                        pendingIntent
                )
                .setOngoing(
                        true
                )
                .setPriority(
                        NotificationCompat
                                .PRIORITY_LOW
                )
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.O) {

            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "ForexPilot AI Market Alerts",
                        NotificationManager
                                .IMPORTANCE_HIGH
                );

        channel.setDescription(
                "BUY and SELL ForexPilot AI signals"
        );

        channel.enableVibration(
                true
        );

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        if (scheduler != null) {

            scheduler.shutdownNow();
            scheduler = null;
        }

        if (httpClient != null) {

            httpClient.dispatcher()
                    .executorService()
                    .shutdown();

            httpClient.connectionPool()
                    .evictAll();

            httpClient = null;
        }

        super.onDestroy();
    }

    @Override
    public void onTimeout(
            int startId,
            int fgsType) {

        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent) {

        return null;
    }
}