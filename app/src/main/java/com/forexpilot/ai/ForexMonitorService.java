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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ForexMonitorService extends Service {

    private static final String CHANNEL_ID = "forexpilot_monitor";
    private static final int FOREGROUND_ID = 9001;

    /*
     * Background scan interval.
     * We keep 15 minutes for now.
     * API-credit optimization will be handled separately.
     */
    private static final long SCAN_INTERVAL_SECONDS = 15 * 60;

    /*
     * IMPORTANT:
     * This must match MainActivity.
     */
    private static final String PREFS_NAME = "ForexPilotSettings";
    private static final String PREF_SELECTED_TIMEFRAME = "selected_timeframe";

    /*
     * Maximum NEW signals per calendar day.
     */
    private static final int MAX_DAILY_SIGNALS = 2;

    private static final String PREF_SIGNAL_DATE = "signal_date";
    private static final String PREF_DAILY_SIGNAL_COUNT = "daily_signal_count";

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
     * Prevents the same signal from generating repeated notifications.
     */
    private final Map<String, String> lastAlertFingerprint = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("ForexPilot AI")
                        .setContentText("Forex monitoring is active")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    FOREGROUND_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(FOREGROUND_ID, notification);
        }

        httpClient = new OkHttpClient();

        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(
                this::scanMarkets,
                0,
                SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Main background scanner.
     */
    private void scanMarkets() {

        /*
         * FIRST CHECK:
         * Never generate new signals while forex is closed.
         */
        if (!isSignalGenerationAllowed()) {
            return;
        }

        String apiKey = BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String selectedTimeframe =
                prefs.getString(PREF_SELECTED_TIMEFRAME, "5min");

        if (!isValidTimeframe(selectedTimeframe)) {
            selectedTimeframe = "5min";
        }

        /*
         * Reset daily counter when the calendar date changes.
         */
        resetDailyCounterIfNeeded();

        /*
         * If two NEW signals have already been generated today,
         * stop scanning for new alerts.
         */
        if (getDailySignalCount() >= MAX_DAILY_SIGNALS) {
            return;
        }

        for (String symbol : SYMBOLS) {

            /*
             * Re-check before every symbol.
             * This prevents signals if the market closes during a scan.
             */
            if (!isSignalGenerationAllowed()) {
                return;
            }

            /*
             * Stop once the daily limit has been reached.
             */
            if (getDailySignalCount() >= MAX_DAILY_SIGNALS) {
                return;
            }

            try {

                JSONArray candles =
                        getCandles(symbol, selectedTimeframe, apiKey);

                if (candles == null || candles.length() == 0) {
                    continue;
                }

                /*
                 * Use newest candle close as the current price
                 * for the background analyzer.
                 */
                JSONObject newest =
                        candles.getJSONObject(candles.length() - 1);

                double livePrice =
                        Double.parseDouble(
                                newest.getString("close")
                        );

                java.util.List<Candle> candleList =
                        new java.util.ArrayList<>();

                for (int i = 0; i < candles.length(); i++) {

                    JSONObject obj = candles.getJSONObject(i);

                    long timestamp =
                            obj.getLong("timestamp");

                    double open =
                            Double.parseDouble(obj.getString("open"));

                    double high =
                            Double.parseDouble(obj.getString("high"));

                    double low =
                            Double.parseDouble(obj.getString("low"));

                    double close =
                            Double.parseDouble(obj.getString("close"));

                    candleList.add(
                            new Candle(
                                    timestamp,
                                    open,
                                    high,
                                    low,
                                    close
                            )
                    );
                }

                SignalEngine.SignalResult result =
                        SignalEngine.analyze(
                                candleList,
                                livePrice
                        );

                if (result == null) {
                    continue;
                }

                String signal = result.signal;

                if (!"BUY".equals(signal)
                        && !"SELL".equals(signal)) {
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
                 * One failed symbol must not stop monitoring
                 * of the remaining markets.
                 */
            }
        }
    }

    /**
     * Controls whether NEW signals are allowed.
     *
     * Rules:
     * - Monday-Friday only
     * - Forex market must be open according to MarketClock
     * - Saturday = no signals
     * - Sunday = no signals
     */
    private boolean isSignalGenerationAllowed() {

        Instant now = Instant.now();

        /*
         * Use MarketClock as the source of truth for
         * actual forex open/closed status.
         */
        if (!MarketClock.isForexOpen(now)) {
            return false;
        }

        /*
         * User requested Monday-Friday signals only.
         * Use New York time because MarketClock uses New York
         * for the forex-week boundary.
         */
        ZonedDateTime newYork =
                now.atZone(
                        ZoneId.of("America/New_York")
                );

        DayOfWeek day =
                newYork.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {
            return false;
        }

        return true;
    }

    /**
     * Reset the two-signal counter when a new day begins.
     *
     * The date is based on South African time.
     */
    private void resetDailyCounterIfNeeded() {

        String today =
                ZonedDateTime.now(
                        ZoneId.of("Africa/Johannesburg")
                )
                .toLocalDate()
                .toString();

        SharedPreferences prefs =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

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

            /*
             * Allow the first signal of the new day
             * to be detected normally.
             */
            lastAlertFingerprint.clear();
        }
    }

    private int getDailySignalCount() {

        SharedPreferences prefs =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        return prefs.getInt(
                PREF_DAILY_SIGNAL_COUNT,
                0
        );
    }

    private void increaseDailySignalCount() {

        SharedPreferences prefs =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

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

    private boolean isValidTimeframe(String timeframe) {

        return "5min".equals(timeframe)
                || "15min".equals(timeframe)
                || "30min".equals(timeframe)
                || "1h".equals(timeframe)
                || "4h".equals(timeframe)
                || "1day".equals(timeframe);
    }

    /**
     * Download candles from Twelve Data.
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

        String url =
                "https://api.twelvedata.com/time_series"
                        + "?symbol=" + encodedSymbol
                        + "&interval=" + interval
                        + "&outputsize=100"
                        + "&apikey=" + apiKey;

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .build();

        try (Response response =
                     httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()
                    || response.body() == null) {
                return null;
            }

            String body =
                    response.body().string();

            JSONObject json =
                    new JSONObject(body);

            /*
             * Twelve Data can return an error object
             * instead of values.
             */
            if (!json.has("values")) {
                return null;
            }

            JSONArray values =
                    json.getJSONArray("values");

            /*
             * Twelve Data normally returns newest first.
             * SignalEngine expects chronological order.
             */
            JSONArray chronological =
                    new JSONArray();

            for (int i = values.length() - 1;
                 i >= 0;
                 i--) {

                JSONObject original =
                        values.getJSONObject(i);

                JSONObject candle =
                        new JSONObject();

                candle.put(
                        "timestamp",
                        parseTimestamp(
                                original.getString("datetime")
                        )
                );

                candle.put(
                        "open",
                        original.getString("open")
                );

                candle.put(
                        "high",
                        original.getString("high")
                );

                candle.put(
                        "low",
                        original.getString("low")
                );

                candle.put(
                        "close",
                        original.getString("close")
                );

                chronological.put(candle);
            }

            return chronological;
        }
    }

    /**
     * Converts Twelve Data datetime into epoch seconds.
     */
    private long parseTimestamp(String datetime) {

        try {

            return java.time.LocalDateTime
                    .parse(
                            datetime.replace(" ", "T")
                    )
                    .atZone(
                            ZoneId.of("America/New_York")
                    )
                    .toEpochSecond();

        } catch (Exception e) {

            return System.currentTimeMillis() / 1000L;
        }
    }

    /**
     * Sends a notification only when the signal is genuinely new.
     */
    private void notifyIfNew(
            String symbol,
            String timeframe,
            SignalEngine.SignalResult result
    ) {

        /*
         * Never notify outside the allowed market period.
         */
        if (!isSignalGenerationAllowed()) {
            return;
        }

        /*
         * Never exceed two NEW signals per day.
         */
        if (getDailySignalCount() >= MAX_DAILY_SIGNALS) {
            return;
        }

        String fingerprint =
                symbol
                        + "|"
                        + timeframe
                        + "|"
                        + result.signal
                        + "|"
                        + result.entry
                        + "|"
                        + result.stopLoss
                        + "|"
                        + result.takeProfit1;

        String key =
                symbol + "|" + timeframe;

        String previous =
                lastAlertFingerprint.get(key);

        if (fingerprint.equals(previous)) {
            return;
        }

        /*
         * Send the notification first.
         */
        sendSignalNotification(
                symbol,
                timeframe,
                result
        );

        /*
         * Only after sending do we record:
         * - this signal fingerprint
         * - one of today's two signals
         */
        lastAlertFingerprint.put(
                key,
                fingerprint
        );

        increaseDailySignalCount();
    }

    private void sendSignalNotification(
            String symbol,
            String timeframe,
            SignalEngine.SignalResult result
    ) {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        String title =
                "ForexPilot AI — "
                        + result.signal;

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
                        + format(result.stopLoss)
                        + "\nTP1: "
                        + format(result.takeProfit1)
                        + "\nTP2: "
                        + format(result.takeProfit2)
                        + "\nTP3: "
                        + format(result.takeProfit3);

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_dialog_info
                        )
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(
                                                message
                                                        + "\n\n"
                                                        + details
                                        )
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true)
                        .build();

        int notificationId =
                Math.abs(
                        (
                                symbol
                                        + timeframe
                                        + result.signal
                                        + System.currentTimeMillis()
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

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "ForexPilot AI Monitoring",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "ForexPilot AI trading signal alerts"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

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