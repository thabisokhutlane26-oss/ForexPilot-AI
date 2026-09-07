package com.forexpilot.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SignalStorage {

    private static final String PREFS =
            "forexpilot_signal_storage";

    private static final String HISTORY =
            "signal_history";

    private static final String ACTIVE_PREFIX =
            "active_signal_";

    private final SharedPreferences preferences;

    public SignalStorage(Context context) {

        preferences =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );
    }

    /*
     * ========================================================
     * SAVE ACTIVE SIGNAL
     * ========================================================
     */

    public void saveActiveSignal(
            String symbol,
            SignalResult result) {

        if (symbol == null
                || symbol.trim().isEmpty()
                || result == null) {

            return;
        }

        try {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "symbol",
                    symbol
            );

            object.put(
                    "action",
                    result.action
            );

            object.put(
                    "entry",
                    result.entry
            );

            object.put(
                    "sl",
                    result.sl
            );

            object.put(
                    "tp1",
                    result.tp1
            );

            object.put(
                    "tp2",
                    result.tp2
            );

            object.put(
                    "tp3",
                    result.tp3
            );

            object.put(
                    "confidence",
                    result.confidence
            );

            object.put(
                    "signalTimeMillis",
                    result.signalTimeMillis
            );

            object.put(
                    "status",
                    result.status
            );

            object.put(
                    "resultReason",
                    result.resultReason
            );

            /*
             * New field:
             * remembers the highest TP reached.
             */

            object.put(
                    "highestTargetReached",
                    result.highestTargetReached
            );

            preferences.edit()
                    .putString(
                            ACTIVE_PREFIX + symbol,
                            object.toString()
                    )
                    .apply();

        } catch (Exception ignored) {
        }
    }

    /*
     * ========================================================
     * LOAD ACTIVE SIGNALS
     * ========================================================
     */

    public Map<String, SignalResult>
    getActiveSignals() {

        Map<String, SignalResult> signals =
                new LinkedHashMap<>();

        Map<String, ?> all =
                preferences.getAll();

        for (Map.Entry<String, ?> entry
                : all.entrySet()) {

            String key =
                    entry.getKey();

            if (!key.startsWith(
                    ACTIVE_PREFIX
            )) {

                continue;
            }

            Object value =
                    entry.getValue();

            if (!(value instanceof String)) {

                continue;
            }

            try {

                JSONObject object =
                        new JSONObject(
                                (String) value
                        );

                String symbol =
                        object.optString(
                                "symbol",
                                ""
                        );

                String action =
                        object.optString(
                                "action",
                                "WAIT"
                        );

                double entryPrice =
                        object.optDouble(
                                "entry",
                                0
                        );

                double sl =
                        object.optDouble(
                                "sl",
                                0
                        );

                double tp1 =
                        object.optDouble(
                                "tp1",
                                0
                        );

                double tp2 =
                        object.optDouble(
                                "tp2",
                                0
                        );

                double tp3 =
                        object.optDouble(
                                "tp3",
                                0
                        );

                int confidence =
                        object.optInt(
                                "confidence",
                                0
                        );

                long signalTimeMillis =
                        object.optLong(
                                "signalTimeMillis",
                                0
                        );

                String status =
                        object.optString(
                                "status",
                                "OPEN"
                        );

                String resultReason =
                        object.optString(
                                "resultReason",
                                ""
                        );

                /*
                 * Read the saved TP progress.
                 *
                 * Old signals that do not have this
                 * field automatically get 0.
                 */

                int highestTargetReached =
                        object.optInt(
                                "highestTargetReached",
                                0
                        );

                if (!symbol.isEmpty()
                        && (
                        "BUY".equals(action)
                                || "SELL".equals(action)
                )) {

                    SignalResult result =
                            new SignalResult(
                                    action,
                                    entryPrice,
                                    sl,
                                    tp1,
                                    tp2,
                                    tp3,
                                    confidence,
                                    signalTimeMillis,
                                    status,
                                    resultReason,
                                    highestTargetReached
                            );

                    if (result.isOpen()) {

                        signals.put(
                                symbol,
                                result
                        );
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return signals;
    }

    /*
     * ========================================================
     * REMOVE ACTIVE SIGNAL
     * ========================================================
     */

    public void removeActiveSignal(
            String symbol) {

        if (symbol == null
                || symbol.trim().isEmpty()) {

            return;
        }

        preferences.edit()
                .remove(
                        ACTIVE_PREFIX + symbol
                )
                .apply();
    }

    /*
     * ========================================================
     * SAVE COMPLETED SIGNAL
     * ========================================================
     */

    public void saveSignal(
            String symbol,
            SignalResult result) {

        if (result == null) {

            return;
        }

        String oldHistory =
                preferences.getString(
                        HISTORY,
                        ""
                );

        String record =
                encode(
                        symbol,
                        result
                );

        String newHistory;

        if (oldHistory == null
                || oldHistory.trim().isEmpty()) {

            newHistory =
                    record;

        } else {

            newHistory =
                    record
                            + "\n"
                            + oldHistory;
        }

        preferences.edit()
                .putString(
                        HISTORY,
                        newHistory
                )
                .apply();
    }

    /*
     * ========================================================
     * GET HISTORY
     * ========================================================
     */

    public List<String> getHistory() {

        List<String> list =
                new ArrayList<>();

        String saved =
                preferences.getString(
                        HISTORY,
                        ""
                );

        if (saved == null
                || saved.trim().isEmpty()) {

            return list;
        }

        String[] records =
                saved.split("\\n");

        for (String record :
                records) {

            if (!record.trim().isEmpty()) {

                list.add(record);
            }
        }

        return list;
    }

    /*
     * ========================================================
     * CLEAR HISTORY
     * ========================================================
     */

    public void clearHistory() {

        preferences.edit()
                .remove(HISTORY)
                .apply();
    }

    /*
     * ========================================================
     * ENCODE HISTORY RECORD
     * ========================================================
     */

    private String encode(
            String symbol,
            SignalResult result) {

        return symbol
                + " | "
                + result.action
                + " | "
                + result.status
                + " | "
                + result.signalTimeMillis
                + " | Entry="
                + result.entry
                + " | SL="
                + result.sl
                + " | TP1="
                + result.tp1
                + " | TP2="
                + result.tp2
                + " | TP3="
                + result.tp3
                + " | Confidence="
                + result.confidence
                + " | Highest TP="
                + result.highestTargetReached
                + " | Result="
                + result.resultReason;
    }
}
