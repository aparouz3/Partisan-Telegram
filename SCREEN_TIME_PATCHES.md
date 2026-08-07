# Screen Time Feature — Patch Documentation

This document describes all modifications made to the Partisan-Telegram codebase to implement the **Screen Time** feature (per-chat Digital Wellbeing tracking).

All custom code blocks in upstream files are wrapped with markers:

```java
// === SCREEN_TIME_FEATURE START === (short description)
... custom code ...
// === SCREEN_TIME_FEATURE END ===
```

Single-line additions use an inline marker: `// === SCREEN_TIME_FEATURE === (description)`

**To find all patches after an upstream merge:**

```bash
grep -rn "SCREEN_TIME_FEATURE" TMessagesProj/src/main/java/
```

---

## New Files (never conflict with upstream)

| File | Purpose |
|---|---|
| `TMessagesProj/src/main/java/org/telegram/messenger/ScreenTimeTracker.java` | Core singleton: per-chat time tracking, daily/hourly stats, limits, SharedPreferences persistence, `formatDuration` / `formatDurationShort`, global timer toggle (`isGlobalTimerEnabled`), per-chat timer visibility (`isTimerVisible`), limit listener (`setLimitReachedListener`, `checkLimitLive`) |
| `TMessagesProj/src/main/java/org/telegram/ui/ScreenTimeActivity.java` | Full settings screen: today's total, global timer toggle, per-chat list with limits, custom Canvas charts (BarChartView, HourlyChartView, BoxPlotView inner classes) |
| `TMessagesProj/src/main/java/org/telegram/ui/Charts/data/ScreenTimeChartData.java` | **DEPRECATED** — no longer used (Telegram chart package was removed due to `sharedUiComponents` NPE). Safe to delete. |
| `.github/workflows/build-apk.yml` | GitHub Actions build workflow (ubuntu-22.04 runner) |

---

## Patched Upstream Files

### 1. `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` (2 spots)

| Location | What | Merge note |
|---|---|---|
| `~L825` | Adds "Screen Time" menu item (id=100, `settings_power` icon) to settings list | If upstream changes the items list, re-add the two `items.add(...)` lines before the Help header |
| `~L930` | `case 100:` in menu click handler → opens `ScreenTimeActivity` | If upstream renumbers menu ids, keep 100 or pick a free one and update both spots |

### 2. `TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java` (3 spots)

| Location | What | Merge note |
|---|---|---|
| `~L521` | `screenTimeDialogsRunnable` field — invalidates visible `DialogCell`s every 1s | Self-contained; conflicts unlikely |
| `~L7092` | `onResume`: starts the runnable | Keep right after `super.onResume()` |
| `~L7469` | `onPause`: cancels the runnable | Keep right after `super.onPause()` |

### 3. `TMessagesProj/src/main/java/org/telegram/ui/Cells/DialogCell.java` (1 spot)

| Location | What | Merge note |
|---|---|---|
| `~L4207` | Draws live timer text below name line, right-aligned (in `onDraw`, inside the `canvas.save()` block after time layout) | **HIGH conflict risk** — DialogCell changes often upstream. On conflict: find the `canvas.restore()` after `timeLayout` drawing and insert the block before it. Uses `currentDialogId`, `timeLeft`, `useForceThreeLines`, `getTimeTextPaint()` |

### 4. `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` (6 spots)

| Location | What | Merge note |
|---|---|---|
| `~L514` | `screenTimeTitleRunnable` + `updateScreenTimeTitle()` method (timer in action bar title via SpannableStringBuilder: RelativeSizeSpan 0.7f, gray, monospace). Also calls `checkLimitLive` every second | **HIGH conflict risk** — self-contained block after `avatarContainer` field; re-insert after any field |
| `~L1761` | `screen_time_toggle = 74` menu id constant | If upstream adds ids >73, bump ours to a free value |
| `~L4161` | Menu click handler for `screen_time_toggle` → toggles per-chat timer, shows Bulletin | Inside the big `if/else if` chain in the menu item click listener |
| `~L4765` | Adds "Screen Time Timer" sub-item to chat header menu | After the search item block |
| `~L30198` | `onResume`: `tracker.onChatResumed(dialog_id)`, sets limit-reached Bulletin listener, initial `checkLimitLive`, starts title runnable | Insert after the early-return guard in `onResume` |
| `~L30428` | `onPause`: cancels title runnable, calls `tracker.onChatPaused()` | Right after `super.onPause()` |

### 5. `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java` (6 spots)

| Location | What | Merge note |
|---|---|---|
| `~L673` | `private int screenTimeRow;` field | With the other `...Row` field declarations |
| `~L10614` | `screenTimeRow = -1;` init | With the other row inits in `updateRowsIds`-style method |
| `~L10822` | `screenTimeRow = rowCount++;` after usernameRow (user profile section) | Row order determines display position |
| `~L10986` | `screenTimeRow = rowCount++;` after usernameRow (chat profile section) | Same |
| `~L13824` | `onBindViewHolder`: binds "Screen time today" value via `detailCell.setTextAndValue()` | In the `VIEW_TYPE_TEXT_DETAIL` binding branch, after `chatIdRow` handling |
| `~L14530` | `getItemViewType`: adds `position == screenTimeRow` to the `VIEW_TYPE_TEXT_DETAIL` check (inline marker — it's a modification of an existing line) | If upstream changes this condition, keep our `|| position == screenTimeRow` |

---

## Key Technical Decisions (for future reference)

- **NO Telegram chart package** (`org.telegram.ui.Charts.*`) — `BaseChartView.onMeasure` NPEs on `sharedUiComponents` which is only initialized in `StatisticActivity`. We use custom Canvas-based inner-class views in `ScreenTimeActivity` instead.
- **DialogCell timer position**: below name line at message-line Y, right-aligned (fixes Persian RTL name overlap).
- **Timer format**: `formatDurationShort` → `M:SS` / `H:MM:SS`.
- **Menu ids**: SettingsActivity uses `100`; ChatActivity uses `74`.
- **Storage**: SharedPreferences via `ScreenTimeTracker` (daily + hourly, resets daily).
- **Build**: GitHub Actions on `ubuntu-22.04` (NOT `ubuntu-latest`); server has only ~3.7GB RAM, cannot build locally.

## Merge Workflow

```bash
git fetch origin master          # origin = wrwrabbit/Partisan-Telegram-Android
git merge origin/master
# Resolve conflicts — search for SCREEN_TIME_FEATURE markers
grep -rn "SCREEN_TIME_FEATURE" TMessagesProj/src/main/java/
# Verify build, then trigger GitHub Actions workflow
```
