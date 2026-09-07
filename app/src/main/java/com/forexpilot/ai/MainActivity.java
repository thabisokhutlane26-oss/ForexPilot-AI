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

    // Refresh 5-minute candle signals every 5 minutes
    private static final long REFRESH_INTERVAL =
            5 * 60 * 1000L;

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

        // Update market status every 30 seconds
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

        // Refresh trading signals every 5 minutes
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

            prices.put(
                    symbol,
                    0.0
            );

            TwelveDataClient client =
                    new TwelveDataClient(
                            new PairCallback(symbol)
                    );

            clients.put(
                    symbol,
                    client
            );

            // Get initial 5-minute candles
            client.candles(
                    symbol,
                    "5min",
                    apiKey
            );

            // Start live price stream
            client.connect(
                    symbol,
                    apiKey
            );
        }

        updateScanner();
    }

    private void refreshSignals(String apiKey) {

        runOnUiThread(() -> {

            updated.setText(
                    "Refreshing 5-minute signals..."
            );

        });

        for (String symbol : SYMBOLS) {

            TwelveDataClient client =
                    clients.get(symbol);

            if (client != null) {

                client.candles(
                        symbol,
                        "5min",
                        apiKey
                );
            }
        }
    }

    private void updateMarketStatus() {

        boolean open =
                MarketClock.isForexOpen(
                        Instant.now()
                );

        market.setText(
                open
                        ? "FOREX MARKET: OPEN"
                       
