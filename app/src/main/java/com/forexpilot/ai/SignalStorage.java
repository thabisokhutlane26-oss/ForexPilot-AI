package com.forexpilot.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class SignalStorage {

    private static final String PREFS =
            "forexpilot_signal_storage";

    private static final String HISTORY =
            "signal_history";

    private final SharedPreferences preferences;

    public SignalStorage(Context context) {

        preferences =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );
    }

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
                encode(symbol, result);

        String newHistory;

        if (oldHistory == null
                || oldHistory.trim().isEmpty()) {

            newHistory = record;

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

        for (String record : records) {

            if (!record.trim().isEmpty()) {

                list.add(record);
            }
        }

        return list;
    }

    public void clearHistory() {

        preferences.edit()
                .remove(HISTORY)
                .apply();
    }

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
                + " | Result="
                + result.resultReason;
    }
}
