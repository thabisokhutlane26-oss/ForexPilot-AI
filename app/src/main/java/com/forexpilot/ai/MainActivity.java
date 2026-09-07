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

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
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

    private Button refreshButton;
    private Button copyButton;

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

    private String selectedTimeframe = "5min";
    private String selectedMarket = "ALL";

    private String bestSymbol = null;
    private SignalResult bestResult = null;

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
        confirmation = findViewById(R.id.confirmation);
        updated = findViewById(R.id.updated);

        refreshButton =
                findViewById(R.id.refreshButton);

        copyButton =
                findViewById(R.id.copyButton);

        title.setText("ForexPilot AI");

        setupButtons();

        updateMarketStatus();

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            scanner.setText(
                    "API KEY REQUIRED\n\n" +
                    "Add your Twelve Data API key " +
                    "to GitHub Secrets."
            );

            bestSignal.setText("WAIT");

            bestSignal.setTextColor(
                    Color.rgb(255, 213, 79)
            );

            confirmation.setText(
                    "WAITING FOR MARKET DATA"
            );

            confirmation.setTextColor(
                    Color.rgb(255, 213, 79)
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
                selectTimeframe("5min", "5M")
        );

        tf15.setOnClickListener(v ->
                selectTimeframe("15min", "15M")
        );

        tf30.setOnClickListener(v ->
                selectTimeframe("30min", "30M")
        );

        tf1h.setOnClickListener(v ->
                selectTimeframe("1h", "1H")
        );

        tf4h.setOnClickListener(v ->
                selectTimeframe("4h", "4H")
        );

        tf1d.setOnClickListener(v ->
                selectTimeframe("1day", "1D")
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

            if (apiKey == null ||
                    apiKey.trim().isEmpty()) {

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
                    "Refreshing signals...",
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

        selectedTimeframe = interval;

        updated.setText(
                "Timeframe selected: "
                        + display
                        + " • Refreshing..."
        );

        String apiKey =
                BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey != null &&
                !apiKey.trim().isEmpty()) {

            refreshSignals(apiKey);
        }
    }

    private void selectMarket(String symbol) {

        selectedMarket = symbol;

        updateScanner();

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

    private void refreshSignals(String apiKey) {

        runOnUiThread(() -> {

            updated.setText(
                    "Refreshing "
                            + timeframeName()
                            + "
