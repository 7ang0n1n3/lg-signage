// Author: 7ANG0N1N3 — https://github.com/7ang0n1n3/lg-signage
package com.signage.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private static final String PREF_FILE = "prefs";
    private static final String PREF_MODE = "display_mode";

    private static final int[] PALETTE = {
        0xFFFF0000, 0xFFFF8800, 0xFFFFFF00,
        0xFF00FF00, 0xFF00FFFF, 0xFF0088FF, 0xFFFFFFFF
    };

    // Display mode rows
    private View rowClock, rowLightning, rowFire, rowMarquee, panelMarquee, rowCrimeEyes, panelCrimeEyes, rowBlank;
    private TextView checkClock, checkLightning, checkFire, checkMarquee, checkCrimeEyes, checkBlank;
    private Button btnEye1, btnEye2, btnEye3;
    private String crimeEyesGif = "evil-eye-1.gif";

    // Text / color / speed / direction
    private EditText etMarqueeText;
    private Button btnSpeedSlow, btnSpeedMed, btnSpeedFast;
    private Button btnDirLeft, btnDirRight, btnDirUp, btnDirDown;
    private View[]  colorViews      = new View[PALETTE.length];
    private int     selectedColorIdx = 0;
    private int     marqueeColor     = 0xFFFF0000;
    private int     marqueeSpeed     = 3;
    private String  marqueeDirection = "left";
    private boolean marqueeBlink     = false;

    // Border style
    private Button btnBstyleOff, btnBstyleOn, btnBstyleDotted, btnBstyleSnake;
    private String  borderStyle = "snake";

    // Border color
    private View[]  borderColorViews    = new View[PALETTE.length];
    private Button  btnBrainbow;
    private int     selectedBorderColorIdx = 0;
    private int     borderColor            = 0xFFFF0000;
    private String  borderColorMode        = "single";

    // Border shape
    private Button btnBshapeDot, btnBshapeStar, btnBshapeSquare;
    private String borderShape = "dot";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);

        Switch   switchAutostart = findViewById(R.id.switch_autostart);
        TextView tvVersion       = findViewById(R.id.tv_version);
        Button   btnClose        = findViewById(R.id.btn_close);

        rowClock     = findViewById(R.id.row_clock);
        rowLightning = findViewById(R.id.row_lightning);
        rowFire      = findViewById(R.id.row_fire);
        rowMarquee   = findViewById(R.id.row_marquee);
        panelMarquee = findViewById(R.id.panel_marquee);
        rowCrimeEyes   = findViewById(R.id.row_crime_eyes);
        panelCrimeEyes = findViewById(R.id.panel_crime_eyes);
        rowBlank       = findViewById(R.id.row_blank);
        btnEye1        = findViewById(R.id.btn_eye1);
        btnEye2        = findViewById(R.id.btn_eye2);
        btnEye3        = findViewById(R.id.btn_eye3);

        checkClock     = findViewById(R.id.check_clock);
        checkLightning = findViewById(R.id.check_lightning);
        checkFire      = findViewById(R.id.check_fire);
        checkMarquee   = findViewById(R.id.check_marquee);
        checkCrimeEyes = findViewById(R.id.check_crime_eyes);
        checkBlank     = findViewById(R.id.check_blank);

        etMarqueeText = findViewById(R.id.et_marquee_text);
        btnSpeedSlow  = findViewById(R.id.btn_speed_slow);
        btnSpeedMed   = findViewById(R.id.btn_speed_med);
        btnSpeedFast  = findViewById(R.id.btn_speed_fast);
        btnDirLeft    = findViewById(R.id.btn_dir_left);
        btnDirRight   = findViewById(R.id.btn_dir_right);
        btnDirUp      = findViewById(R.id.btn_dir_up);
        btnDirDown    = findViewById(R.id.btn_dir_down);

        btnBstyleOff    = findViewById(R.id.btn_bstyle_off);
        btnBstyleOn     = findViewById(R.id.btn_bstyle_on);
        btnBstyleDotted = findViewById(R.id.btn_bstyle_dotted);
        btnBstyleSnake  = findViewById(R.id.btn_bstyle_snake);

        btnBrainbow     = findViewById(R.id.btn_brainbow);

        btnBshapeDot    = findViewById(R.id.btn_bshape_dot);
        btnBshapeStar   = findViewById(R.id.btn_bshape_star);
        btnBshapeSquare = findViewById(R.id.btn_bshape_square);

        try {
            tvVersion.setText(getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("—");
        }

        switchAutostart.setChecked(isAutostartEnabled());
        switchAutostart.setOnCheckedChangeListener((v, checked) -> setAutostartEnabled(checked));

        // Load saved prefs
        marqueeColor     = prefs.getInt   ("marquee_color",      0xFFFF0000);
        marqueeSpeed     = prefs.getInt   ("marquee_speed",      3);
        marqueeDirection = prefs.getString("marquee_direction",  "left");
        marqueeBlink     = prefs.getBoolean("marquee_blink",     false);
        borderStyle      = prefs.getString("border_style",       "snake");
        borderColorMode  = prefs.getString("border_color_mode",  "single");
        borderColor      = prefs.getInt   ("border_color",       0xFFFF0000);
        borderShape      = prefs.getString("border_shape",       "dot");
        etMarqueeText.setText(prefs.getString("marquee_text", "HELLO WORLD"));

        // Find palette index for text color
        for (int i = 0; i < PALETTE.length; i++)
            if (PALETTE[i] == marqueeColor) { selectedColorIdx = i; break; }

        // Find palette index for border color
        for (int i = 0; i < PALETTE.length; i++)
            if (PALETTE[i] == borderColor) { selectedBorderColorIdx = i; break; }

        setupColorPalette();
        setupBorderColorPalette();
        updateSpeedUI();
        updateDirUI();
        updateBorderStyleUI();
        updateBorderColorUI();
        updateBorderShapeUI();

        // Speed
        btnSpeedSlow.setOnClickListener(v -> { marqueeSpeed = 1; updateSpeedUI(); saveMarqueePrefs(); });
        btnSpeedMed .setOnClickListener(v -> { marqueeSpeed = 3; updateSpeedUI(); saveMarqueePrefs(); });
        btnSpeedFast.setOnClickListener(v -> { marqueeSpeed = 7; updateSpeedUI(); saveMarqueePrefs(); });

        // Direction
        btnDirLeft .setOnClickListener(v -> { marqueeDirection = "left";  updateDirUI(); saveMarqueePrefs(); });
        btnDirRight.setOnClickListener(v -> { marqueeDirection = "right"; updateDirUI(); saveMarqueePrefs(); });
        btnDirUp   .setOnClickListener(v -> { marqueeDirection = "up";    updateDirUI(); saveMarqueePrefs(); });
        btnDirDown .setOnClickListener(v -> { marqueeDirection = "down";  updateDirUI(); saveMarqueePrefs(); });

        // Blink
        Switch swBlink = findViewById(R.id.sw_marquee_blink);
        swBlink.setChecked(marqueeBlink);
        swBlink.setOnCheckedChangeListener((v, c) -> { marqueeBlink = c; saveMarqueePrefs(); });

        // Border style
        btnBstyleOff   .setOnClickListener(v -> { borderStyle = "off";    updateBorderStyleUI(); saveMarqueePrefs(); });
        btnBstyleOn    .setOnClickListener(v -> { borderStyle = "on";     updateBorderStyleUI(); saveMarqueePrefs(); });
        btnBstyleDotted.setOnClickListener(v -> { borderStyle = "dotted"; updateBorderStyleUI(); saveMarqueePrefs(); });
        btnBstyleSnake .setOnClickListener(v -> { borderStyle = "snake";  updateBorderStyleUI(); saveMarqueePrefs(); });

        // Rainbow toggle
        btnBrainbow.setOnClickListener(v -> {
            borderColorMode = "rainbow".equals(borderColorMode) ? "single" : "rainbow";
            updateBorderColorUI();
            saveMarqueePrefs();
        });

        // Border shape
        btnBshapeDot   .setOnClickListener(v -> { borderShape = "dot";    updateBorderShapeUI(); saveMarqueePrefs(); });
        btnBshapeStar  .setOnClickListener(v -> { borderShape = "star";   updateBorderShapeUI(); saveMarqueePrefs(); });
        btnBshapeSquare.setOnClickListener(v -> { borderShape = "square"; updateBorderShapeUI(); saveMarqueePrefs(); });

        // Save text on focus change
        etMarqueeText.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) saveMarqueePrefs(); });

        updateDisplayUI(prefs.getString(PREF_MODE, "worldclock"));

        // Load crime eyes pref
        crimeEyesGif = prefs.getString("crime_eyes_gif", "evil-eye-1.gif");
        updateEyeGifUI();

        btnEye1.setOnClickListener(v -> { crimeEyesGif = "evil-eye-1.gif"; saveEyeGifPref(); updateEyeGifUI(); });
        btnEye2.setOnClickListener(v -> { crimeEyesGif = "evil-eye-2.gif"; saveEyeGifPref(); updateEyeGifUI(); });
        btnEye3.setOnClickListener(v -> { crimeEyesGif = "evil-eye-3.gif"; saveEyeGifPref(); updateEyeGifUI(); });

        rowClock    .setOnClickListener(v -> setMode("worldclock"));
        rowLightning.setOnClickListener(v -> setMode("lightning"));
        rowFire     .setOnClickListener(v -> setMode("fire"));
        rowMarquee  .setOnClickListener(v -> setMode("marquee"));
        rowCrimeEyes.setOnClickListener(v -> setMode("crime_eyes"));
        rowBlank    .setOnClickListener(v -> setMode("blank"));

        btnClose.setOnClickListener(v -> finish());
    }

    // ---- Color palettes ---------------------------------------------------

    private void setupColorPalette() {
        LinearLayout container = findViewById(R.id.ll_colors);
        buildPalette(container, colorViews, idx -> {
            selectedColorIdx = idx;
            marqueeColor = PALETTE[idx];
            updateTextColorUI();
            saveMarqueePrefs();
        });
        updateTextColorUI();
    }

    private void setupBorderColorPalette() {
        LinearLayout container = findViewById(R.id.ll_border_colors);
        buildPalette(container, borderColorViews, idx -> {
            selectedBorderColorIdx = idx;
            borderColor = PALETTE[idx];
            borderColorMode = "single";
            updateBorderColorUI();
            saveMarqueePrefs();
        });
    }

    interface OnColorPick { void pick(int idx); }

    private void buildPalette(LinearLayout container, View[] views, OnColorPick cb) {
        container.removeAllViews();
        float dp  = getResources().getDisplayMetrics().density;
        int   gap = (int)(5 * dp);
        for (int i = 0; i < PALETTE.length; i++) {
            final int idx = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int)(40 * dp), 1);
            if (i < PALETTE.length - 1) lp.setMarginEnd(gap);
            View v = new View(this);
            v.setLayoutParams(lp);
            container.addView(v);
            views[i] = v;
            v.setOnClickListener(vv -> cb.pick(idx));
        }
    }

    private void updateTextColorUI() {
        applyPaletteSelection(colorViews, selectedColorIdx);
    }

    private void updateBorderColorUI() {
        if ("rainbow".equals(borderColorMode)) {
            // Highlight rainbow button, dim all swatches
            btnBrainbow.setBackgroundColor(0xFF2d3a5a);
            btnBrainbow.setTextColor(0xFFa78bfa);
            for (View v : borderColorViews) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(PALETTE[borderColorViews[0] == v ? 0 :
                    java.util.Arrays.asList(borderColorViews).indexOf(v)]);
                v.setBackground(gd);
                v.setAlpha(0.3f);
            }
            // Properly set colors since indexOf above is clunky
            for (int i = 0; i < borderColorViews.length; i++) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(PALETTE[i]);
                borderColorViews[i].setBackground(gd);
                borderColorViews[i].setAlpha(0.3f);
            }
        } else {
            btnBrainbow.setBackgroundColor(0xFF2a2a2a);
            btnBrainbow.setTextColor(0xFF888888);
            applyPaletteSelection(borderColorViews, selectedBorderColorIdx);
        }
    }

    private void applyPaletteSelection(View[] views, int selectedIdx) {
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < views.length; i++) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(PALETTE[i]);
            if (i == selectedIdx) gd.setStroke((int)(3 * dp), 0xFFFFFFFF);
            views[i].setBackground(gd);
            views[i].setAlpha(i == selectedIdx ? 1.0f : 0.45f);
        }
    }

    // ---- Speed / Direction / Border button UI -----------------------------

    private void updateSpeedUI() {
        int[]    speeds = {1, 3, 7};
        Button[] btns   = {btnSpeedSlow, btnSpeedMed, btnSpeedFast};
        for (int i = 0; i < btns.length; i++) {
            boolean on = speeds[i] == marqueeSpeed;
            btns[i].setBackgroundColor(on ? 0xFF2d3a5a : 0xFF2a2a2a);
            btns[i].setTextColor(on ? 0xFFa78bfa : 0xFF888888);
        }
    }

    private void updateDirUI() {
        String[] dirs = {"left", "right", "up", "down"};
        Button[] btns = {btnDirLeft, btnDirRight, btnDirUp, btnDirDown};
        for (int i = 0; i < btns.length; i++) {
            boolean on = dirs[i].equals(marqueeDirection);
            btns[i].setBackgroundColor(on ? 0xFF2d3a5a : 0xFF2a2a2a);
            btns[i].setTextColor(on ? 0xFFa78bfa : 0xFF888888);
        }
    }

    private void updateBorderStyleUI() {
        String[] styles = {"off", "on", "dotted", "snake"};
        Button[] btns   = {btnBstyleOff, btnBstyleOn, btnBstyleDotted, btnBstyleSnake};
        for (int i = 0; i < btns.length; i++) {
            boolean on = styles[i].equals(borderStyle);
            btns[i].setBackgroundColor(on ? 0xFF2d3a5a : 0xFF2a2a2a);
            btns[i].setTextColor(on ? 0xFFa78bfa : 0xFF888888);
        }
    }

    private void updateBorderShapeUI() {
        String[] shapes = {"dot", "star", "square"};
        Button[] btns   = {btnBshapeDot, btnBshapeStar, btnBshapeSquare};
        for (int i = 0; i < btns.length; i++) {
            boolean on = shapes[i].equals(borderShape);
            btns[i].setBackgroundColor(on ? 0xFF2d3a5a : 0xFF2a2a2a);
            btns[i].setTextColor(on ? 0xFFa78bfa : 0xFF888888);
        }
    }

    // ---- Mode selection ---------------------------------------------------

    private void setMode(String mode) {
        prefs.edit().putString(PREF_MODE, mode).apply();
        updateDisplayUI(mode);
        sendBroadcast(new android.content.Intent("com.signage.app.SETTINGS_CHANGED"));
    }

    private void updateDisplayUI(String mode) {
        checkClock    .setVisibility("worldclock".equals(mode) ? View.VISIBLE : View.GONE);
        checkLightning.setVisibility("lightning" .equals(mode) ? View.VISIBLE : View.GONE);
        checkFire     .setVisibility("fire"      .equals(mode) ? View.VISIBLE : View.GONE);
        checkMarquee  .setVisibility("marquee"   .equals(mode) ? View.VISIBLE : View.GONE);
        checkCrimeEyes.setVisibility("crime_eyes".equals(mode) ? View.VISIBLE : View.GONE);
        checkBlank    .setVisibility("blank"     .equals(mode) ? View.VISIBLE : View.GONE);
        rowClock    .setBackgroundColor("worldclock".equals(mode) ? 0xFF1e2e2a : 0xFF1e1e1e);
        rowLightning.setBackgroundColor("lightning" .equals(mode) ? 0xFF2a2518 : 0xFF1e1e1e);
        rowFire     .setBackgroundColor("fire"      .equals(mode) ? 0xFF2e1e10 : 0xFF1e1e1e);
        rowMarquee  .setBackgroundColor("marquee"   .equals(mode) ? 0xFF1e1e30 : 0xFF1e1e1e);
        rowCrimeEyes.setBackgroundColor("crime_eyes".equals(mode) ? 0xFF2e1a1a : 0xFF1e1e1e);
        rowBlank    .setBackgroundColor("blank"     .equals(mode) ? 0xFF222222 : 0xFF1e1e1e);
        panelMarquee  .setVisibility("marquee"   .equals(mode) ? View.VISIBLE : View.GONE);
        panelCrimeEyes.setVisibility("crime_eyes".equals(mode) ? View.VISIBLE : View.GONE);
    }

    private void updateEyeGifUI() {
        String[] gifs = {"evil-eye-1.gif", "evil-eye-2.gif", "evil-eye-3.gif"};
        Button[] btns = {btnEye1, btnEye2, btnEye3};
        for (int i = 0; i < btns.length; i++) {
            boolean on = gifs[i].equals(crimeEyesGif);
            btns[i].setBackgroundColor(on ? 0xFF2d3a5a : 0xFF2a2a2a);
            btns[i].setTextColor(on ? 0xFFa78bfa : 0xFF888888);
        }
    }

    private void saveEyeGifPref() {
        prefs.edit().putString("crime_eyes_gif", crimeEyesGif).apply();
        android.content.Intent intent = new android.content.Intent("com.signage.app.SETTINGS_CHANGED");
        sendBroadcast(intent);
    }

    // ---- Persistence ------------------------------------------------------

    private void saveMarqueePrefs() {
        prefs.edit()
            .putString ("marquee_text",      etMarqueeText.getText().toString())
            .putInt    ("marquee_color",      marqueeColor)
            .putInt    ("marquee_speed",      marqueeSpeed)
            .putString ("marquee_direction",  marqueeDirection)
            .putBoolean("marquee_blink",      marqueeBlink)
            .putString ("border_style",       borderStyle)
            .putString ("border_color_mode",  borderColorMode)
            .putInt    ("border_color",       borderColor)
            .putString ("border_shape",       borderShape)
            .apply();
    }

    // ---- Autostart --------------------------------------------------------

    private boolean isAutostartEnabled() {
        ComponentName receiver = new ComponentName(this, BootReceiver.class);
        int state = getPackageManager().getComponentEnabledSetting(receiver);
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setAutostartEnabled(boolean enabled) {
        ComponentName receiver = new ComponentName(this, BootReceiver.class);
        int state = enabled
            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(
            receiver, state, PackageManager.DONT_KILL_APP);
    }
}
