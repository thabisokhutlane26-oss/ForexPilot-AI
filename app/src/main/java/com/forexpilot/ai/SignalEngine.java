package com.forexpilot.ai;

import java.util.List;

public final class SignalEngine {

    public static SignalResult analyze(List<Candle> candles) {

        if (candles == null || candles.size() < 30) {
            return new SignalResult(
                    "WAIT",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        double[] closes = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            closes[i] = candles.get(i).close;
        }

        double ema20 = ema(closes, 20);
        double ema50 = ema(closes, 50);
        double rsi = rsi(closes, 14);
        double atr = atr(candles, 14);

        double entry = closes[closes.length - 1];

        boolean buy =
                ema20 > ema50 &&
                rsi >= 52 &&
                rsi < 72;

        boolean sell =
                ema20 < ema50 &&
                rsi <= 48 &&
                rsi > 28;

        int confidence;

        if (buy) {

            confidence = (int) Math.min(
                    95,
                    60 + ((ema20 - ema50) / Math.max(atr, 0.00001)) * 8
            );

            return new SignalResult(
                    "BUY",
                    entry,
                    entry - 1.5 * atr,
                    entry + 1.0 * atr,
                    entry + 2.0 * atr,
                    entry + 3.0 * atr,
                    confidence
            );

        } else if (sell) {

            confidence = (int) Math.min(
                    95,
                    60 + ((ema50 - ema20) / Math.max(atr, 0.00001)) * 8
            );

            return new SignalResult(
                    "SELL",
                    entry,
                    entry + 1.5 * atr,
                    entry - 1.0 * atr,
                    entry - 2.0 * atr,
                    entry - 3.0 * atr,
                    confidence
            );

        } else {

            return new SignalResult(
                    "WAIT",
                    entry,
                    0,
                    0,
                    0,
                    0,
                    45
            );
        }
    }

    private static double ema(double[] values, int period) {

        double multiplier = 2.0 / (period + 1);

        double ema = values[0];

        for (int i = 1; i < values.length; i++) {
            ema = values[i] * multiplier
                    + ema * (1 - multiplier);
        }

        return ema;
    }

    private static double rsi(double[] values, int period) {

        if (values.length <= period) {
            return 50;
        }

        double gains = 0;
        double losses = 0;

        for (int i = values.length - period; i < values.length; i++) {

            double difference = values[i] - values[i - 1];

            if (difference > 0) {
                gains += difference;
            } else {
                losses -= difference;
            }
        }

        if (losses == 0) {
            return 100;
        }

        double relativeStrength = gains / losses;

        return 100 - (100 / (1 + relativeStrength));
    }

    private static double atr(List<Candle> candles, int period) {

        int start = Math.max(
                1,
                candles.size() - period
        );

        double total = 0;

        for (int i = start; i < candles.size(); i++) {

            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);

            double trueRange = Math.max(
                    current.high - current.low,
                    Math.max(
                            Math.abs(current.high - previous.close),
                            Math.abs(current.low - previous.close)
                    )
            );

            total += trueRange;
        }

        return total / Math.max(
                1,
                candles.size() - start
        );
    }
}
