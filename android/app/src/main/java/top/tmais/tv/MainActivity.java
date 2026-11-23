package top.tmais.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cadastro nativo: o código aparece sem depender do JavaScript da WebView. */
public final class MainActivity extends Activity {
    private static final String PREF = "tmais_tv_settings";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();
    private SharedPreferences prefs;
    private WebView browser;
    private TextView status, pairCode;
    private Button retry;
    private boolean paired = false;
    private boolean registering = false;
    private int generation = 0;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        if (!prefs.contains("id")) {
            prefs.edit().putString("id", randomHex(12)).putString("token", randomHex(32)).apply();
        }
        if (prefs.getString("server", "").isEmpty()) showSetup(); else startPairing();
    }
    private String randomHex(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return hex.toString();
    }
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(5894 |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(8, 13, 8, 13);
        return view;
    }
    private LinearLayout layout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(70, 18, 70, 18);
        root.setBackgroundColor(Color.rgb(9, 39, 85));
        return root;
    }
    private void title(LinearLayout root) {
        TextView t = label("▣ TMAIS TV", 36, Color.WHITE);
        t.setTypeface(null, Typeface.BOLD);
        root.addView(t);
    }
    private void clearBrowser() {
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
    }
    private void showSetup() {
        generation++;
        paired = false;
        registering = false;
        clearBrowser();
        LinearLayout root = layout();
        title(root);
        root.addView(label("Endereço HTTPS do painel PHP", 18, 0xffcbdaf3));
        EditText address = new EditText(this);
        address.setText(prefs.getString("server", ""));
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setTextSize(20);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0xffc3d3e8);
        address.setHint("https://orgsite.com.br/tvtmais");
        root.addView(address, new LinearLayout.LayoutParams(-1, -2));
        TextView error = label("", 15, 0xffffb4b4);
        Button connect = new Button(this);
        connect.setText("CONECTAR TV");
        connect.setOnClickListener(v -> {
            String value = address.getText().toString().trim().replaceAll("/+$", "");
            if (!value.startsWith("https://") || Uri.parse(value).getHost() == null) {
                error.setText("Digite um endereço HTTPS válido.");
                return;
            }
            prefs.edit().putString("server", value).apply();
            startPairing();
        });
        root.addView(connect);
        root.addView(error);
        root.addView(label("Depois de conectar, digite o código de 6 dígitos no botão VINCULAR TV do painel.", 16, 0xffc3d3e8));
        setContentView(root);
        hideSystemBars();
    }
    private String apiUrl() {
        String root = prefs.getString("server", "");
        return root + "/api.php?id=" + Uri.encode(prefs.getString("id", "")) +
                "&token=" + Uri.encode(prefs.getString("token", "")) + "&action=playlist";
    }
    private String deviceUrl() {
        return prefs.getString("server", "") + "/device.php?id=" +
                Uri.encode(prefs.getString("id", "")) + "&token=" +
                Uri.encode(prefs.getString("token", ""));
    }
    private void startMonitor() {
        try {
            Intent monitor = new Intent(this, WakeService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(monitor);
            else startService(monitor);
        } catch (RuntimeException ignored) { }
    }
    private void startPairing() {
        generation++;
        paired = false;
        registering = false;
        clearBrowser();
        startMonitor();
        LinearLayout root = layout();
        title(root);
        root.addView(label("Vincular esta televisão", 23, Color.WHITE));
        pairCode = label("------", 64, 0xffffd24e);
        pairCode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(pairCode);
        status = label("Conectando ao painel...", 18, 0xffd9e6f8);
        root.addView(status);
        retry = new Button(this);
        retry.setText("TENTAR NOVAMENTE");
        retry.setOnClickListener(v -> startPairing());
        root.addView(retry);
        Button settings = new Button(this);
        settings.setText("CONFIGURAR SERVIDOR");
        settings.setOnClickListener(v -> showSetup());
        root.addView(settings);
        root.addView(label("No computador, abra o painel e clique em VINCULAR TV.", 16, 0xffc0d2ec));
        setContentView(root);
        hideSystemBars();
        int token = generation;
        queryServer(token, true);
    }
    private static String request(String url, boolean page) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept", page ? "text/html" : "application/json");
        try {
            int http = conn.getResponseCode();
            if (http < 200 || http >= 300) throw new Exception("Servidor respondeu HTTP " + http);
            try (InputStream input = conn.getInputStream()) {
                if (page) return "";
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] part = new byte[2048];
                int n;
                while ((n = input.read(part)) != -1) {
                    if (buffer.size() + n > 65536) throw new Exception("Resposta do servidor muito grande");
                    buffer.write(part, 0, n);
                }
                return buffer.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }
    private void queryServer(int token, boolean first) {
        if (token != generation || paired || registering) return;
        registering = true;
        network.execute(() -> {
            String message = null, code = null;
            boolean linked = false;
            try {
                // device.php registra a TV no banco existente. Precisa acontecer
                // ANTES da consulta api.php, caso seja uma instalação nova.
                if (first) request(deviceUrl(), true);
                JSONObject json = new JSONObject(request(apiUrl(), false));
                if (json.has("error")) throw new Exception(json.optString("error"));
                linked = json.optBoolean("paired", false);
                if (!linked) {
                    code = json.optString("code", "");
                    if (!code.matches("[0-9]{6}")) throw new Exception("Servidor não devolveu o código de vinculação");
                }
            } catch (Exception e) {
                message = e.getMessage();
            }
            final String resultCode = code, resultMessage = message;
            final boolean resultLinked = linked;
            handler.post(() -> {
                if (token != generation) return;
                registering = false;
                if (resultLinked) { paired = true; openPlayer(); return; }
                if (resultMessage != null) {
                    pairCode.setText("------");
                    status.setText("Falha na conexão: " + resultMessage + "\nConfira o Wi-Fi, data e hora, HTTPS e arquivos PHP.");
                    // Falhas são visíveis e recuperáveis; não ficar preso em Conectando.
                    handler.postDelayed(() -> queryServer(token, true), 10000);
                } else {
                    pairCode.setText(resultCode);
                    status.setText("Digite este código no painel. Aguardando vinculação...");
                    handler.postDelayed(() -> queryServer(token, false), 5000);
                }
            });
        });
    }
    private void openPlayer() {
        clearBrowser();
        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        WebSettings web = browser.getSettings();
        web.setJavaScriptEnabled(true);
        web.setDomStorageEnabled(true);
        web.setMediaPlaybackRequiresUserGesture(false);
        web.setAllowFileAccess(false);
        web.setAllowContentAccess(false);
        if (android.os.Build.VERSION.SDK_INT >= 21) web.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        browser.setKeepScreenOn(true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    handler.post(() -> {
                        if (browser == view) startPairing();
                    });
                }
            }
        });
        setContentView(browser);
        hideSystemBars();
        browser.loadUrl(deviceUrl());
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            new AlertDialog.Builder(this).setTitle("TMAIS TV")
                .setMessage("Deseja configurar o servidor ou refazer a vinculação?")
                .setPositiveButton("Configurar", (d, w) -> showSetup())
                .setNeutralButton("Vincular novamente", (d, w) -> startPairing())
                .setNegativeButton("Continuar", (d, w) -> {}).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (browser != null) browser.onResume();
    }
    @Override protected void onPause() {
        if (browser != null) browser.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        generation++;
        clearBrowser();
        network.shutdownNow();
        super.onDestroy();
    }
}
