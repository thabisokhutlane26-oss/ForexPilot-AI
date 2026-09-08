package com.forexpilot.ai;

import java.util.ArrayList;
import java.util.List;

public final class SignalEngine {

    private SignalEngine() {
    }

    /*
     * ========================================================
     * STANDARD CANDLE ANALYSIS
     * ========================================================
     *
     * Keeps compatibility with the existing app.
     */

    public static SignalResult analyze(
            List<Candle> candles) {

        return analyze(
                candles,
                0
        );
    }

    /*
     * ========================================================
     * LIVE PRICE ANALYSIS
     * ========================================================
     *
     * If livePrice is greater than zero, the latest
     * candle close is temporarily replaced by the
     * live market price for the directional analysis.
     *
     * The original candle list is NEVER modified.
     */

    public static SignalResult analyze(
            List<Candle> candles,
            double livePrice) {

        if (candles == null
                || candles.size() < 60) {

            return new SignalResult(
                    "WAIT",
                    livePrice > 0 ? livePrice : 0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        /*
         * Make a safe working copy.
         */

        List<Candle> working =
                new ArrayList<>(
                        candles
                );

        int size =
                working.size();

        Candle latest =
                working.get(size - 1);

        /*
         * Use live price only when it is valid.
         */

        double entry =
                livePrice > 0
                        ? livePrice
                        : latest.close;

        if (entry <= 0) {

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

        /*
         * Build a temporary current candle.
         *
         * This gives the engine a live approximation
         * of the current candle instead of waiting for
         * the candle to close.
         */

        if (livePrice > 0) {

            double liveHigh =
                    Math.max(
                            latest.high,
                            livePrice
                    );

            double liveLow =
                    Math.min(
                            latest.low,
                            livePrice
                    );

            working.set(
                    size - 1,
                    new Candle(
                            latest.open,
                            liveHigh,
                            liveLow,
                            livePrice
                    )
            );
        }

        /*
         * Build close array.
         */

        double[] closes =
                new double[size];

        for (int i = 0;
             i < size;
             i++) {

            closes[i] =
                    working.get(i).close;
        }

        /*
         * Core indicators.
         */

        double ema20 =
                ema(
                        closes,
                        20
                );

        double ema50 =
                ema(
                        closes,
                        50
                );

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
                rsi(
                        closes,
                        14
                );

        double atr =
                atr(
                        working,
                        14
                );

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
         * Current candles.
         */

        Candle current =
                working.get(size - 1);

        Candle previous =
                working.get(size - 2);

        Candle twoBack =
                working.get(size - 3);

        double currentRange =
                Math.max(
                        0,
                        current.high
                                - current.low
                );

        double currentBody =
                Math.abs(
                        current.close
                                - current.open
                );

        /*
         * Candle direction.
         */

        boolean bullishCandle =
                current.close
                        > current.open;

        boolean bearishCandle =
                current.close
                        < current.open;

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
         * Close location.
         */

        double closeLocation =
                0.5;

        if (currentRange > 0) {

            closeLocation =
                    (
                            current.close
                                    - current.low
                    )
                            / currentRange;
        }

        boolean bullishCloseStrength =
                closeLocation >= 0.70;

        boolean bearishCloseStrength =
                closeLocation <= 0.30;

        /*
         * Trend.
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
         * We keep a reasonable zone instead of
         * buying at extreme RSI or selling at
         * extreme RSI.
         */

        boolean bullishMomentum =
                rsi >= 52
                        && rsi <= 68;

        boolean bearishMomentum =
                rsi <= 48
                        && rsi >= 32;

        /*
         * Recent structure.
         */

        int structureStart =
                Math.max(
                        0,
                        size - 6
                );

        double recentHigh =
                -Double.MAX_VALUE;

        double recentLow =
                Double.MAX_VALUE;

        for (int i =
                     structureStart;
             i < size - 1;
             i++) {

            Candle candle =
                    working.get(i);

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

        /*
         * IMPORTANT:
         *
         * Use live price for the breakout check.
         * This means the app can detect a current
         * price breaking recent structure before
         * the candle officially closes.
         */

        boolean bullishBreak =
                entry > recentHigh;

        boolean bearishBreak =
                entry < recentLow;

        /*
         * Higher-high / higher-low structure.
         */

        boolean higherHigh =
                current.high > previous.high
                        && previous.high
                        > twoBack.high;

        boolean higherLow =
                current.low > previous.low
                        && previous.low
                        > twoBack.low;

        boolean lowerHigh =
                current.high < previous.high
                        && previous.high
                        < twoBack.high;

        boolean lowerLow =
                current.low < previous.low
                        && previous.low
                        < twoBack.low;

        boolean bullishStructure =
                higherHigh
                        || higherLow
                        || bullishBreak;

        boolean bearishStructure =
                lowerHigh
                        || lowerLow
                        || bearishBreak;

        /*
         * Price position.
         */

        boolean priceAboveTrend =
                entry > ema20
                        && entry > ema50;

        boolean priceBelowTrend =
                entry < ema20
                        && entry < ema50;

        /*
         * Volatility expansion.
         */

        boolean rangeExpansion =
                currentRange
                        >= atr * 1.25;

        boolean bullishSurge =
                rangeExpansion
                        && bullishCandle
                        && bullishCloseStrength;

        boolean bearishSurge =
                rangeExpansion
                        && bearishCandle
                        && bearishCloseStrength;

        /*
         * Meaningful movement.
         */

        boolean meaningfulMovement =
                currentRange
                        >= atr * 0.60;

        /*
         * Live price distance from the
         * latest candle close.
         *
         * This helps detect a meaningful
         * intrabar move.
         */

        double liveMove =
                0;

        if (livePrice > 0) {

            liveMove =
                    Math.abs(
                            livePrice
                                    - latest.close
                    );
        }

        boolean livePriceMoving =
                liveMove
                        >= atr * 0.15;

        /*
         * ====================================================
         * BUY SCORE
         * ====================================================
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
         * Live movement gives a small additional
         * confirmation, but cannot create a BUY
         * by itself.
         */

        if (livePriceMoving
                && bullishCandle) {

            buyScore += 4;
        }

        /*
         * ====================================================
         * SELL SCORE
         * ====================================================
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

        if (livePriceMoving
                && bearishCandle) {

            sellScore += 4;
        }

        /*
         * ====================================================
         * SIGNAL THRESHOLD
         * ====================================================
         */

        final int minimumScore =
                70;

        /*
         * ====================================================
         * BUY
         * ====================================================
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
             * Volatility-aware levels.
             */

            double sl =
                    entry
                            - (3.0 * atr);

            double tp1 =
                    entry
                            + (2.0 * atr);

            double tp2 =
                    entry
                            + (4.0 * atr);

            double tp3 =
                    entry
                            + (6.0 * atr);

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
         * ====================================================
         * SELL
         * ====================================================
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
                    entry
                            + (3.0 * atr);

            double tp1 =
                    entry
                            - (2.0 * atr);

            double tp2 =
                    entry
                            - (4.0 * atr);

            double tp3 =
                    entry
                            - (6.0 * atr);

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
         * ====================================================
         * WAIT
         * ====================================================
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
     * ========================================================
     * EXPONENTIAL MOVING AVERAGE
     * ========================================================
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
     * ========================================================
     * EMA UNTIL INDEX
     * ========================================================
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
     * ========================================================
     * RSI
     * ========================================================
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
     * ========================================================
     * ATR
     * ========================================================
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
                        candles.size()
                                - period
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
