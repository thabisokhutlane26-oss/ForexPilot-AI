package com.forexpilot.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.Instant;
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
    private TextView updated;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Map<String, TwelveDataClient> clients =
            new LinkedHashMap<>();

    private final Map<String, SignalResult> results =
            new LinkedHashMap<>();

    private final Map<String, Double> prices =
            new LinkedHashMap<>();

    private static final String[] SYMBOLS = {
            "XAU/USD",
            "EUR/USD",
            "GBP/USD",
            "USD/JPY",
            "NZD/USD"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        title = findViewById(R.id.title);
        market = findViewById(R.id.market);
        session = findViewById(R.id.session);
        scanner = findViewById(R.id.scanner);
        bestSignal = findViewById(R.id.bestSignal);
        bestDetails = findViewById(R.id.bestDetails);
        updated = findViewById(R.id.updated);

        title.setText("ForexPilot AI");

        updateMarketStatus();

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null || apiKey.trim().isEmpty()) {

            scanner.setText(
                    "API KEY REQUIRED\n\n" +
                    "Add your Twelve Data API key " +
                    "to GitHub Secrets."
            );

            bestSignal.setText("WAIT");

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
                                30000
                        );
                    }
                },
                30000
        );
    }

    private void startScanner(String apiKey) {

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

            prices.put(symbol, 0.0);

            TwelveDataClient client =
                    new TwelveDataClient(
                            new PairCallback(symbol)
                    );

            clients.put(symbol, client);

            client.candles(
                    symbol,
                    "5min",
                    apiKey
            );

            client.connect(
                    symbol,
                    apiKey
            );
        }

        updateScanner();
    }

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

    private void updateScanner() {

        StringBuilder text =
                new StringBuilder();

        text.append(
                "5 MINUTE MARKET SCANNER\n\n"
        );

        for (String symbol : SYMBOLS) {

            SignalResult result =
                    results.get(symbol);

            double currentPrice =
                    prices.get(symbol);

            text.append(symbol)
                    .append("\n");

            text.append("Signal: ")
                    .append(result.action)
                    .append("\n");

            text.append("Confidence: ")
                    .append(result.confidence)
                    .append("%\n");

            if (currentPrice > 0) {

                text.append("Price: ")
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

                text.append("Entry: ")
                        .append(
                                formatPrice(
                                        symbol,
                                        result.entry
                                )
                        )
                        .append("\n");

                text.append("SL: ")
                        .append(
                                formatPrice(
                                        symbol,
                                        result.sl
                                )
                        )
                        .append("\n");

                text.append("TP1: ")
                        .append(
                                formatPrice(
                                        symbol,
                                        result.tp1
                                )
                        )
                        .append("\n");

                text.append("TP2: ")
                        .append(
                                formatPrice(
                                        symbol,
                                        result.tp2
                                )
                        )
                        .append("\n");

                text.append("TP3: ")
                        .append(
                                formatPrice(
                                        symbol,
                                        result.tp3
                                )
                        )
                        .append("\n");
            }

            text.append("\n");
        }

        scanner.setText(text.toString());

        findBestSignal();
    }

    private void findBestSignal() {

        String bestSymbol = null;

        SignalResult bestResult = null;

        for (String symbol : SYMBOLS) {

            SignalResult result =
                    results.get(symbol);

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

            bestSignal.setText(
                    "WAIT"
            );

            bestDetails.setText(
                    "No strong BUY or SELL setup " +
                    "right now."
            );

            return;
        }

        bestSignal.setText(
                bestSymbol
                        + " • "
                        + bestResult.action
                        + " • "
                        + bestResult.confidence
                        + "%"
        );

        bestDetails.setText(
                String.format(
                        Locale.US,

                        "BEST SIGNAL\n\n" +
                        "Market: %s\n" +
                        "Signal: %s\n" +
                        "Confidence: %d%%\n\n" +
                        "Entry: %s\n" +
                        "Stop Loss: %s\n" +
                        "TP1: %s\n" +
                        "TP2: %s\n" +
                        "TP3: %s",

                        bestSymbol,
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
                        )
                )
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

                updateScanner();

                updated.setText(
                        "Live prices updating..."
                );
            });
        }

        @Override
        public void candles(
                List<Candle> candles) {

            SignalResult result =
                    SignalEngine.analyze(
                            candles
                    );

            runOnUiThread(() -> {

                results.put(
                        symbol,
                        result
                );

                updateScanner();

                updated.setText(
                        "Signals calculated from " +
                        "5-minute candles"
                );
            });
        }

        @Override
        public void error(String error) {

            runOnUiThread(() -> {

                updated.setText(
                        symbol
                                + ": "
                                + error
                );
            });
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        handler.removeCallbacksAndMessages(
                null
        );

        for (TwelveDataClient client
                : clients.values()) {

            client.close();
        }

        clients.clear();
    }
}
