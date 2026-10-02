package com.forexpilot.ai;

public class Candle {

    public final long timestamp;

    public final double open;
    public final double high;
    public final double low;
    public final double close;

    public Candle(
            long timestamp,
            double open,
            double high,
            double low,
            double close
    ) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
    }

    /*
     * Compatibility constructor.
     *
     * This allows existing parts of the app that still create
     * Candle objects using OHLC only to continue compiling.
     */
    public Candle(
            double open,
            double high,
            double low,
            double close
    ) {
        this(
                0L,
                open,
                high,
                low,
                close
        );
    }
}