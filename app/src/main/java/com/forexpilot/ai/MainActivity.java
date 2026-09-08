package com.forexpilot.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;

import java.time.Instant;
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

    private CandleStickChart candleChart;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Map<String, TwelveDataClient> clients =
            new LinkedHashMap<>();

    /*
     * CURRENT MARKET SIGNAL.
     *
     * This follows the latest live market structure.
     */
    private final Map<String, SignalResult> results =
            new LinkedHashMap<>();

    /*
     * ACTIVE TRADES.
     *
     * Existing trades continue being monitored separately.
     */
    private final Map<String, SignalResult> activeTrades =
            new LinkedHashMap<>();

    /*
     * Latest live prices received from Twelve Data.
     */
    private final Map<String, Double> prices =
            new LinkedHashMap<>();

    /*
     * Candle data used by chart and signal engine.
     */
    private final Map<String, List<Candle>> candleData =
            new LinkedHashMap<>();

    /*
     * Prevent unnecessary BUY -> SELL or SELL -> BUY
     * flipping without a WAIT transition.
     */
    private final Map<String, String> lastMarketDirection =
            new LinkedHashMap<>();

    private final Map<String, Boolean> reversalWaiting =
            new LinkedHashMap<>();

    /*
     * History displayed by the activity.
     */
    private final List<String> signalHistory =
            new ArrayList<>();

    private SignalStorage signalStorage;

    private static final String[] SYMBOLS = {
            "XAU/USD",
            "EUR/USD",
            "GBP/USD",
            "USD/JPY",
            "NZD/USD"
    };

    private String selectedTimeframe = "5min";
    private String selectedMarket = "ALL";

    private String bestSymbol = null;
    private SignalResult bestResult = null;

    private static final long REFRESH_INTERVAL =
            5 * 60 * 1000L;

    private int wins = 0;
    private int losses = 0;
    private int expired = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_main
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

        candleChart =
                findViewById(R.id.candleChart);

        signalStorage =
                new SignalStorage(this);

        loadSavedHistory();

        loadActiveSignals();

        title.setText(
                "ForexPilot AI"
        );

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

            bestSignal.setText(
                    "WAIT"
            );

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

        startScanner(apiKey);

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        updateMarketStatus();

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

    /*
     * ========================================================
     * CHART
     * ========================================================
     */

    private void setupChart() {

        candleChart.setBackgroundColor(
                Color.rgb(
                        11,
                        15,
                        20
                )
        );

        candleChart.setDrawGridBackground(
                false
        );

        candleChart.setDragEnabled(
                true
        );

        candleChart.setScaleEnabled(
                true
        );

        candleChart.setPinchZoom(
                true
        );

        candleChart.setDoubleTapToZoomEnabled(
                true
        );

        candleChart.setHighlightPerDragEnabled(
                true
        );

        candleChart.setAutoScaleMinMaxEnabled(
                true
        );

        candleChart.setNoDataText(
                "Waiting for candle data..."
        );

        candleChart.setNoDataTextColor(
                Color.LTGRAY
        );

        Description description =
                new Description();

        description.setText("");

        candleChart.setDescription(
                description
        );

        Legend legend =
                candleChart.getLegend();

        legend.setEnabled(
                false
        );

        YAxis leftAxis =
                candleChart.getAxisLeft();

        leftAxis.setTextColor(
                Color.LTGRAY
        );

        leftAxis.setDrawGridLines(
                true
        );

        YAxis rightAxis =
                candleChart.getAxisRight();

        rightAxis.setEnabled(
                false
        );

        candleChart.getXAxis()
                .setTextColor(
                        Color.LTGRAY
                );

        candleChart.getXAxis()
                .setDrawGridLines(
                        false
                );
    }

    private void updateChart() {

        String chartSymbol =
                getChartSymbol();

        if (chartSymbol == null) {
            return;
        }

        List<Candle> candles =
                candleData.get(
                        chartSymbol
                );

        if (candles == null
                || candles.isEmpty()) {

            candleChart.clear();

            candleChart.setNoDataText(
                    "Waiting for "
                            + chartSymbol
                            + " candle data..."
            );

            candleChart.invalidate();

            return;
        }

        ArrayList<CandleEntry> entries =
                new ArrayList<>();

        int start =
                Math.max(
                        0,
                        candles.size() - 80
                );

        int x = 0;

        for (int i = start;
             i < candles.size();
             i++) {

            Candle candle =
                    candles.get(i);

            entries.add(
                    new CandleEntry(
                            x,
                            (float) candle.high,
                            (float) candle.low,
                            (float) candle.open,
                            (float) candle.close
                    )
            );

            x++;
        }

        if (entries.isEmpty()) {
            return;
        }

        CandleDataSet dataSet =
                new CandleDataSet(
                        entries,
                        chartSymbol
                );

        dataSet.setDecreasingColor(
                Color.rgb(
                        255,
                        80,
                        80
                )
        );

        dataSet.setDecreasingPaintStyle(
                android.graphics.Paint.Style.FILL
        );

        dataSet.setIncreasingColor(
                Color.rgb(
                        76,
                        255,
                        120
                )
        );

        dataSet.setIncreasingPaintStyle(
                android.graphics.Paint.Style.FILL
        );

        dataSet.setNeutralColor(
                Color.rgb(
                        180,
                        180,
                        180
                )
        );

        dataSet.setShadowColor(
                Color.LTGRAY
        );

        dataSet.setShadowWidth(
                1.0f
        );

        dataSet.setBarSpace(
                0.15f
        );

        dataSet.setDrawValues(
                false
        );

        CandleData candleDataSet =
                new CandleData(
                        dataSet
                );

        candleChart.setData(
                candleDataSet
        );

        drawSignalLevels(
                chartSymbol
        );

        candleChart.notifyDataSetChanged();

        candleChart.invalidate();

        candleChart.moveViewToX(
                entries.size() - 1
        );
    }

    /*
     * ========================================================
     * SIGNAL LEVELS
     * ========================================================
     */

    private void drawSignalLevels(
            String symbol) {

        YAxis axis =
                candleChart.getAxisLeft();

        axis.removeAllLimitLines();

        SignalResult result =
                results.get(symbol);

        if (result == null
                || "WAIT".equals(result.action)
                || result.entry <= 0) {

            return;
        }

        addLevel(
                axis,
                result.entry,
                "ENTRY",
                Color.WHITE
        );

        if (result.sl > 0) {

            addLevel(
                    axis,
                    result.sl,
                    "SL",
                    Color.rgb(
                            255,
                            80,
                            80
                    )
            );
        }

        if (result.tp1 > 0) {

            addLevel(
                    axis,
                    result.tp1,
                    "TP1",
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );
        }

        if (result.tp2 > 0) {

            addLevel(
                    axis,
                    result.tp2,
                    "TP2",
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );
        }

        if (result.tp3 > 0) {

            addLevel(
                    axis,
                    result.tp3,
                    "TP3",
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );
        }
    }

    private void addLevel(
            YAxis axis,
            double value,
            String label,
            int color) {

        LimitLine line =
                new LimitLine(
                        (float) value,
                        label
                );

        line.setLineWidth(
                1.2f
        );

        line.setLineColor(
                color
        );

        line.setTextColor(
                color
        );

        line.setTextSize(
                10f
        );

        axis.addLimitLine(
                line
        );
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
     * ========================================================
     * HISTORY
     * ========================================================
     */

    private void loadSavedHistory() {

        signalHistory.clear();

        List<String> saved =
                signalStorage.getHistory();

        signalHistory.addAll(
                saved
        );

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

    /*
     * ========================================================
     * ACTIVE TRADES
     * ========================================================
     */

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
     * ========================================================
     * BUTTONS
     * ========================================================
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
                selectMarket(
                        "XAU/USD"
                )
        );

        marketEur.setOnClickListener(v ->
                selectMarket(
                        "EUR/USD"
                )
        );

        marketGbp.setOnClickListener(v ->
                selectMarket(
                        "GBP/USD"
                )
        );

        marketJpy.setOnClickListener(v ->
                selectMarket(
                        "USD/JPY"
                )
        );

        marketNzd.setOnClickListener(v ->
                selectMarket(
                        "NZD/USD"
                )
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

            refreshSignals(
                    apiKey
            );

            Toast.makeText(
                    MainActivity.this,
                    "Refreshing live market...",
                    Toast.LENGTH_SHORT
            ).show();
        });

        copyButton.setOnClickListener(v ->
                copyBestSignal()
        );
    }

    private void selectTimeframe(
            String interval,
            String display) {

        selectedTimeframe =
                interval;

        for (String symbol : SYMBOLS) {

            results.put(
                    symbol,
                    new SignalResult(
                            "WAIT",
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

            refreshSignals(
                    apiKey
            );
        }
    }

    private void selectMarket(
            String symbol) {

        selectedMarket =
                symbol;

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
     * ========================================================
     * START SCANNER
     * ========================================================
     */

    private void startScanner(
            String apiKey) {

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

    /*
     * ========================================================
     * REFRESH
     * ========================================================
     */

    private void refreshSignals(
            String apiKey) {

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

    /*
     * ========================================================
     * MARKET STATUS
     * ========================================================
     */

    private void updateMarketStatus() {

        boolean open =
                MarketClock.isForexOpen(
                        Instant.now()
                );

        market.setText(
                open
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
     * ========================================================
     * SCANNER
     * ========================================================
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
                    .append(
                            result.action
                    )
                    .append("\n");

            text.append(
                    "Confidence: "
            )
                    .append(
                            result.confidence
                    )
                    .append("%\n");

            SignalResult active =
                    activeTrades.get(symbol);

            if (active != null
                    && active.isOpen()) {

                text.append(
                        "ACTIVE TRADE: "
                )
                        .append(
                                active.action
                        )
                        .append(" • ")
                        .append(
                                active.status
                        )
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

    /*
     * ========================================================
     * BEST CURRENT SIGNAL
     * ========================================================
     */

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

                bestSymbol =
                        symbol;

                bestResult =
                        result;
            }
        }

        if (bestResult == null) {

            bestSignal.setText(
                    "WAIT"
            );

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

    /*
     * ========================================================
     * CURRENT SIGNAL STATUS
     * ========================================================
     */

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

        if ("BUY".equals(bestResult.action)) {

            signalStatus.setTextColor(
                    Color.rgb(
                            76,
                            255,
                            120
                    )
            );

        } else if ("SELL".equals(bestResult.action)) {

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
     * ========================================================
     * PERFORMANCE
     * ========================================================
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
     * ========================================================
     * ACTIVE TRADE RESULT TRACKING
     * ========================================================
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

        trade.updateStatus(
                price
        );

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

        activeTrades.remove(
                symbol
        );

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
     * ========================================================
     * CREATE NEW CURRENT SIGNAL
     * ========================================================
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
                lastMarketDirection.containsKey(symbol)
                        ? lastMarketDirection.get(symbol)
                        : "WAIT";

        if (previousDirection == null) {
            previousDirection = "WAIT";
        }

        if ("WAIT".equals(newDirection)) {

            results.put(
                    symbol,
                    analyzed
            );

            return;
        }

        if ("WAIT".equals(previousDirection)) {

            createNewMarketSignal(
                    symbol,
                    analyzed
            );

            return;
        }

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

        boolean waiting =
                reversalWaiting.containsKey(symbol)
                        && Boolean.TRUE.equals(
                        reversalWaiting.get(symbol)
                );

        if (!waiting) {

            SignalResult wait =
                    new SignalResult(
                            "WAIT",
                            analyzed.entry,
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
     * ========================================================
     * HISTORY DISPLAY
     * ========================================================
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

    /*
     * ========================================================
     * TIME
     * ========================================================
     */

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
     * ========================================================
     * COPY
     * ========================================================
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

    /*
     * ========================================================
     * PRICE FORMAT
     * ========================================================
     */

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
     * ========================================================
     * TWELVE DATA CALLBACK
     * ========================================================
     */

    private class PairCallback
            implements TwelveDataClient.Callback {

        private final String symbol;

        PairCallback(
                String symbol) {

            this.symbol =
                    symbol;
        }

        @Override
        public void price(
                double price) {

            runOnUiThread(() -> {

                prices.put(
                        symbol,
                        price
                );

                /*
                 * Existing active trades are monitored
                 * using the live Twelve Data price.
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

            /*
             * =================================================
             * IMPORTANT LIVE PRICE CONNECTION
             * =================================================
             *
             * Get the latest real-time price received from
             * Twelve Data.
             *
             * The SignalEngine now uses this live price
             * together with the candle data.
             */
            TwelveDataClient liveClient =
                    clients.get(symbol);

            double livePrice = 0;

            if (liveClient != null) {

                livePrice =
                        liveClient.getLatestPrice();
            }

            /*
             * Analyze using BOTH:
             *
             * 1. Latest candle history
             * 2. Current live market price
             *
             * This allows ForexPilot AI to react to current
             * price movement instead of relying only on the
             * last completed candle.
             */
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

                /*
                 * The CURRENT market signal is allowed to
                 * change as live market structure changes.
                 */
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
        public void error(
                String error) {

            runOnUiThread(() -> {

                updated.setText(
                        symbol
                                + ": "
                                + error
                );
            });
        }
    }

    /*
     * ========================================================
     * CLEANUP
     * ========================================================
     */

    @Override
    protected void onDestroy() {

        super.onDestroy();

        handler.removeCallbacksAndMessages(
                null
        );

        for (TwelveDataClient client :
                clients.values()) {

            client.close();
        }

        clients.clear();
    }
}
