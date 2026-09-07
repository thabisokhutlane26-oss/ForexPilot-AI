package com.forexpilot.ai;

public class SignalResult {

    public final String action;

    public final double entry;
    public final double sl;
    public final double tp1;
    public final double tp2;
    public final double tp3;

    public final int confidence;

    // Time when this signal was created
    public final long signalTimeMillis;

    // OPEN, WIN, LOSS, EXPIRED, WAIT
    public String status;

    // Reason for the result
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
     * Updates the signal status
     * using the current live market price.
     */
    public void updateStatus(double currentPrice) {

        if (currentPrice <= 0) {
            return;
        }

        if (!"OPEN".equals(status)) {
            return;
        }

        if ("BUY".equals(action)) {

            // Stop Loss
            if (currentPrice <= sl) {

                status = "LOSS";

                resultReason =
                        "STOP LOSS HIT";

                return;
            }

            // TP3
            if (currentPrice >= tp3) {

                status = "WIN";

                resultReason =
                        "TP3 HIT";

                return;
            }

            // TP2
            if (currentPrice >= tp2) {

                status = "WIN";

                resultReason =
                        "TP2 HIT";

                return;
            }

            // TP1
            if (currentPrice >= tp1) {

                status = "WIN";

                resultReason =
                        "TP1 HIT";

                return;
            }
        }

        if ("SELL".equals(action)) {

            // Stop Loss
            if (currentPrice >= sl) {

                status = "LOSS";

                resultReason =
                        "STOP LOSS HIT";

                return;
            }

            // TP3
            if (currentPrice <= tp3) {

                status = "WIN";

                resultReason =
                        "TP3 HIT";

                return;
            }

            // TP2
            if (currentPrice <= tp2) {

                status = "WIN";

                resultReason =
                        "TP2 HIT";

                return;
            }

            // TP1
            if (currentPrice <= tp1) {

                status = "WIN";

                resultReason =
                        "TP1 HIT";

                return;
            }
        }
    }

    /**
     * Manually expire an active signal.
     */
    public void expire() {

        if ("OPEN".equals(status)) {

            status = "EXPIRED";

            resultReason =
                    "SIGNAL EXPIRED";
        }
    }

    /**
     * Returns true when this signal
     * is currently active.
     */
    public boolean isOpen() {

        return "OPEN".equals(status);
    }

    /**
     * Returns true when this signal
     * has completed.
     */
    public boolean isCompleted() {

        return "WIN".equals(status)
                || "LOSS".equals(status)
                || "EXPIRED".equals(status);
    }
}
