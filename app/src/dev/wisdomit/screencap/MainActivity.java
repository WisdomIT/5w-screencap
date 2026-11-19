package dev.wisdomit.screencap;

import android.app.Activity;
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

    private TextView pill, saveState, buttonHint, recTitle, capResult, diagState, fwText;
    private ImageView thumb;
    private Button capButton, diagButton;
    private Spinner capKey, capPress, recKey, recPress, quality;
    private Switch block, autoRecord, floating;
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
                final JSONObject s = Settings.load(MainActivity.this);
                final JSONObject st = Daemon.call("status", 3000);
                final CarInfo info = CarInfo.read(version);
                ui.post(new Runnable() {
                    @Override public void run() {
                        fill(s);
                        status(st);
                        qr.setText(info.issueUrl());
                        fwText.setText(info.firmware + "\nHMG-API " + info.hmgapi + " · 5W Screencap " + version);
                    }
                });
            }
        });
    }

    // ---- layout ----

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(TOP_INSET_DP), 0, 0);

        LinearLayout left = vertical();
        left.setPadding(dp(28), dp(14), dp(22), dp(18));
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout head = horizontal();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("5W Screencap", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title);
        head.addView(text("  v" + version, 16, MUTED));
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        pill = text("확인 중", 16, SUB);
        pill.setPadding(dp(14), dp(5), dp(14), dp(5));
        pill.setBackground(round(CARD, 999));
        head.addView(pill);
        left.addView(head);

        LinearLayout row1 = horizontal(), row2 = horizontal();
        row1.addView(captureCard(), cardLp(0, dp(12)));
        row1.addView(buttonCard(), cardLp(dp(12), 0));
        row2.addView(recordCard(), cardLp(0, dp(12)));
        row2.addView(diagCard(), cardLp(dp(12), 0));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        rowLp.topMargin = dp(12);
        left.addView(row1, rowLp);
        LinearLayout.LayoutParams rowLp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        rowLp2.topMargin = dp(12);
        left.addView(row2, rowLp2);

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
        capPress = spinner(Choices.PRESSES);
        c.addView(labeled("캡처", capKey, capPress));
        recKey = spinner(keyTitles());
        recPress = spinner(Choices.PRESSES);
        c.addView(labeled("녹화", recKey, recPress));
        block = new Switch(this);
        c.addView(switchRow("버튼의 원래 기능 막기", block));
        buttonHint = text("", 14, MUTED);
        c.addView(buttonHint);
        saveState = text("", 13, MUTED);
        c.addView(saveState);
        AdapterView.OnItemSelectedListener keyChanged = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Spinner press = p == capKey ? capPress : recPress;
                setPresses(press, pos - 1, press.getSelectedItemPosition());
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        capKey.setOnItemSelectedListener(keyChanged);
        recKey.setOnItemSelectedListener(keyChanged);
        capPress.setOnItemSelectedListener(onSelect());
        recPress.setOnItemSelectedListener(onSelect());
        block.setOnCheckedChangeListener(onToggle());
        return c;
    }

    private View recordCard() {
        LinearLayout c = card("녹화", "");
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
        diagButton = button("진단 실행", false);
        diagButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runDiag(); }
        });
        c.addView(diagButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        diagState = text("약 10초 · USB의 5W-Screencap/diag 에 저장됩니다. 캡처 이미지나 개인 정보는 담지 않습니다.", 14, MUTED);
        c.addView(diagState, margins(dp(8)));
        return c;
    }

    // ---- settings <-> controls ----

    private void fill(JSONObject s) {
        loading = true;
        Choices.Pick cp = Choices.pick(s.optString(Settings.CAPTURE)), rp = Choices.pick(s.optString(Settings.RECORD));
        capKey.setSelection(cp.key + 1, false);
        setPresses(capPress, cp.key, cp.press);
        recKey.setSelection(rp.key + 1, false);
        setPresses(recPress, rp.key, rp.press);
        block.setChecked(cp.key >= 0 && !Choices.isStar(cp.key) && cp.press == Choices.PRESS_SHORT ? cp.replace
                : rp.key >= 0 && !Choices.isStar(rp.key) && rp.press == Choices.PRESS_SHORT ? rp.replace : true);
        String q = s.optString(Settings.QUALITY, "standard");
        for (int i = 0; i < QUALITY_VALUES.length; i++) if (QUALITY_VALUES[i].equals(q)) quality.setSelection(i, false);
        autoRecord.setChecked(s.optBoolean(Settings.AUTO_RECORD));
        floating.setChecked(s.optBoolean(Settings.FLOATING, true));
        hint();
        ui.post(new Runnable() {   // spinners report their first selection after layout
            @Override public void run() { loading = false; }
        });
    }

    private void setPresses(Spinner press, int key, int selected) {
        String[] items = Choices.pressesFor(key);
        ArrayAdapter<String> a = adapter(items);
        press.setAdapter(a);
        press.setSelection(Math.min(selected, items.length - 1), false);
        press.setEnabled(key >= 0);
    }

    private Choices.Pick pickOf(Spinner key, Spinner press) {
        return new Choices.Pick(key.getSelectedItemPosition() - 1, press.getSelectedItemPosition(), block.isChecked());
    }

    private void hint() {
        Choices.Pick c = pickOf(capKey, capPress), r = pickOf(recKey, recPress);
        boolean dbl = c.press == Choices.PRESS_DOUBLE && c.key >= 0 && !Choices.isStar(c.key)
                || r.press == Choices.PRESS_DOUBLE && r.key >= 0 && !Choices.isStar(r.key);
        boolean star = c.key >= 0 && Choices.isStar(c.key) && c.press == Choices.PRESS_SHORT
                || r.key >= 0 && Choices.isStar(r.key) && r.press == Choices.PRESS_SHORT;
        if (dbl) {
            block.setChecked(true);
            block.setEnabled(false);
        } else {
            block.setEnabled(true);
        }
        String clash = Choices.clash(Choices.value(c), Choices.value(r));
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
        final String capture = Choices.value(pickOf(capKey, capPress)), record = Choices.value(pickOf(recKey, recPress));
        if (Choices.clash(capture, record) != null) {
            saveState.setText("저장하지 않음");
            return;
        }
        final JSONObject v = new JSONObject();
        try {
            v.put(Settings.CAPTURE, capture).put(Settings.RECORD, record)
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

    private void runDiag() {
        diagButton.setEnabled(false);
        diagButton.setText("진단 중…");
        diagState.setText("평소 10초 안에 끝납니다. 화면을 끄지 마세요.");
        diagState.setTextColor(SUB);
        background(new Runnable() {
            @Override public void run() {
                final JSONObject r = Daemon.call("diag " + Settings.cached(MainActivity.this), 60000);
                ui.post(new Runnable() {
                    @Override public void run() {
                        diagButton.setEnabled(true);
                        diagButton.setText("다시 실행");
                        if (r.optBoolean("ok")) {
                            diagState.setText("완료 · USB의 5W-Screencap/" + r.optString("dir") + "/SUMMARY.txt");
                            diagState.setTextColor(GOOD_FG);
                        } else {
                            diagState.setText("실패 · " + Feedback.error(r.optString("error")));
                            diagState.setTextColor(BAD_FG);
                        }
                    }
                });
            }
        });
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
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(adapter(items));
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
        TextView s = text(sub.isEmpty() ? "" : "  " + sub, 14, MUTED);
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
        if (b != null) r.addView(b, new LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.WRAP_CONTENT));
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
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
