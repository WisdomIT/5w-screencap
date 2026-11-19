package dev.wisdomit.screencap;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * The Launch screen (the app menu tile "화면 캡처"; NEMESIS Manager's Launch opens the HMG-API app instead).
 * Left: four cards (capture test, buttons, recording, diagnostics). Right: the issue QR,
 * filled with this car's versions. Built in code, no layout XML (keeps the resource build
 * simple), in the same style as pretendard-systemfont's status screen.
 *
 * Every change is saved to HMG-API at once; the button bindings follow the saved values.
 */
public final class MainActivity extends Activity {
    // dark car palette (same values as pretendard-systemfont)
    static final int BG = 0xFF111317, CARD = 0xFF1C1F24, LINE = 0xFF2A2E35;
    static final int TEXT = 0xFFF2F3F5, SUB = 0xFF9AA0A8, MUTED = 0xFF6B717A;
    static final int GOOD_FG = 0xFF4ADE9B, GOOD_BG = 0xFF143326, WARN_FG = 0xFFF5C451, WARN_BG = 0xFF3A2F12;
    static final int BAD_FG = 0xFFFF7B7B, BAD_BG = 0xFF3D1A1A, ACCENT = 0xFF3D8BFD;
    /** the car's status bar is drawn over the top of every app */
    static final int TOP_INSET_DP = 56;
    static final String[] QUALITY_VALUES = { "standard", "sharp", "full" };
    static final String[] QUALITY_TITLES = {
        "표준 · 1024×384 · 약 12fps · 10MB/분", "선명 · 1280×480 · 약 8fps · 12MB/분", "원본 · 1920×720 · 약 3fps · 12MB/분" };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private String version = "?";
    private boolean loading = true;

