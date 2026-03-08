// Author: 7ANG0N1N3 — https://github.com/7ang0n1n3/lg-signage
package com.signage.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

public class MainActivity extends Activity {

    private static final String PREF_FILE = "prefs";
    private static final String PREF_MODE = "display_mode";
    private static final String ACTION_SETTINGS_CHANGED = "com.signage.app.SETTINGS_CHANGED";

    private WebView webView;
    private String  loadedUrl = "";

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String url = buildUrl();
            if (!url.equals(loadedUrl)) {
                loadedUrl = url;
                webView.loadUrl(url);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();

        startService(new Intent(this, WebServerService.class));
        startService(new Intent(this, SchedulerService.class));

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applyImmersive();
            }
        });

        registerReceiver(settingsReceiver, new IntentFilter(ACTION_SETTINGS_CHANGED));

        loadCurrentMode();

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        String url = buildUrl();
        if (!url.equals(loadedUrl)) {
            loadedUrl = url;
            webView.loadUrl(url);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(settingsReceiver);
        webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Disabled for kiosk mode
    }

    private void loadCurrentMode() {
        String url = buildUrl();
        loadedUrl = url;
        webView.loadUrl(url);
    }

    private String buildUrl() {
        String mode = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            .getString(PREF_MODE, "worldclock");
        if ("marquee".equals(mode)) {
            return buildMarqueeUrl();
        }
        if ("crime_eyes".equals(mode)) {
            String gif = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
                .getString("crime_eyes_gif", "evil-eye-1.gif");
            return "file:///android_asset/crime_eyes.html?gif=" + gif;
        }
        if ("blank".equals(mode)) {
            return "file:///android_asset/blank.html";
        }
        return "file:///android_asset/" + mode + ".html";
    }

    private String buildMarqueeUrl() {
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String  text       = prefs.getString ("marquee_text",      "HELLO WORLD");
        int     colorInt   = prefs.getInt    ("marquee_color",     0xFFFF0000);
        String  color      = String.format("#%06X", colorInt & 0xFFFFFF);
        int     speed      = prefs.getInt    ("marquee_speed",     3);
        String  dir        = prefs.getString ("marquee_direction", "left");
        boolean blink      = prefs.getBoolean("marquee_blink",     false);
        String  bstyle     = prefs.getString ("border_style",      "snake");
        String  bcolormode = prefs.getString ("border_color_mode", "single");
        int     bcolorInt  = prefs.getInt    ("border_color",      0xFFFF0000);
        String  bcolor     = String.format("#%06X", bcolorInt & 0xFFFFFF);
        String  bshape     = prefs.getString ("border_shape",      "dot");
        try {
            String encText   = java.net.URLEncoder.encode(text,   "UTF-8").replace("+", "%20");
            String encColor  = java.net.URLEncoder.encode(color,  "UTF-8");
            String encBcolor = java.net.URLEncoder.encode(bcolor, "UTF-8");
            return "file:///android_asset/marquee.html"
                + "?text="       + encText
                + "&color="      + encColor
                + "&speed="      + speed
                + "&direction="  + dir
                + "&blink="      + blink
                + "&bstyle="     + bstyle
                + "&bcolormode=" + bcolormode
                + "&bcolor="     + encBcolor
                + "&bshape="     + bshape;
        } catch (java.io.UnsupportedEncodingException e) {
            return "file:///android_asset/marquee.html"
                + "?text=HELLO%20WORLD&color=%23FF0000&speed=3&direction=left"
                + "&blink=false&bstyle=snake&bcolormode=single&bcolor=%23FF0000&bshape=dot";
        }
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
}
