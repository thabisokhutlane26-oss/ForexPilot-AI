package com.forexpilot.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static final int FOREGROUND_ID = 9001;

    /*
     * Check one pair every 15 minutes.
     *
     * Five pairs are rotated, which greatly reduces
     * unnecessary Twelve Data requests.
     */
    private static final long SCAN_INTERVAL_SECONDS =
            15 * 60;

    private static final String PREFS_NAME =
            "ForexPilotSettings";

    private static final String PREF_SELECTED_TIMEFRAME =
            "selected_timeframe";

    private static final int MAX_DAILY_SIGNALS = 2;

    private static final String PREF_SIGNAL_DATE =
            "signal_date";

    private static final String PREF_DAILY_SIGNAL_COUNT =
            "daily_signal_count";

    /*
     * Stores which pair should be scanned next.
     */
    private static final String PREF_SCAN_INDEX =
            "background_scan_index";

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
     * Prevent duplicate notifications.
     */
    private final Map<String, String> lastAlertFingerprint =
            new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setContentTitle(
                                "ForexPilot AI"
                        )
                        .setContentText(
                                "Forex monitoring is active"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_dialog_info
                        )
                        .setOngoing(true)
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_LOW
                        )
                        .build();

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q) {

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
                        .writeTimeout(
                                15,
                                TimeUnit.SECONDS
                        )
                        .build();

        scheduler =
                Executors
                        .newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(
                this::scanMarkets,
                0,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /*
     * ========================================================
     * MARKET SCANNER
     * ========================================================
     */
    private void scanMarkets() {

        /*
         * Never generate a new signal while the
         * forex market is closed.
         */
        if (!isSignalGenerationAllowed()) {
            return;
        }

        resetDailyCounterIfNeeded();

        /*
         * Maximum two new signals per day.
         */
        if (getDailySignalCount()
                >= MAX_DAILY_SIGNALS) {
            return;
        }

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null
                || apiKey.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String selectedTimeframe =
                prefs.getString(
                        PREF_SELECTED_TIMEFRAME,
                        "5min"
                );

        if (!isValidTimeframe(
                selectedTimeframe)) {

            selectedTimeframe = "5min";
        }

        /*
         * ====================================================
         * ROTATING PAIR SCANNER
         * ====================================================
         *
         * Instead of:
         *
         * XAU/USD
         * EUR/USD
         * GBP/USD
         * USD/JPY
         * NZD/USD
         *
         * on EVERY scan, we check only one pair and move
         * to the next pair on the next scan.
         *
         * This dramatically reduces API usage.
         * ====================================================
         */

        int scanIndex =
                prefs.getInt(
                        PREF_SCAN_INDEX,
                        0
                );

        if (scanIndex < 0
                || scanIndex >= SYMBOLS.length) {

            scanIndex = 0;
        }

        String symbol =
                SYMBOLS[scanIndex];

        int nextIndex =
                (scanIndex + 1)
                        % SYMBOLS.length;

        prefs.edit()
                .putInt(
                        PREF_SCAN_INDEX,
                        nextIndex
                )
                .apply();

        /*
         * Check the market again immediately before
         * making the API request.
         */
        if (!isSignalGenerationAllowed()) {
            return;
        }

        if (getDailySignalCount()
                >= MAX_DAILY_SIGNALS) {
            return;
        }

        try {

            JSONArray candles =
                    getCandles(
                            symbol,
                            selectedTimeframe,
                            apiKey
                    );

            if (candles == null
                    || candles.length() < 60) {
                return;
            }

            List<Candle> candleList =
                    new ArrayList<>();

            for (int i = 0;
                 i < candles.length();
                 i++) {

                JSONObject obj =
                        candles.getJSONObject(i);

                double open =
                        Double.parseDouble(
                                obj.getString("open")
                        );

                double high =
                        Double.parseDouble(
                                obj.getString("high")
                        );

                double low =
                        Double.parseDouble(
                                obj.getString("low")
                        );

                double close =
                        Double.parseDouble(
                                obj.getString("close")
                        );

                if (open <= 0
                        || high <= 0
                        || low <= 0
                        || close <= 0) {
                    continue;
                }

                candleList.add(
                        new Candle(
                                open,
                                high,
                                low,
                                close
                        )
                );
            }

            if (candleList.size() < 60) {
                return;
            }

            Candle latest =
                    candleList.get(
                            candleList.size() - 1
                    );

            double livePrice =
                    latest.close;

            SignalResult result =
                    SignalEngine.analyze(
                            candleList,
                            livePrice
                    );

            if (result == null) {
                return;
            }

            if (!"BUY".equals(
                    result.action)
                    && !"SELL".equals(
                    result.action)) {
                return;
            }

            if (result.entry <= 0) {
                return;
            }

            notifyIfNew(
                    symbol,
                    selectedTimeframe,
                    result
            );

        } catch (Exception ignored) {

            /*
             * A failed request must not stop
             * the background service.
             */
        }
    }

    /*
     * ========================================================
     * MARKET OPEN CHECK
     * ========================================================
     */
    private boolean isSignalGenerationAllowed() {

        Instant now =
                Instant.now();

        if (!MarketClock.isForexOpen(now)) {
            return false;
        }

        ZonedDateTime newYork =
                now.atZone(
                        ZoneId.of(
                                "America/New_York"
                        )
                );

        DayOfWeek day =
                newYork.getDayOfWeek();

        /*
         * ForexPilot AI only creates NEW signals
         * Monday through Friday.
         */
        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {

            return false;
        }

        return true;
    }

    /*
     * ========================================================
     * DAILY SIGNAL COUNTER
     * ========================================================
     */
    private void resetDailyCounterIfNeeded() {

        String today =
                ZonedDateTime.now(
                        ZoneId.of(
                                "Africa/Johannesburg"
                        )
                )
                .toLocalDate()
                .toString();

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String savedDate =
                prefs.getString(
                        PREF_SIGNAL_DATE,
                        ""
                );

        if (!today.equals(savedDate)) {

            prefs.edit()
                    .putString(
                            PREF_SIGNAL_DATE,
                            today
                    )
                    .putInt(
                            PREF_DAILY_SIGNAL_COUNT,
                            0
                    )
                    .apply();

            lastAlertFingerprint.clear();
        }
    }

    private int getDailySignalCount() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        return prefs.getInt(
                PREF_DAILY_SIGNAL_COUNT,
                0
        );
    }

    private void increaseDailySignalCount() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        int current =
                prefs.getInt(
                        PREF_DAILY_SIGNAL_COUNT,
                        0
                );

        prefs.edit()
                .putInt(
                        PREF_DAILY_SIGNAL_COUNT,
                        current + 1
                )
                .apply();
    }

    /*
     * ========================================================
     * TIMEFRAME VALIDATION
     * ========================================================
     */
    private boolean isValidTimeframe(
            String timeframe) {

        return "5min".equals(timeframe)
                || "15min".equals(timeframe)
                || "30min".equals(timeframe)
                || "1h".equals(timeframe)
                || "4h".equals(timeframe)
                || "1day".equals(timeframe);
    }

    /*
     * ========================================================
     * TWELVE DATA
     * ========================================================
     */
    private JSONArray getCandles(
            String symbol,
            String interval,
            String apiKey
    ) throws Exception {

        String encodedSymbol =
                URLEncoder.encode(
                        symbol,
                        StandardCharsets.UTF_8.toString()
                );

        String encodedKey =
                URLEncoder.encode(
                        apiKey,
                        StandardCharsets.UTF_8.toString()
                );

        String url =
                "https://api.twelvedata.com/time_series"
                        + "?symbol=" + encodedSymbol
                        + "&interval=" + interval
                        + "&outputsize=100"
                        + "&order=asc"
                        + "&apikey=" + encodedKey;

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

            JSONObject json =
                    new JSONObject(body);

            /*
             * Twelve Data can return an API error
             * inside a successful HTTP response.
             */
            if (!json.has("values")) {
                return null;
            }

            JSONArray values =
                    json.getJSONArray(
                            "values"
                    );

            if (values.length() == 0) {
                return null;
            }

            /*
             * Twelve Data normally returns newest first.
             *
             * SignalEngine expects oldest -> newest.
             */
            JSONArray chronological =
                    new JSONArray();

            for (int i =
                         values.length() - 1;
                 i >= 0;
                 i--) {

                chronological.put(
                        values.getJSONObject(i)
                );
            }

            return chronological;
        }
    }

    /*
     * ========================================================
     * NEW SIGNAL CHECK
     * ========================================================
     */
    private void notifyIfNew(
            String symbol,
            String timeframe,
            SignalResult result) {

        if (!isSignalGenerationAllowed()) {
            return;
        }

        if (getDailySignalCount()
                >= MAX_DAILY_SIGNALS) {
            return;
        }

        String fingerprint =
                symbol
                        + "|"
                        + timeframe
                        + "|"
                        + result.action
                        + "|"
                        + result.entry
                        + "|"
                        + result.sl
                        + "|"
                        + result.tp1;

        String key =
                symbol
                        + "|"
                        + timeframe;

        String previous =
                lastAlertFingerprint.get(key);

        if (fingerprint.equals(previous)) {
            return;
        }

        sendSignalNotification(
                symbol,
                timeframe,
                result
        );

        lastAlertFingerprint.put(
                key,
                fingerprint
        );

        increaseDailySignalCount();
    }

    /*
     * ========================================================
     * SIGNAL NOTIFICATION
     * ========================================================
     */
    private void sendSignalNotification(
            String symbol,
            String timeframe,
            SignalResult result) {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (manager == null) {
            return;
        }

        String title =
                "ForexPilot AI — "
                        + result.action;

        String message =
                symbol
                        + " • "
                        + timeframe
                        + " • "
                        + result.confidence
                        + "% confidence";

        String details =
                "Entry: "
                        + format(result.entry)
                        + "\nSL: "
                        + format(result.sl)
                        + "\nTP1: "
                        + format(result.tp1)
                        + "\nTP2: "
                        + format(result.tp2)
                        + "\nTP3: "
                        + format(result.tp3);

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable
                                        .ic_dialog_info
                        )
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(
                                                message
                                                        + "\n\n"
                                                        + details
                                        )
                        )
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .build();

        int notificationId =
                Math.abs(
                        (
                                symbol
                                        + timeframe
                                        + result.action
                                        + System
                                        .currentTimeMillis()
                        ).hashCode()
                );

        manager.notify(
                notificationId,
                notification
        );
    }

    private String format(double value) {

        return String.format(
                java.util.Locale.US,
                "%.5f",
                value
        );
    }

    /*
     * ========================================================
     * NOTIFICATION CHANNEL
     * ========================================================
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "ForexPilot AI Monitoring",
                            NotificationManager
                                    .IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "ForexPilot AI trading signal alerts"
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
        }

        if (httpClient != null) {

            httpClient.dispatcher()
                    .executorService()
                    .shutdown();
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}