package com.forexpilot.ai;

public class SignalResult {

    public final String action;

    public final double entry;
    public final double sl;
    public final double tp1;
    public final double tp2;
    public final double tp3;

    public final int confidence;

    public final long signalTimeMillis;

    // OPEN, WIN, LOSS, EXPIRED, WAIT
    public String status;

    public String resultReason;

    public SignalResult(
            String action,
            double entry,
            double sl,
            double tp1,
            double tp2,
            double tp3,
            int confidence) {

        this(
                action,
                entry,
                sl,
                tp1,
                tp2,
                tp3,
                confidence,
                System.currentTimeMillis(),
                null,
                ""
        );
    }

    /*
     * Constructor used when restoring a signal
     * from permanent storage.
     */
    public SignalResult(
            String action,
            double entry,
            double sl,
            double tp1,
            double tp2,
            double tp3,
            int confidence,
            long signalTimeMillis,
            String status,
            String resultReason) {

        this.action = action;
        this.entry = entry;
        this.sl = sl;
        this.tp1 = tp1;
        this.tp2 = tp2;
        this.tp3 = tp3;
        this.confidence = confidence;

        this.signalTimeMillis = signalTimeMillis;

        if (status == null || status.trim().isEmpty()) {

            if ("BUY".equals(action)
                    || "SELL".equals(action)) {

                this.status = "OPEN";

            } else {

                this.status = "WAIT";
            }

        } else {

            this.status = status;
        }

        this.resultReason =
                resultReason == null
                        ? ""
                        : resultReason;
    }

    public void updateStatus(double currentPrice) {

        if (currentPrice <= 0) {
            return;
        }

        if (!"OPEN".equals(status)) {
            return;
        }

        if ("BUY".equals(action)) {

            if (currentPrice <= sl) {

                status = "LOSS";

                resultReason =
                        "STOP LOSS HIT";

                return;
            }

            if (currentPrice >= tp3) {

                status = "WIN";

                resultReason =
                        "TP3 HIT";

                return;
            }

            if (currentPrice >= tp2) {

                status = "WIN";

                resultReason =
                        "TP2 HIT";

                return;
            }

            if (currentPrice >= tp1) {

                status = "WIN";

                resultReason =
                        "TP1 HIT";

                return;
            }
        }

        if ("SELL".equals(action)) {

            if (currentPrice >= sl) {

                status = "LOSS";

                resultReason =
                        "STOP LOSS HIT";

                return;
            }

            if (currentPrice <= tp3) {

                status = "WIN";

                resultReason =
                        "TP3 HIT";

                return;
            }

            if (currentPrice <= tp2) {

                status = "WIN";

                resultReason =
                        "TP2 HIT";

                return;
            }

            if (currentPrice <= tp1) {

                status = "WIN";

                resultReason =
                        "TP1 HIT";

                return;
            }
        }
    }

    public void expire() {

        if ("OPEN".equals(status)) {

            status = "EXPIRED";

            resultReason =
                    "SIGNAL EXPIRED";
        }
    }

    public boolean isOpen() {

        return "OPEN".equals(status);
    }

    public boolean isCompleted() {

        return "WIN".equals(status)
                || "LOSS".equals(status)
                || "EXPIRED".equals(status);
    }
}
