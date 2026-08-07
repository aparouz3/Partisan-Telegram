/*
 * Screen Time Activity — shows per-chat daily screen time.
 * Uses Telegram's native Charts package (BarChartView, LinearBarChartView).
 * Minimal design with small avatars (like Storage usage list). Per-chat time limits
 * with in-app Bulletin notification when limit is reached.
 * Includes: bar chart per chat, hourly distribution curve, and box plot.
 * Live timer display toggle per chat.
 * Custom limit input.
 */
package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.ScreenTimeTracker;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Charts.BarChartView;
import org.telegram.ui.Charts.LinearBarChartView;
import org.telegram.ui.Charts.data.ScreenTimeChartData;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

public class ScreenTimeActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private List<long[]> data;
    private long maxTime = 1;

    private long[] hourlyTotal;
    private long[] boxPlot;

    private BarChartView barChartView;
    private LinearBarChartView hourlyChartView;
    private BoxPlotView boxPlotView;

    // Live timer update runnable
    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = listView.getChildAt(i);
                    if (child instanceof ChatTimeCell) {
                        ((ChatTimeCell) child).updateLiveTime();
                    }
                }
            }
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Screen Time");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        fragmentView = new RecyclerListView(context);
        listView = (RecyclerListView) fragmentView;
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView.setOnItemClickListener((view, position) -> {
            int type = adapter.getItemViewType(position);
            if (type == 3) {
                int idx = position - adapter.getItemsStartOffset();
                if (data != null && idx >= 0 && idx < data.size()) {
                    long[] entry = data.get(idx);
                    showLimitDialog(entry[0]);
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
        AndroidUtilities.runOnUIThread(updateTimerRunnable, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.cancelRunOnUIThread(updateTimerRunnable);
    }

    private void refreshData() {
        ScreenTimeTracker tracker = ScreenTimeTracker.getInstance();
        data = tracker.getTodayPerChat();
        maxTime = 1;
        if (data != null) {
            for (long[] entry : data) {
                if (entry[1] > maxTime) {
                    maxTime = entry[1];
                }
            }
        }
        hourlyTotal = tracker.getHourlyDistribution();
        boxPlot = tracker.getHourlyBoxPlot();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateCharts();
    }

    private void updateCharts() {
        if (barChartView != null && data != null && !data.isEmpty()) {
            int n = data.size();
            long[] x = new long[n];
            long[] y = new long[n];
            for (int i = 0; i < n; i++) {
                x[i] = i;
                y[i] = data.get(i)[1];
            }
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            ScreenTimeChartData chartData = new ScreenTimeChartData(x, y, color, "screen_time", false);
            barChartView.setData(chartData);
            barChartView.pickerDelegate.set(0f, 1f);
        }

        if (hourlyChartView != null && hourlyTotal != null) {
            long[] x = new long[24];
            long[] y = new long[24];
            for (int i = 0; i < 24; i++) {
                x[i] = i;
                y[i] = hourlyTotal[i];
            }
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            ScreenTimeChartData chartData = new ScreenTimeChartData(x, y, color, "hourly", true);
            hourlyChartView.setData(chartData);
            hourlyChartView.pickerDelegate.set(0f, 1f);
        }

        if (boxPlotView != null) {
            boxPlotView.setData(boxPlot);
        }
    }

    private TLRPC.User getUser(long dialogId) {
        try {
            return getMessagesController().getUser(dialogId);
        } catch (Exception e) {
            return null;
        }
    }

    private TLRPC.Chat getChat(long dialogId) {
        try {
            return getMessagesController().getChat(-dialogId);
        } catch (Exception e) {
            return null;
        }
    }

    private String getChatName(long dialogId) {
        TLRPC.User user = getUser(dialogId);
        if (user != null) {
            return UserObject.getUserName(user);
        }
        TLRPC.Chat chat = getChat(dialogId);
        if (chat != null) {
            return chat.title;
        }
        return "Unknown";
    }

    private void showLimitDialog(long dialogId) {
        String chatName = getChatName(dialogId);
        ScreenTimeTracker tracker = ScreenTimeTracker.getInstance();
        long currentLimit = tracker.getLimit(dialogId);

        String[] options = {
            "Remove limit",
            "5 minutes",
            "15 minutes",
            "30 minutes",
            "1 hour",
            "2 hours",
            "Custom..."
        };
        long[] optionValues = {0, 5*60*1000L, 15*60*1000L, 30*60*1000L, 60*60*1000L, 2*60*60*1000L, -1};

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle("Time limit for " + chatName);
        builder.setItems(options, (dialog, which) -> {
            if (which == 6) {
                showCustomLimitDialog(dialogId, chatName);
            } else {
                applyLimit(dialogId, chatName, optionValues[which], options[which]);
            }
        });
        builder.setNegativeButton("Cancel", null);

        // Add timer visibility toggle
        boolean timerVisible = tracker.isTimerVisible(dialogId);
        builder.setPositiveButton(timerVisible ? "Hide timer" : "Show timer", (d, w) -> {
            tracker.toggleTimerVisible(dialogId);
            refreshData();
        });

        builder.create().show();
    }

    private void showCustomLimitDialog(long dialogId, String chatName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle("Custom limit for " + chatName);

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        TextView label = new TextView(getParentActivity());
        label.setText("Enter minutes:");
        label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        container.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditText input = new EditText(getParentActivity());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 45");
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        container.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 8, 0, 0));

        builder.setView(container);
        builder.setPositiveButton("Set", (d, w) -> {
            String text = input.getText().toString().trim();
            try {
                int minutes = Integer.parseInt(text);
                if (minutes > 0) {
                    applyLimit(dialogId, chatName, minutes * 60 * 1000L, minutes + " minutes");
                }
            } catch (NumberFormatException ignored) {}
        });
        builder.setNegativeButton("Cancel", null);
        builder.create().show();
        input.requestFocus();
    }

    private void applyLimit(long dialogId, String chatName, long limitMs, String label) {
        ScreenTimeTracker tracker = ScreenTimeTracker.getInstance();
        tracker.setLimit(dialogId, limitMs);
        tracker.resetAlert(dialogId);
        refreshData();

        Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(getContext(), getResourceProvider());
        layout.imageView.setImageResource(R.drawable.msg_check_s);
        if (limitMs == 0) {
            layout.textView.setText("Time limit removed for " + chatName);
        } else {
            layout.textView.setText("Limit set: " + label + " for " + chatName);
        }
        Bulletin.make(this, layout, 3000).show();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            return adapter.getItemViewType(pos) == 3;
        }

        @Override
        public int getItemViewType(int position) {
            int offset = getItemsStartOffset();
            int items = (data == null ? 0 : data.size());
            int itemsEnd = offset + (items == 0 ? 1 : items);
            if (position == 0) return 0; // Total header
            if (position == 1) return 1; // Description
            if (position == 2) return 2; // "By Chat" header
            if (position == offset && items == 0) return 4; // empty state
            if (position >= offset && position < itemsEnd) return 3; // chat row
            if (position == itemsEnd) return 1; // section desc
            if (position == itemsEnd + 1) return 5; // bar chart header
            if (position == itemsEnd + 2) return 6; // bar chart
            if (position == itemsEnd + 3) return 7; // bar chart desc
            if (position == itemsEnd + 4) return 5; // hourly header
            if (position == itemsEnd + 5) return 8; // hourly chart
            if (position == itemsEnd + 6) return 7; // hourly desc
            if (position == itemsEnd + 7) return 5; // box plot header
            if (position == itemsEnd + 8) return 9; // box plot chart
            if (position == itemsEnd + 9) return 7; // box plot desc
            return 0;
        }

        @Override
        public int getItemCount() {
            int items = (data == null ? 0 : data.size());
            int chatSection = 3 + (items == 0 ? 1 : items);
            int chartSection = 10;
            return chatSection + chartSection;
        }

        public int getItemsStartOffset() {
            return 3;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case 2:
                    view = new HeaderCell(context, getResourceProvider());
                    ((HeaderCell) view).setText("By Chat");
                    break;
                case 3:
                    view = new ChatTimeCell(context);
                    break;
                case 4:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case 5:
                    view = new HeaderCell(context, getResourceProvider());
                    break;
                case 6:
                    barChartView = new BarChartView(context);
                    view = wrapChartView(barChartView);
                    break;
                case 8:
                    hourlyChartView = new LinearBarChartView(context);
                    view = wrapChartView(hourlyChartView);
                    break;
                case 9:
                    boxPlotView = new BoxPlotView(context);
                    view = wrapChartView(boxPlotView);
                    break;
                case 7:
                default:
                    view = new TextInfoPrivacyCell(context);
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        private View wrapChartView(View chart) {
            FrameLayout container = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int w = MeasureSpec.getSize(widthMeasureSpec);
                    int h = AndroidUtilities.dp(240);
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                    );
                    chart.measure(
                        MeasureSpec.makeMeasureSpec(w - AndroidUtilities.dp(32), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h - AndroidUtilities.dp(16), MeasureSpec.EXACTLY)
                    );
                }
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    chart.layout(AndroidUtilities.dp(16), AndroidUtilities.dp(8),
                        getWidth() - AndroidUtilities.dp(16), getHeight() - AndroidUtilities.dp(8));
                }
            };
            container.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            container.addView(chart);
            return container;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            if (type == 0) {
                long total = ScreenTimeTracker.getInstance().getTodayTotal();
                TextCell cell = (TextCell) holder.itemView;
                cell.setTextAndValue("Today's Total", ScreenTimeTracker.formatDuration(total), true);
            } else if (type == 3) {
                int idx = position - getItemsStartOffset();
                if (data != null && idx >= 0 && idx < data.size()) {
                    long[] entry = data.get(idx);
                    ((ChatTimeCell) holder.itemView).setData(entry[0], getChatName(entry[0]), entry[1], maxTime);
                }
            } else if (type == 4) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setText("No chats tracked yet", false);
            } else if (type == 5) {
                int items = (data == null ? 0 : data.size());
                int itemsEnd = getItemsStartOffset() + (items == 0 ? 1 : items);
                if (position == itemsEnd + 1) {
                    ((HeaderCell) holder.itemView).setText("Time per Chat");
                } else if (position == itemsEnd + 4) {
                    ((HeaderCell) holder.itemView).setText("Hourly Distribution");
                } else if (position == itemsEnd + 7) {
                    ((HeaderCell) holder.itemView).setText("Usage Spread");
                }
            } else if (type == 7) {
                int items = (data == null ? 0 : data.size());
                int itemsEnd = getItemsStartOffset() + (items == 0 ? 1 : items);
                if (position == itemsEnd) {
                    ((TextInfoPrivacyCell) holder.itemView).setText("Tap any chat to set a daily time limit or toggle the live timer. You'll get a notification when the limit is reached.");
                } else if (position == itemsEnd + 3) {
                    ((TextInfoPrivacyCell) holder.itemView).setText("Bar chart comparing screen time across all chats today.");
                } else if (position == itemsEnd + 6) {
                    ((TextInfoPrivacyCell) holder.itemView).setText("Screen time per hour of the day. Helps identify peak usage times.");
                } else if (position == itemsEnd + 9) {
                    ((TextInfoPrivacyCell) holder.itemView).setText("Distribution of screen time across active hours. The box shows Q1–Q3 with the median marked in red. Whiskers show min and max.");
                }
            }
        }
    }

    // --- Chat row with small avatar + live timer ---
    private class ChatTimeCell extends FrameLayout {
        private final BackupImageView avatarImageView;
        private final AvatarDrawable avatarDrawable;
        private final TextView nameText;
        private final TextView valueText;
        private final View progressView;
        private final TextView limitText;
        private long dialogId;
        private long ms;
        private long maxMs;

        public ChatTimeCell(Context context) {
            super(context);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector), false));

            avatarDrawable = new AvatarDrawable();
            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

            nameText = new TextView(context);
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameText.setMaxLines(1);
            nameText.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 2, 0, 0));

            valueText = new TextView(context);
            valueText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueText.setGravity(Gravity.RIGHT);
            addView(valueText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 4, 0, 0));

            progressView = new View(context);
            progressView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 52, 24, 0, 0));

            limitText = new TextView(context);
            limitText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            limitText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            limitText.setVisibility(View.GONE);
            addView(limitText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 30, 0, 0));
        }

        public void setData(long dialogId, String name, long ms, long maxMs) {
            this.dialogId = dialogId;
            this.ms = ms;
            this.maxMs = maxMs;

            nameText.setText(name);
            valueText.setText(ScreenTimeTracker.formatDuration(ms));

            // Set avatar
            TLRPC.User user = getUser(dialogId);
            TLRPC.Chat chat = getChat(dialogId);
            if (user != null) {
                avatarDrawable.setInfo(getCurrentAccount(), user);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            } else if (chat != null) {
                avatarDrawable.setInfo(getCurrentAccount(), chat);
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }

            float ratio = maxMs > 0 ? (float) ms / maxMs : 0;
            progressView.setScaleX(Math.max(0.02f, ratio));

            long limit = ScreenTimeTracker.getInstance().getLimit(dialogId);
            if (limit > 0) {
                limitText.setVisibility(View.VISIBLE);
                String limitStr = "Limit: " + ScreenTimeTracker.formatDuration(limit);
                if (ms >= limit) {
                    limitText.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    limitStr += " — Reached!";
                } else {
                    limitText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                    long remaining = limit - ms;
                    limitStr += " (" + ScreenTimeTracker.formatDuration(remaining) + " left)";
                }
                limitText.setText(limitStr);
            } else {
                limitText.setVisibility(View.GONE);
            }
        }

        public void updateLiveTime() {
            if (dialogId == 0) return;
            boolean timerVisible = ScreenTimeTracker.getInstance().isTimerVisible(dialogId);
            if (timerVisible) {
                long liveMs = ScreenTimeTracker.getInstance().getLiveChatTime(dialogId);
                valueText.setText(ScreenTimeTracker.formatDurationShort(liveMs));
            }
        }
    }

    // --- Box plot chart ---
    private class BoxPlotView extends View {
        private long[] box;
        private final Paint boxPaint;
        private final Paint linePaint;
        private final Paint textPaint;
        private final Paint medianPaint;
        private final Paint fillPaint;
        private final RectF boxRect;

        public BoxPlotView(Context context) {
            super(context);
            box = new long[]{0,0,0,0,0};
            boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(AndroidUtilities.dp(2));
            boxPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setStrokeWidth(AndroidUtilities.dp(2));
            linePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            medianPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            medianPaint.setStrokeWidth(AndroidUtilities.dp(3));
            medianPaint.setColor(Theme.getColor(Theme.key_text_RedBold));
            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            fillPaint.setAlpha(40);
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(11));
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            boxRect = new RectF();
        }

        public void setData(long[] b) {
            if (b != null && b.length == 5) {
                box = b;
            }
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = View.getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            setMeasuredDimension(w, AndroidUtilities.dp(200));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            int chartH = h - AndroidUtilities.dp(28);
            int bottom = chartH;

            long maxVal = box[4] > 0 ? box[4] : 1;
            int centerX = w / 2;
            float boxW = AndroidUtilities.dp(60);

            float minY = bottom - (box[0] * 1f / maxVal) * chartH;
            float q1Y = bottom - (box[1] * 1f / maxVal) * chartH;
            float q3Y = bottom - (box[3] * 1f / maxVal) * chartH;
            float medianY = bottom - (box[2] * 1f / maxVal) * chartH;
            float maxY = bottom - (box[4] * 1f / maxVal) * chartH;

            canvas.drawLine(centerX, maxY, centerX, q3Y, linePaint);
            canvas.drawLine(centerX, q1Y, centerX, minY, linePaint);

            canvas.drawLine(centerX - boxW/2, maxY, centerX + boxW/2, maxY, linePaint);
            canvas.drawLine(centerX - boxW/2, minY, centerX + boxW/2, minY, linePaint);

            boxRect.set(centerX - boxW/2, q3Y, centerX + boxW/2, q1Y);
            canvas.drawRect(boxRect, fillPaint);
            canvas.drawRect(boxRect, boxPaint);

            canvas.drawLine(centerX - boxW/2, medianY, centerX + boxW/2, medianY, medianPaint);

            String[] labels = {"min", "Q1", "median", "Q3", "max"};
            float[] ys = {minY, q1Y, medianY, q3Y, maxY};
            for (int i = 0; i < 5; i++) {
                String label = labels[i] + ": " + ScreenTimeTracker.formatDuration(box[i]);
                canvas.drawText(label, centerX + boxW/2 + AndroidUtilities.dp(8), ys[i] + AndroidUtilities.dp(4), textPaint);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        return list;
    }
}
