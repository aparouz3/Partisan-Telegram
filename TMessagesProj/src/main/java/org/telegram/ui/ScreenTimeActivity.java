/*
 * Screen Time Activity — shows per-chat daily screen time with avatars.
 * Part of the Screen Time Tracker feature.
 */
package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ScreenTimeTracker;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

public class ScreenTimeActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private List<long[]> data;
    private long maxTime = 1;

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
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(16));
        listView.setClipToPadding(false);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            int count = (data == null ? 0 : data.size()) + 2; // header + subheader + items
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return 0; // header (total)
            if (position == 1) return 1; // subheader
            return 2; // chat row
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new TotalHeaderCell(context);
            } else if (viewType == 1) {
                view = new SectionHeaderCell(context);
            } else {
                view = new ChatRowCell(context);
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            if (type == 0) {
                long total = ScreenTimeTracker.getInstance().getTodayTotal();
                ((TotalHeaderCell) holder.itemView).setTotal(total);
            } else if (type == 2) {
                int idx = position - 2;
                if (data != null && idx < data.size()) {
                    long[] entry = data.get(idx);
                    long did = entry[0];
                    long ms = entry[1];
                    ((ChatRowCell) holder.itemView).setData(did, getChatName(did), ms, maxTime);
                }
            }
        }
    }

    // --- Custom views ---

    private class TotalHeaderCell extends LinearLayout {
        private final TextView totalText;
        private final TextView label;

        public TotalHeaderCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, AndroidUtilities.dp(40), 0, AndroidUtilities.dp(24));

            totalText = new TextView(context);
            totalText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 42);
            totalText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            totalText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            totalText.setGravity(Gravity.CENTER);
            addView(totalText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            label = new TextView(context);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            label.setGravity(Gravity.CENTER);
            label.setText("Today's Screen Time in Telegram");
            addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        public void setTotal(long ms) {
            totalText.setText(ScreenTimeTracker.formatDuration(ms));
        }
    }

    private class SectionHeaderCell extends LinearLayout {
        public SectionHeaderCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            TextView tv = new TextView(context);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tv.setText("By Chat");
            addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private class ChatRowCell extends LinearLayout {
        private final BackupImageView avatarImage;
        private final AvatarDrawable avatarDrawable;
        private final TextView nameText;
        private final TextView timeText;
        private final ProgressBarView progressBar;

        public ChatRowCell(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector), false));

            avatarDrawable = new AvatarDrawable();
            avatarImage = new BackupImageView(context);
            avatarImage.setRoundRadius(AndroidUtilities.dp(24));
            addView(avatarImage, LayoutHelper.createLinear(AndroidUtilities.dp(48), AndroidUtilities.dp(48), 0, 0, AndroidUtilities.dp(12), 0));

            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);
            textLayout.setGravity(Gravity.CENTER_VERTICAL);

            nameText = new TextView(context);
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameText.setMaxLines(1);
            nameText.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(nameText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            progressBar = new ProgressBarView(context);
            textLayout.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(3), 0, 6, 0, 0));

            timeText = new TextView(context);
            timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            textLayout.addView(timeText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        }

        public void setData(long dialogId, String name, long ms, long maxMs) {
            nameText.setText(name);
            timeText.setText(ScreenTimeTracker.formatDuration(ms));
            progressBar.setRatio(maxMs > 0 ? (float) ms / maxMs : 0);

            // Set avatar
            if (dialogId > 0) {
                TLRPC.User user = getUser(dialogId);
                if (user != null) {
                    avatarDrawable.setInfo(currentAccount, user);
                    avatarImage.setForUserOrChat(user, avatarDrawable);
                } else {
                    avatarDrawable.setInfo(dialogId, name, null, null);
                    avatarImage.setForUserOrChat(null, avatarDrawable);
                }
            } else {
                TLRPC.Chat chat = getChat(dialogId);
                if (chat != null) {
                    avatarDrawable.setInfo(currentAccount, chat);
                    avatarImage.setForUserOrChat(chat, avatarDrawable);
                } else {
                    avatarDrawable.setInfo(dialogId, name, null, null);
                    avatarImage.setForUserOrChat(null, avatarDrawable);
                }
            }
        }
    }

    private class ProgressBarView extends View {
        private float ratio = 0;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ProgressBarView(Context context) {
            super(context);
            bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            bgPaint.setAlpha(30);
        }

        public void setRatio(float r) {
            ratio = Math.max(0, Math.min(1, r));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getMeasuredWidth();
            int h = getMeasuredHeight();
            float r = h / 2f;
            // Background track
            canvas.drawRoundRect(0, 0, w, h, r, r, bgPaint);
            // Filled portion — gradient-like with two colors
            float fillW = w * ratio;
            if (fillW > 0) {
                paint.setColor(Theme.getColor(Theme.key_chats_actionBackground));
                canvas.drawRoundRect(0, 0, fillW, h, r, r, paint);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        list.add(new ThemeDescription(null, 0, new Class[]{TotalHeaderCell.class}, new String[]{"totalText"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        list.add(new ThemeDescription(null, 0, new Class[]{ChatRowCell.class}, new String[]{"nameText"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        list.add(new ThemeDescription(null, 0, new Class[]{ChatRowCell.class}, new String[]{"timeText"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        return list;
    }
}
