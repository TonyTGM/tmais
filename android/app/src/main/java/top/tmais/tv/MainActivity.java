package top.tmais.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputType;
import android.net.Uri;
import java.security.SecureRandom;

public class MainActivity extends Activity {
    private WebView browser;
    private SharedPreferences prefs;
    private static final String PREF = "tmais_tv_settings";
    private final SecureRandom random = new SecureRandom();

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        if (!prefs.contains("id")) prefs.edit().putString("id", newHex(12)).putString("token", newHex(32)).apply();
        if (prefs.getString("server", "").isEmpty()) showSetup(); else { startMonitor(); startPlayer(); }
    }
    private String newHex(int bytes) {
        byte[] b = new byte[bytes]; random.nextBytes(b); StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(String.format(java.util.Locale.US, "%02x", v & 0xff));return s.toString();
    }
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER); t.setPadding(8, 12, 8, 12);return t;
    }
    private void showSetup() {
        if (browser != null) {browser.destroy();browser=null;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(90,35,90,35);root.setBackgroundColor(Color.rgb(7,30,73));
        TextView title=text("▣ TMAIS TV",34,Color.WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(text("Digite o endereço HTTPS onde instalou o painel PHP.",18,0xffc8d8ee));
        EditText url = new EditText(this);url.setSingleLine(true);url.setTextColor(Color.WHITE);url.setHintTextColor(0xff91a8c6);url.setHint("https://seu-dominio.com/tmais-tv/php");url.setText(prefs.getString("server",""));url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);url.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));root.addView(url,new LinearLayout.LayoutParams(-1,-2));
        Button save=new Button(this);save.setText("CONECTAR TV");save.setTextColor(Color.WHITE);save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff0865f7));root.addView(save);
        TextView error=text("",15,0xffff9e9e);root.addView(error);
        root.addView(text("Depois de conectar, informe no painel o código mostrado nesta TV. Pressione VOLTAR ou MENU para acessar as configurações.",14,0xffb3c1db));
        save.setOnClickListener(v->{String s=url.getText().toString().trim().replaceAll("/+$","");if(!s.startsWith("https://")){error.setText("Para segurança, use HTTPS com certificado válido.");return;}prefs.edit().putString("server",s).apply();startMonitor();startPlayer();});
        setContentView(root);hideSystemBars();
    }
    private void startMonitor() {
        try {
            Intent launch = new Intent(this, WakeService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(launch);
            else startService(launch);
        } catch (RuntimeException e) {
            android.util.Log.w("TMAIS-TV", "Monitor indisponível neste aparelho", e);
        }
    }
    private void startPlayer() {
        if(browser!=null)browser.destroy();
        browser=new WebView(this);browser.setBackgroundColor(Color.BLACK);browser.setWebViewClient(new WebViewClient());
        browser.setWebChromeClient(new WebChromeClient());
        WebSettings s=browser.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);browser.setKeepScreenOn(true);
        setContentView(browser);hideSystemBars();
        String url=prefs.getString("server","")+"/device.php?id="+Uri.encode(prefs.getString("id",""))+"&token="+Uri.encode(prefs.getString("token",""));
        browser.loadUrl(url);
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_BACK||keyCode==KeyEvent.KEYCODE_MENU){new AlertDialog.Builder(this).setTitle("TMAIS TV").setMessage("Deseja configurar o endereço do servidor?").setPositiveButton("Configurar",(d,w)->showSetup()).setNegativeButton("Continuar",(d,w)->{}).show();return true;}
        return super.onKeyDown(keyCode,event);
    }
    @Override protected void onResume(){super.onResume();hideSystemBars();if(browser!=null)browser.onResume();}
    @Override protected void onPause(){if(browser!=null)browser.onPause();super.onPause();}
    @Override protected void onDestroy(){if(browser!=null){browser.destroy();browser=null;}super.onDestroy();}
}
