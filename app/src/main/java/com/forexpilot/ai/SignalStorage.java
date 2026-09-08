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
     * SAVE / UPDATE ACTIVE SIGNAL
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
                    createJson(
                            symbol,
                            result
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

                SignalResult result =
                        decode(object);

                String symbol =
                        object.optString(
                                "symbol",
                                ""
                        );

                if (!symbol.isEmpty()
                        && result != null
                        && result.isOpen()) {

                    signals.put(
                            symbol,
                            result
                    );

                } else if (result != null
                        && result.isCompleted()) {

                    /*
                     * Clean up stale completed
                     * active trades.
                     */

                    preferences.edit()
                            .remove(key)
                            .apply();
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
     * SAVE / UPDATE HISTORY
     *
     * The signalTimeMillis is used as the
     * unique identity of the signal.
     *
     * This means:
     *
     * OPEN
     *   ↓
     * TP1 HIT
     *   ↓
     * TP2 HIT
     *   ↓
     * WIN
     *
     * remains ONE history record.
     * ========================================================
     */

    public void saveSignal(
            String symbol,
            SignalResult result) {

        if (symbol == null
                || symbol.trim().isEmpty()
                || result == null) {

            return;
        }

        upsertHistory(
                symbol,
                result
        );
    }

    /*
     * ========================================================
     * INSERT OR UPDATE HISTORY
     * ========================================================
     */

    private void upsertHistory(
            String symbol,
            SignalResult result) {

        List<String> records =
                getHistory();

        String newRecord =
                encode(
                        symbol,
                        result
                );

        String signalId =
                buildSignalId(
                        symbol,
                        result.signalTimeMillis
                );

        List<String> updated =
                new ArrayList<>();

        boolean replaced = false;

        for (String record : records) {

            if (record == null
                    || record.trim().isEmpty()) {

                continue;
            }

            if (recordMatches(
                    record,
                    signalId
            )) {

                if (!replaced) {

                    updated.add(
                            newRecord
                    );

                    replaced = true;
                }

            } else {

                updated.add(record);
            }
        }

        /*
         * If this is a brand-new signal,
         * put it at the top of history.
         */

        if (!replaced) {

            updated.add(
                    0,
                    newRecord
            );
        }

        /*
         * Rebuild the history string.
         */

        StringBuilder builder =
                new StringBuilder();

        for (String record :
                updated) {

            if (builder.length() > 0) {

                builder.append("\n");
            }

            builder.append(record);
        }

        preferences.edit()
                .putString(
                        HISTORY,
                        builder.toString()
                )
                .apply();
    }

    /*
     * ========================================================
     * GET HISTORY
     *
     * Newest records are returned first.
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

            if (record != null
                    && !record.trim().isEmpty()) {

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
     * CREATE JSON
     * ========================================================
     */

    private JSONObject createJson(
            String symbol,
            SignalResult result)
            throws Exception {

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

        object.put(
                "highestTargetReached",
                result.highestTargetReached
        );

        return object;
    }

    /*
     * ========================================================
     * DECODE JSON
     * ========================================================
     */

    private SignalResult decode(
            JSONObject object) {

        try {

            String action =
                    object.optString(
                            "action",
                            "WAIT"
                    );

            double entry =
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

            int highestTargetReached =
                    object.optInt(
                            "highestTargetReached",
                            0
                    );

            if (!"BUY".equals(action)
                    && !"SELL".equals(action)) {

                return null;
            }

            return new SignalResult(
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
                    highestTargetReached
            );

        } catch (Exception ignored) {

            return null;
        }
    }

    /*
     * ========================================================
     * BUILD UNIQUE SIGNAL ID
     * ========================================================
     */

    private String buildSignalId(
            String symbol,
            long signalTimeMillis) {

        return symbol
                + " | "
                + signalTimeMillis;
    }

    /*
     * ========================================================
     * CHECK WHETHER HISTORY RECORD
     * BELONGS TO THE SAME SIGNAL
     * ========================================================
     */

    private boolean recordMatches(
            String record,
            String signalId) {

        if (record == null
                || signalId == null) {

            return false;
        }

        /*
         * The history format contains:
         *
         * SYMBOL | ACTION | STATUS | TIME | ...
         *
         * We identify the record using
         * the symbol and exact signal time.
         */

        String[] parts =
                record.split(
                        " \\| ",
                        5
                );

        if (parts.length < 4) {

            return false;
        }

        String symbol =
                parts[0];

        String time =
                parts[3];

        return signalId.equals(
                symbol
                        + " | "
                        + time
        );
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
