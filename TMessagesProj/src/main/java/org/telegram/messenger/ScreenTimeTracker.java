/*
 * Screen Time Tracker for Telegram
 * Tracks per-chat daily screen time (like Digital Wellbeing).
 * Stores elapsed milliseconds per dialog_id per day in SharedPreferences.
 */
package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.LongSparseArray;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScreenTimeTracker {

    private static ScreenTimeTracker instance;
    private final SharedPreferences prefs;

    private long currentDialogId = 0;
    private long currentStartTime = 0;
    private boolean tracking = false;

    private ScreenTimeTracker() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences("screentime", Context.MODE_PRIVATE);
    }

    public static synchronized ScreenTimeTracker getInstance() {
        if (instance == null) {
            instance = new ScreenTimeTracker();
        }
        return instance;
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private String chatDayKey(long dialogId) {
        return todayKey() + "_" + dialogId;
    }

    /**
     * Called when a chat is resumed (becomes visible).
     */
    public void onChatResumed(long dialogId) {
        // Flush any previous session first (safety)
        flushCurrent();
        currentDialogId = dialogId;
        currentStartTime = System.currentTimeMillis();
        tracking = true;
    }

    /**
     * Called when a chat is paused or destroyed.
     */
    public void onChatPaused() {
        flushCurrent();
        tracking = false;
        currentDialogId = 0;
    }

    private void flushCurrent() {
        if (tracking && currentDialogId != 0 && currentStartTime > 0) {
            long elapsed = System.currentTimeMillis() - currentStartTime;
            if (elapsed > 0 && elapsed < 86400000L) { // sanity: less than 24h
                String key = chatDayKey(currentDialogId);
                long existing = prefs.getLong(key, 0);
                prefs.edit().putLong(key, existing + elapsed).apply();
            }
        }
        currentStartTime = 0;
    }

    /**
     * Get total screen time for today (ms).
     */
    public long getTodayTotal() {
        flushCurrent();
        long total = 0;
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(todayKey() + "_")) {
                total += prefs.getLong(key, 0);
            }
        }
        return total;
    }

    /**
     * Get a list of {dialogId, ms} pairs for today, sorted by time descending.
     */
    public List<long[]> getTodayPerChat() {
        flushCurrent();
        String prefix = todayKey() + "_";
        List<long[]> list = new ArrayList<>();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                try {
                    long did = Long.parseLong(key.substring(prefix.length()));
                    long ms = prefs.getLong(key, 0);
                    if (ms > 1000) { // skip <1s
                        list.add(new long[]{did, ms});
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        list.sort((a, b) -> Long.compare(b[1], a[1]));
        return list;
    }

    /**
     * Format milliseconds to a human-readable string (e.g. "2h 15m" or "5m 30s").
     */
    public static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Reset all tracking data for today.
     */
    public void resetToday() {
        String prefix = todayKey() + "_";
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : new ArrayList<>(prefs.getAll().keySet())) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}
