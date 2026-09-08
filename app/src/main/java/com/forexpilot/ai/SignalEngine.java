package com.forexpilot.ai;

import java.util.List;

public final class SignalEngine {

    private SignalEngine() {
    }

    public static SignalResult analyze(
            List<Candle> candles) {

        if (candles == null
                || candles.size() < 60) {

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

        int size = candles.size();

        double[] closes =
                new double[size];

        for (int i = 0; i < size; i++) {

            closes[i] =
                    candles.get(i).close;
        }

        double entry =
                closes[size - 1];

        if (entry <= 0) {

            return new SignalResult(
                    "WAIT",
                    entry,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        /*
         * Core indicators.
         */
        double ema20 =
                ema(closes, 20);

        double ema50 =
                ema(closes, 50);

        double previousEma20 =
                emaUntil(
                        closes,
                        size - 2,
                        20
                );

        double previousEma50 =
                emaUntil(
                        closes,
                        size - 2,
                        50
                );

        double rsi =
                rsi(closes, 14);

        double atr =
                atr(candles, 14);

        if (atr <= 0) {

            return new SignalResult(
                    "WAIT",
                    entry,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        /*
         * Current and previous candles.
         */
        Candle current =
                candles.get(size - 1);

        Candle previous =
                candles.get(size - 2);

        Candle twoBack =
                candles.get(size - 3);

        double currentRange =
                Math.max(
                        0,
                        current.high - current.low
                );

        double currentBody =
                Math.abs(
                        current.close
                                - current.open
                );

        double previousRange =
                Math.max(
                        0,
                        previous.high - previous.low
                );

        /*
         * Candle direction.
         */
        boolean bullishCandle =
                current.close > current.open;

        boolean bearishCandle =
                current.close < current.open;

        /*
         * Strong candle confirmation.
         */
        boolean strongBullishCandle =
                bullishCandle
                        && currentRange > 0
                        && currentBody
                        >= currentRange * 0.50;

        boolean strongBearishCandle =
                bearishCandle
                        && currentRange > 0
                        && currentBody
                        >= currentRange * 0.50;

        /*
         * Close location inside the candle.
         *
         * A bullish candle closing near its high
         * is stronger.
         *
         * A bearish candle closing near its low
         * is stronger.
         */
        double closeLocation = 0.5;

        if (currentRange > 0) {

            closeLocation =
                    (current.close
                            - current.low)
                            / currentRange;
        }

        boolean bullishCloseStrength =
                closeLocation >= 0.70;

        boolean bearishCloseStrength =
                closeLocation <= 0.30;

        /*
         * Trend direction.
         */
        boolean bullishTrend =
                ema20 > ema50;

        boolean bearishTrend =
                ema20 < ema50;

        /*
         * EMA slope.
         */
        boolean ema20Rising =
                ema20 > previousEma20;

        boolean ema20Falling =
                ema20 < previousEma20;

        boolean ema50Rising =
                ema50 > previousEma50;

        boolean ema50Falling =
                ema50 < previousEma50;

        boolean bullishSlope =
                ema20Rising
                        && ema50Rising;

        boolean bearishSlope =
                ema20Falling
                        && ema50Falling;

        /*
         * Momentum.
         *
         * We avoid buying when RSI is already
         * extremely overbought and avoid selling
         * when RSI is extremely oversold.
         */
        boolean bullishMomentum =
                rsi >= 52
                        && rsi <= 68;

        boolean bearishMomentum =
                rsi <= 48
                        && rsi >= 32;

        /*
         * Recent market structure.
         *
         * We examine several candles instead of
         * comparing only the latest candle with
         * the previous candle.
         */
        int structureStart =
                Math.max(
                        0,
                        size - 6
                );

        double recentHigh =
                Double.MIN_VALUE;

        double recentLow =
                Double.MAX_VALUE;

        for (int i = structureStart;
             i < size - 1;
             i++) {

            Candle candle =
                    candles.get(i);

            recentHigh =
                    Math.max(
                            recentHigh,
                            candle.high
                    );

            recentLow =
                    Math.min(
                            recentLow,
                            candle.low
                    );
        }

        boolean bullishBreak =
                current.close > recentHigh;

        boolean bearishBreak =
                current.close < recentLow;

        /*
         * Higher-high / higher-low structure.
         */
        boolean higherHigh =
                current.high > previous.high
                        && previous.high > twoBack.high;

        boolean higherLow =
                current.low > previous.low
                        && previous.low > twoBack.low;

        boolean lowerHigh =
                current.high < previous.high
                        && previous.high < twoBack.high;

        boolean lowerLow =
                current.low < previous.low
                        && previous.low < twoBack.low;

        boolean bullishStructure =
                higherHigh
                        || higherLow
                        || bullishBreak;

        boolean bearishStructure =
                lowerHigh
                        || lowerLow
                        || bearishBreak;

        /*
         * Price location relative to both EMAs.
         */
        boolean priceAboveTrend =
                entry > ema20
                        && entry > ema50;

        boolean priceBelowTrend =
                entry < ema20
                        && entry < ema50;

        /*
         * Detect a meaningful range expansion.
         *
         * This is our basic "spike/movement"
         * detector.
         *
         * It does NOT automatically create a trade.
         * Direction and confirmation are still required.
         */
        boolean rangeExpansion =
                currentRange >= atr * 1.25;

        boolean bullishSurge =
                rangeExpansion
                        && bullishCandle
                        && bullishCloseStrength;

        boolean bearishSurge =
                rangeExpansion
                        && bearishCandle
                        && bearishCloseStrength;

        /*
         * Avoid treating a tiny candle as a
         * meaningful breakout.
         */
        boolean meaningfulMovement =
                currentRange >= atr * 0.60;

        /*
         * BUY score.
         */
        int buyScore = 0;

        if (bullishTrend) {
            buyScore += 18;
        }

        if (bullishSlope) {
            buyScore += 12;
        }

        if (bullishMomentum) {
            buyScore += 15;
        }

        if (bullishCandle) {
            buyScore += 8;
        }

        if (strongBullishCandle) {
            buyScore += 8;
        }

        if (bullishCloseStrength) {
            buyScore += 6;
        }

        if (bullishStructure) {
            buyScore += 12;
        }

        if (bullishBreak) {
            buyScore += 10;
        }

        if (priceAboveTrend) {
            buyScore += 8;
        }

        if (bullishSurge) {
            buyScore += 8;
        }

        if (meaningfulMovement) {
            buyScore += 3;
        }

        /*
         * SELL score.
         */
        int sellScore = 0;

        if (bearishTrend) {
            sellScore += 18;
        }

        if (bearishSlope) {
            sellScore += 12;
        }

        if (bearishMomentum) {
            sellScore += 15;
        }

        if (bearishCandle) {
            sellScore += 8;
        }

        if (strongBearishCandle) {
            sellScore += 8;
        }

        if (bearishCloseStrength) {
            sellScore += 6;
        }

        if (bearishStructure) {
            sellScore += 12;
        }

        if (bearishBreak) {
            sellScore += 10;
        }

        if (priceBelowTrend) {
            sellScore += 8;
        }

        if (bearishSurge) {
            sellScore += 8;
        }

        if (meaningfulMovement) {
            sellScore += 3;
        }

        /*
         * Minimum confirmation.
         *
         * We still prefer quality over forcing
         * a signal every few seconds.
         */
        final int minimumScore = 70;

        /*
         * BUY.
         */
        if (buyScore >= minimumScore
                && buyScore > sellScore
                && bullishTrend
                && priceAboveTrend) {

            int confidence =
                    Math.min(
                            95,
                            buyScore
                    );

            /*
             * Wider volatility-aware levels.
             *
             * SL is based on ATR.
             * TP levels expand progressively.
             */
            double sl =
                    entry - (3.0 * atr);

            double tp1 =
                    entry + (2.0 * atr);

            double tp2 =
                    entry + (4.0 * atr);

            double tp3 =
                    entry + (6.0 * atr);

            return new SignalResult(
                    "BUY",
                    entry,
                    sl,
                    tp1,
                    tp2,
                    tp3,
                    confidence
            );
        }

        /*
         * SELL.
         */
        if (sellScore >= minimumScore
                && sellScore > buyScore
                && bearishTrend
                && priceBelowTrend) {

            int confidence =
                    Math.min(
                            95,
                            sellScore
                    );

            double sl =
                    entry + (3.0 * atr);

            double tp1 =
                    entry - (2.0 * atr);

            double tp2 =
                    entry - (4.0 * atr);

            double tp3 =
                    entry - (6.0 * atr);

            return new SignalResult(
                    "SELL",
                    entry,
                    sl,
                    tp1,
                    tp2,
                    tp3,
                    confidence
            );
        }

        /*
         * WAIT.
         *
         * We deliberately do not manufacture
         * a signal when the market is unclear.
         */
        int waitConfidence =
                Math.max(
                        buyScore,
                        sellScore
                );

        waitConfidence =
                Math.min(
                        69,
                        waitConfidence
                );

        return new SignalResult(
                "WAIT",
                entry,
                0,
                0,
                0,
                0,
                waitConfidence
        );
    }

    /*
     * Exponential Moving Average.
     */
    private static double ema(
            double[] values,
            int period) {

        if (values == null
                || values.length == 0) {

            return 0;
        }

        double multiplier =
                2.0 / (period + 1);

        double result =
                values[0];

        for (int i = 1;
             i < values.length;
             i++) {

            result =
                    values[i] * multiplier
                            + result
                            * (1 - multiplier);
        }

        return result;
    }

    /*
     * EMA using candles up to a specific index.
     */
    private static double emaUntil(
            double[] values,
            int lastIndex,
            int period) {

        if (values == null
                || values.length == 0
                || lastIndex < 0) {

            return 0;
        }

        int end =
                Math.min(
                        lastIndex,
                        values.length - 1
                );

        double multiplier =
                2.0 / (period + 1);

        double result =
                values[0];

        for (int i = 1;
             i <= end;
             i++) {

            result =
                    values[i] * multiplier
                            + result
                            * (1 - multiplier);
        }

        return result;
    }

    /*
     * RSI.
     */
    private static double rsi(
            double[] values,
            int period) {

        if (values == null
                || values.length <= period) {

            return 50;
        }

        double gains = 0;
        double losses = 0;

        int start =
                values.length - period;

        for (int i = start;
             i < values.length;
             i++) {

            double difference =
                    values[i]
                            - values[i - 1];

            if (difference > 0) {

                gains += difference;

            } else {

                losses -= difference;
            }
        }

        if (losses == 0) {

            if (gains == 0) {
                return 50;
            }

            return 100;
        }

        double relativeStrength =
                gains / losses;

        return 100
                - (
                100
                        / (
                        1
                                + relativeStrength
                )
        );
    }

    /*
     * Average True Range.
     */
    private static double atr(
            List<Candle> candles,
            int period) {

        if (candles == null
                || candles.size() < 2) {

            return 0;
        }

        int start =
                Math.max(
                        1,
                        candles.size() - period
                );

        double total = 0;

        int count = 0;

        for (int i = start;
             i < candles.size();
             i++) {

            Candle current =
                    candles.get(i);

            Candle previous =
                    candles.get(i - 1);

            double trueRange =
                    Math.max(
                            current.high
                                    - current.low,

                            Math.max(
                                    Math.abs(
                                            current.high
                                                    - previous.close
                                    ),

                                    Math.abs(
                                            current.low
                                                    - previous.close
                                    )
                            )
                    );

            total += trueRange;

            count++;
        }

        if (count == 0) {
            return 0;
        }

        return total / count;
    }
}
