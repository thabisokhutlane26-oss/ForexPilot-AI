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

    /*
     * Possible statuses:
     *
     * WAIT
     * OPEN
     * TP1 HIT
     * TP2 HIT
     * WIN
     * LOSS
     * EXPIRED
     */
    public String status;

    public String resultReason;

    /*
     * Tracks the highest target reached.
     *
     * 0 = no TP reached
     * 1 = TP1 reached
     * 2 = TP2 reached
     * 3 = TP3 reached
     */
    public int highestTargetReached;

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
                "",
                0
        );
    }

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

        this(
                action,
                entry,
                sl,
                tp1,
                tp2,
                tp3,
                confidence,
                signalTimeMillis,
                status,
                resultReason,
                0
        );
    }

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
            String resultReason,
            int highestTargetReached) {

        this.action = action;
        this.entry = entry;
        this.sl = sl;
        this.tp1 = tp1;
        this.tp2 = tp2;
        this.tp3 = tp3;
        this.confidence = confidence;

        this.signalTimeMillis =
                signalTimeMillis;

        if (status == null
                || status.trim().isEmpty()) {

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

        this.highestTargetReached =
                Math.max(
                        0,
                        Math.min(
                                3,
                                highestTargetReached
                        )
                );
    }

    /*
     * ========================================================
     * UPDATE SIGNAL RESULT
     * ========================================================
     */

    public void updateStatus(
            double currentPrice) {

        if (currentPrice <= 0) {
            return;
        }

        /*
         * Only OPEN signals can change.
         */
        if (!"OPEN".equals(status)
                && !"TP1 HIT".equals(status)
                && !"TP2 HIT".equals(status)) {

            return;
        }

        if ("BUY".equals(action)) {

            updateBuy(currentPrice);

        } else if ("SELL".equals(action)) {

            updateSell(currentPrice);
        }
    }

    /*
     * ========================================================
     * BUY LOGIC
     * ========================================================
     */

    private void updateBuy(
            double currentPrice) {

        /*
         * Stop loss has priority.
         *
         * If price reaches SL before a target,
         * the signal becomes LOSS.
         */
        if (currentPrice <= sl) {

            status = "LOSS";

            resultReason =
                    "STOP LOSS HIT";

            return;
        }

        /*
         * TP3 is the final target.
         */
        if (currentPrice >= tp3) {

            highestTargetReached = 3;

            status = "WIN";

            resultReason =
                    "TP3 HIT";

            return;
        }

        /*
         * TP2 reached.
         */
        if (currentPrice >= tp2) {

            highestTargetReached = 2;

            status = "TP2 HIT";

            resultReason =
                    "TP2 HIT • TARGET 2 REACHED";

            return;
        }

        /*
         * TP1 reached.
         */
        if (currentPrice >= tp1) {

            highestTargetReached = 1;

            status = "TP1 HIT";

            resultReason =
                    "TP1 HIT • TARGET 1 REACHED";
        }
    }

    /*
     * ========================================================
     * SELL LOGIC
     * ========================================================
     */

    private void updateSell(
            double currentPrice) {

        /*
         * Stop loss has priority.
         */
        if (currentPrice >= sl) {

            status = "LOSS";

            resultReason =
                    "STOP LOSS HIT";

            return;
        }

        /*
         * TP3 is the final target.
         */
        if (currentPrice <= tp3) {

            highestTargetReached = 3;

            status = "WIN";

            resultReason =
                    "TP3 HIT";

            return;
        }

        /*
         * TP2 reached.
         */
        if (currentPrice <= tp2) {

            highestTargetReached = 2;

            status = "TP2 HIT";

            resultReason =
                    "TP2 HIT • TARGET 2 REACHED";

            return;
        }

        /*
         * TP1 reached.
         */
        if (currentPrice <= tp1) {

            highestTargetReached = 1;

            status = "TP1 HIT";

            resultReason =
                    "TP1 HIT • TARGET 1 REACHED";
        }
    }

    /*
     * ========================================================
     * EXPIRE SIGNAL
     * ========================================================
     */

    public void expire() {

        if ("OPEN".equals(status)
                || "TP1 HIT".equals(status)
                || "TP2 HIT".equals(status)) {

            status = "EXPIRED";

            resultReason =
                    "SIGNAL EXPIRED";
        }
    }

    /*
     * ========================================================
     * STATUS HELPERS
     * ========================================================
     */

    public boolean isOpen() {

        return "OPEN".equals(status)
                || "TP1 HIT".equals(status)
                || "TP2 HIT".equals(status);
    }

    public boolean isCompleted() {

        return "WIN".equals(status)
                || "LOSS".equals(status)
                || "EXPIRED".equals(status);
    }

    public boolean isWin() {

        return "WIN".equals(status);
    }

    public boolean isLoss() {

        return "LOSS".equals(status);
    }

    public boolean isExpired() {

        return "EXPIRED".equals(status);
    }

    public boolean hasReachedTP1() {

        return highestTargetReached >= 1;
    }

    public boolean hasReachedTP2() {

        return highestTargetReached >= 2;
    }

    public boolean hasReachedTP3() {

        return highestTargetReached >= 3;
    }
}
