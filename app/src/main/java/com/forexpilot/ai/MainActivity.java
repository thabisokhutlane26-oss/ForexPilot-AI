package com.forexpilot.ai;

import android.Manifest;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView title;
    private TextView market;
    private TextView session;
    private TextView scanner;
    private TextView bestSignal;
    private TextView bestDetails;
    private TextView confirmation;
    private TextView updated;

    private TextView performanceSummary;
    private TextView signalStatus;
    private TextView signalTime;
    private TextView signalResult;
    private TextView history;

    private Button refreshButton;
    private Button copyButton;
    private Button logoutButton;

    private WebView candleChart;

    private boolean chartReady = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Map<String, TwelveDataClient> clients =
            new LinkedHashMap<>();

    private final Map<String, SignalResult> results =
            new LinkedHashMap<>();

    private final Map<String, SignalResult> activeTrades =
            new LinkedHashMap<>();

    private final Map<String, Double> prices =
            new LinkedHashMap<>();

    private final Map<String, List<Candle>> candleData =
            new LinkedHashMap<>();

    private final Map<String, String> lastMarketDirection =
            new LinkedHashMap<>();

    private final Map<String, Boolean> reversalWaiting =
            new LinkedHashMap<>();

    private final List<String> signalHistory =
            new ArrayList<>();

    private SignalStorage signalStorage;

    private FirebaseAuth firebaseAuth;

    private static final String[] SYMBOLS = {
            "XAU/USD",
            "EUR/USD",
            "GBP/USD",
            "USD/JPY",
            "NZD/USD"
    };

    private static final String PREFS_NAME =
            "ForexPilotSettings";

    private static final String PREF_TIMEFRAME =
            "selected_timeframe";

    /*
     * =========================================================
     * DAILY NEW SIGNAL PROTECTION
     * =========================================================
     */

    private static final int MAX_DAILY_SIGNALS = 2;

    private static final String PREF_SIGNAL_DATE =
            "signal_date";

    private static final String PREF_DAILY_SIGNAL_COUNT =
            "daily_signal_count";

    private String selectedTimeframe = "5min";
    private String selectedMarket = "ALL";

    private String bestSymbol = null;
    private SignalResult bestResult = null;

    private static final long REFRESH_INTERVAL =
            5 * 60 * 1000L;

    private static final int
            NOTIFICATION_PERMISSION_REQUEST = 1001;

    private int wins = 0;
    private int losses = 0;
    private int expired = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        selectedTimeframe =
                preferences.getString(
                        PREF_TIMEFRAME,
                        "5min"
                );

        title = findViewById(R.id.title);
        market = findViewById(R.id.market);
        session = findViewById(R.id.session);
        scanner = findViewById(R.id.scanner);
        bestSignal = findViewById(R.id.bestSignal);
        bestDetails = findViewById(R.id.bestDetails);
        confirmation = findViewById(R.id.confirmation);
        updated = findViewById(R.id.updated);

        performanceSummary =
                findViewById(R.id.performanceSummary);

        signalStatus =
                findViewById(R.id.signalStatus);

        signalTime =
                findViewById(R.id.signalTime);

        signalResult =
                findViewById(R.id.signalResult);

        history =
                findViewById(R.id.history);

        refreshButton =
                findViewById(R.id.refreshButton);

        copyButton =
                findViewById(R.id.copyButton);

        logoutButton =
                findViewById(R.id.logoutButton);

        candleChart =
                findViewById(R.id.candleChart);

        signalStorage =
                new SignalStorage(this);

        firebaseAuth =
                FirebaseAuth.getInstance();

        resetDailyCounterIfNeeded();

        loadSavedHistory();
        loadActiveSignals();

        title.setText("ForexPilot AI");

        setupChart();
        setupButtons();
        updateMarketStatus();
        updatePerformance();

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null
                || apiKey.trim().isEmpty()) {

            scanner.setText(
                    "API KEY REQUIRED\n\n"
                            + "Add your Twelve Data API key "
                            + "to GitHub Secrets."
            );

            bestSignal.setText("WAIT");

            bestSignal.setTextColor(
                    Color.rgb(
                            255,
                            213,
                            79
                    )
            );

            confirmation.setText(
                    "WAITING FOR MARKET DATA"
            );

            confirmation.setTextColor(
                    Color.rgb(
                            255,
                            213,
                            79
                    )
            );

            bestDetails.setText(
                    "No live market data available."
            );

            updated.setText(
                    "Waiting for API key..."
            );

            return;
        }

        requestNotificationPermissionIfNeeded();

        startBackgroundMonitor();

        startScanner(apiKey);

        handler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {

                        updateMarketStatus();

                        resetDailyCounterIfNeeded();

                        handler.postDelayed(
                                this,
                                30000L
                        );
                    }
                },
                30000L
        );

        handler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {

                        refreshSignals(apiKey);

                        handler.postDelayed(
                                this,
                                REFRESH_INTERVAL
                        );
                    }
                },
                REFRESH_INTERVAL
        );
    }

    private void requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST
                );
            }
        }
    }

    private void startBackgroundMonitor() {

        Intent serviceIntent =
                new Intent(
                        this,
                        ForexMonitorService.class
                );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            ContextCompat.startForegroundService(
                    this,
                    serviceIntent
            );

        } else {

            startService(
                    serviceIntent
            );
        }
    }

    /*
     * =========================================================
     * MARKET SIGNAL PROTECTION
     * =========================================================
     */

    private boolean isNewSignalAllowed() {

        resetDailyCounterIfNeeded();

        Instant now =
                Instant.now();

        /*
         * MarketClock controls the actual
         * forex opening/closing window.
         */
        if (!MarketClock.isForexOpen(now)) {
            return false;
        }

        /*
         * Explicit Monday-Friday protection.
         */
        ZonedDateTime newYork =
                now.atZone(
                        ZoneId.of(
                                "America/New_York"
                        )
                );

        DayOfWeek day =
                newYork.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {

            return false;
        }

        /*
         * Maximum two NEW signals per day.
         */
        return getDailySignalCount()
                < MAX_DAILY_SIGNALS;
    }

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

        if (current >= MAX_DAILY_SIGNALS) {
            return;
        }

        prefs.edit()
                .putInt(
                        PREF_DAILY_SIGNAL_COUNT,
                        current + 1
                )
                .apply();
    }

    /*
     * =========================================================
     * INTERACTIVE TRADINGVIEW-STYLE CHART
     * =========================================================
     */

    private void setupChart() {

        candleChart.setBackgroundColor(
                Color.rgb(
                        11,
                        15,
                        20
                )
        );

        WebSettings settings =
                candleChart.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        candleChart.setVerticalScrollBarEnabled(false);
        candleChart.setHorizontalScrollBarEnabled(false);
        candleChart.setOverScrollMode(
                WebView.OVER_SCROLL_NEVER
        );

        candleChart.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url) {

                        super.onPageFinished(
                                view,
                                url
                        );

                        chartReady = true;

                        updateChart();
                    }
                }
        );

        candleChart.loadDataWithBaseURL(
                "https://www.tradingview.com/",
                createChartHtml(),
                "text/html",
                "UTF-8",
                null
        );
    }

    private String createChartHtml() {

        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta name=\"viewport\" "
                + "content=\"width=device-width, "
                + "initial-scale=1.0, "
                + "maximum-scale=1.0, "
                + "user-scalable=no\">"

                + "<script "
                + "src=\"https://unpkg.com/"
                + "lightweight-charts/"
                + "dist/lightweight-charts."
                + "standalone.production.js\">"
                + "</script>"

                + "<style>"

                + "html,body{"
                + "margin:0;"
                + "padding:0;"
                + "width:100%;"
                + "height:100%;"
                + "overflow:hidden;"
                + "background:#0B0F14;"
                + "font-family:Arial,sans-serif;"
                + "}"

                + "#chart{"
                + "position:absolute;"
                + "left:0;"
                + "top:0;"
                + "right:0;"
                + "bottom:26px;"
                + "}"

                + "#watermark{"
                + "position:absolute;"
                + "left:8px;"
                + "bottom:4px;"
                + "font-size:10px;"
                + "color:#6F7885;"
                + "z-index:10;"
                + "}"

                + "#symbol{"
                + "position:absolute;"
                + "left:10px;"
                + "top:8px;"
                + "z-index:10;"
                + "color:#FFFFFF;"
                + "font-size:13px;"
                + "font-weight:bold;"
                + "background:rgba(11,15,20,0.75);"
                + "padding:5px 8px;"
                + "border-radius:4px;"
                + "}"

                + "</style>"
                + "</head>"

                + "<body>"

                + "<div id=\"chart\"></div>"
                + "<div id=\"symbol\">ForexPilot AI</div>"

                + "<div id=\"watermark\">"
                + "Charts by TradingView"
                + "</div>"

                + "<script>"

                + "let chart=null;"
                + "let candleSeries=null;"
                + "let priceLines=[];"

                + "function createTradingChart(){"

                + "const container="
                + "document.getElementById('chart');"

                + "chart="
                + "LightweightCharts.createChart("
                + "container,{"

                + "layout:{"
                + "background:{color:'#0B0F14'},"
                + "textColor:'#AAB4C3'"
                + "},"

                + "grid:{"
                + "vertLines:{color:'#151C25'},"
                + "horzLines:{color:'#151C25'}"
                + "},"

                + "crosshair:{"
                + "mode:LightweightCharts."
                + "CrosshairMode.Normal"
                + "},"

                + "rightPriceScale:{"
                + "borderColor:'#27313D',"
                + "scaleMargins:{"
                + "top:0.08,"
                + "bottom:0.08"
                + "}"
                + "},"

                + "timeScale:{"
                + "borderColor:'#27313D',"
                + "timeVisible:true,"
                + "secondsVisible:false,"
                + "rightOffset:5,"
                + "barSpacing:7,"
                + "minBarSpacing:2"
                + "},"

                + "handleScroll:{"
                + "mouseWheel:true,"
                + "pressedMouseMove:true,"
                + "horzTouchDrag:true"
                + "},"

                + "handleScale:{"
                + "mouseWheel:true,"
                + "pinch:true,"
                + "axisPressedMouseMove:true"
                + "},"

                + "autoSize:true,"
                + "attributionLogo:true"
                + "});"

                + "candleSeries="
                + "chart.addSeries("
                + "LightweightCharts.CandlestickSeries,"
                + "{"

                + "upColor:'#4CFF78',"
                + "downColor:'#FF5050',"
                + "borderUpColor:'#4CFF78',"
                + "borderDownColor:'#FF5050',"
                + "wickUpColor:'#4CFF78',"
                + "wickDownColor:'#FF5050'"

                + "});"

                + "}"

                + "function clearPriceLines(){"

                + "if(!candleSeries)return;"

                + "for(let i=0;i<priceLines.length;i++){"
                + "candleSeries.removePriceLine("
                + "priceLines[i]);"
                + "}"

                + "priceLines=[];"
                + "}"

                + "function addPriceLine("
                + "value,title,color){"

                + "if(!candleSeries||"
                + "!value||value<=0)return;"

                + "const line="
                + "candleSeries.createPriceLine({"

                + "price:value,"
                + "color:color,"
                + "lineWidth:1,"
                + "lineStyle:"
                + "LightweightCharts.LineStyle.Dashed,"
                + "axisLabelVisible:true,"
                + "title:title"
                + "});"

                + "priceLines.push(line);"
                + "}"

                + "function setChartData("
                + "data,symbol,entry,sl,tp1,tp2,tp3){"

                + "if(!chart||!candleSeries)return;"

                + "document.getElementById('symbol')"
                + ".innerText="
                + "symbol;"

                + "candleSeries.setData(data);"

                + "clearPriceLines();"

                + "addPriceLine("
                + "entry,'ENTRY','#FFFFFF');"

                + "addPriceLine("
                + "sl,'SL','#FF5050');"

                + "addPriceLine("
                + "tp1,'TP1','#4CFF78');"

                + "addPriceLine("
                + "tp2,'TP2','#4CFF78');"

                + "addPriceLine("
                + "tp3,'TP3','#4CFF78');"

                + "chart.timeScale().fitContent();"
                + "}"

                + "function resizeChart(){"

                + "if(chart){"
                + "chart.timeScale().applyOptions({"
                + "rightOffset:5"
                + "});"
                + "}"

                + "}"

                + "window.addEventListener("
                + "'resize',resizeChart);"

                + "createTradingChart();"

                + "</script>"

                + "</body>"
                + "</html>";
    }

    private void updateChart() {

        if (!chartReady
                || candleChart == null) {
            return;
        }

        String chartSymbol =
                getChartSymbol();

        if (chartSymbol == null) {
            return;
        }

        List<Candle> candles =
                candleData.get(chartSymbol);

        if (candles == null
                || candles.isEmpty()) {

            return;
        }

        SignalResult result =
                results.get(chartSymbol);

        try {

            JSONArray array =
                    new JSONArray();

            long intervalSeconds =
                    timeframeSeconds();

            long nowSeconds =
                    System.currentTimeMillis()
                            / 1000L;

            long firstTime =
                    nowSeconds
                            - (
                            (long) candles.size()
                                    * intervalSeconds
                    );

            for (int i = 0;
                 i < candles.size();
                 i++) {

                Candle candle =
                        candles.get(i);

                JSONObject item =
                        new JSONObject();

                item.put(
                        "time",
                        firstTime
                                + (
                                (long) i
                                        * intervalSeconds
                        )
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

            double entry = 0;
            double sl = 0;
            double tp1 = 0;
            double tp2 = 0;
            double tp3 = 0;

            if (result != null
                    && !"WAIT".equals(
                    result.action
            )) {

                entry = result.entry;
                sl = result.sl;
                tp1 = result.tp1;
                tp2 = result.tp2;
                tp3 = result.tp3;
            }

            String javascript =
                    "setChartData("
                            + array.toString()
                            + ","
                            + JSONObject.quote(
                            chartSymbol
                    )
                            + ","
                            + entry
                            + ","
                            + sl
                            + ","
                            + tp1
                            + ","
                            + tp2
                            + ","
                            + tp3
                            + ");";

            candleChart.evaluateJavascript(
                    javascript,
                    null
            );

        } catch (Exception e) {

            updated.setText(
                    "Chart error: "
                            + e.getMessage()
            );
        }
    }

    private long timeframeSeconds() {

        switch (selectedTimeframe) {

            case "15min":
                return 15L * 60L;

            case "30min":
                return 30L * 60L;

            case "1h":
                return 60L * 60L;

            case "4h":
                return 4L * 60L * 60L;

            case "1day":
                return 24L * 60L * 60L;

            default:
                return 5L * 60L;
        }
    }

    private String getChartSymbol() {

        if (!"ALL".equals(selectedMarket)) {
            return selectedMarket;
        }

        if (bestSymbol != null) {
            return bestSymbol;
        }

        return "XAU/USD";
    }

    /*
     * =========================================================
     * HISTORY
     * =========================================================
     */

    private void loadSavedHistory() {

        signalHistory.clear();

        List<String> saved =
                signalStorage.getHistory();

        signalHistory.addAll(saved);

        wins = 0;
        losses = 0;
        expired = 0;

        for (String record : saved) {

            if (record.contains(
                    " | WIN | "
            )) {

                wins++;

            } else if (record.contains(
                    " | LOSS | "
            )) {

                losses++;

            } else if (record.contains(
                    " | EXPIRED | "
            )) {

                expired++;
            }
        }
    }

    private void loadActiveSignals() {

        Map<String, SignalResult> active =
                signalStorage.getActiveSignals();

        for (String symbol : SYMBOLS) {

            SignalResult result =
                    active.get(symbol);

            if (result != null
                    && result.isOpen()) {

                activeTrades.put(
                        symbol,
                        result
                );
            }

            results.put(
                    symbol,
                    new SignalResult(
                            "WAIT",
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                    )
            );

            prices.put(
                    symbol,
                    0.0
            );

            candleData.put(
                    symbol,
                    new ArrayList<>()
            );

            lastMarketDirection.put(
                    symbol,
                    "WAIT"
            );

            reversalWaiting.put(
                    symbol,
                    false
            );
        }
    }

    /*
     * =========================================================
     * BUTTONS
     * =========================================================
     */

    private void setupButtons() {

        Button tf5 =
                findViewById(R.id.tf5);

        Button tf15 =
                findViewById(R.id.tf15);

        Button tf30 =
                findViewById(R.id.tf30);

        Button tf1h =
                findViewById(R.id.tf1h);

        Button tf4h =
                findViewById(R.id.tf4h);

        Button tf1d =
                findViewById(R.id.tf1d);

        Button marketGold =
                findViewById(R.id.marketGold);

        Button marketEur =
                findViewById(R.id.marketEur);

        Button marketGbp =
                findViewById(R.id.marketGbp);

        Button marketJpy =
                findViewById(R.id.marketJpy);

        Button marketNzd =
                findViewById(R.id.marketNzd);

        tf5.setOnClickListener(v ->
                selectTimeframe(
                        "5min",
                        "5M"
                )
        );

        tf15.setOnClickListener(v ->
                selectTimeframe(
                        "15min",
                        "15M"
                )
        );

        tf30.setOnClickListener(v ->
                selectTimeframe(
                        "30min",
                        "30M"
                )
        );

        tf1h.setOnClickListener(v ->
                selectTimeframe(
                        "1h",
                        "1H"
                )
        );

        tf4h.setOnClickListener(v ->
                selectTimeframe(
                        "4h",
                        "4H"
                )
        );

        tf1d.setOnClickListener(v ->
                selectTimeframe(
                        "1day",
                        "1D"
                )
        );

        marketGold.setOnClickListener(v ->
                selectMarket("XAU/USD")
        );

        marketEur.setOnClickListener(v ->
                selectMarket("EUR/USD")
        );

        marketGbp.setOnClickListener(v ->
                selectMarket("GBP/USD")
        );

        marketJpy.setOnClickListener(v ->
                selectMarket("USD/JPY")
        );

        marketNzd.setOnClickListener(v ->
                selectMarket("NZD/USD")
        );

        refreshButton.setOnClickListener(v -> {

            String apiKey =
                    BuildConfig.TWELVE_DATA_API_KEY;

            if (apiKey == null
                    || apiKey.trim().isEmpty()) {

                Toast.makeText(
                        MainActivity.this,
                        "API key is missing.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            refreshSignals(apiKey);

            Toast.makeText(
                    MainActivity.this,
                    "Refreshing live market...",
                    Toast.LENGTH_SHORT
            ).show();
        });

        copyButton.setOnClickListener(
                v -> copyBestSignal()
        );

        logoutButton.setOnClickListener(
                v -> logout()
        );
    }

    private void logout() {

        firebaseAuth.signOut();

        Toast.makeText(
                this,
                "Logged out",
                Toast.LENGTH_SHORT
        ).show();

        Intent intent =
                new Intent(
                        MainActivity.this,
                        LoginActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }

    private void selectTimeframe(
            String interval,
            String display) {

        selectedTimeframe = interval;

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        PREF_TIMEFRAME,
                        interval
                )
                .apply();

        for (String symbol : SYMBOLS) {

            results.put(
                    symbol,
                    new SignalResult(
                            "WAIT",
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                    )
            );

            lastMarketDirection.put(
                    symbol,
                    "WAIT"
            );

            reversalWaiting.put(
                    symbol,
                    false
            );
        }

        bestSymbol = null;
        bestResult = null;

        updated.setText(
                "Timeframe selected: "
                        + display
                        + " • Refreshing..."
        );

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey != null
                && !apiKey.trim().isEmpty()) {

            refreshSignals(apiKey);
        }
    }

    private void selectMarket(
            String symbol) {

        selectedMarket = symbol;

        updateScanner();
        updateChart();

        updated.setText(
                symbol
                        + " selected • "
                        + timeframeName()
        );
    }

    private String timeframeName() {

        switch (selectedTimeframe) {

            case "15min":
                return "15M";

            case "30min":
                return "30M";

            case "1h":
                return "1H";

            case "4h":
                return "4H";

            case "1day":
                return "1D";

            default:
                return "5M";
        }
    }

    /*
     * =========================================================
     * MARKET DATA
     * =========================================================
     */

    private void startScanner(
            String apiKey) {

        /*
         * Don't request candle data while the
         * forex market is closed.
         */
        if (!isForexWeekdayOpen()) {

            updateMarketStatus();

            updated.setText(
                    "MARKET CLOSED • "
                            + "NEW SIGNALS PAUSED"
            );

            updateScanner();

            return;
        }

        for (String symbol : SYMBOLS) {

            if (!results.containsKey(symbol)) {

                results.put(
                        symbol,
                        new SignalResult(
                                "WAIT",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0
                        )
                );
            }

            if (!prices.containsKey(symbol)) {

                prices.put(
                        symbol,
                        0.0
                );
            }

            if (!candleData.containsKey(symbol)) {

                candleData.put(
                        symbol,
                        new ArrayList<>()
                );
            }

            TwelveDataClient client =
                    new TwelveDataClient(
                            new PairCallback(symbol)
                    );

            clients.put(
                    symbol,
                    client
            );

            client.candles(
                    symbol,
                    selectedTimeframe,
                    apiKey
            );

            client.connect(
                    symbol,
                    apiKey
            );
        }

        updateScanner();
    }

    private void refreshSignals(
            String apiKey) {

        /*
         * IMPORTANT:
         *
         * Do not request new candle data when
         * the forex market is closed.
         *
         * This prevents unnecessary Twelve Data
         * API usage while still allowing existing
         * active trades to be tracked by price().
         */
        if (!MarketClock.isForexOpen(
                Instant.now()
        )) {

            runOnUiThread(() -> {

                updateMarketStatus();

                updated.setText(
                        "MARKET CLOSED • "
                                + "NEW SIGNALS PAUSED"
                );
            });

            return;
        }

        /*
         * Explicit Monday-Friday protection.
         */
        ZonedDateTime newYork =
                Instant.now().atZone(
                        ZoneId.of(
                                "America/New_York"
                        )
                );

        DayOfWeek day =
                newYork.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {

            runOnUiThread(() -> {

                updateMarketStatus();

                updated.setText(
                        "WEEKEND • "
                                + "NEW SIGNALS PAUSED"
                );
            });

            return;
        }

        runOnUiThread(() ->
                updated.setText(
                        "Refreshing "
                                + timeframeName()
                                + " live market..."
                )
        );

        for (String symbol : SYMBOLS) {

            TwelveDataClient client =
                    clients.get(symbol);

            if (client != null) {

                client.candles(
                        symbol,
                        selectedTimeframe,
                        apiKey
                );

            } else {

                TwelveDataClient newClient =
                        new TwelveDataClient(
                                new PairCallback(symbol)
                        );

                clients.put(
                        symbol,
                        newClient
                );

                newClient.candles(
                        symbol,
                        selectedTimeframe,
                        apiKey
                );

                newClient.connect(
                        symbol,
                        apiKey
                );
            }
        }
    }

    private boolean isForexWeekdayOpen() {

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

        return day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY;
    }

    private void updateMarketStatus() {

        boolean open =
                MarketClock.isForexOpen(
                        Instant.now()
                );

        ZonedDateTime newYork =
                Instant.now().atZone(
                        ZoneId.of(
                                "America/New_York"
                        )
                );

        DayOfWeek day =
                newYork.getDayOfWeek();

        boolean weekday =
                day != DayOfWeek.SATURDAY
                        && day != DayOfWeek.SUNDAY;

        boolean actuallyOpen =
                open && weekday;

        market.setText(
                actuallyOpen
                        ? "FOREX MARKET: OPEN"
                        : "FOREX MARKET: CLOSED"
        );

        session.setText(
                "Sessions: "
                        + MarketClock.session(
                        Instant.now()
                )
        );
    }

    /*
     * =========================================================
     * SCANNER
     * =========================================================
     */

    private void updateScanner() {

        StringBuilder text =
                new StringBuilder();

        text.append(
                timeframeName()
                        + " LIVE MARKET SCANNER\n\n"
        );

        for (String symbol : SYMBOLS) {

            if (!"ALL".equals(selectedMarket)
                    && !selectedMarket.equals(symbol)) {
                continue;
            }

            SignalResult result =
                    results.get(symbol);

            if (result == null) {
                continue;
            }

            double currentPrice =
                    prices.containsKey(symbol)
                            ? prices.get(symbol)
                            : 0;

            text.append(symbol)
                    .append("\n");

            text.append(
                    "CURRENT SIGNAL: "
            )
                    .append(result.action)
                    .append("\n");

            text.append(
                    "Confidence: "
            )
                    .append(result.confidence)
                    .append("%\n");

            SignalResult active =
                    activeTrades.get(symbol);

            if (active != null
                    && active.isOpen()) {

                text.append(
                        "ACTIVE TRADE: "
                )
                        .append(active.action)
                        .append(" • ")
                        .append(active.status)
                        .append("\n");
            }

            if (currentPrice > 0) {

                text.append(
                        "Live Price: "
                )
                        .append(
                                formatPrice(
                                        symbol,
                                        currentPrice
                                )
                        )
                        .append("\n");
            }

            if (!"WAIT".equals(result.action)
                    && result.entry > 0) {

                text.append(
                        "Entry: "
                )
                        .append(
                                formatPrice(
                                        symbol,
                                        result.entry
                                )
                        )
                        .append("\n");

                text.append(
                        "SL: "
                )
                        .append(
                                formatPrice(
                                        symbol,
                                        result.sl
                                )
                        )
                        .append("\n");

                text.append(
                        "TP1: "
                )
                        .append(
                                formatPrice(
                                        symbol,
                                        result.tp1
                                )
                        )
                        .append("\n");

                text.append(
                        "TP2: "
                )
                        .append(
                                formatPrice(
                                        symbol,
                                        result.tp2
                                )
                        )
                        .append("\n");

                text.append(
                        "TP3: "
                )
                        .append(
                                formatPrice(
                                        symbol,
                                        result.tp3
                                )
                        )
                        .append("\n");

                text.append(
                        "Signal Time: "
                )
                        .append(
                                formatSignalTime(
                                        result.signalTimeMillis
                                )
                        )
                        .append("\n");
            }

            text.append("\n");
        }

        scanner.setText(
                text.toString()
        );

        findBestSignal();

        updatePerformance();
    }

    private void findBestSignal() {

        bestSymbol = null;
        bestResult = null;

        for (String symbol : SYMBOLS) {

            if (!"ALL".equals(selectedMarket)
                    && !selectedMarket.equals(symbol)) {
                continue;
            }

            SignalResult result =
                    results.get(symbol);

            if (result == null) {
                continue;
            }

            if ("WAIT".equals(result.action)) {
                continue;
            }

            if (bestResult == null
                    || result.confidence
                    > bestResult.confidence) {

                bestSymbol = symbol;
                bestResult = result;
            }
        }

        if (bestResult == null) {

            bestSignal.setText("WAIT");

            bestSignal.setTextColor(
                    Color.rgb(
                            255,
                            213,
                            79
                    )
            );

            confirmation.setText(
                    "WAITING FOR CONFIRMATION"
            );

            confirmation.setTextColor(
                    Color.rgb(
                            255,
                            213,
                            79
                    )
            );

            bestDetails.setText(
                    "No confirmed current BUY or SELL setup."
            );

            updateCurrentSignalStatus();

            updateChart();

            return;
        }

        String action =
                bestResult.action;

        bestSignal.setText(
                bestSymbol
                        + " • "
                        + action
                        + " • "
                        + bestResult.confidence
                        + "%"
        );

        if ("BUY".equals(action)) {

            bestSignal.setTextColor(
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );

            confirmation.setText(
                    "BUY CONFIRMED"
            );

            confirmation.setTextColor(
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );

        } else if ("SELL".equals(action)) {

            bestSignal.setTextColor(
                    Color.rgb(
                            255,
                            80,
                            80
                    )
            );

            confirmation.setText(
                    "SELL CONFIRMED"
            );

            confirmation.setTextColor(
                    Color.rgb(
                            255,
                            80,
                            80
                    )
            );
        }

        bestDetails.setText(
                String.format(
                        Locale.US,
                        "CURRENT MARKET SIGNAL\n\n"
                                + "Market: %s\n"
                                + "Timeframe: %s\n"
                                + "Signal: %s\n"
                                + "Confidence: %d%%\n\n"
                                + "Entry: %s\n"
                                + "Stop Loss: %s\n"
                                + "TP1: %s\n"
                                + "TP2: %s\n"
                                + "TP3: %s\n\n"
                                + "Signal Time: %s",
                        bestSymbol,
                        timeframeName(),
                        bestResult.action,
                        bestResult.confidence,
                        formatPrice(
                                bestSymbol,
                                bestResult.entry
                        ),
                        formatPrice(
                                bestSymbol,
                                bestResult.sl
                        ),
                        formatPrice(
                                bestSymbol,
                                bestResult.tp1
                        ),
                        formatPrice(
                                bestSymbol,
                                bestResult.tp2
                        ),
                        formatPrice(
                                bestSymbol,
                                bestResult.tp3
                        ),
                        formatSignalTime(
                                bestResult.signalTimeMillis
                        )
                )
        );

        updateCurrentSignalStatus();

        updateChart();
    }

    private void updateCurrentSignalStatus() {

        if (bestResult == null) {

            signalStatus.setText(
                    "WAITING FOR SIGNAL"
            );

            signalStatus.setTextColor(
                    Color.rgb(
                            255,
                            213,
                            79
                    )
            );

            signalTime.setText(
                    "Signal time: --"
            );

            signalResult.setText(
                    "Result: --"
            );

            return;
        }

        signalStatus.setText(
                bestResult.action
        );

        if ("BUY".equals(
                bestResult.action
        )) {

            signalStatus.setTextColor(
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );

        } else if ("SELL".equals(
                bestResult.action
        )) {

            signalStatus.setTextColor(
                    Color.rgb(
                            255,
                            80,
                            80
                    )
            );

        } else {

            signalStatus.setTextColor(
                    Color.rgb(
                            255,
                            213,
                            79
                    )
            );
        }

        signalTime.setText(
                "Signal time: "
                        + formatSignalTime(
                        bestResult.signalTimeMillis
                )
        );

        signalResult.setText(
                "Current market setup • "
                        + timeframeName()
        );
    }

    /*
     * =========================================================
     * PERFORMANCE
     * =========================================================
     */

    private void updatePerformance() {

        int total =
                wins
                        + losses
                        + expired;

        double winRate = 0;

        if (total > 0) {

            winRate =
                    ((double) wins / total)
                            * 100.0;
        }

        int open =
                countOpenSignals();

        performanceSummary.setText(
                String.format(
                        Locale.US,
                        "WIN RATE: %.1f%%\n\n"
                                + "WINS: %d\n"
                                + "LOSSES: %d\n"
                                + "OPEN: %d\n"
                                + "EXPIRED: %d",
                        winRate,
                        wins,
                        losses,
                        open,
                        expired
                )
        );

        updateHistoryDisplay();
    }

    private int countOpenSignals() {

        int count = 0;

        for (SignalResult result :
                activeTrades.values()) {

            if (result != null
                    && result.isOpen()) {

                count++;
            }
        }

        return count;
    }

    /*
     * =========================================================
     * TRADE TRACKING
     * =========================================================
     */

    private void checkActiveTrade(
            String symbol,
            double price) {

        if (price <= 0) {
            return;
        }

        SignalResult trade =
                activeTrades.get(symbol);

        if (trade == null
                || !trade.isOpen()) {

            return;
        }

        String oldStatus =
                trade.status;

        int oldTarget =
                trade.highestTargetReached;

        trade.updateStatus(price);

        String newStatus =
                trade.status;

        int newTarget =
                trade.highestTargetReached;

        if (oldStatus.equals(newStatus)
                && oldTarget == newTarget) {

            return;
        }

        if ("TP1 HIT".equals(newStatus)
                || "TP2 HIT".equals(newStatus)) {

            signalStorage.saveActiveSignal(
                    symbol,
                    trade
            );

            updateScanner();

            return;
        }

        if ("WIN".equals(newStatus)) {

            wins++;

            completeTrade(
                    symbol,
                    trade
            );

        } else if ("LOSS".equals(newStatus)) {

            losses++;

            completeTrade(
                    symbol,
                    trade
            );

        } else if ("EXPIRED".equals(newStatus)) {

            expired++;

            completeTrade(
                    symbol,
                    trade
            );
        }

        updatePerformance();
    }

    private void completeTrade(
            String symbol,
            SignalResult trade) {

        signalStorage.removeActiveSignal(
                symbol
        );

        signalStorage.saveSignal(
                symbol,
                trade
        );

        activeTrades.remove(symbol);

        signalHistory.add(
                0,
                buildHistoryText(
                        symbol,
                        trade
                )
        );

        if (signalHistory.size() > 50) {

            signalHistory.remove(
                    signalHistory.size() - 1
            );
        }
    }

    /*
     * =========================================================
     * SIGNAL PROCESSING
     * =========================================================
     */

    private void processCurrentSignal(
            String symbol,
            SignalResult analyzed) {

        if (analyzed == null) {
            return;
        }

        String newDirection =
                analyzed.action;

        String previousDirection =
                lastMarketDirection.containsKey(
                        symbol
                )
                        ? lastMarketDirection.get(symbol)
                        : "WAIT";

        if (previousDirection == null) {
            previousDirection = "WAIT";
        }

        /*
         * WAIT never creates a new signal.
         */
        if ("WAIT".equals(newDirection)) {

            results.put(
                    symbol,
                    analyzed
            );

            return;
        }

        /*
         * No new BUY/SELL while market is closed.
         */
        if (!isNewSignalAllowed()) {

            results.put(
                    symbol,
                    new SignalResult(
                            "WAIT",
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                    )
            );

            return;
        }

        /*
         * Existing active trades continue tracking.
         * Never create a duplicate trade for the same pair.
         */
        SignalResult existingTrade =
                activeTrades.get(symbol);

        if (existingTrade != null
                && existingTrade.isOpen()) {

            results.put(
                    symbol,
                    analyzed
            );

            lastMarketDirection.put(
                    symbol,
                    analyzed.action
            );

            reversalWaiting.put(
                    symbol,
                    false
            );

            return;
        }

        /*
         * First confirmed BUY/SELL after WAIT.
         */
        if ("WAIT".equals(
                previousDirection
        )) {

            createNewMarketSignal(
                    symbol,
                    analyzed
            );

            return;
        }

        /*
         * Same direction continues.
         */
        if (previousDirection.equals(
                newDirection
        )) {

            results.put(
                    symbol,
                    analyzed
            );

            reversalWaiting.put(
                    symbol,
                    false
            );

            return;
        }

        /*
         * Direction changed.
         * Require confirmation before creating
         * a new signal.
         */
        boolean waiting =
                reversalWaiting.containsKey(
                        symbol
                )
                        && Boolean.TRUE.equals(
                        reversalWaiting.get(
                                symbol
                        )
                );

        if (!waiting) {

            SignalResult wait =
                    new SignalResult(
                            "WAIT",
                            analyzed.entry,
                            0,
                            0,
                            0,
                            0,
                            0
                    );

            results.put(
                    symbol,
                    wait
            );

            reversalWaiting.put(
                    symbol,
                    true
            );

            return;
        }

        /*
         * Second confirmation.
         * Check market and daily limit again.
         */
        if (!isNewSignalAllowed()) {

            return;
        }

        createNewMarketSignal(
                symbol,
                analyzed
        );

        reversalWaiting.put(
                symbol,
                false
        );
    }

    private void createNewMarketSignal(
            String symbol,
            SignalResult signal) {

        if (signal == null) {
            return;
        }

        /*
         * Only BUY/SELL can become new trades.
         */
        if (!"BUY".equals(signal.action)
                && !"SELL".equals(signal.action)) {

            return;
        }

        /*
         * Final market protection.
         */
        if (!isNewSignalAllowed()) {
            return;
        }

        /*
         * Do not duplicate an active trade.
         */
        SignalResult existing =
                activeTrades.get(symbol);

        if (existing != null
                && existing.isOpen()) {

            results.put(
                    symbol,
                    signal
            );

            return;
        }

        /*
         * Final daily-limit check.
         */
        if (getDailySignalCount()
                >= MAX_DAILY_SIGNALS) {

            return;
        }

        /*
         * Count ONLY a genuinely new signal.
         */
        increaseDailySignalCount();

        results.put(
                symbol,
                signal
        );

        lastMarketDirection.put(
                symbol,
                signal.action
        );

        signalStorage.saveSignal(
                symbol,
                signal
        );

        activeTrades.put(
                symbol,
                signal
        );

        signalStorage.saveActiveSignal(
                symbol,
                signal
        );
    }

    /*
     * =========================================================
     * HISTORY DISPLAY
     * =========================================================
     */

    private void updateHistoryDisplay() {

        if (signalHistory.isEmpty()) {

            history.setText(
                    "No signal history yet."
            );

            return;
        }

        StringBuilder text =
                new StringBuilder();

        int limit =
                Math.min(
                        signalHistory.size(),
                        50
                );

        for (int i = 0;
             i < limit;
             i++) {

            text.append(
                    i + 1
            )
                    .append(". ")
                    .append(
                            signalHistory.get(i)
                    )
                    .append("\n\n");
        }

        history.setText(
                text.toString()
        );
    }

    private String buildHistoryText(
            String symbol,
            SignalResult result) {

        String target =
                result.highestTargetReached > 0
                        ? "TP"
                        + result.highestTargetReached
                        + " reached"
                        : "No TP reached";

        return symbol
                + " • "
                + result.action
                + " • "
                + result.status
                + "\n"
                + formatSignalTime(
                result.signalTimeMillis
        )
                + "\n"
                + target
                + "\n"
                + result.resultReason;
    }

    private String formatSignalTime(
            long millis) {

        if (millis <= 0) {
            return "--";
        }

        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat(
                        "dd MMM yyyy • HH:mm:ss",
                        Locale.US
                );

        return format.format(
                new java.util.Date(
                        millis
                )
        );
    }

    /*
     * =========================================================
     * COPY SIGNAL
     * =========================================================
     */

    private void copyBestSignal() {

        if (bestSymbol == null
                || bestResult == null
                || "WAIT".equals(
                bestResult.action
        )) {

            Toast.makeText(
                    this,
                    "No current BUY or SELL signal.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String text =
                "ForexPilot AI Signal\n\n"
                        + "Market: "
                        + bestSymbol
                        + "\n"
                        + "Timeframe: "
                        + timeframeName()
                        + "\n"
                        + "Signal: "
                        + bestResult.action
                        + "\n"
                        + "Confidence: "
                        + bestResult.confidence
                        + "%"
                        + "\n"
                        + "Signal Time: "
                        + formatSignalTime(
                        bestResult.signalTimeMillis
                )
                        + "\n\n"
                        + "Entry: "
                        + formatPrice(
                        bestSymbol,
                        bestResult.entry
                )
                        + "\n"
                        + "Stop Loss: "
                        + formatPrice(
                        bestSymbol,
                        bestResult.sl
                )
                        + "\n"
                        + "TP1: "
                        + formatPrice(
                        bestSymbol,
                        bestResult.tp1
                )
                        + "\n"
                        + "TP2: "
                        + formatPrice(
                        bestSymbol,
                        bestResult.tp2
                )
                        + "\n"
                        + "TP3: "
                        + formatPrice(
                        bestSymbol,
                        bestResult.tp3
                );

        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );

        ClipData clip =
                ClipData.newPlainText(
                        "ForexPilot AI Signal",
                        text
                );

        clipboard.setPrimaryClip(
                clip
        );

        Toast.makeText(
                this,
                "Signal copied!",
                Toast.LENGTH_SHORT
        ).show();
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

    /*
     * =========================================================
     * TWELVE DATA CALLBACK
     * =========================================================
     */

    private class PairCallback
            implements TwelveDataClient.Callback {

        private final String symbol;

        PairCallback(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public void price(double price) {

            runOnUiThread(() -> {

                prices.put(
                        symbol,
                        price
                );

                /*
                 * Existing trades continue tracking
                 * even when the market is closed.
                 */
                checkActiveTrade(
                        symbol,
                        price
                );

                updateScanner();

                updateChart();

                updated.setText(
                        "LIVE • "
                                + symbol
                                + " • "
                                + timeframeName()
                );
            });
        }

        @Override
        public void candles(
                List<Candle> candles) {

            if (candles == null
                    || candles.isEmpty()) {

                return;
            }

            List<Candle> copy =
                    new ArrayList<>(
                            candles
                    );

            TwelveDataClient liveClient =
                    clients.get(symbol);

            double livePrice = 0;

            if (liveClient != null) {

                livePrice =
                        liveClient.getLatestPrice();
            }

            SignalResult analyzed =
                    SignalEngine.analyze(
                            candles,
                            livePrice
                    );

            runOnUiThread(() -> {

                candleData.put(
                        symbol,
                        copy
                );

                processCurrentSignal(
                        symbol,
                        analyzed
                );

                updateScanner();

                updateChart();

                updated.setText(
                        "LIVE MARKET UPDATED • "
                                + symbol
                                + " • "
                                + timeframeName()
                );
            });
        }

        @Override
        public void error(String error) {

            runOnUiThread(() ->
                    updated.setText(
                            symbol
                                    + ": "
                                    + error
                    )
            );
        }
    }

    /*
     * =========================================================
     * CLEANUP
     * =========================================================
     */

    @Override
    protected void onDestroy() {

        super.onDestroy();

        handler.removeCallbacksAndMessages(
                null
        );

        chartReady = false;

        if (candleChart != null) {

            candleChart.stopLoading();

            candleChart.loadUrl(
                    "about:blank"
            );

            candleChart.destroy();
        }

        for (TwelveDataClient client :
                clients.values()) {

            client.close();
        }

        clients.clear();
    }
}