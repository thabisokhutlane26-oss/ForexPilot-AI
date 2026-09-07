package com.forexpilot.ai;

public class SignalResult {

    public final String action;
    public final double entry;
    public final double sl;
    public final double tp1;
    public final double tp2;
    public final double tp3;
    public final int confidence;

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
    }
}
