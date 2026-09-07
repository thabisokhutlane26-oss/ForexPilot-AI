package com.forexpilot.ai;

public class SignalResult {

    public final String action;

    public final double entry;
    public final double sl;
    public final double tp1;
    public final double tp2;
    public final double tp3;

    public final int confidence;

    // Signal date/time
    public final long signalTimeMillis;

    // Signal status
    // OPEN, WIN, LOSS, EXPIRED
    public String status;

    // Which target was reached
    public String resultReason;

    public SignalResult(
            String action,
            double entry,
            double sl,
            double tp1,
            double tp2,
            double tp3,
            int confidence) {

        this.action = action;

        this.entry = entry;
        this.sl = sl;

        this.tp1 = tp1;
        this.tp2 = tp2;
        this.tp3 = tp3;

        this.confidence = confidence;

        this.signalTimeMillis =
                System.currentTimeMillis();

        if ("BUY".equals(action)
                || "SELL".equals(action)) {

            this.status = "OPEN";

        } else {

            this.status = "WAIT";
        }

        this.resultReason = "";
    }

    /**
     * Update the signal result
     * using the latest live market price.
     */
    public void updateStatus(double currentPrice) {

        if (currentPrice <= 0) {
            return;
        }

        if (!"OPEN".equals(status)) {
            return;
        }

        if ("BUY".equals(action)) {

            // Stop Loss hit first
            if (currentPrice <= sl) {

                status = "LOSS";
                resultReason = "STOP LOSS HIT";

                return;
            }

            // Take profit levels
            if (currentPrice >= tp3) {

                status = "WIN";
                resultReason = "TP3 HIT";

                return;
            }

            if (currentPrice >= tp2) {

                status = "WIN";
                resultReason = "TP2 HIT";

                return;
            }

            if (currentPrice >= tp1) {

                status = "WIN";
                resultReason = "TP1 HIT";

                return;
            }
        }

        if ("SELL".equals(action)) {

            // Stop Loss hit first
            if (currentPrice >= sl) {

                status = "LOSS";
                resultReason = "STOP LOSS HIT";

                return;
            }

            // Take profit levels
            if (currentPrice <= tp3) {

                status = "WIN";
                resultReason = "TP3 HIT";

                return;
            }

            if (currentPrice <= tp2) {

                status = "WIN";
                resultReason = "TP2 HIT";

                return;
            }

            if (currentPrice <= tp1) {

                status = "WIN";
                resultReason = "TP1 HIT";
            }
        }
    }
}
