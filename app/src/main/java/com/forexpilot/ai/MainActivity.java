package com.forexpilot.ai;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ForexPilotSettings";
    private static final String PREF_SELECTED_TIMEFRAME = "selected_timeframe";

    private static final String PREF_SIGNAL_DATE = "signal_date";
    private static final String PREF_DAILY_SIGNAL_COUNT = "daily_signal_count";

    private static final String PREF_CHART_CACHE_PREFIX = "chart_cache_";
    private static final String PREF_CHART_CACHE_TIME_PREFIX = "chart_cache_time_";

    private static final int MAX_CACHED_CANDLES = 100;
    private static final int MAX_DAILY_SIGNALS = 2;

    private static final String[] SYMBOLS = {
            "XAU/USD",
            "EUR/USD",
            "GBP/USD",
            "USD/JPY",
            "NZD/USD"
    };

    private TextView title;
    private TextView market;
    private TextView session;
    private TextView scanner;
    private TextView bestSignal;
    private TextView bestDetails;
    private TextView confirmation;
    private TextView updated;
    private TextView signalStatus;
    private TextView signalTime;
    private TextView signalResult;
    private TextView history;

    private TextView chartPair;
    private TextView chartStatus;

    private Button refreshButton;
    private Button copyButton;
    private Button logoutButton;

    private Button tf5;
    private Button tf15;
    private Button tf30;
    private Button tf1h;
    private Button tf4h;
    private Button tf1d;

    private Button marketGold;
    private Button marketEur;
    private Button marketGbp;
    private Button marketJpy;
    private Button marketNzd;

    private Button indicatorButton;
    private Button drawingButton;
    private Button fitChartButton;
    private Button fullscreenButton;

    private WebView candleChart;
    private WebView chartWebView;

    private FrameLayout chartContainer;
    private LinearLayout chartWorkspace;
    private LinearLayout mainContent;
    private ScrollView mainScroll;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Map<String, SignalResult> currentSignals =
            new HashMap<>();

    private final Map<String, SignalResult> activeTrades =
            new HashMap<>();

    private final Map<String, Double> latestPrices =
            new HashMap<>();

    private final Map<String, List<Candle>> candleData =
            new HashMap<>();

    private final Map<String, TwelveDataClient> liveClients =
            new HashMap<>();

    private SharedPreferences preferences;
    private SignalStorage signalStorage;
    private FirebaseAuth firebaseAuth;

    private String selectedTimeframe = "15min";
    private String selectedMarket = "ALL";
    private String chartSymbol = "XAU/USD";

    private boolean chartReady = false;
    private boolean scannerRunning = false;
    private boolean chartFullscreen = false;

    private int normalChartHeightDp = 390;

    private final Runnable refreshRunnable =
            new Runnable() {
                @Override
                public void run() {
                    refreshSignals();

                    handler.postDelayed(
                            this,
                            5 * 60 * 1000L
                    );
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                com.forexpilot.ai.R.layout.activity_main
        );

        preferences = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        );

        signalStorage =
                new SignalStorage(this);

        firebaseAuth =
                FirebaseAuth.getInstance();

        selectedTimeframe =
                preferences.getString(
                        PREF_SELECTED_TIMEFRAME,
                        "15min"
                );

        bindViews();
        setupChart();
        setupButtons();
        loadActiveSignals();
        loadCachedCandleData();

        updateMarketStatus();
        updateChart();

        requestNotificationPermission();

        startScanner();

        handler.post(refreshRunnable);
    }

    private void bindViews() {

        title = findViewById(R.id.title);
        market = findViewById(R.id.market);
        session = findViewById(R.id.session);
        scanner = findViewById(R.id.scanner);
        bestSignal = findViewById(R.id.bestSignal);
        bestDetails = findViewById(R.id.bestDetails);
        confirmation = findViewById(R.id.confirmation);
        updated = findViewById(R.id.updated);
        signalStatus = findViewById(R.id.signalStatus);
        signalTime = findViewById(R.id.signalTime);
        signalResult = findViewById(R.id.signalResult);
        history = findViewById(R.id.history);

        chartPair = findViewById(R.id.chartPair);
        chartStatus = findViewById(R.id.chartStatus);

        refreshButton =
                findViewById(R.id.refreshButton);

        copyButton =
                findViewById(R.id.copyButton);

        logoutButton =
                findViewById(R.id.logoutButton);

        candleChart =
                findViewById(R.id.candleChart);

        chartWebView = candleChart;

        chartContainer =
                findViewById(R.id.chartContainer);

        indicatorButton =
                findViewById(R.id.indicatorButton);

        drawingButton =
                findViewById(R.id.drawingButton);

        fitChartButton =
                findViewById(R.id.fitChartButton);

        fullscreenButton =
                findViewById(R.id.fullscreenButton);

        tf5 = findViewById(R.id.tf5);
        tf15 = findViewById(R.id.tf15);
        tf30 = findViewById(R.id.tf30);
        tf1h = findViewById(R.id.tf1h);
        tf4h = findViewById(R.id.tf4h);
        tf1d = findViewById(R.id.tf1d);

        marketGold =
                findViewById(R.id.marketGold);

        marketEur =
                findViewById(R.id.marketEur);

        marketGbp =
                findViewById(R.id.marketGbp);

        marketJpy =
                findViewById(R.id.marketJpy);

        marketNzd =
                findViewById(R.id.marketNzd);

        if (chartContainer != null) {
            normalChartHeightDp =
                    chartContainer.getLayoutParams().height > 0
                            ? 390
                            : 390;
        }

        if (chartContainer != null) {
            View parent = chartContainer.getParent();

            if (parent instanceof LinearLayout) {
                chartWorkspace =
                        (LinearLayout) parent;
            }
        }

        if (chartWorkspace != null) {
            View parent =
                    chartWorkspace.getParent();

            if (parent instanceof LinearLayout) {
                mainContent =
                        (LinearLayout) parent;
            }
        }

        if (mainContent != null) {
            View parent =
                    mainContent.getParent();

            if (parent instanceof ScrollView) {
                mainScroll =
                        (ScrollView) parent;
            }
        }
    }

    private void setupButtons() {

        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> {

                updateMarketStatus();

                if (isForexWeekdayOpen()) {

                    refreshSignals();

                } else {

                    updateChart();

                    Toast.makeText(
                            this,
                            "Market closed • showing last real market data",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }

        if (copyButton != null) {
            copyButton.setOnClickListener(
                    v -> copyCurrentSignal()
            );
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(
                    v -> logout()
            );
        }

        if (indicatorButton != null) {
            indicatorButton.setOnClickListener(
                    v -> showIndicatorMenu()
            );
        }

        if (drawingButton != null) {
            drawingButton.setOnClickListener(
                    v -> showDrawingMenu()
            );
        }

        if (fitChartButton != null) {
            fitChartButton.setOnClickListener(
                    v -> fitChart()
            );
        }

        if (fullscreenButton != null) {
            fullscreenButton.setOnClickListener(
                    v -> toggleChartFullscreen()
            );
        }

        if (tf5 != null) {
            tf5.setOnClickListener(
                    v -> selectTimeframe("5min")
            );
        }

        if (tf15 != null) {
            tf15.setOnClickListener(
                    v -> selectTimeframe("15min")
            );
        }

        if (tf30 != null) {
            tf30.setOnClickListener(
                    v -> selectTimeframe("30min")
            );
        }

        if (tf1h != null) {
            tf1h.setOnClickListener(
                    v -> selectTimeframe("1h")
            );
        }

        if (tf4h != null) {
            tf4h.setOnClickListener(
                    v -> selectTimeframe("4h")
            );
        }

        if (tf1d != null) {
            tf1d.setOnClickListener(
                    v -> selectTimeframe("1day")
            );
        }

        if (marketGold != null) {
            marketGold.setOnClickListener(
                    v -> selectMarket("XAU/USD")
            );
        }

        if (marketEur != null) {
            marketEur.setOnClickListener(
                    v -> selectMarket("EUR/USD")
            );
        }

        if (marketGbp != null) {
            marketGbp.setOnClickListener(
                    v -> selectMarket("GBP/USD")
            );
        }

        if (marketJpy != null) {
            marketJpy.setOnClickListener(
                    v -> selectMarket("USD/JPY")
            );
        }

        if (marketNzd != null) {
            marketNzd.setOnClickListener(
                    v -> selectMarket("NZD/USD")
            );
        }

        updateTimeframeButtons();
        updateMarketButtons();
        updateChartToolbarText();
    }

    private void updateChartToolbarText() {

        if (chartPair != null) {
            chartPair.setText(
                    chartSymbol
            );
        }

        if (chartStatus != null) {
            chartStatus.setText(
                    "Professional chart • " +
                            selectedTimeframe
            );
        }
    }

    private void selectTimeframe(
            String timeframe
    ) {

        selectedTimeframe = timeframe;

        preferences.edit()
                .putString(
                        PREF_SELECTED_TIMEFRAME,
                        timeframe
                )
                .apply();

        loadCachedCandleData();

        updateTimeframeButtons();
        updateChartToolbarText();
        updateChart();

        if (isForexWeekdayOpen()) {
            refreshSignals();
        } else {
            updateMarketStatus();
        }
    }

    private void selectMarket(
            String symbol
    ) {

        selectedMarket = symbol;
        chartSymbol = symbol;

        loadCachedCandleData();

        updateMarketButtons();
        updateChartToolbarText();
        updateChart();

        if (isForexWeekdayOpen()) {
            refreshSignals();
        }
    }

    private void updateTimeframeButtons() {

        setButtonState(
                tf5,
                "5M",
                "5min"
        );

        setButtonState(
                tf15,
                "15M",
                "15min"
        );

        setButtonState(
                tf30,
                "30M",
                "30min"
        );

        setButtonState(
                tf1h,
                "1H",
                "1h"
        );

        setButtonState(
                tf4h,
                "4H",
                "4h"
        );

        setButtonState(
                tf1d,
                "1D",
                "1day"
        );
    }

    private void setButtonState(
            Button button,
            String text,
            String timeframe
    ) {

        if (button == null) {
            return;
        }

        button.setText(text);

        if (timeframe.equals(
                selectedTimeframe
        )) {

            button.setTextColor(
                    Color.WHITE
            );

        } else {

            button.setTextColor(
                    Color.LTGRAY
            );
        }
    }

    private void updateMarketButtons() {

        setMarketButtonState(
                marketGold,
                "XAU/USD",
                "XAU/USD"
        );

        setMarketButtonState(
                marketEur,
                "EUR/USD",
                "EUR/USD"
        );

        setMarketButtonState(
                marketGbp,
                "GBP/USD",
                "GBP/USD"
        );

        setMarketButtonState(
                marketJpy,
                "USD/JPY",
                "USD/JPY"
        );

        setMarketButtonState(
                marketNzd,
                "NZD/USD",
                "NZD/USD"
        );
    }

    private void setMarketButtonState(
            Button button,
            String text,
            String symbol
    ) {

        if (button == null) {
            return;
        }

        button.setText(text);

        if (symbol.equals(
                selectedMarket
        )) {

            button.setTextColor(
                    Color.WHITE
            );

        } else {

            button.setTextColor(
                    Color.LTGRAY
            );
        }
    }

    private boolean isForexWeekdayOpen() {

        java.time.Instant now =
                java.time.Instant.now();

        java.time.ZonedDateTime ny =
                now.atZone(
                        ZoneId.of(
                                "America/New_York"
                        )
                );

        DayOfWeek day =
                ny.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY) {
            return false;
        }

        if (day == DayOfWeek.SUNDAY) {
            return false;
        }

        return MarketClock.isForexOpen(
                now
        );
    }

    private void updateMarketStatus() {

        boolean open =
                isForexWeekdayOpen();

        if (market != null) {

            if (open) {

                market.setText(
                        "FOREX MARKET: OPEN"
                );

                market.setTextColor(
                        Color.GREEN
                );

            } else {

                market.setText(
                        "FOREX MARKET: CLOSED"
                );

                market.setTextColor(
                        Color.RED
                );
            }
        }

        if (session != null) {

            if (open) {

                session.setText(
                        "Session: " +
                                MarketClock.session(
                                        java.time.Instant.now()
                                )
                );

            } else {

                session.setText(
                        "Weekend / market closed"
                );
            }
        }

        if (scanner != null) {

            if (open) {

                scanner.setText(
                        "Scanner: ACTIVE"
                );

            } else {

                scanner.setText(
                        "Scanner: PAUSED • NO NEW SIGNALS"
                );
            }
        }

        if (open) {

            showDailySignalStatus();

        } else if (confirmation != null) {

            confirmation.setText(
                    "MARKET CLOSED • NEW SIGNALS PAUSED"
            );
        }
    }

    private String todayKey() {

        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(new Date());
    }

    private void resetDailyCounterIfNeeded() {

        String today =
                todayKey();

        String savedDate =
                preferences.getString(
                        PREF_SIGNAL_DATE,
                        ""
                );

        if (!today.equals(savedDate)) {

            preferences.edit()
                    .putString(
                            PREF_SIGNAL_DATE,
                            today
                    )
                    .putInt(
                            PREF_DAILY_SIGNAL_COUNT,
                            0
                    )
                    .apply();
        }
    }

    private int getDailySignalCount() {

        resetDailyCounterIfNeeded();

        return preferences.getInt(
                PREF_DAILY_SIGNAL_COUNT,
                0
        );
    }

    private void increaseDailySignalCount() {

        resetDailyCounterIfNeeded();

        int count =
                preferences.getInt(
                        PREF_DAILY_SIGNAL_COUNT,
                        0
                );

        preferences.edit()
                .putString(
                        PREF_SIGNAL_DATE,
                        todayKey()
                )
                .putInt(
                        PREF_DAILY_SIGNAL_COUNT,
                        count + 1
                )
                .apply();
    }

    private boolean isNewSignalAllowed() {

        if (!isForexWeekdayOpen()) {
            return false;
        }

        return getDailySignalCount()
                < MAX_DAILY_SIGNALS;
    }

    private void showDailySignalStatus() {

        int count =
                getDailySignalCount();

        if (confirmation != null) {

            confirmation.setText(
                    "Daily signals: " +
                            count +
                            "/" +
                            MAX_DAILY_SIGNALS
            );
        }
    }

    private void startScanner() {

        if (scannerRunning) {
            return;
        }

        scannerRunning = true;

        updateMarketStatus();

        if (!isForexWeekdayOpen()) {

            updateChart();

            return;
        }

        refreshSignals();

        startLivePriceConnections();
    }

    private void refreshSignals() {

        updateMarketStatus();

        if (!isForexWeekdayOpen()) {

            updateChart();

            if (updated != null) {

                updated.setText(
                        "LAST REAL MARKET DATA • MARKET CLOSED"
                );
            }

            return;
        }

        resetDailyCounterIfNeeded();

        if (getDailySignalCount()
                >= MAX_DAILY_SIGNALS) {

            if (confirmation != null) {

                confirmation.setText(
                        "2/2 DAILY SIGNALS USED • WAITING FOR TOMORROW"
                );
            }

            updateChart();

            return;
        }

        String apiKey =
                getApiKey();

        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            if (confirmation != null) {

                confirmation.setText(
                        "API KEY REQUIRED"
                );
            }

            return;
        }

        List<String> symbolsToScan =
                new ArrayList<>();

        if ("ALL".equals(
                selectedMarket
        )) {

            for (String symbol :
                    SYMBOLS) {

                symbolsToScan.add(
                        symbol
                );
            }

        } else {

            symbolsToScan.add(
                    selectedMarket
            );
        }

        for (String symbol :
                symbolsToScan) {

            if (!isNewSignalAllowed()) {
                break;
            }

            TwelveDataClient client =
                    new TwelveDataClient(
                            new PairCallback(symbol)
                    );

            client.candles(
                    symbol,
                    selectedTimeframe,
                    apiKey
            );
        }

        updateChart();
    }

    private String getApiKey() {

        return preferences.getString(
                "api_key",
                ""
        );
    }

    private class PairCallback
            implements TwelveDataClient.Callback {

        private final String symbol;

        PairCallback(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public void price(double price) {

            runOnUiThread(() -> {

                if (price > 0) {

                    latestPrices.put(
                            symbol,
                            price
                    );
                }

                checkActiveTrade(
                        symbol,
                        price
                );

                updateBestSignalDisplay();

                if (symbol.equals(
                        chartSymbol
                )) {

                    updateChart();
                }
            });
        }

        @Override
        public void candles(
                List<Candle> candles
        ) {

            if (candles == null ||
                    candles.isEmpty()) {

                return;
            }

            List<Candle> copy =
                    new ArrayList<>(
                            candles
                    );

            candleData.put(
                    symbol,
                    copy
            );

            saveCachedCandles(
                    symbol,
                    copy
            );

            runOnUiThread(() -> {

                processCurrentSignal(
                        symbol,
                        copy
                );

                updateChart();

                if (updated != null) {

                    updated.setText(
                            "LIVE MARKET DATA • " +
                                    symbol
                    );
                }
            });
        }

        @Override
        public void error(
                String message
        ) {

            runOnUiThread(() -> {

                if (updated != null) {

                    updated.setText(
                            "DATA ERROR • " +
                                    message
                    );
                }
            });
        }
    }

    private void processCurrentSignal(
            String symbol,
            List<Candle> candles
    ) {

        if (candles == null ||
                candles.size() < 60) {

            return;
        }

        double livePrice =
                candles.get(
                        candles.size() - 1
                ).close;

        latestPrices.put(
                symbol,
                livePrice
        );

        SignalResult result =
                SignalEngine.analyze(
                        candles,
                        livePrice
                );

        if (result == null) {
            return;
        }

        currentSignals.put(
                symbol,
                result
        );

        if (!"BUY".equals(
                result.action
        ) && !"SELL".equals(
                result.action
        )) {

            updateBestSignalDisplay();

            return;
        }

        SignalResult existing =
                activeTrades.get(
                        symbol
                );

        if (existing != null &&
                existing.isOpen()) {

            existing.updateStatus(
                    livePrice
            );

            signalStorage.saveActiveSignal(
                    symbol,
                    existing
            );

            updateBestSignalDisplay();

            return;
        }

        if (!isNewSignalAllowed()) {

            updateBestSignalDisplay();

            return;
        }

        createNewMarketSignal(
                symbol,
                result
        );
    }

    private void createNewMarketSignal(
            String symbol,
            SignalResult result
    ) {

        if (result == null) {
            return;
        }

        if (!"BUY".equals(
                result.action
        ) && !"SELL".equals(
                result.action
        )) {

            return;
        }

        if (!isNewSignalAllowed()) {
            return;
        }

        SignalResult existing =
                activeTrades.get(
                        symbol
                );

        if (existing != null &&
                existing.isOpen()) {

            return;
        }

        SignalResult trade =
                new SignalResult(
                        result.action,
                        result.entry,
                        result.sl,
                        result.tp1,
                        result.tp2,
                        result.tp3,
                        result.confidence,
                        result.signalTimeMillis,
                        "OPEN",
                        "",
                        result.highestTargetReached
                );

        activeTrades.put(
                symbol,
                trade
        );

        currentSignals.put(
                symbol,
                trade
        );

        signalStorage.saveActiveSignal(
                symbol,
                trade
        );

        signalStorage.saveSignal(
                symbol,
                trade
        );

        increaseDailySignalCount();

        updateBestSignalDisplay();
        updateHistoryDisplay();
        showDailySignalStatus();
    }

    private void checkActiveTrade(
            String symbol,
            double currentPrice
    ) {

        if (currentPrice <= 0) {
            return;
        }

        SignalResult trade =
                activeTrades.get(
                        symbol
                );

        if (trade == null) {
            return;
        }

        if (!trade.isOpen()) {
            return;
        }

        String oldStatus =
                trade.status;

        trade.updateStatus(
                currentPrice
        );

        signalStorage.saveActiveSignal(
                symbol,
                trade
        );

        currentSignals.put(
                symbol,
                trade
        );

        if (!oldStatus.equals(
                trade.status
        )) {

            if (trade.isCompleted()) {

                signalStorage.removeActiveSignal(
                        symbol
                );
            }
        }

        updateBestSignalDisplay();
        updateHistoryDisplay();
    }

    private void loadActiveSignals() {

        for (String symbol :
                SYMBOLS) {

            candleData.put(
                    symbol,
                    new ArrayList<>()
            );
        }

        Map<String, SignalResult> saved =
                signalStorage.getActiveSignals();

        if (saved != null) {

            activeTrades.clear();

            activeTrades.putAll(
                    saved
            );

            currentSignals.putAll(
                    saved
            );
        }

        updateHistoryDisplay();
    }

    private String chartCacheKey(
            String symbol
    ) {

        String safeSymbol =
                symbol
                        .replace(
                                "/",
                                "_"
                        )
                        .replace(
                                " ",
                                "_"
                        );

        String safeTimeframe =
                selectedTimeframe
                        .replace(
                                "/",
                                "_"
                        )
                        .replace(
                                " ",
                                "_"
                        );

        return PREF_CHART_CACHE_PREFIX +
                safeSymbol +
                "_" +
                safeTimeframe;
    }

    private String chartCacheTimeKey(
            String symbol
    ) {

        String safeSymbol =
                symbol
                        .replace(
                                "/",
                                "_"
                        )
                        .replace(
                                " ",
                                "_"
                        );

        String safeTimeframe =
                selectedTimeframe
                        .replace(
                                "/",
                                "_"
                        )
                        .replace(
                                " ",
                                "_"
                        );

        return PREF_CHART_CACHE_TIME_PREFIX +
                safeSymbol +
                "_" +
                safeTimeframe;
    }

    private void saveCachedCandles(
            String symbol,
            List<Candle> candles
    ) {

        if (candles == null ||
                candles.isEmpty()) {

            return;
        }

        try {

            int start =
                    Math.max(
                            0,
                            candles.size()
                                    - MAX_CACHED_CANDLES
                    );

            JSONArray array =
                    new JSONArray();

            for (int i = start;
                 i < candles.size();
                 i++) {

                Candle c =
                        candles.get(i);

                JSONObject object =
                        new JSONObject();

                object.put(
                        "open",
                        c.open
                );

                object.put(
                        "high",
                        c.high
                );

                object.put(
                        "low",
                        c.low
                );

                object.put(
                        "close",
                        c.close
                );

                array.put(
                        object
                );
            }

            preferences.edit()
                    .putString(
                            chartCacheKey(symbol),
                            array.toString()
                    )
                    .putLong(
                            chartCacheTimeKey(symbol),
                            System.currentTimeMillis()
                    )
                    .apply();

        } catch (Exception ignored) {
        }
    }

    private void loadCachedCandleData() {

        for (String symbol :
                SYMBOLS) {

            List<Candle> candles =
                    loadCachedCandles(
                            symbol
                    );

            candleData.put(
                    symbol,
                    candles
            );
        }
    }

    private List<Candle> loadCachedCandles(
            String symbol
    ) {

        List<Candle> result =
                new ArrayList<>();

        String json =
                preferences.getString(
                        chartCacheKey(symbol),
                        ""
                );

        if (json == null ||
                json.trim().isEmpty()) {

            return result;
        }

        try {

            JSONArray array =
                    new JSONArray(json);

            for (int i = 0;
                 i < array.length();
                 i++) {

                JSONObject object =
                        array.getJSONObject(i);

                result.add(
                        new Candle(
                                object.getDouble(
                                        "open"
                                ),
                                object.getDouble(
                                        "high"
                                ),
                                object.getDouble(
                                        "low"
                                ),
                                object.getDouble(
                                        "close"
                                )
                        )
                );
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    private long getCachedCandlesSavedAt(
            String symbol
    ) {

        return preferences.getLong(
                chartCacheTimeKey(symbol),
                0L
        );
    }

    private String formatCachedTime(
            long millis
    ) {

        if (millis <= 0) {
            return "";
        }

        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "dd MMM yyyy HH:mm",
                            Locale.US
                    );

            return format.format(
                    new Date(millis)
            );

        } catch (Exception e) {

            return "";
        }
    }

    private void setupChart() {

        if (chartWebView == null) {
            return;
        }

        WebSettings settings =
                chartWebView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);

        chartWebView.setBackgroundColor(
                Color.rgb(
                        11,
                        15,
                        20
                )
        );

        chartWebView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );

                        chartReady = true;

                        updateChart();
                    }
                }
        );

        chartWebView.loadDataWithBaseURL(
                "https://www.tradingview.com/",
                createChartHtml(),
                "text/html",
                "UTF-8",
                null
        );
    }

    private String createChartHtml() {

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta name='viewport' " +
                "content='width=device-width, initial-scale=1.0'>" +

                "<style>" +

                "html,body{" +
                "margin:0;" +
                "padding:0;" +
                "width:100%;" +
                "height:100%;" +
                "background:#0B0F14;" +
                "overflow:hidden;" +
                "}" +

                "#chart{" +
                "position:absolute;" +
                "left:0;" +
                "top:0;" +
                "right:0;" +
                "bottom:0;" +
                "}" +

                "#status{" +
                "position:absolute;" +
                "top:8px;" +
                "left:10px;" +
                "z-index:20;" +
                "font-family:Arial;" +
                "font-size:11px;" +
                "color:#AAB4C3;" +
                "background:rgba(11,15,20,.78);" +
                "padding:4px 7px;" +
                "border-radius:4px;" +
                "}" +

                "#drawLayer{" +
                "position:absolute;" +
                "left:0;" +
                "top:0;" +
                "width:100%;" +
                "height:100%;" +
                "z-index:15;" +
                "pointer-events:none;" +
                "}" +

                "</style>" +

                "<script src='" +
                "https://unpkg.com/lightweight-charts/" +
                "dist/lightweight-charts.standalone.production.js" +
                "'></script>" +

                "</head>" +

                "<body>" +

                "<div id='chart'></div>" +

                "<div id='status'>" +
                "Loading chart..." +
                "</div>" +

                "<div id='drawLayer'></div>" +

                "<script>" +

                "var chart=null;" +
                "var candleSeries=null;" +
                "var ema20=null;" +
                "var ema50=null;" +
                "var priceLines=[];" +
                "var drawingMode=false;" +

                "function setStatus(text){" +
                "document.getElementById('status').innerText=text;" +
                "}" +

                "function initChart(){" +

                "if(typeof LightweightCharts==='undefined'){" +
                "setStatus('CHART LIBRARY NOT AVAILABLE');" +
                "return;" +
                "}" +

                "chart=LightweightCharts.createChart(" +
                "document.getElementById('chart'),{" +

                "layout:{" +
                "background:{color:'#0B0F14'}," +
                "textColor:'#B8C2D1'" +
                "}," +

                "grid:{" +
                "vertLines:{color:'#18202B'}," +
                "horzLines:{color:'#18202B'}" +
                "}," +

                "rightPriceScale:{" +
                "borderColor:'#26303C'" +
                "}," +

                "timeScale:{" +
                "borderColor:'#26303C'," +
                "timeVisible:true," +
                "secondsVisible:false" +
                "}," +

                "crosshair:{mode:1}" +

                "});" +

                "if(chart.addSeries && " +
                "LightweightCharts.CandlestickSeries){" +

                "candleSeries=chart.addSeries(" +
                "LightweightCharts.CandlestickSeries,{" +

                "upColor:'#16C784'," +
                "downColor:'#EA3943'," +
                "borderUpColor:'#16C784'," +
                "borderDownColor:'#EA3943'," +
                "wickUpColor:'#16C784'," +
                "wickDownColor:'#EA3943'" +
                "});" +

                "}else if(chart.addCandlestickSeries){" +

                "candleSeries=" +
                "chart.addCandlestickSeries({" +

                "upColor:'#16C784'," +
                "downColor:'#EA3943'," +
                "borderUpColor:'#16C784'," +
                "borderDownColor:'#EA3943'," +
                "wickUpColor:'#16C784'," +
                "wickDownColor:'#EA3943'" +

                "});" +

                "}else{" +

                "setStatus('CANDLE CHART NOT SUPPORTED');" +
                "return;" +

                "}" +

                "setStatus('Chart ready');" +

                "}" +

                "function clearPriceLines(){" +

                "if(!candleSeries) return;" +

                "for(var i=0;i<priceLines.length;i++){" +
                "try{" +
                "candleSeries.removePriceLine(priceLines[i]);" +
                "}catch(e){}" +
                "}" +

                "priceLines=[];" +

                "}" +

                "function addPriceLine(price,title,color){" +

                "if(!candleSeries || price<=0) return;" +

                "var line=candleSeries.createPriceLine({" +
                "price:price," +
                "color:color," +
                "lineWidth:1," +
                "lineStyle:2," +
                "axisLabelVisible:true," +
                "title:title" +
                "});" +

                "priceLines.push(line);" +
                "}" +

                "function setChartData(" +
                "data,symbol,entry,sl,tp1,tp2,tp3,statusText){" +

                "if(!candleSeries){" +
                "setStatus('Chart is loading...');" +
                "return;" +
                "}" +

                "if(!data || data.length===0){" +
                "setStatus('NO CACHED MARKET DATA');" +
                "return;" +
                "}" +

                "candleSeries.setData(data);" +

                "clearPriceLines();" +

                "addPriceLine(entry,'ENTRY','#FFFFFF');" +
                "addPriceLine(sl,'SL','#EA3943');" +
                "addPriceLine(tp1,'TP1','#16C784');" +
                "addPriceLine(tp2,'TP2','#16C784');" +
                "addPriceLine(tp3,'TP3','#16C784');" +

                "if(ema20){" +
                "try{chart.removeSeries(ema20);}catch(e){}" +
                "ema20=null;" +
                "}" +

                "if(ema50){" +
                "try{chart.removeSeries(ema50);}catch(e){}" +
                "ema50=null;" +
                "}" +

                "chart.timeScale().fitContent();" +

                "setStatus(statusText+' • '+symbol);" +

                "}" +

                "function calculateEMA(data,period){" +

                "var result=[];" +

                "if(!data || data.length<period) return result;" +

                "var multiplier=2/(period+1);" +
                "var sum=0;" +

                "for(var i=0;i<period;i++){" +
                "sum+=Number(data[i].close);" +
                "}" +

                "var previous=sum/period;" +

                "result.push({" +
                "time:data[period-1].time," +
                "value:previous" +
                "});" +

                "for(var j=period;j<data.length;j++){" +

                "var close=Number(data[j].close);" +
                "var current=((close-previous)*multiplier)+previous;" +

                "result.push({" +
                "time:data[j].time," +
                "value:current" +
                "});" +

                "previous=current;" +
                "}" +

                "return result;" +
                "}" +

                "function showEMA(period){" +

                "if(!candleSeries) return;" +

                "var data=candleSeries.data ? " +
                "candleSeries.data() : [];" +

                "if(!data || data.length<period){" +
                "setStatus('Not enough candles for EMA '+period);" +
                "return;" +
                "}" +

                "var emaData=calculateEMA(data,period);" +

                "var series=null;" +

                "if(chart.addSeries && " +
                "LightweightCharts.LineSeries){" +

                "series=chart.addSeries(" +
                "LightweightCharts.LineSeries,{" +
                "lineWidth:2," +
                "priceLineVisible:false," +
                "lastValueVisible:false" +
                "});" +

                "}else if(chart.addLineSeries){" +

                "series=chart.addLineSeries({" +
                "lineWidth:2," +
                "priceLineVisible:false," +
                "lastValueVisible:false" +
                "});" +

                "}" +

                "if(!series){" +
                "setStatus('Indicator unavailable');" +
                "return;" +
                "}" +

                "series.setData(emaData);" +

                "return series;" +
                "}" +

                "function showEMA20(){" +

                "if(ema20){" +
                "try{chart.removeSeries(ema20);}catch(e){}" +
                "ema20=null;" +
                "setStatus('EMA 20 OFF');" +
                "return;" +
                "}" +

                "var data=getCurrentData();" +

                "var values=calculateEMA(data,20);" +

                "if(!values.length){" +
                "setStatus('Not enough candles for EMA 20');" +
                "return;" +
                "}" +

                "if(chart.addSeries && LightweightCharts.LineSeries){" +

                "ema20=chart.addSeries(" +
                "LightweightCharts.LineSeries,{" +
                "color:'#FFD54F'," +
                "lineWidth:2," +
                "priceLineVisible:false," +
                "lastValueVisible:true," +
                "title:'EMA 20'" +
                "});" +

                "}else if(chart.addLineSeries){" +

                "ema20=chart.addLineSeries({" +
                "color:'#FFD54F'," +
                "lineWidth:2," +
                "priceLineVisible:false," +
                "lastValueVisible:true," +
                "title:'EMA 20'" +
                "});" +

                "}" +

                "if(ema20){" +
                "ema20.setData(values);" +
                "setStatus('EMA 20 ON');" +
                "}" +

                "}" +

                "function showEMA50(){" +

                "if(ema50){" +
                "try{chart.removeSeries(ema50);}catch(e){}" +
                "ema50=null;" +
                "setStatus('EMA 50 OFF');" +
                "return;" +
                "}" +

                "var data=getCurrentData();" +

                "var values=calculateEMA(data,50);" +

                "if(!values.length){" +
                "setStatus('Not enough candles for EMA 50');" +
                "return;" +
                "}" +

                "if(chart.addSeries && LightweightCharts.LineSeries){" +

                "ema50=chart.addSeries(" +
                "LightweightCharts.LineSeries,{" +
                "color:'#42A5F5'," +
                "lineWidth:2," +
                "priceLineVisible:false," +
                "lastValueVisible:true," +
                "title:'EMA 50'" +
                "});" +

                "}else if(chart.addLineSeries){" +

                "ema50=chart.addLineSeries({" +
                "color:'#42A5F5'," +
                "lineWidth:2," +
                "priceLineVisible:false," +
                "lastValueVisible:true," +
                "title:'EMA 50'" +
                "});" +

                "}" +

                "if(ema50){" +
                "ema50.setData(values);" +
                "setStatus('EMA 50 ON');" +
                "}" +

                "}" +

                "function getCurrentData(){" +

                "if(!candleSeries) return [];" +

                "try{" +
                "return candleSeries.data();" +
                "}catch(e){" +
                "return [];" +
                "}" +

                "}" +

                "function clearIndicators(){" +

                "if(ema20){" +
                "try{chart.removeSeries(ema20);}catch(e){}" +
                "ema20=null;" +
                "}" +

                "if(ema50){" +
                "try{chart.removeSeries(ema50);}catch(e){}" +
                "ema50=null;" +
                "}" +

                "setStatus('Indicators cleared');" +

                "}" +

                "function fitChart(){" +

                "if(chart){" +
                "chart.timeScale().fitContent();" +
                "setStatus('Chart fitted');" +
                "}" +

                "}" +

                "function enableHorizontalDrawing(){" +

                "if(!candleSeries || !chart) return;" +

                "drawingMode=true;" +

                "var layer=document.getElementById('drawLayer');" +

                "layer.style.pointerEvents='auto';" +

                "layer.style.cursor='crosshair';" +

                "setStatus('DRAW MODE • TAP CHART FOR LEVEL');" +

                "}" +

                "function disableDrawing(){" +

                "drawingMode=false;" +

                "var layer=document.getElementById('drawLayer');" +

                "layer.style.pointerEvents='none';" +

                "layer.style.cursor='default';" +

                "setStatus('Drawing mode OFF');" +

                "}" +

                "function clearDrawings(){" +

                "clearPriceLines();" +

                "setStatus('Drawings cleared');" +

                "}" +

                "document.addEventListener('DOMContentLoaded',function(){" +

                "var layer=document.getElementById('drawLayer');" +

                "layer.addEventListener('click',function(event){" +

                "if(!drawingMode || !candleSeries) return;" +

                "var rect=layer.getBoundingClientRect();" +

                "var y=event.clientY-rect.top;" +

                "try{" +

                "var price=candleSeries.coordinateToPrice(y);" +

                "if(price!==null && price>0){" +

                "addPriceLine(price,'LEVEL','#FFFFFF');" +
                "setStatus('LEVEL '+price.toFixed(5));" +
                "}" +

                "}catch(e){" +
                "setStatus('Unable to draw level');" +
                "}" +

                "});" +

                "});" +

                "window.onload=function(){" +
                "initChart();" +
                "};" +

                "</script>" +

                "</body>" +
                "</html>";
    }

    private void updateChart() {

        if (!chartReady ||
                chartWebView == null) {
            return;
        }

        if (chartSymbol == null ||
                chartSymbol.trim().isEmpty()) {

            chartSymbol = "XAU/USD";
        }

        updateChartToolbarText();

        List<Candle> candles =
                candleData.get(
                        chartSymbol
                );

        if (candles == null ||
                candles.isEmpty()) {

            chartWebView.evaluateJavascript(
                    "setStatus('NO CACHED MARKET DATA');",
                    null
            );

            return;
        }

        try {

            JSONArray array =
                    new JSONArray();

            long nowSeconds =
                    System.currentTimeMillis()
                            / 1000L;

            long intervalSeconds =
                    timeframeSeconds(
                            selectedTimeframe
                    );

            long firstTime =
                    nowSeconds -
                            ((long) candles.size()
                                    * intervalSeconds);

            for (int i = 0;
                 i < candles.size();
                 i++) {

                Candle candle =
                        candles.get(i);

                JSONObject item =
                        new JSONObject();

                item.put(
                        "time",
                        firstTime +
                                ((long) i *
                                        intervalSeconds)
                );

                item.put(
                        "open",
                        candle.open
                );

                item.put(
                        "high",
                        candle.high
                );

                item.put(
                        "low",
                        candle.low
                );

                item.put(
                        "close",
                        candle.close
                );

                array.put(item);
            }

            SignalResult result =
                    currentSignals.get(
                            chartSymbol
                    );

            double entry = 0;
            double sl = 0;
            double tp1 = 0;
            double tp2 = 0;
            double tp3 = 0;

            if (result != null &&
                    ("BUY".equals(
                            result.action
                    ) ||
                     "SELL".equals(
                             result.action
                     ))) {

                entry = result.entry;
                sl = result.sl;
                tp1 = result.tp1;
                tp2 = result.tp2;
                tp3 = result.tp3;
            }

            long cachedAt =
                    getCachedCandlesSavedAt(
                            chartSymbol
                    );

            String chartStatusText;

            if (isForexWeekdayOpen()) {

                chartStatusText =
                        "LIVE MARKET DATA";

            } else {

                String cachedTime =
                        formatCachedTime(
                                cachedAt
                        );

                if (cachedTime.isEmpty()) {

                    chartStatusText =
                            "LAST MARKET DATA";

                } else {

                    chartStatusText =
                            "LAST REAL DATA • " +
                                    cachedTime;
                }
            }

            String jsData =
                    array.toString()
                            .replace(
                                    "\\",
                                    "\\\\"
                            )
                            .replace(
                                    "'",
                                    "\\'"
                            );

            String jsSymbol =
                    chartSymbol.replace(
                            "'",
                            "\\'"
                    );

            String jsStatus =
                    chartStatusText.replace(
                            "'",
                            "\\'"
                    );

            String javascript =
                    "setChartData(" +
                            jsData +
                            ",'" +
                            jsSymbol +
                            "'," +
                            entry +
                            "," +
                            sl +
                            "," +
                            tp1 +
                            "," +
                            tp2 +
                            "," +
                            tp3 +
                            ",'" +
                            jsStatus +
                            "');";

            chartWebView.evaluateJavascript(
                    javascript,
                    null
            );

        } catch (Exception e) {

            chartWebView.evaluateJavascript(
                    "setStatus('CHART DATA ERROR');",
                    null
            );
        }
    }

    private long timeframeSeconds(
            String timeframe
    ) {

        if ("5min".equals(timeframe)) {
            return 5L * 60L;
        }

        if ("15min".equals(timeframe)) {
            return 15L * 60L;
        }

        if ("30min".equals(timeframe)) {
            return 30L * 60L;
        }

        if ("1h".equals(timeframe)) {
            return 60L * 60L;
        }

        if ("4h".equals(timeframe)) {
            return 4L * 60L * 60L;
        }

        if ("1day".equals(timeframe)) {
            return 24L * 60L * 60L;
        }

        return 15L * 60L;
    }

    private void startLivePriceConnections() {

        String apiKey =
                getApiKey();

        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            return;
        }

        for (String symbol :
                SYMBOLS) {

            if (liveClients.containsKey(
                    symbol
            )) {

                continue;
            }

            TwelveDataClient client =
                    new TwelveDataClient(
                            new PairCallback(symbol)
                    );

            liveClients.put(
                    symbol,
                    client
            );

            client.connect(
                    symbol,
                    apiKey
            );
        }
    }

    private void closeLiveConnections() {

        for (TwelveDataClient client :
                liveClients.values()) {

            try {
                client.close();
            } catch (Exception ignored) {
            }
        }

        liveClients.clear();
    }

    private void updateBestSignalDisplay() {

        SignalResult best = null;
        String bestSymbol = null;

        for (String symbol :
                SYMBOLS) {

            SignalResult result =
                    currentSignals.get(
                            symbol
                    );

            if (result == null) {
                continue;
            }

            if (best == null ||
                    result.confidence >
                            best.confidence) {

                best = result;
                bestSymbol = symbol;
            }
        }

        if (best == null) {

            if (bestSignal != null) {
                bestSignal.setText("WAIT");
                bestSignal.setTextColor(
                        Color.LTGRAY
                );
            }

            if (bestDetails != null) {

                bestDetails.setText(
                        "Waiting for market data"
                );
            }

            return;
        }

        if (bestSignal != null) {

            bestSignal.setText(
                    best.action
            );

            if ("BUY".equals(
                    best.action
            )) {

                bestSignal.setTextColor(
                        Color.GREEN
                );

            } else if ("SELL".equals(
                    best.action
            )) {

                bestSignal.setTextColor(
                        Color.RED
                );

            } else {

                bestSignal.setTextColor(
                        Color.LTGRAY
                );
            }
        }

        if (bestDetails != null) {

            StringBuilder details =
                    new StringBuilder();

            details.append(
                    bestSymbol
            );

            details.append(
                    "\nTimeframe: "
            );

            details.append(
                    selectedTimeframe
            );

            details.append(
                    "\nConfidence: "
            );

            details.append(
                    best.confidence
            );

            details.append("%");

            if ("BUY".equals(
                    best.action
            ) || "SELL".equals(
                    best.action
            )) {

                details.append(
                        "\nEntry: "
                );

                details.append(
                        formatPrice(
                                best.entry
                        )
                );

                details.append(
                        "\nStop Loss: "
                );

                details.append(
                        formatPrice(
                                best.sl
                        )
                );

                details.append(
                        "\nTP1: "
                );

                details.append(
                        formatPrice(
                                best.tp1
                        )
                );

                details.append(
                        "\nTP2: "
                );

                details.append(
                        formatPrice(
                                best.tp2
                        )
                );

                details.append(
                        "\nTP3: "
                );

                details.append(
                        formatPrice(
                                best.tp3
                        )
                );

            } else {

                details.append(
                        "\nNo confirmed setup"
                );
            }

            bestDetails.setText(
                    details.toString()
            );
        }

        if (signalStatus != null) {

            signalStatus.setText(
                    best.status
            );
        }

        if (signalTime != null) {

            signalTime.setText(
                    new SimpleDateFormat(
                            "dd MMM yyyy HH:mm",
                            Locale.US
                    ).format(
                            new Date(
                                    best.signalTimeMillis
                            )
                    )
            );
        }

        if (signalResult != null) {

            if (best.resultReason == null ||
                    best.resultReason.isEmpty()) {

                signalResult.setText(
                        "Signal active"
                );

            } else {

                signalResult.setText(
                        best.resultReason
                );
            }
        }

        if (bestSymbol != null &&
                "ALL".equals(
                        selectedMarket
                )) {

            chartSymbol =
                    bestSymbol;

            updateChart();
        }
    }

    private String formatPrice(
            double value
    ) {

        if (value <= 0) {
            return "-";
        }

        return String.format(
                Locale.US,
                "%.5f",
                value
        );
    }

    private void updateHistoryDisplay() {

        if (history == null) {
            return;
        }

        List<String> savedHistory =
                signalStorage.getHistory();

        if (savedHistory == null ||
                savedHistory.isEmpty()) {

            history.setText(
                    "No completed signals yet."
            );

            return;
        }

        StringBuilder builder =
                new StringBuilder();

        for (String item :
                savedHistory) {

            builder.append(item);
            builder.append("\n\n");
        }

        history.setText(
                builder.toString()
        );
    }

    private void copyCurrentSignal() {

        SignalResult result =
                currentSignals.get(
                        chartSymbol
                );

        if (result == null) {

            Toast.makeText(
                    this,
                    "No signal available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String text =
                chartSymbol +
                        "\nTimeframe: " +
                        selectedTimeframe +
                        "\nSignal: " +
                        result.action +
                        "\nConfidence: " +
                        result.confidence +
                        "%";

        if ("BUY".equals(
                result.action
        ) || "SELL".equals(
                result.action
        )) {

            text +=
                    "\nEntry: " +
                            formatPrice(
                                    result.entry
                            ) +
                            "\nStop Loss: " +
                            formatPrice(
                                    result.sl
                            ) +
                            "\nTP1: " +
                            formatPrice(
                                    result.tp1
                            ) +
                            "\nTP2: " +
                            formatPrice(
                                    result.tp2
                            ) +
                            "\nTP3: " +
                            formatPrice(
                                    result.tp3
                            );
        }

        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );

        if (clipboard != null) {

            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "ForexPilot AI Signal",
                            text
                    )
            );

            Toast.makeText(
                    this,
                    "Signal copied",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showIndicatorMenu() {

        String[] options = {
                "EMA 20",
                "EMA 50",
                "Clear indicators"
        };

        new AlertDialog.Builder(this)
                .setTitle("INDICATORS")
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {

                                runChartJavaScript(
                                        "showEMA20();"
                                );

                            } else if (which == 1) {

                                runChartJavaScript(
                                        "showEMA50();"
                                );

                            } else {

                                runChartJavaScript(
                                        "clearIndicators();"
                                );
                            }
                        }
                )
                .show();
    }

    private void showDrawingMenu() {

        String[] options = {
                "Horizontal level",
                "Clear drawings",
                "Exit drawing mode"
        };

        new AlertDialog.Builder(this)
                .setTitle("DRAWING TOOLS")
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {

                                runChartJavaScript(
                                        "enableHorizontalDrawing();"
                                );

                                Toast.makeText(
                                        this,
                                        "Tap the chart to place a level",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else if (which == 1) {

                                runChartJavaScript(
                                        "clearDrawings();"
                                );

                            } else {

                                runChartJavaScript(
                                        "disableDrawing();"
                                );
                            }
                        }
                )
                .show();
    }

    private void fitChart() {

        runChartJavaScript(
                "fitChart();"
        );

        Toast.makeText(
                this,
                "Chart fitted",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void runChartJavaScript(
            String javascript
    ) {

        if (chartWebView == null ||
                !chartReady) {

            Toast.makeText(
                    this,
                    "Chart is still loading",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        chartWebView.evaluateJavascript(
                javascript,
                null
        );
    }

    private void toggleChartFullscreen() {

        if (chartFullscreen) {

            exitChartFullscreen();

        } else {

            enterChartFullscreen();
        }
    }

    private void enterChartFullscreen() {

        if (chartFullscreen ||
                chartWorkspace == null ||
                mainContent == null) {

            return;
        }

        chartFullscreen = true;

        if (mainContent != null) {

            for (int i = 0;
                 i < mainContent.getChildCount();
                 i++) {

                View child =
                        mainContent.getChildAt(i);

                if (child != chartWorkspace) {
                    child.setVisibility(
                            View.GONE
                    );
                }
            }
        }

        if (chartContainer != null) {

            chartContainer.getLayoutParams().height =
                    WindowManager.LayoutParams.MATCH_PARENT;

            chartContainer.requestLayout();
        }

        if (chartWorkspace != null) {

            chartWorkspace.getLayoutParams().height =
                    WindowManager.LayoutParams.MATCH_PARENT;

            chartWorkspace.requestLayout();
        }

        if (mainScroll != null) {

            mainScroll.setFillViewport(true);
            mainScroll.setVerticalScrollBarEnabled(false);
        }

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.KITKAT) {

            getWindow().getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
        }

        if (fullscreenButton != null) {
            fullscreenButton.setText("EXIT");
        }

        if (chartStatus != null) {
            chartStatus.setText(
                    "FULL-SCREEN CHART • " +
                            chartSymbol +
                            " • " +
                            selectedTimeframe
            );
        }

        handler.postDelayed(
                () -> runChartJavaScript(
                        "fitChart();"
                ),
                250
        );
    }

    private void exitChartFullscreen() {

        if (!chartFullscreen) {
            return;
        }

        chartFullscreen = false;

        if (mainContent != null) {

            for (int i = 0;
                 i < mainContent.getChildCount();
                 i++) {

                View child =
                        mainContent.getChildAt(i);

                child.setVisibility(
                        View.VISIBLE
                );
            }
        }

        if (chartContainer != null) {

            chartContainer.getLayoutParams().height =
                    dpToPx(normalChartHeightDp);

            chartContainer.requestLayout();
        }

        if (chartWorkspace != null) {

            chartWorkspace.getLayoutParams().height =
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

            chartWorkspace.requestLayout();
        }

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.KITKAT) {

            getWindow().getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_VISIBLE
                    );
        }

        if (fullscreenButton != null) {
            fullscreenButton.setText("FULL");
        }

        updateChartToolbarText();

        handler.postDelayed(
                () -> runChartJavaScript(
                        "fitChart();"
                ),
                250
        );
    }

    private int dpToPx(
            int dp
    ) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }

    private void requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        1001
                );
            }
        }
    }

    private void logout() {

        try {

            if (firebaseAuth != null) {
                firebaseAuth.signOut();
            }

        } catch (Exception ignored) {
        }

        closeLiveConnections();

        Intent intent =
                getPackageManager()
                        .getLaunchIntentForPackage(
                                getPackageName()
                        );

        if (intent != null) {

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK
            );

            startActivity(intent);
        }

        finish();
    }

    @Override
    protected void onResume() {

        super.onResume();

        updateMarketStatus();

        loadCachedCandleData();

        updateChart();

        if (isForexWeekdayOpen()) {
            startLivePriceConnections();
        }
    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                refreshRunnable
        );

        closeLiveConnections();

        if (chartWebView != null) {

            chartWebView.stopLoading();
            chartWebView.destroy();
        }

        super.onDestroy();
    }
}