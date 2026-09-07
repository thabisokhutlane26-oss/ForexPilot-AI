package com.forexpilot.ai;

import java.util.List;

public final class SignalEngine {

    private SignalEngine() {
    }

    public static SignalResult analyze(
            List<Candle> candles) {

        if (candles == null || candles.size() < 60) {

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

        double[] closes =
                new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {

            closes[i] =
                    candles.get(i).close;
        }

        double ema20 =
                ema(closes, 20);

        double ema50 =
                ema(closes, 50);

        double rsi =
                rsi(closes, 14);

        double atr =
                atr(candles, 14);

        double entry =
                closes[closes.length - 1];

        if (atr <= 0 || entry <= 0) {

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
         * ====================================================
         * 1. TREND CONFIRMATION
         * ====================================================
         */

        boolean bullishTrend =
                ema20 > ema50;

        boolean bearishTrend =
                ema20 < ema50;


        /*
         * ====================================================
         * 2. RSI MOMENTUM CONFIRMATION
         * ====================================================
         */

        boolean bullishMomentum =
                rsi >= 52 && rsi < 70;

        boolean bearishMomentum =
                rsi <= 48 && rsi > 30;


        /*
         * ====================================================
         * 3. CANDLE CONFIRMATION
         * ====================================================
         */

        Candle current =
                candles.get(
                        candles.size() - 1
                );

        Candle previous =
                candles.get(
                        candles.size() - 2
                );

        boolean bullishCandle =
                current.close > current.open;

        boolean bearishCandle =
                current.close < current.open;


        /*
         * ====================================================
         * 4. CANDLE STRENGTH
         * ====================================================
         */

        double currentBody =
                Math.abs(
                        current.close
                                - current.open
                );

        double currentRange =
                current.high
                        - current.low;

        boolean strongBullishCandle =
                bullishCandle
                        && currentRange > 0
                        && currentBody
                        >= currentRange * 0.45;

        boolean strongBearishCandle =
                bearishCandle
                        && currentRange > 0
                        && currentBody
                        >= currentRange * 0.45;


        /*
         * ====================================================
         * 5. MARKET STRUCTURE
         * ====================================================
         */

        boolean higherHigh =
                current.high > previous.high;

        boolean higherLow =
                current.low > previous.low;

        boolean lowerHigh =
                current.high < previous.high;

        boolean lowerLow =
                current.low < previous.low;

        boolean bullishStructure =
                higherHigh && higherLow;

        boolean bearishStructure =
                lowerHigh && lowerLow;


        /*
         * ====================================================
         * 6. PRICE LOCATION
         * ====================================================
         */

        boolean priceAboveTrend =
                entry > ema20
                        && entry > ema50;

        boolean priceBelowTrend =
                entry < ema20
                        && entry < ema50;


        /*
         * ====================================================
         * 7. BUY SCORE
         * ====================================================
         */

        int buyScore = 0;

        if (bullishTrend) {
            buyScore += 20;
        }

        if (bullishMomentum) {
            buyScore += 20;
        }

        if (bullishCandle) {
            buyScore += 10;
        }

        if (strongBullishCandle) {
            buyScore += 10;
        }

        if (bullishStructure) {
            buyScore += 15;
        }

        if (priceAboveTrend) {
            buyScore += 15;
        }


        /*
         * ====================================================
         * 8. SELL SCORE
         * ====================================================
         */

        int sellScore = 0;

        if (bearishTrend) {
            sellScore += 20;
        }

        if (bearishMomentum) {
            sellScore += 20;
        }

        if (bearishCandle) {
            sellScore += 10;
        }

        if (strongBearishCandle) {
            sellScore += 10;
        }

        if (bearishStructure) {
            sellScore += 15;
        }

        if (priceBelowTrend) {
            sellScore += 15;
        }


        /*
         * ====================================================
         * 9. MINIMUM CONFIRMATION
         * ====================================================
         */

        final int minimumScore = 65;


        /*
         * ====================================================
         * 10. BUY SIGNAL
         * ====================================================
         */

        if (buyScore >= minimumScore
                && buyScore > sellScore) {

            int confidence =
                    Math.min(
                            95,
                            buyScore
                    );


            /*
             * =================================================
             * WIDER BUY TRADE LEVELS
             *
             * SL  = 3.0 ATR
             * TP1 = 2.0 ATR
             * TP2 = 4.0 ATR
             * TP3 = 6.0 ATR
             * =================================================
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
         * ====================================================
         * 11. SELL SIGNAL
         * ====================================================
         */

        if (sellScore >= minimumScore
                && sellScore > buyScore) {

            int confidence =
                    Math.min(
                            95,
                            sellScore
                    );


            /*
             * =================================================
             * WIDER SELL TRADE LEVELS
             *
             * SL  = 3.0 ATR
             * TP1 = 2.0 ATR
             * TP2 = 4.0 ATR
             * TP3 = 6.0 ATR
             * =================================================
             */

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
         * ====================================================
         * 12. WAIT
         * ====================================================
         */

        int waitConfidence =
                Math.max(
                        buyScore,
                        sellScore
                );

        waitConfidence =
                Math.min(
                        64,
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
     * EMA
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

        double ema =
                values[0];

        for (int i = 1;
             i < values.length;
             i++) {

            ema =
                    values[i] * multiplier
                            + ema * (1 - multiplier);
        }

        return ema;
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