    private TextView pill, saveState, buttonHint, recTitle, capResult, diagState, fwText, diagTitle, diagDetail, diagResult;
    private ImageView thumb;
    private Button capButton, diagButton;
    private LinearLayout diagPanel;
    private ProgressBar diagProgress;
    /** the stage views, made when the first status names the stages (screencapd's Diag.STAGES) */
    private final java.util.List<TextView> stepViews = new java.util.ArrayList<TextView>();
    private LinearLayout stepGrid;
    private boolean diagPolling;
    /** this screen saw the diagnostics running; otherwise a finished run is shown as the last one */
    private boolean sawDiagRunning;
    private Spinner capKey, capPress, recKey, recPress, quality;
    private Switch autoRecord, floating;
    private QrView qr;
    private String usbRoot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) { }
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        background(new Runnable() {
            @Override public void run() {
                final JSONObject s = Settings.sync(MainActivity.this, Settings.load(MainActivity.this));
                final JSONObject st = Daemon.call("status", 3000);
                final CarInfo info = CarInfo.read(version);
                final JSONObject diag = Daemon.call("diag-status", 3000);
                ui.post(new Runnable() {
                    @Override public void run() {
                        fill(s);
                        status(st);
                        qr.setText(info.issueUrl());
                        fwText.setText(info.firmware + "\nHMG-API " + info.hmgapi + " · 5W Screencap " + version);
                        if (showDiag(diag)) pollDiag();   // it may have been running while this screen was away
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        diagPolling = false;
    }

    // ---- layout ----

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(TOP_INSET_DP), 0, 0);

        // left: scrolls, so nothing is cut off whatever the font size or the car's screen
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout left = vertical();
        left.setPadding(dp(28), dp(14), dp(22), dp(18));
        scroll.addView(left, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout head = horizontal();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("5W Screencap", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title);
        head.addView(text("  v" + version, 16, MUTED));
        head.addView(text("   made by WisdomIT", 15, MUTED));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        pill = text("확인 중", 16, SUB);
        pill.setPadding(dp(14), dp(5), dp(14), dp(5));
        pill.setBackground(round(CARD, 999));
        head.addView(pill);
        Button close = button("닫기  ✕", false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { close(); }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.leftMargin = dp(12);
        head.addView(close, closeLp);
        left.addView(head);

        LinearLayout row1 = horizontal(), row2 = horizontal();
        row1.setBaselineAligned(false);
        row2.setBaselineAligned(false);
        row1.addView(captureCard(), cardLp(0, dp(12)));
        row1.addView(buttonCard(), cardLp(dp(12), 0));
        row2.addView(recordCard(), cardLp(0, dp(12)));
        row2.addView(diagCard(), cardLp(dp(12), 0));
        left.addView(row1, margins(dp(12)));
        left.addView(row2, margins(dp(12)));

        // right: issue QR
        LinearLayout right = vertical();
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setPadding(dp(26), dp(22), dp(26), dp(18));
        right.setBackground(leftLine());
        qr = new QrView(this);
        right.addView(qr, new LinearLayout.LayoutParams(dp(210), dp(210)));
        TextView qrTitle = text("문제 신고 · 기능 제안", 19, TEXT);
        qrTitle.setTypeface(Typeface.DEFAULT_BOLD);
        qrTitle.setGravity(Gravity.CENTER);
        right.addView(qrTitle, margins(dp(12)));
        TextView cap = text("휴대폰으로 스캔하면 GitHub 이슈 작성 화면이 열립니다. 이 차의 펌웨어와 버전이 미리 채워집니다. 진단을 실행했다면 USB의 SUMMARY.txt를 첨부해 주세요.", 14, SUB);
        cap.setGravity(Gravity.CENTER);
        right.addView(cap, margins(dp(6)));
        fwText = text("", 12, MUTED);
        fwText.setGravity(Gravity.CENTER);
        fwText.setTypeface(Typeface.MONOSPACE);
        right.addView(fwText, margins(dp(10)));
        root.addView(right, new LinearLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View captureCard() {
        LinearLayout c = card("캡처 테스트", "이 차에서 캡처가 되는지 확인");
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        thumb.setBackground(round(0xFF252931, 8));
        row.addView(thumb, new LinearLayout.LayoutParams(dp(196), dp(74)));
        LinearLayout col = vertical();
        col.setPadding(dp(14), 0, 0, 0);
        capButton = button("테스트 캡처", true);
        capButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { testCapture(); }
        });
        col.addView(capButton);
        capResult = text("아직 캡처하지 않음", 15, SUB);
        col.addView(capResult, margins(dp(6)));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.addView(row);
        return c;
    }

    private View buttonCard() {
        LinearLayout c = card("버튼", "HMG-API 버튼 연결");
        capKey = spinner(keyTitles());
        capPress = spinner(Choices.PRESS_TITLES);
        c.addView(labeled("캡처", capKey, capPress));
        recKey = spinner(keyTitles());
        recPress = spinner(Choices.PRESS_TITLES);
        c.addView(labeled("녹화", recKey, recPress));
        buttonHint = text("", 14, MUTED);
        c.addView(buttonHint, margins(dp(4)));
        saveState = text("", 13, MUTED);
        c.addView(saveState);
        AdapterView.OnItemSelectedListener keyChanged = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Spinner press = p == capKey ? capPress : recPress;
                String prev = pressOf(p == capKey ? capKey : recKey, press);
                setPresses(press, keyOf((Spinner) p), prev);
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        capKey.setOnItemSelectedListener(keyChanged);
        recKey.setOnItemSelectedListener(keyChanged);
        capPress.setOnItemSelectedListener(onSelect());
        recPress.setOnItemSelectedListener(onSelect());
        return c;
    }

    private View recordCard() {
        LinearLayout c = card("녹화", " ");
        recTitle = (TextView) ((LinearLayout) c.getChildAt(0)).getChildAt(1);
        quality = spinner(QUALITY_TITLES);
        quality.setOnItemSelectedListener(onSelect());
        c.addView(labeled("화질", quality, null));
        autoRecord = new Switch(this);
        autoRecord.setOnCheckedChangeListener(onToggle());
        c.addView(switchRow("USB 연결 시 자동 녹화", autoRecord));
        floating = new Switch(this);
        floating.setOnCheckedChangeListener(onToggle());
        c.addView(switchRow("플로팅 버튼 표시", floating));
        return c;
    }

    private View diagCard() {
        LinearLayout c = card("진단", "문제 신고에 첨부할 정보");
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        diagButton = button("진단 실행", false);
        diagButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startDiag(); }
        });
        row.addView(diagButton);
        diagState = text("약 20초 · USB의 5W-Screencap/diag 에 저장됩니다. 캡처 이미지나 개인 정보는 담지 않습니다.", 13, MUTED);
        diagState.setPadding(dp(12), 0, 0, 0);
        row.addView(diagState, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.addView(row, margins(dp(4)));

        // progress, as pretendard-systemfont's status screen shows it
        diagPanel = vertical();
        diagPanel.setVisibility(View.GONE);
        diagTitle = text("", 16, TEXT);
        diagTitle.setTypeface(Typeface.DEFAULT_BOLD);
        diagPanel.addView(diagTitle);
        diagProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        diagPanel.addView(diagProgress, margins(dp(6)));
        stepGrid = vertical();
        diagPanel.addView(stepGrid, margins(dp(6)));
        diagDetail = text("", 13, SUB);
        diagPanel.addView(diagDetail, margins(dp(6)));
        diagResult = text("", 13, SUB);
        diagResult.setTypeface(Typeface.MONOSPACE);
        diagResult.setVisibility(View.GONE);
        diagPanel.addView(diagResult, margins(dp(6)));
        c.addView(diagPanel, margins(dp(8)));
        return c;
    }

    // ---- settings <-> controls ----

    private void fill(JSONObject s) {
        loading = true;
        String ck = s.optString("capture_key"), rk = s.optString("record_key");
        capKey.setSelection(Choices.keyIndex(ck) + 1, false);
        setPresses(capPress, ck, s.optString("capture_press"));
        recKey.setSelection(Choices.keyIndex(rk) + 1, false);
        setPresses(recPress, rk, s.optString("record_press"));
        String q = s.optString(Settings.QUALITY, "standard");
        for (int i = 0; i < QUALITY_VALUES.length; i++) if (QUALITY_VALUES[i].equals(q)) quality.setSelection(i, false);
        autoRecord.setChecked(s.optBoolean(Settings.AUTO_RECORD));
        floating.setChecked(s.optBoolean(Settings.FLOATING, true));
        hint();
        ui.post(new Runnable() {   // spinners report their first selection after layout
            @Override public void run() { loading = false; }
        });
    }

    /** the press list for this button, keeping the press if it has it */
    private void setPresses(Spinner press, String key, String selected) {
        String[] values = Choices.pressValues(key);
        press.setAdapter(adapter(Choices.pressTitles(key)));
        int at = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) at = i;
        if (Choices.isStar(key) && "double".equals(selected)) at = 0;
        press.setSelection(at, false);
        press.setEnabled(Choices.keyIndex(key) >= 0);
    }

    private static String keyOf(Spinner key) {
        int i = key.getSelectedItemPosition() - 1;
        return i < 0 ? Choices.OFF : Choices.KEYS[i][0];
    }

    private static String pressOf(Spinner key, Spinner press) {
        String[] values = Choices.pressValues(keyOf(key));
        int i = press.getSelectedItemPosition();
        return i >= 0 && i < values.length ? values[i] : values[0];
    }

    private void hint() {
        String ck = keyOf(capKey), cp = pressOf(capKey, capPress), rk = keyOf(recKey), rp = pressOf(recKey, recPress);
        String clash = Choices.clash(Choices.combined(ck, cp), Choices.combined(rk, rp));
        boolean star = Choices.isStar(ck) && "short".equals(cp) || Choices.isStar(rk) && "short".equals(rp);
        boolean dbl = "double".equals(cp) && Choices.keyIndex(ck) >= 0 || "double".equals(rp) && Choices.keyIndex(rk) >= 0;
        if (clash != null) {
            buttonHint.setText(clash);
            buttonHint.setTextColor(WARN_FG);
        } else if (star) {
            buttonHint.setText("☆ 1회 누름은 차량 설정 > 버튼 > ☆ 에서 \"화면 캡처\" 또는 \"화면 녹화 시작/정지\"를 골라야 동작합니다.");
            buttonHint.setTextColor(SUB);
        } else if (dbl) {
            buttonHint.setText("연속 2회를 쓰는 버튼은 원래 기능이 항상 꺼집니다. 한 번만 누르면 아무 일도 일어나지 않습니다.");
            buttonHint.setTextColor(WARN_FG);
        } else {
            buttonHint.setText("1회 누름은 바로 실행됩니다. 길게 누름(0.8초)은 그 버튼의 원래 길게 누름 기능을 대신합니다.");
            buttonHint.setTextColor(MUTED);
        }
    }

    private void changed() {
        if (loading) return;
        hint();
        String ck = keyOf(capKey), cp = pressOf(capKey, capPress), rk = keyOf(recKey), rp = pressOf(recKey, recPress);
        final String capture = Choices.combined(ck, cp), record = Choices.combined(rk, rp);
        if (Choices.clash(capture, record) != null) {
            saveState.setText("저장하지 않음");
            return;
        }
        final JSONObject v = new JSONObject();
        try {
            v.put("capture_key", ck).put("capture_press", cp).put("record_key", rk).put("record_press", rp)
                    .put(Settings.CAPTURE, capture).put(Settings.RECORD, record)
                    .put(Settings.QUALITY, QUALITY_VALUES[Math.max(0, quality.getSelectedItemPosition())])
                    .put(Settings.AUTO_RECORD, autoRecord.isChecked()).put(Settings.FLOATING, floating.isChecked());
        } catch (Exception ignored) { }
        saveState.setText("저장 중…");
        background(new Runnable() {
            @Override public void run() {
                final boolean ok = Settings.save(MainActivity.this, v);
                if (ok) FloatingService.sync(MainActivity.this, v.optBoolean(Settings.FLOATING, true));
                ui.post(new Runnable() {
                    @Override public void run() {
                        saveState.setText(ok ? "저장됨 · 바로 적용됩니다" : "저장하지 못했습니다. HMG-API에서 이 mod가 켜져 있는지 확인하세요");
                        saveState.setTextColor(ok ? MUTED : BAD_FG);
                    }
                });
            }
        });
    }

    /** back to the car's home screen */
    private void close() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
        finish();
    }

    private AdapterView.OnItemSelectedListener onSelect() {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { changed(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
    }

    private CompoundButton.OnCheckedChangeListener onToggle() {
        return new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { changed(); }
        };
    }

    // ---- service ----

    private void status(JSONObject st) {
        if (!st.optBoolean("ok")) {
            pill.setText("캡처 서비스 응답 없음");
            pill.setTextColor(BAD_FG);
            pill.setBackground(round(BAD_BG, 999));
            recTitle.setText("");
            return;
        }
        boolean rec = st.optBoolean("recording");
        pill.setText(rec ? "녹화 중" : "서비스 동작 중");
        pill.setTextColor(rec ? BAD_FG : GOOD_FG);
        pill.setBackground(round(rec ? BAD_BG : GOOD_BG, 999));
        usbRoot = st.isNull("usb") ? null : st.optString("usb");
        long free = st.optLong("freeBytes", -1);
        if (usbRoot == null) recTitle.setText("USB 없음");
        else if (free >= 0) recTitle.setText(String.format("USB 남은 공간 %.1fGB · 약 %d시간", free / 1e9, free / (10L * 1000 * 1000 * 60)));
    }

    private void testCapture() {
        capButton.setEnabled(false);
        capButton.setText("캡처 중…");
        Actions.capture(this, new Actions.Done() {
            @Override public void done(final JSONObject r) {
                final JSONObject st = Daemon.call("status", 3000);
                final Bitmap b = r.optBoolean("ok") && !st.isNull("usb") ? thumbnail(st.optString("usb") + "/" + r.optString("file")) : null;
                ui.post(new Runnable() {
                    @Override public void run() {
                        capButton.setEnabled(true);
                        capButton.setText("다시 캡처");
                        status(st);
                        if (r.optBoolean("ok")) {
                            capResult.setText("정상 · " + r.optInt("width") + "×" + r.optInt("height") + " · " + (r.optLong("ms") / 1000.0) + "초"
                                    + (r.optBoolean("overlayExcluded") ? " · 플로팅 버튼 제외됨" : "") + "\n" + r.optString("file"));
                            capResult.setTextColor(GOOD_FG);
                            if (b != null) thumb.setImageBitmap(b);
                        } else {
                            capResult.setText("실패 · " + Feedback.error(r.optString("error")));
                            capResult.setTextColor(BAD_FG);
                        }
                    }
                });
            }
        });
    }

    private static Bitmap thumbnail(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) {
            return null;
        }
    }

    private void startDiag() {
        diagButton.setEnabled(false);
        diagState.setText("진단을 시작하는 중…");
        diagState.setTextColor(SUB);
        background(new Runnable() {
            @Override public void run() {
                final JSONObject r = Daemon.call("diag-start " + Settings.cached(MainActivity.this), 5000);
                ui.post(new Runnable() {
                    @Override public void run() {
                        diagButton.setEnabled(true);
                        if (!r.optBoolean("ok")) {
                            diagState.setText("진단을 시작하지 못했습니다 · " + Feedback.error(r.optString("error")));
                            diagState.setTextColor("no_usb".equals(r.optString("error")) ? WARN_FG : BAD_FG);
                            return;
                        }
                        sawDiagRunning = true;
                        showDiag(r);
                        pollDiag();
                    }
                });
            }
        });
    }

    /** asks screencapd every 0.7 s while the diagnostics run */
    private void pollDiag() {
        if (diagPolling) return;
        diagPolling = true;
        ui.post(new Runnable() {
            @Override public void run() {
                final Runnable self = this;
                background(new Runnable() {
                    @Override public void run() {
                        final JSONObject st = Daemon.call("diag-status", 3000);
                        ui.post(new Runnable() {
                            @Override public void run() {
                                if (!diagPolling) return;
                                if (showDiag(st)) ui.postDelayed(self, 700);
                                else diagPolling = false;
                            }
                        });
                    }
                });
            }
        });
    }

    /** draws the diagnostics' state; true while it should keep watching */
    private boolean showDiag(JSONObject st) {
        String state = st.optString("state");
        org.json.JSONArray stages = st.optJSONArray("stages");
        int total = st.optInt("total", stages == null ? 0 : stages.length());
        int idx = st.optInt("stage", -1);
        long sec = st.optLong("elapsed");
        if ("running".equals(state)) {
            sawDiagRunning = true;
            diagButton.setEnabled(false);
            diagButton.setText("진단 중…");
            diagPanel.setVisibility(View.VISIBLE);
            diagTitle.setTextColor(TEXT);
            diagTitle.setText("진단 중 · " + (idx < 0 ? "준비" : (idx + 1) + "/" + total + " " + stageName(stages, idx)));
            diagProgress.setMax(Math.max(total, 1));
            diagProgress.setProgress(Math.max(idx, 0));
            drawSteps(stages, idx);
            String now = st.optString("detail");
            diagDetail.setText((now.isEmpty() ? "" : "지금: " + now + "\n") + sec + "초 경과 · USB를 뽑지 마세요");
            diagDetail.setTextColor(SUB);
            diagResult.setVisibility(View.GONE);
            diagState.setText("진단이 끝나면 결과가 아래에 나옵니다");
            diagState.setTextColor(SUB);
            return true;
        }
        diagButton.setEnabled(true);
        diagButton.setText("진단 실행");
        if ("done".equals(state) || "error".equals(state)) {
            diagPanel.setVisibility(View.VISIBLE);
            diagProgress.setMax(Math.max(total, 1));
            diagProgress.setProgress("done".equals(state) ? total : Math.max(idx, 0));
            drawSteps(stages, "done".equals(state) ? total : idx);
            int fails = 0;
            StringBuilder lines = new StringBuilder();
            for (int i = 0; stages != null && i < stages.length(); i++) {
                JSONObject g = stages.optJSONObject(i);
                if (g == null || g.isNull("result")) continue;
                boolean ok = "ok".equals(g.optString("result"));
                if (!ok) fails++;
                String note = g.optString("note");
                if (!note.isEmpty()) lines.append(ok ? "✓ " : "✗ ").append(g.optString("name")).append("  ").append(note).append('\n');
            }
            diagResult.setText(lines.toString().trim());
            diagResult.setVisibility(lines.length() > 0 ? View.VISIBLE : View.GONE);
            String dir = st.optString("dir");
            if ("error".equals(state)) {
                diagTitle.setTextColor(WARN_FG);
                boolean removed = "usb_removed".equals(st.optString("error")) || "no_usb".equals(st.optString("error"));
                diagTitle.setText(removed ? "USB가 분리되어 진단을 멈췄습니다" : "진단을 멈췄습니다");
                diagDetail.setText(removed ? "USB를 다시 꽂고 진단 실행을 눌러 주세요." : "다시 실행해 주세요. 계속 멈추면 오른쪽 QR로 알려 주세요.");
            } else if (sawDiagRunning) {
                diagTitle.setTextColor(fails == 0 ? GOOD_FG : WARN_FG);
                diagTitle.setText((fails == 0 ? "진단 완료 · " : "진단 완료 · 확인할 항목 " + fails + "개 · ") + sec + "초");
                diagDetail.setText("USB의 5W-Screencap/" + dir + " 폴더에 저장했습니다. 문제를 알릴 때는 그 안의 SUMMARY.txt를 첨부해 주세요.");
            } else {
                diagTitle.setTextColor(SUB);
                diagTitle.setText("지난 진단 결과" + (fails > 0 ? " · 확인할 항목 " + fails + "개" : ""));
                diagDetail.setText("USB의 5W-Screencap/" + dir + " (새로 진단하려면 진단 실행을 누르세요)");
            }
            diagDetail.setTextColor(SUB);
            diagState.setText("약 20초 · USB의 5W-Screencap/diag 에 저장됩니다. 캡처 이미지나 개인 정보는 담지 않습니다.");
            diagState.setTextColor(MUTED);
            return false;
        }
        diagPanel.setVisibility(View.GONE);   // idle, or screencapd not answering
        return false;
    }

    /** ✓ finished (✗ if it found a problem), ● running, ○ waiting; four to a row */
    private void drawSteps(org.json.JSONArray stages, int current) {
        int n = stages == null ? 0 : stages.length();
        if (stepViews.size() != n) {
            stepGrid.removeAllViews();
            stepViews.clear();
            LinearLayout line = null;
            for (int i = 0; i < n; i++) {
                if (i % 4 == 0) {
                    line = horizontal();
                    stepGrid.addView(line, margins(i == 0 ? 0 : dp(3)));
                }
                TextView t = text("", 14, MUTED);
                t.setSingleLine(true);
                line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                stepViews.add(t);
            }
        }
        for (int i = 0; i < n; i++) {
            JSONObject g = stages.optJSONObject(i);
            String name = g == null ? "" : g.optString("name");
            String result = g == null || g.isNull("result") ? null : g.optString("result");
            TextView t = stepViews.get(i);
            if (result != null && i != current) {
                boolean ok = "ok".equals(result);
                t.setText((ok ? "✓ " : "✗ ") + name);
                t.setTextColor(ok ? GOOD_FG : BAD_FG);
            } else if (i == current) {
                t.setText("● " + name);
                t.setTextColor(ACCENT);
            } else {
                t.setText("○ " + name);
                t.setTextColor(MUTED);
            }
        }
    }

    private static String stageName(org.json.JSONArray stages, int i) {
        JSONObject g = stages == null ? null : stages.optJSONObject(i);
        return g == null ? "" : g.optString("name");
    }

    // ---- small view helpers ----

    private static void background(Runnable r) {
        new Thread(r, "launch").start();
    }

    private String[] keyTitles() {
        String[] t = new String[Choices.KEYS.length + 1];
        t[0] = Choices.OFF_LABEL;
        for (int i = 0; i < Choices.KEYS.length; i++) t[i + 1] = Choices.KEYS[i][1];
        return t;
    }

    private ArrayAdapter<String> adapter(String[] items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                return style((TextView) super.getView(pos, convert, parent), false);
            }
            @Override public View getDropDownView(int pos, View convert, ViewGroup parent) {
                return style((TextView) super.getDropDownView(pos, convert, parent), true);
            }
        };
    }

    private TextView style(TextView t, boolean dropDown) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        t.setTextColor(TEXT);
        t.setSingleLine(true);
        t.setPadding(dp(12), dp(dropDown ? 12 : 8), dp(12), dp(dropDown ? 12 : 8));
        if (dropDown) t.setBackgroundColor(0xFF252931);
        return t;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this, Spinner.MODE_DROPDOWN);
        s.setAdapter(adapter(items));
        s.setBackground(round(0xFF252931, 10));
        s.setMinimumHeight(dp(44));
        return s;
    }

    private LinearLayout card(String title, String sub) {
        LinearLayout c = vertical();
        c.setPadding(dp(18), dp(14), dp(18), dp(14));
        c.setBackground(round(CARD, 18));
        LinearLayout h = horizontal();
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t);
        TextView s = text(sub.trim(), 14, MUTED);
        s.setPadding(dp(8), 0, 0, 0);
        h.addView(s);
        c.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private View labeled(String label, View a, View b) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = text(label, 16, SUB);
        r.addView(l, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (b != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f);
            lp.leftMargin = dp(8);
            r.addView(b, lp);
        }
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private View switchRow(String label, Switch s) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(text(label, 16, SUB), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(s);
        r.setMinimumHeight(dp(40));
        return r;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        b.setTextColor(primary ? 0xFFFFFFFF : TEXT);
        b.setAllCaps(false);
        b.setBackground(round(primary ? ACCENT : 0xFF252931, 10));
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setMinHeight(dp(42));
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams cardLp(int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);   // the row is as tall as its taller card
        p.leftMargin = left / 2;
        p.rightMargin = right / 2;
        return p;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable leftLine() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(BG);
        d.setStroke(1, LINE);
        return d;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
