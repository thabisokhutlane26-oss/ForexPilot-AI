package com.forexpilot.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements TwelveDataClient.Callback {

    private TextView title;
    private TextView market;
    private TextView session;
    private TextView pair;
    private TextView price;
    private TextView signal;
    private TextView confidence;
    private TextView levels;
    private TextView updated;

    private TwelveDataClient client;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private static final String SYMBOL = "EUR/USD";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        title = findViewById(R.id.title);
        market = findViewById(R.id.market);
        session = findViewById(R.id.session);
        pair = findViewById(R.id.pair);
        price = findViewById(R.id.price);
        signal = findViewById(R.id.signal);
        confidence = findViewById(R.id.confidence);
        levels = findViewById(R.id.levels);
        updated = findViewById(R.id.updated);

        title.setText("ForexPilot AI");

        pair.setText("EUR/USD • 5 Minute");

        updateMarketStatus();

        client = new TwelveDataClient(this);

        String apiKey = BuildConfig.TWELVE_DATA_API_KEY;

        if (apiKey == null || apiKey.trim().isEmpty()) {

            signal.setText("WAIT");
            confidence.setText(
                    "API key required for live data"
            );

            price.setText("--");

            levels.setText(
                    "Entry: --\n" +
                    "Stop Loss: --\n" +
                    "Take Profit 1: --\n" +
                    "Take Profit 2: --\n" +
                    "Take Profit 3: --"
            );

            updated.setText(
                    "Add your Twelve Data API key"
            );

        } else {

            client.candles(
                    SYMBOL,
                    "5min",
                    apiKey
            );

            client.connect(
                    SYMBOL,
                    apiKey
            );
        }

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
                "Session: "
                        + MarketClock.session(
                        Instant.now()
                )
        );
    }

    @Override
    public void price(double p) {

        runOnUiThread(() -> {

            price.setText(
                    String.format(
                            Locale.US,
                            "%.5f",
                            p
                    )
            );

            updated.setText(
                    "Live price • just updated"
            );
        });
    }

    @Override
    public void candles(List<Candle> candles) {

        SignalResult result =
                SignalEngine.analyze(candles);

        runOnUiThread(() -> {

            signal.setText(
                    result.action
            );

            confidence.setText(
                    "Confidence: "
                            + result.confidence
                            + "%"
            );

            if ("WAIT".equals(result.action)) {

                levels.setText(
                        String.format(
                                Locale.US,
                                "Entry: %.5f\n" +
                                "Stop Loss: --\n" +
                                "Take Profit 1: --\n" +
                                "Take Profit 2: --\n" +
                                "Take Profit 3: --",
                                result.entry
                        )
                );

            } else {

                levels.setText(
                        String.format(
                                Locale.US,
                                "Entry: %.5f\n" +
                                "Stop Loss: %.5f\n" +
                                "Take Profit 1: %.5f\n" +
                                "Take Profit 2: %.5f\n" +
                                "Take Profit 3: %.5f",
                                result.entry,
                                result.sl,
                                result.tp1,
                                result.tp2,
                                result.tp3
                        )
                );
            }

            updated.setText(
                    "Signal calculated from EUR/USD 5m candles"
            );
        });
    }

    @Override
    public void error(String error) {

        runOnUiThread(() -> {

            updated.setText(
                    "Data error: "
                            + error
            );
        });
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        handler.removeCallbacksAndMessages(
                null
        );

        if (client != null) {
            client.close();
        }
    }
}
