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
import android.view.ViewGroup;
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

        preferences =
                getSharedPreferences(
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
        removePersonalBranding();
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

        refreshButton = findViewById(R.id.refreshButton);
        copyButton = findViewById(R.id.copyButton);
        logoutButton = findViewById(R.id.logoutButton);

        candleChart = findViewById(R.id.candleChart);
        chartWebView = candleChart;

        chartContainer = findViewById(R.id.chartContainer);

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

        marketGold = findViewById(R.id.marketGold);
        marketEur = findViewById(R.id.marketEur);
        marketGbp = findViewById(R.id.marketGbp);
        marketJpy = findViewById(R.id.marketJpy);
        marketNzd = findViewById(R.id.marketNzd);

        if (chartContainer != null) {

            normalChartHeightDp = 390;

            ViewParentHolder holder =
                    new ViewParentHolder(chartContainer);

            chartWorkspace =
                    holder.getLinearParent();

        }

        if (chartWorkspace != null) {

            android.view.ViewParent parent =
                    chartWorkspace.getParent();

            if (parent instanceof LinearLayout) {
                mainContent =
                        (LinearLayout) parent;
            }
        }

        if (mainContent != null) {

            android.view.ViewParent parent =
                    mainContent.getParent();

            if (parent instanceof ScrollView) {
                mainScroll =
                        (ScrollView) parent;
            }
        }
    }

    /*
     * Removes personal branding from the current XML without
     * requiring an XML change.
     */
    private void removePersonalBranding() {

        View root =
                findViewById(android.R.id.content);

        removeBrandingRecursive(root);
    }

    private void removeBrandingRecursive(View view) {

        if (view instanceof TextView) {

            TextView textView =
                    (TextView) view;

            CharSequence value =
                    textView.getText();

            if (value != null) {

                String text =
                        value.toString()
                                .toLowerCase(Locale.US);

                if (text.contains("made by") ||
                        text.contains("thabiso") ||
                        text.contains("khutlane")) {

                    textView.setVisibility(
                            View.GONE
                    );
                }
            }
        }

        if (view instanceof ViewGroup) {

            ViewGroup group =
                    (ViewGroup) view;

            for (int i = 0;
                 i < group.getChildCount();
                 i++) {

                removeBrandingRecursive(
                        group.getChildAt(i)
                );
            }
        }
    }

    private static class ViewParentHolder {

        private final View view;

        ViewParentHolder(View view) {
            this.view = view;
        }

        LinearLayout getLinearParent() {

            android.view.ViewParent parent =
                    view.getParent();

            if (parent instanceof LinearLayout) {
                return (LinearLayout) parent;
            }

            return null;
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
            chartPair.setText(chartSymbol);
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

        setButtonState(tf5, "5M", "5min");
        setButtonState(tf15, "15M", "15min");
        setButtonState(tf30, "30M", "30min");
        setButtonState(tf1h, "1H", "1h");
        setButtonState(tf4h, "4H", "4h");
        setButtonState(tf1d, "1D", "1day");
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

        if (timeframe.equals(selectedTimeframe)) {
            button.setTextColor(Color.WHITE);
        } else {
            button.setTextColor(Color.LTGRAY);
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

        if (symbol.equals(selectedMarket)) {
            button.setTextColor(Color.WHITE);
        } else {
            button.setTextColor(Color.LTGRAY);
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

        if (day == DayOfWeek.SATURDAY ||
                day == DayOfWeek.SUNDAY) {
            return false;
        }

        return MarketClock.isForexOpen(now);
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
        ).format(
                new Date()
        );
    }

    private void resetDailyCounterIfNeeded() {

        String today = todayKey();

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

        String apiKey = getApiKey();

        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            if (confirmation != null) {

                confirmation.setText(
                        "API KEY REQUIRED"
                );
            }

            updateChart();
            return;
        }

        List<String> symbolsToScan =
                new ArrayList<>();

        if ("ALL".equals(selectedMarket)) {

            for (String symbol : SYMBOLS) {
                symbolsToScan.add(symbol);
            }

        } else {

            symbolsToScan.add(
                    selectedMarket
            );
        }

        for (String symbol : symbolsToScan) {

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

                if (symbol.equals(chartSymbol)) {
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
                    new ArrayList<>(candles);

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
        public void error(String message) {

            runOnUiThread(() -> {

                if (updated != null) {

                    updated.setText(
                            "DATA ERROR • " +
                                    message
                    );
                }

                if (chartWebView != null &&
                        chartReady) {

                    String safe =
                            message == null
                                    ? "UNKNOWN DATA ERROR"
                                    : message
                                    .replace(
                                            "'",
                                            "\\'"
                                    );

                    chartWebView.evaluateJavascript(
                            "setChartMessage('DATA ERROR: " +
                                    safe +
                                    "');",
                            null
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

        if (!"BUY".equals(result.action) &&
                !"SELL".equals(result.action)) {

            updateBestSignalDisplay();
            return;
        }

        SignalResult existing =
                activeTrades.get(symbol);

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

        if (!"BUY".equals(result.action) &&
                !"SELL".equals(result.action)) {
            return;
        }

        if (!isNewSignalAllowed()) {
            return;
        }

        SignalResult existing =
                activeTrades.get(symbol);

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
                activeTrades.get(symbol);

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

        if (!oldStatus.equals(trade.status)) {

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

        for (String symbol : SYMBOLS) {

            candleData.put(
                    symbol,
                    new ArrayList<>()
            );
        }

        Map<String, SignalResult> saved =
                signalStorage.getActiveSignals();

        if (saved != null) {

            activeTrades.clear();

            activeTrades.putAll(saved);

            currentSignals.putAll(saved);
        }

        updateHistoryDisplay();
    }

    private String chartCacheKey(
            String symbol
    ) {

        String safeSymbol =
                symbol
                        .replace("/", "_")
                        .replace(" ", "_");

        String safeTimeframe =
                selectedTimeframe
                        .replace("/", "_")
                        .replace(" ", "_");

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
                        .replace("/", "_")
                        .replace(" ", "_");

        String safeTimeframe =
                selectedTimeframe
                        .replace("/", "_")
                        .replace(" ", "_");

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
                            candles.size() -
                                    MAX_CACHED_CANDLES
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

                object.put("open", c.open);
                object.put("high", c.high);
                object.put("low", c.low);
                object.put("close", c.close);

                array.put(object);
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

        for (String symbol : SYMBOLS) {

            candleData.put(
                    symbol,
                    loadCachedCandles(symbol)
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
                                object.getDouble("open"),
                                object.getDouble("high"),
                                object.getDouble("low"),
                                object.getDouble("close")
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

        return new SimpleDateFormat(
                "dd MMM yyyy HH:mm",
                Locale.US
        ).format(
                new Date(millis)
        );
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
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        chartWebView.setBackgroundColor(
                Color.rgb(11, 15, 20)
        );

        chartWebView.setVerticalScrollBarEnabled(false);
        chartWebView.setHorizontalScrollBarEnabled(false);

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
                null,
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
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,user-scalable=no'>" +

                "<style>" +
                "html,body{" +
                "margin:0;" +
                "padding:0;" +
                "width:100%;" +
                "height:100%;" +
                "overflow:hidden;" +
                "background:#0B0F14;" +
                "}" +

                "#root{" +
                "position:absolute;" +
                "left:0;" +
                "top:0;" +
                "right:0;" +
                "bottom:0;" +
                "display:flex;" +
                "flex-direction:column;" +
                "background:#0B0F14;" +
                "}" +

                "#chartArea{" +
                "position:relative;" +
                "width:100%;" +
                "height:100%;" +
                "min-height:180px;" +
                "background:#0B0F14;" +
                "}" +

                "#mainCanvas{" +
                "position:absolute;" +
                "left:0;" +
                "top:0;" +
                "width:100%;" +
                "height:100%;" +
                "}" +

                "#lowerCanvas{" +
                "position:absolute;" +
                "left:0;" +
                "bottom:0;" +
                "width:100%;" +
                "height:28%;" +
                "display:none;" +
                "border-top:1px solid #27303B;" +
                "}" +

                "#status{" +
                "position:absolute;" +
                "left:8px;" +
                "top:8px;" +
                "z-index:20;" +
                "padding:5px 8px;" +
                "border-radius:4px;" +
                "font-family:Arial,sans-serif;" +
                "font-size:11px;" +
                "color:#AEB8C5;" +
                "background:rgba(11,15,20,.90);" +
                "}" +

                "#indicatorTitle{" +
                "position:absolute;" +
                "left:8px;" +
                "bottom:4px;" +
                "z-index:20;" +
                "font-family:Arial,sans-serif;" +
                "font-size:10px;" +
                "color:#AEB8C5;" +
                "background:rgba(11,15,20,.90);" +
                "padding:3px 6px;" +
                "}" +

                "</style>" +
                "</head>" +

                "<body>" +

                "<div id='root'>" +

                "<div id='chartArea'>" +

                "<canvas id='mainCanvas'></canvas>" +

                "<canvas id='lowerCanvas'></canvas>" +

                "<div id='indicatorTitle'></div>" +

                "</div>" +

                "</div>" +

                "<div id='status'>Loading market chart...</div>" +

                "<script>" +

                "var currentData=[];" +

                "var signalLines=[];" +
                "var drawingLines=[];" +

                "var ema20Enabled=false;" +
                "var ema50Enabled=false;" +
                "var sma200Enabled=false;" +
                "var bollingerEnabled=false;" +

                "var lowerIndicator='NONE';" +
                "var drawingMode=false;" +

                "var mainCanvas=null;" +
                "var lowerCanvas=null;" +

                "function setChartMessage(message){" +
                "var s=document.getElementById('status');" +
                "s.innerText=message;" +
                "}" +

                "function setStatus(message){" +
                "setChartMessage(message);" +
                "}" +

                "function resizeCanvas(canvas){" +

                "if(!canvas)return;" +

                "var rect=canvas.getBoundingClientRect();" +

                "var ratio=window.devicePixelRatio||1;" +

                "canvas.width=Math.max(1,Math.floor(rect.width*ratio));" +
                "canvas.height=Math.max(1,Math.floor(rect.height*ratio));" +

                "var ctx=canvas.getContext('2d');" +

                "ctx.setTransform(ratio,0,0,ratio,0,0);" +
                "}" +

                "function resizeAll(){" +

                "resizeCanvas(mainCanvas);" +
                "resizeCanvas(lowerCanvas);" +

                "if(currentData.length>0){" +
                "drawEverything();" +
                "}" +
                "}" +

                "function priceRange(data){" +

                "var high=-Infinity;" +
                "var low=Infinity;" +

                "for(var i=0;i<data.length;i++){" +

                "high=Math.max(high,Number(data[i].high));" +
                "low=Math.min(low,Number(data[i].low));" +

                "}" +

                "var spread=high-low;" +

                "if(!isFinite(spread)||spread<=0){" +
                "spread=Math.max(Math.abs(high)*0.001,0.0001);" +
                "}" +

                "return {" +
                "high:high+spread*0.08," +
                "low:low-spread*0.08" +
                "};" +
                "}" +

                "function mapPrice(price,range,top,bottom){" +

                "if(range.high===range.low)return bottom;" +

                "return top+" +
                "((range.high-price)/" +
                "(range.high-range.low))*"+
                "(bottom-top);" +
                "}" +

                "function calculateEMA(data,period){" +

                "var result=[];" +

                "if(!data||data.length<period)return result;" +

                "var multiplier=2/(period+1);" +
                "var sum=0;" +

                "for(var i=0;i<period;i++){" +
                "sum+=Number(data[i].close);" +
                "}" +

                "var previous=sum/period;" +

                "result.push({" +
                "index:period-1," +
                "value:previous" +
                "});" +

                "for(var j=period;j<data.length;j++){" +

                "var close=Number(data[j].close);" +

                "var value=((close-previous)*multiplier)+previous;" +

                "previous=value;" +

                "result.push({" +
                "index:j," +
                "value:value" +
                "});" +

                "}" +

                "return result;" +
                "}" +

                "function calculateSMA(data,period){" +

                "var result=[];" +

                "if(!data||data.length<period)return result;" +

                "for(var i=period-1;i<data.length;i++){" +

                "var sum=0;" +

                "for(var j=i-period+1;j<=i;j++){" +
                "sum+=Number(data[j].close);" +
                "}" +

                "result.push({" +
                "index:i," +
                "value:sum/period" +
                "});" +

                "}" +

                "return result;" +
                "}" +

                "function calculateRSI(data,period){" +

                "var result=[];" +

                "if(!data||data.length<=period)return result;" +

                "var gains=0;" +
                "var losses=0;" +

                "for(var i=1;i<=period;i++){" +

                "var change=" +
                "Number(data[i].close)-Number(data[i-1].close);" +

                "if(change>0)gains+=change;" +
                "else losses+=Math.abs(change);" +

                "}" +

                "var avgGain=gains/period;" +
                "var avgLoss=losses/period;" +

                "var rsi=avgLoss===0?100:" +
                "(100-(100/(1+(avgGain/avgLoss))));" +

                "result.push({" +
                "index:period," +
                "value:rsi" +
                "});" +

                "for(var k=period+1;k<data.length;k++){" +

                "var diff=" +
                "Number(data[k].close)-Number(data[k-1].close);" +

                "var gain=diff>0?diff:0;" +
                "var loss=diff<0?Math.abs(diff):0;" +

                "avgGain=((avgGain*(period-1))+gain)/period;" +
                "avgLoss=((avgLoss*(period-1))+loss)/period;" +

                "rsi=avgLoss===0?100:" +
                "(100-(100/(1+(avgGain/avgLoss))));" +

                "result.push({" +
                "index:k," +
                "value:rsi" +
                "});" +
                "}" +

                "return result;" +
                "}" +

                "function calculateATR(data,period){" +

                "var result=[];" +
                "if(!data||data.length<=period)return result;" +

                "var trs=[];" +

                "for(var i=1;i<data.length;i++){" +

                "var high=Number(data[i].high);" +
                "var low=Number(data[i].low);" +
                "var pc=Number(data[i-1].close);" +

                "trs.push(Math.max(" +
                "high-low," +
                "Math.abs(high-pc)," +
                "Math.abs(low-pc)" +
                "));" +

                "}" +

                "var sum=0;" +

                "for(var j=0;j<period;j++){" +
                "sum+=trs[j];" +
                "}" +

                "var atr=sum/period;" +

                "result.push({" +
                "index:period," +
                "value:atr" +
                "});" +

                "for(var k=period;k<trs.length;k++){" +

                "atr=((atr*(period-1))+trs[k])/period;" +

                "result.push({" +
                "index:k+1," +
                "value:atr" +
                "});" +

                "}" +

                "return result;" +
                "}" +

                "function calculateStochastic(data,period){" +

                "var result=[];" +
                "if(!data||data.length<period)return result;" +

                "for(var i=period-1;i<data.length;i++){" +

                "var highest=-Infinity;" +
                "var lowest=Infinity;" +

                "for(var j=i-period+1;j<=i;j++){" +

                "highest=Math.max(highest,Number(data[j].high));" +
                "lowest=Math.min(lowest,Number(data[j].low));" +
                "}" +

                "var close=Number(data[i].close);" +

                "var value=highest===lowest?" +
                "50:" +
                "((close-lowest)/(highest-lowest))*100;" +

                "result.push({" +
                "index:i," +
                "value:value" +
                "});" +

                "}" +

                "return result;" +
                "}" +

                "function calculateMACD(data){" +

                "var result=[];" +

                "var ema12=calculateEMA(data,12);" +
                "var ema26=calculateEMA(data,26);" +

                "var map12={};" +

                "for(var i=0;i<ema12.length;i++){" +
                "map12[ema12[i].index]=ema12[i].value;" +
                "}" +

                "for(var j=0;j<ema26.length;j++){" +

                "var index=ema26[j].index;" +

                "if(map12[index]!==undefined){" +

                "result.push({" +
                "index:index," +
                "value:map12[index]-ema26[j].value" +
                "});" +

                "}" +

                "}" +

                "if(result.length<9)return {" +
                "line:result," +
                "signal:[]," +
                "histogram:[]" +
                "};" +

                "var signal=[];" +
                "var multiplier=2/10;" +
                "var sum=0;" +

                "for(var k=0;k<9;k++){" +
                "sum+=result[k].value;" +
                "}" +

                "var previous=sum/9;" +

                "signal.push({" +
                "index:result[8].index," +
                "value:previous" +
                "});" +

                "for(var n=9;n<result.length;n++){" +

                "var value=((result[n].value-previous)*multiplier)+previous;" +
                "previous=value;" +

                "signal.push({" +
                "index:result[n].index," +
                "value:value" +
                "});" +
                "}" +

                "var signalMap={};" +

                "for(var p=0;p<signal.length;p++){" +
                "signalMap[signal[p].index]=signal[p].value;" +
                "}" +

                "var histogram=[];" +

                "for(var q=0;q<result.length;q++){" +

                "var sv=signalMap[result[q].index];" +

                "if(sv!==undefined){" +
                "histogram.push({" +
                "index:result[q].index," +
                "value:result[q].value-sv" +
                "});" +
                "}" +
                "}" +

                "return {" +
                "line:result," +
                "signal:signal," +
                "histogram:histogram" +
                "};" +
                "}" +

                "function calculateBollinger(data,period,mult){" +

                "var upper=[];" +
                "var middle=[];" +
                "var lower=[];" +

                "if(!data||data.length<period){" +
                "return {" +
                "upper:upper," +
                "middle:middle," +
                "lower:lower" +
                "};" +
                "}" +

                "for(var i=period-1;i<data.length;i++){" +

                "var sum=0;" +

                "for(var j=i-period+1;j<=i;j++){" +
                "sum+=Number(data[j].close);" +
                "}" +

                "var mean=sum/period;" +
                "var variance=0;" +

                "for(var k=i-period+1;k<=i;k++){" +

                "var d=Number(data[k].close)-mean;" +
                "variance+=d*d;" +

                "}" +

                "var sd=Math.sqrt(variance/period);" +

                "upper.push({" +
                "index:i," +
                "value:mean+(mult*sd)" +
                "});" +

                "middle.push({" +
                "index:i," +
                "value:mean" +
                "});" +

                "lower.push({" +
                "index:i," +
                "value:mean-(mult*sd)" +
                "});" +

                "}" +

                "return {" +
                "upper:upper," +
                "middle:middle," +
                "lower:lower" +
                "};" +
                "}" +

                "function drawGrid(ctx,w,h,top,bottom,left,right,range){" +

                "ctx.strokeStyle='#18212C';" +
                "ctx.lineWidth=1;" +

                "for(var i=0;i<6;i++){" +

                "var y=top+((bottom-top)/5)*i;" +

                "ctx.beginPath();" +
                "ctx.moveTo(left,y);" +
                "ctx.lineTo(right,y);" +
                "ctx.stroke();" +

                "var price=range.high-" +
                "((range.high-range.low)/5)*i;" +

                "ctx.fillStyle='#718096';" +
                "ctx.font='10px Arial';" +

                "ctx.fillText(price.toFixed(5),right-62,y-2);" +
                "}" +

                "for(var j=0;j<7;j++){" +

                "var x=left+((right-left)/6)*j;" +

                "ctx.beginPath();" +
                "ctx.moveTo(x,top);" +
                "ctx.lineTo(x,bottom);" +
                "ctx.stroke();" +
                "}" +
                "}" +

                "function drawLineSeries(ctx,series,range,left,right,top,bottom,color,width){" +

                "if(!series||series.length===0)return;" +

                "ctx.strokeStyle=color;" +
                "ctx.lineWidth=width;" +
                "ctx.beginPath();" +

                "var started=false;" +

                "for(var i=0;i<series.length;i++){" +

                "var item=series[i];" +

                "var x=left+" +
                "((right-left)*item.index/" +
                "Math.max(1,currentData.length-1));" +

                "var y=mapPrice(item.value,range,top,bottom);" +

                "if(!started){" +
                "ctx.moveTo(x,y);" +
                "started=true;" +
                "}else{" +
                "ctx.lineTo(x,y);" +
                "}" +
                "}" +

                "ctx.stroke();" +
                "}" +

                "function drawEverything(){" +

                "if(!mainCanvas)return;" +

                "var ctx=mainCanvas.getContext('2d');" +
                "var w=mainCanvas.clientWidth;" +
                "var h=mainCanvas.clientHeight;" +

                "ctx.clearRect(0,0,w,h);" +

                "if(!currentData||currentData.length===0){" +

                "setChartMessage('NO MARKET DATA');" +
                "return;" +
                "}" +

                "var lowerVisible=lowerIndicator!=='NONE';" +

                "var lowerHeight=lowerVisible?h*0.28:0;" +
                "var top=28;" +
                "var bottom=h-lowerHeight-25;" +
                "var left=8;" +
                "var right=w-70;" +

                "var visibleData=currentData;" +

                "var range=priceRange(visibleData);" +

                "drawGrid(ctx,w,h,top,bottom,left,right,range);" +

                "var count=visibleData.length;" +
                "var spacing=(right-left)/Math.max(1,count);" +
                "var bodyWidth=Math.max(2,spacing*0.62);" +

                "for(var i=0;i<count;i++){" +

                "var c=visibleData[i];" +

                "var x=left+(spacing*i)+(spacing/2);" +

                "var openY=mapPrice(Number(c.open),range,top,bottom);" +
                "var closeY=mapPrice(Number(c.close),range,top,bottom);" +
                "var highY=mapPrice(Number(c.high),range,top,bottom);" +
                "var lowY=mapPrice(Number(c.low),range,top,bottom);" +

                "var up=Number(c.close)>=Number(c.open);" +

                "ctx.strokeStyle=up?'#16C784':'#EA3943';" +
                "ctx.fillStyle=up?'#16C784':'#EA3943';" +
                "ctx.lineWidth=1;" +

                "ctx.beginPath();" +
                "ctx.moveTo(x,highY);" +
                "ctx.lineTo(x,lowY);" +
                "ctx.stroke();" +

                "var bodyTop=Math.min(openY,closeY);" +
                "var bodyHeight=Math.max(1,Math.abs(closeY-openY));" +

                "ctx.fillRect(" +
                "x-bodyWidth/2," +
                "bodyTop," +
                "bodyWidth," +
                "bodyHeight" +
                ");" +
                "}" +

                "if(ema20Enabled){" +
                "drawLineSeries(" +
                "ctx,calculateEMA(currentData,20)," +
                "range,left,right,top,bottom," +
                "'#FFD54F',2" +
                ");" +
                "}" +

                "if(ema50Enabled){" +
                "drawLineSeries(" +
                "ctx,calculateEMA(currentData,50)," +
                "range,left,right,top,bottom," +
                "'#42A5F5',2" +
                ");" +
                "}" +

                "if(sma200Enabled){" +
                "drawLineSeries(" +
                "ctx,calculateSMA(currentData,200)," +
                "range,left,right,top,bottom," +
                "'#FFFFFF',2" +
                ");" +
                "}" +

                "if(bollingerEnabled){" +

                "var bands=calculateBollinger(currentData,20,2);" +

                "drawLineSeries(ctx,bands.upper,range,left,right,top,bottom,'#90CAF9',1);" +
                "drawLineSeries(ctx,bands.middle,range,left,right,top,bottom,'#B0BEC5',1);" +
                "drawLineSeries(ctx,bands.lower,range,left,right,top,bottom,'#90CAF9',1);" +

                "}" +

                "for(var s=0;s<signalLines.length;s++){" +

                "var line=signalLines[s];" +

                "var y=mapPrice(line.price,range,top,bottom);" +

                "ctx.strokeStyle=line.color;" +
                "ctx.lineWidth=1;" +

                "ctx.beginPath();" +
                "ctx.moveTo(left,y);" +
                "ctx.lineTo(right,y);" +
                "ctx.stroke();" +

                "ctx.fillStyle=line.color;" +
                "ctx.font='10px Arial';" +
                "ctx.fillText(line.title,right+3,y+3);" +
                "}" +

                "for(var d=0;d<drawingLines.length;d++){" +

                "var draw=drawingLines[d];" +
                "var dy=mapPrice(draw.price,range,top,bottom);" +

                "ctx.strokeStyle='#FFFFFF';" +
                "ctx.lineWidth=1;" +
                "ctx.setLineDash([5,4]);" +

                "ctx.beginPath();" +
                "ctx.moveTo(left,dy);" +
                "ctx.lineTo(right,dy);" +
                "ctx.stroke();" +

                "ctx.setLineDash([]);" +

                "ctx.fillStyle='#FFFFFF';" +
                "ctx.font='10px Arial';" +
                "ctx.fillText('LEVEL',right+3,dy+3);" +
                "}" +

                "drawLowerIndicator();" +

                "ctx.fillStyle='#8A96A8';" +
                "ctx.font='10px Arial';" +
                "ctx.fillText(" +
                "'CANDLES: '+currentData.length," +
                "8,15" +
                ");" +

                "setChartMessage(" +
                "lowerVisible?" +
                "lowerIndicator+' • '+currentData.length+' candles' :" +
                "currentData.length+' candles • chart ready'" +
                ");" +

                "}" +

                "function drawLowerIndicator(){" +

                "var canvas=lowerCanvas;" +
                "var ctx=canvas.getContext('2d');" +
                "var w=canvas.clientWidth;" +
                "var h=canvas.clientHeight;" +

                "ctx.clearRect(0,0,w,h);" +

                "if(lowerIndicator==='NONE'){" +
                "canvas.style.display='none';" +
                "return;" +
                "}" +

                "canvas.style.display='block';" +

                "var series=[];" +
                "var series2=[];" +
                "var histogram=[];" +
                "var min=Infinity;" +
                "var max=-Infinity;" +

                "if(lowerIndicator==='RSI'){" +
                "series=calculateRSI(currentData,14);" +
                "min=0;" +
                "max=100;" +
                "}else if(lowerIndicator==='ATR'){" +
                "series=calculateATR(currentData,14);" +
                "}else if(lowerIndicator==='STOCHASTIC'){" +
                "series=calculateStochastic(currentData,14);" +
                "min=0;" +
                "max=100;" +
                "}else if(lowerIndicator==='MACD'){" +

                "var macd=calculateMACD(currentData);" +
                "series=macd.line;" +
                "series2=macd.signal;" +
                "histogram=macd.histogram;" +
                "}" +

                "for(var i=0;i<series.length;i++){" +
                "min=Math.min(min,series[i].value);" +
                "max=Math.max(max,series[i].value);" +
                "}" +

                "for(var j=0;j<series2.length;j++){" +
                "min=Math.min(min,series2[j].value);" +
                "max=Math.max(max,series2[j].value);" +
                "}" +

                "if(!isFinite(min)||!isFinite(max))return;" +

                "if(min===max){" +
                "min-=1;" +
                "max+=1;" +
                "}" +

                "var pad=(max-min)*0.12;" +
                "min-=pad;" +
                "max+=pad;" +

                "var left=8;" +
                "var right=w-8;" +
                "var top=12;" +
                "var bottom=h-12;" +

                "ctx.strokeStyle='#18212C';" +
                "ctx.lineWidth=1;" +

                "for(var g=0;g<4;g++){" +

                "var gy=top+((bottom-top)/3)*g;" +

                "ctx.beginPath();" +
                "ctx.moveTo(left,gy);" +
                "ctx.lineTo(right,gy);" +
                "ctx.stroke();" +
                "}" +

                "function mapLower(value){" +
                "return top+((max-value)/(max-min))*(bottom-top);" +
                "}" +

                "if(lowerIndicator==='MACD'){" +

                "for(var b=0;b<histogram.length;b++){" +

                "var hx=left+" +
                "((right-left)*histogram[b].index/" +
                "Math.max(1,currentData.length-1));" +

                "var hy=mapLower(histogram[b].value);" +
                "var zero=mapLower(0);" +

                "ctx.fillStyle=histogram[b].value>=0?'#16C784':'#EA3943';" +

                "ctx.fillRect(" +
                "hx-2," +
                "Math.min(hy,zero)," +
                "4," +
                "Math.max(1,Math.abs(zero-hy))" +
                ");" +
                "}" +

                "}" +

                "function drawLowerLine(items,color,width){" +

                "if(!items||items.length===0)return;" +

                "ctx.strokeStyle=color;" +
                "ctx.lineWidth=width;" +
                "ctx.beginPath();" +

                "for(var z=0;z<items.length;z++){" +

                "var x=left+" +
                "((right-left)*items[z].index/" +
                "Math.max(1,currentData.length-1));" +

                "var y=mapLower(items[z].value);" +

                "if(z===0)ctx.moveTo(x,y);" +
                "else ctx.lineTo(x,y);" +
                "}" +

                "ctx.stroke();" +
                "}" +

                "if(lowerIndicator==='RSI'){" +
                "drawLowerLine(series,'#AB47BC',2);" +
                "}else if(lowerIndicator==='ATR'){" +
                "drawLowerLine(series,'#FF7043',2);" +
                "}else if(lowerIndicator==='STOCHASTIC'){" +
                "drawLowerLine(series,'#26C6DA',2);" +
                "}else if(lowerIndicator==='MACD'){" +
                "drawLowerLine(series,'#42A5F5',2);" +
                "drawLowerLine(series2,'#FFCA28',2);" +
                "}" +

                "var label=document.getElementById('indicatorTitle');" +
                "label.innerText=lowerIndicator==='NONE'?'':lowerIndicator;" +
                "}" +

                "function setChartData(data,symbol,entry,sl,tp1,tp2,tp3,statusText){" +

                "if(!data||data.length===0){" +
                "setChartMessage('NO MARKET DATA');" +
                "return;" +
                "}" +

                "currentData=data;" +

                "signalLines=[];" +

                "if(entry>0){" +
                "signalLines.push({price:entry,title:'ENTRY',color:'#FFFFFF'});" +
                "}" +

                "if(sl>0){" +
                "signalLines.push({price:sl,title:'SL',color:'#EA3943'});" +
                "}" +

                "if(tp1>0){" +
                "signalLines.push({price:tp1,title:'TP1',color:'#16C784'});" +
                "}" +

                "if(tp2>0){" +
                "signalLines.push({price:tp2,title:'TP2',color:'#16C784'});" +
                "}" +

                "if(tp3>0){" +
                "signalLines.push({price:tp3,title:'TP3',color:'#16C784'});" +
                "}" +

                "drawEverything();" +

                "}" +

                "function setOverlayIndicator(name,enabled){" +

                "if(name==='EMA20')ema20Enabled=enabled;" +
                "if(name==='EMA50')ema50Enabled=enabled;" +
                "if(name==='SMA200')sma200Enabled=enabled;" +
                "if(name==='BOLLINGER')bollingerEnabled=enabled;" +

                "drawEverything();" +

                "setChartMessage(name+' '+(enabled?'ON':'OFF'));" +
                "}" +

                "function setLowerIndicator(name){" +

                "lowerIndicator=name;" +

                "drawEverything();" +

                "setChartMessage(name==='NONE'?" +
                "'Lower indicators cleared':" +
                "name+' active'" +
                ");" +

                "}" +

                "function clearIndicators(){" +

                "ema20Enabled=false;" +
                "ema50Enabled=false;" +
                "sma200Enabled=false;" +
                "bollingerEnabled=false;" +
                "lowerIndicator='NONE';" +

                "drawEverything();" +

                "setChartMessage('Indicators cleared');" +
                "}" +

                "function fitChart(){" +

                "drawEverything();" +

                "setChartMessage('Chart fitted');" +
                "}" +

                "function addDrawingLine(price,title){" +

                "drawingLines.push({" +
                "price:price," +
                "title:title" +
                "});" +

                "drawEverything();" +
                "}" +

                "function enableHorizontalDrawing(){" +

                "drawingMode=true;" +

                "setChartMessage('DRAW MODE • TAP CHART FOR LEVEL');" +
                "}" +

                "function disableDrawing(){" +

                "drawingMode=false;" +

                "setChartMessage('Drawing mode OFF');" +
                "}" +

                "function clearDrawings(){" +

                "drawingLines=[];" +

                "drawEverything();" +

                "setChartMessage('Drawings cleared');" +
                "}" +

                "function chartClick(event){" +

                "if(!drawingMode||currentData.length===0)return;" +

                "var rect=mainCanvas.getBoundingClientRect();" +

                "var y=event.clientY-rect.top;" +

                "var h=mainCanvas.clientHeight;" +

                "var top=28;" +

                "var lowerHeight=" +
                "(lowerIndicator!=='NONE'?h*0.28:0);" +

                "var bottom=h-lowerHeight-25;" +

                "var range=priceRange(currentData);" +

                "var price=range.high-" +
                "((y-top)/(bottom-top))" +
                "*(range.high-range.low);" +

                "if(price>0){" +

                "addDrawingLine(price,'LEVEL');" +

                "disableDrawing();" +

                "}" +

                "}" +

                "function init(){" +

                "mainCanvas=document.getElementById('mainCanvas');" +
                "lowerCanvas=document.getElementById('lowerCanvas');" +

                "mainCanvas.addEventListener('click',chartClick);" +

                "window.addEventListener('resize',resizeAll);" +

                "resizeAll();" +

                "setChartMessage('CHART READY • WAITING FOR MARKET DATA');" +

                "}" +

                "window.onload=init;" +

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
                candleData.get(chartSymbol);

        if (candles == null ||
                candles.isEmpty()) {

            chartWebView.evaluateJavascript(
                    "setChartMessage('WAITING FOR MARKET DATA • " +
                            chartSymbol.replace("'", "") +
                            "');",
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
                            (
                                    (long) candles.size()
                                            *
                                            intervalSeconds
                            );

            for (int i=0;
                 i<candles.size();
                 i++) {

                Candle candle =
                        candles.get(i);

                JSONObject item =
                        new JSONObject();

                item.put(
                        "time",
                        firstTime +
                                ((long)i *
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
                    currentSignals.get(chartSymbol);

            double entry=0;
            double sl=0;
            double tp1=0;
            double tp2=0;
            double tp3=0;

            if (result != null &&
                    (
                            "BUY".equals(result.action) ||
                            "SELL".equals(result.action)
                    )) {

                entry=result.entry;
                sl=result.sl;
                tp1=result.tp1;
                tp2=result.tp2;
                tp3=result.tp3;
            }

            long cachedAt =
                    getCachedCandlesSavedAt(
                            chartSymbol
                    );

            String statusText;

            if (isForexWeekdayOpen()) {

                statusText =
                        "LIVE MARKET DATA";

            } else {

                String cached =
                        formatCachedTime(cachedAt);

                statusText =
                        cached.isEmpty()
                                ? "LAST MARKET DATA"
                                : "LAST REAL DATA • " +
                                cached;
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
                    chartSymbol
                            .replace(
                                    "'",
                                    "\\'"
                            );

            String jsStatus =
                    statusText
                            .replace(
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
                    "setChartMessage('CHART DATA ERROR');",
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

        for (String symbol : SYMBOLS) {

            if (liveClients.containsKey(symbol)) {
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

        SignalResult best=null;
        String bestSymbol=null;

        for (String symbol : SYMBOLS) {

            SignalResult result =
                    currentSignals.get(symbol);

            if (result == null) {
                continue;
            }

            if (best == null ||
                    result.confidence >
                            best.confidence) {

                best=result;
                bestSymbol=symbol;
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

            if ("BUY".equals(best.action)) {

                bestSignal.setTextColor(
                        Color.GREEN
                );

            } else if ("SELL".equals(best.action)) {

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

            details.append(bestSymbol);

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

            if ("BUY".equals(best.action) ||
                    "SELL".equals(best.action)) {

                details.append(
                        "\nEntry: "
                );

                details.append(
                        formatPrice(best.entry)
                );

                details.append(
                        "\nStop Loss: "
                );

                details.append(
                        formatPrice(best.sl)
                );

                details.append(
                        "\nTP1: "
                );

                details.append(
                        formatPrice(best.tp1)
                );

                details.append(
                        "\nTP2: "
                );

                details.append(
                        formatPrice(best.tp2)
                );

                details.append(
                        "\nTP3: "
                );

                details.append(
                        formatPrice(best.tp3)
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
            signalStatus.setText(best.status);
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

            signalResult.setText(
                    best.resultReason == null ||
                            best.resultReason.isEmpty()
                            ? "Signal active"
                            : best.resultReason
            );
        }

        if (bestSymbol != null &&
                "ALL".equals(selectedMarket)) {

            chartSymbol=bestSymbol;

            updateChart();
        }
    }

    private String formatPrice(double value) {

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

        for (String item : savedHistory) {

            builder.append(item);
            builder.append("\n\n");
        }

        history.setText(
                builder.toString()
        );
    }

    private void copyCurrentSignal() {

        SignalResult result =
                currentSignals.get(chartSymbol);

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

        if ("BUY".equals(result.action) ||
                "SELL".equals(result.action)) {

            text +=
                    "\nEntry: " +
                            formatPrice(result.entry) +
                            "\nStop Loss: " +
                            formatPrice(result.sl) +
                            "\nTP1: " +
                            formatPrice(result.tp1) +
                            "\nTP2: " +
                            formatPrice(result.tp2) +
                            "\nTP3: " +
                            formatPrice(result.tp3);
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
                "SMA 200",
                "Bollinger Bands",
                "RSI 14",
                "MACD 12/26/9",
                "ATR 14",
                "Stochastic 14",
                "Clear indicators"
        };

        new AlertDialog.Builder(this)
                .setTitle(
                        "TECHNICAL INDICATORS"
                )
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {

                                runChartJavaScript(
                                        "setOverlayIndicator(" +
                                                "'EMA20'," +
                                                "!ema20Enabled);"
                                );

                            } else if (which == 1) {

                                runChartJavaScript(
                                        "setOverlayIndicator(" +
                                                "'EMA50'," +
                                                "!ema50Enabled);"
                                );

                            } else if (which == 2) {

                                runChartJavaScript(
                                        "setOverlayIndicator(" +
                                                "'SMA200'," +
                                                "!sma200Enabled);"
                                );

                            } else if (which == 3) {

                                runChartJavaScript(
                                        "setOverlayIndicator(" +
                                                "'BOLLINGER'," +
                                                "!bollingerEnabled);"
                                );

                            } else if (which == 4) {

                                runChartJavaScript(
                                        "setLowerIndicator(" +
                                                "lowerIndicator==='RSI'" +
                                                "? 'NONE' : 'RSI'" +
                                                ");"
                                );

                            } else if (which == 5) {

                                runChartJavaScript(
                                        "setLowerIndicator(" +
                                                "lowerIndicator==='MACD'" +
                                                "? 'NONE' : 'MACD'" +
                                                ");"
                                );

                            } else if (which == 6) {

                                runChartJavaScript(
                                        "setLowerIndicator(" +
                                                "lowerIndicator==='ATR'" +
                                                "? 'NONE' : 'ATR'" +
                                                ");"
                                );

                            } else if (which == 7) {

                                runChartJavaScript(
                                        "setLowerIndicator(" +
                                                "lowerIndicator==='STOCHASTIC'" +
                                                "? 'NONE' : 'STOCHASTIC'" +
                                                ");"
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
                .setTitle(
                        "DRAWING TOOLS"
                )
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

        chartFullscreen=true;

        for (int i=0;
             i<mainContent.getChildCount();
             i++) {

            View child =
                    mainContent.getChildAt(i);

            if (child != chartWorkspace) {

                child.setVisibility(
                        View.GONE
                );
            }
        }

        if (chartContainer != null) {

            chartContainer
                    .getLayoutParams()
                    .height =
                    WindowManager.LayoutParams
                            .MATCH_PARENT;

            chartContainer.requestLayout();
        }

        if (chartWorkspace != null) {

            chartWorkspace
                    .getLayoutParams()
                    .height =
                    WindowManager.LayoutParams
                            .MATCH_PARENT;

            chartWorkspace.requestLayout();
        }

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.KITKAT) {

            getWindow()
                    .getDecorView()
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
                300
        );
    }

    private void exitChartFullscreen() {

        if (!chartFullscreen) {
            return;
        }

        chartFullscreen=false;

        if (mainContent != null) {

            for (int i=0;
                 i<mainContent.getChildCount();
                 i++) {

                mainContent
                        .getChildAt(i)
                        .setVisibility(
                                View.VISIBLE
                        );
            }
        }

        if (chartContainer != null) {

            chartContainer
                    .getLayoutParams()
                    .height =
                    dpToPx(
                            normalChartHeightDp
                    );

            chartContainer.requestLayout();
        }

        if (chartWorkspace != null) {

            chartWorkspace
                    .getLayoutParams()
                    .height =
                    ViewGroup.LayoutParams
                            .WRAP_CONTENT;

            chartWorkspace.requestLayout();
        }

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.KITKAT) {

            getWindow()
                    .getDecorView()
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
                300
        );
    }

    private int dpToPx(int dp) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp*density
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
                                Manifest.permission
                                        .POST_NOTIFICATIONS
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