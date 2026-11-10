package app.tunebox;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.net.URLEncoder;

/* YouTube Music en SoundBits: página de inicio oficial, barra inferior de SoundBits y
   puente mínimo exclusivo para SponsorBlock/estado del reproductor. */
public class YouTubeActivity extends Activity {
  static final String HOME = "https://music.youtube.com/";
  static final int BG = 0xFF030303, CARD = 0xFF212121, TX = 0xFFFFFFFF, MU = 0xFFAAAAAA, AC = 0xFFFF6A1A;

  WebView web;
  EditText bar;
  ProgressBar prog;
  FrameLayout root;
  LinearLayout page;
  View customView;
  WebChromeClient chrome;
  WebChromeClient.CustomViewCallback customCb;
  PowerManager.WakeLock playbackLock;
  View splash;

  /* SponsorBlock para los reproductores de YouTube y YouTube Music, incluidos cambios SPA. */
  static final String SB_JS = """
(function(){if(window.__tbSb)return;window.__tbSb=1;
var vid="",segs=[],last=-1,lastPlaying=null;
window.__tbSbSet=function(id,a){if(id===vid){segs=Array.isArray(a)?a:[];last=-1}};
function cur(){var p=location.pathname;
if(p.indexOf("/shorts/")===0){var s=p.substr(8,11);return /^[A-Za-z0-9_-]{11}$/.test(s)?s:""}
var v=new URLSearchParams(location.search).get("v");return v&&/^[A-Za-z0-9_-]{11}$/.test(v)?v:""}
function tick(){var id=cur();
if(id!==vid){vid=id;segs=[];last=-1;if(id){try{TBSB.segments(id)}catch(e){}}}
var v=document.querySelector("video");if(!v)return;
var playing=!v.paused&&!v.ended;
if(playing!==lastPlaying){lastPlaying=playing;try{TBSB.playback(playing,document.title||"YouTube Music")}catch(e){}}
if(!segs.length)return;var t=v.currentTime;
for(var i=0;i<segs.length;i++){var x=segs[i];
if(t>=x.start&&t<x.end&&last!==i){last=i;v.currentTime=Math.min(x.end+0.15,v.duration||x.end+0.15);
try{TBSB.skipped(x.category)}catch(e){}break}}}
setInterval(tick,500)})();
""";

  class Sb {
    @JavascriptInterface public void segments(final String id) {
      if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) return;
      new Thread(new Runnable() { public void run() {
        final String data = MainActivity.sbQuery(id).toString();
        runOnUiThread(new Runnable() { public void run() {
          if (web != null) web.evaluateJavascript("window.__tbSbSet&&__tbSbSet(" + JSONObject.quote(id) + "," + data + ")", null);
        } });
      } }, "SponsorBlock-ytm").start();
    }
    @JavascriptInterface public void skipped(final String cat) {
      if (cat == null || !MainActivity.SB_ALL.contains(cat)) return;
      runOnUiThread(new Runnable() { public void run() {
        Toast.makeText(YouTubeActivity.this, "SponsorBlock: segmento omitido (" + cat + ")", Toast.LENGTH_SHORT).show();
      } });
    }
    @JavascriptInterface public void playback(final boolean playing, final String title) {
      runOnUiThread(new Runnable() { public void run() { setAudioPlaying(playing); } });
    }
  }

  int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

  static boolean ytHost(String h) {
    if (h == null) return false;
    h = h.toLowerCase();
    return h.equals("youtu.be") || h.equals("youtube.com") || h.endsWith(".youtube.com");
  }

  static boolean okHost(String h) {
    if (ytHost(h)) return true;
    if (h == null) return false;
    h = h.toLowerCase();
    return h.equals("google.com") || h.endsWith(".google.com");
  }

  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    PlayService.cmd("pause"); // evita dos audios a la vez
    try {
      PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
      if (pm != null) {
        playbackLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":YouTubeMusicPlayback");
        playbackLock.setReferenceCounted(false);
      }
    } catch (Exception ignored) {}

    root = new FrameLayout(this);
    root.setBackgroundColor(BG);
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    root.addView(page, new FrameLayout.LayoutParams(-1, -1));
    root.setOnApplyWindowInsetsListener((v, ins) -> {
      page.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
          ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
      return ins;
    });

    // Búsqueda rápida con paleta YT Music y marca SoundBits.
    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(14), dp(8), dp(14), dp(8));
    top.setBackgroundColor(BG);
    TextView brand = new TextView(this);
    brand.setText("▶");
    brand.setTextColor(AC);
    brand.setTextSize(20);
    brand.setGravity(Gravity.CENTER);
    brand.setPadding(0, 0, dp(10), 0);
    top.addView(brand, new LinearLayout.LayoutParams(dp(30), dp(42)));
    bar = new EditText(this);
    bar.setSingleLine(true);
    bar.setHint("Buscar en YouTube Music");
    bar.setHintTextColor(MU);
    bar.setTextColor(TX);
    bar.setTextSize(15);
    bar.setSelectAllOnFocus(true);
    bar.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    bar.setPadding(dp(18), dp(10), dp(18), dp(10));
    GradientDrawable pill = new GradientDrawable();
    pill.setColor(CARD);
    pill.setCornerRadius(dp(24));
    bar.setBackground(pill);
    bar.setOnEditorActionListener((v, id, ev) -> {
      if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_GO
          || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
        go(bar.getText().toString());
        return true;
      }
      return false;
    });
    top.addView(bar, new LinearLayout.LayoutParams(0, -2, 1f));
    page.addView(top, new LinearLayout.LayoutParams(-1, -2));

    prog = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    prog.setMax(100);
    prog.setProgressTintList(ColorStateList.valueOf(AC));
    prog.setVisibility(View.INVISIBLE);
    page.addView(prog, new LinearLayout.LayoutParams(-1, dp(2)));

    web = new WebView(this);
    web.setBackgroundColor(BG);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setSupportZoom(false);
    s.setBuiltInZoomControls(false);
    s.setMediaPlaybackRequiresUserGesture(false);
    web.addJavascriptInterface(new Sb(), "TBSB");
    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    cm.setAcceptThirdPartyCookies(web, true);

    web.setWebViewClient(new WebViewClient() {
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        if (!r.isForMainFrame()) return false;
        Uri u = r.getUrl();
        boolean https = "https".equals(u.getScheme());
        if (https && okHost(u.getHost())) return false;
        if (https) { try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {} }
        return true;
      }
      public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) { showUrl(url); }
      public void onPageFinished(WebView v, String url) {
        Uri u = Uri.parse(url == null ? "" : url);
        if ("https".equals(u.getScheme()) && ytHost(u.getHost())) v.evaluateJavascript(SB_JS, null);
        if (splash != null) hideSplash();
      }
    });
    chrome = new WebChromeClient() {
      public void onProgressChanged(WebView v, int p) {
        prog.setProgress(p);
        prog.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
      }
      public void onShowCustomView(View view, CustomViewCallback cb) {
        if (customView != null) { cb.onCustomViewHidden(); return; }
        customView = view; customCb = cb;
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        page.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
      }
      public void onHideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        page.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (customCb != null) { customCb.onCustomViewHidden(); customCb = null; }
      }
    };
    web.setWebChromeClient(chrome);
    page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
    page.addView(soundBitsNav(), new LinearLayout.LayoutParams(-1, dp(60)));

    setContentView(root);
    showIconSplash();
    if (saved != null) {
      web.restoreState(saved);
    } else {
      String q = getIntent() == null ? null : getIntent().getStringExtra("q");
      if (q != null && !q.trim().isEmpty()) go(q); else web.loadUrl(HOME);
    }
  }

  /* Barra inferior de SoundBits, con accesos para Inicio, YouTube Music y sus secciones. */
  View soundBitsNav() {
    LinearLayout nav = new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setGravity(Gravity.CENTER);
    nav.setBackgroundColor(BG);
    nav.setPadding(0, dp(5), 0, dp(3));
    nav.setElevation(dp(8));
    nav.addView(navItem("⌂", "Inicio", v -> finish()));
    nav.addView(navItem("▶", "YouTube Music", v -> web.loadUrl(HOME)));
    nav.addView(navItem("⌕", "Explorar", v -> web.loadUrl(HOME + "explore")));
    nav.addView(navItem("♫", "Podcast", v -> web.loadUrl(HOME + "podcasts")));
    nav.addView(navItem("▤", "Biblioteca", v -> web.loadUrl(HOME + "library")));
    return nav;
  }

  View navItem(String icon, String label, View.OnClickListener listener) {
    LinearLayout item = new LinearLayout(this);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setGravity(Gravity.CENTER);
    item.setClickable(true);
    item.setFocusable(true);
    item.setOnClickListener(listener);
    TextView glyph = new TextView(this);
    glyph.setText(icon);
    glyph.setTextColor(TX);
    glyph.setGravity(Gravity.CENTER);
    glyph.setTextSize(22);
    item.addView(glyph, new LinearLayout.LayoutParams(-1, dp(30)));
    TextView text = new TextView(this);
    text.setText(label);
    text.setTextColor(MU);
    text.setGravity(Gravity.CENTER);
    text.setTextSize(9);
    text.setSingleLine(true);
    item.addView(text, new LinearLayout.LayoutParams(-1, dp(16)));
    item.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
    return item;
  }

  /* Animación de entrada inspirada en el icono: triángulo naranja, vinilo giratorio y nota. */
  void showIconSplash() {
    FrameLayout overlay = new FrameLayout(this);
    overlay.setBackgroundColor(Color.BLACK);
    FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER);
    GradientDrawable orange = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[]{0xFFFF7A1A, 0xFFFF3B12, 0xFFD91E18});
    orange.setShape(GradientDrawable.RECTANGLE);
    orange.setCornerRadius(dp(24));
    View triangle = new View(this);
    triangle.setBackground(orange);
    FrameLayout.LayoutParams tri = new FrameLayout.LayoutParams(dp(126), dp(126), Gravity.CENTER);
    triangle.setRotation(45f);
    overlay.addView(triangle, tri);

    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_launcher);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    FrameLayout.LayoutParams icon = new FrameLayout.LayoutParams(dp(116), dp(116), Gravity.CENTER);
    overlay.addView(logo, icon);

    TextView word = new TextView(this);
    word.setText("SoundBits");
    word.setTextColor(TX);
    word.setTextSize(22);
    word.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    word.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams wordLp = new FrameLayout.LayoutParams(-1, dp(40), Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
    wordLp.topMargin = dp(118);
    overlay.addView(word, wordLp);
    overlay.setAlpha(0f);
    root.addView(overlay, outer);
    splash = overlay;

    AnimatorSet entrance = new AnimatorSet();
    entrance.playTogether(
        ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f),
        ObjectAnimator.ofFloat(logo, View.SCALE_X, .72f, 1f),
        ObjectAnimator.ofFloat(logo, View.SCALE_Y, .72f, 1f),
        ObjectAnimator.ofFloat(triangle, View.ROTATION, 20f, 45f));
    entrance.setDuration(650);
    entrance.setInterpolator(new AccelerateDecelerateInterpolator());
    entrance.start();
  }

  void hideSplash() {
    final View old = splash;
    if (old == null) return;
    splash = null;
    old.animate().alpha(0f).setDuration(320).withEndAction(new Runnable() {
      public void run() { if (root != null) root.removeView(old); }
    }).start();
  }

  void setAudioPlaying(boolean playing) {
    try {
      if (playbackLock == null) return;
      if (playing && !playbackLock.isHeld()) playbackLock.acquire();
      else if (!playing && playbackLock.isHeld()) playbackLock.release();
    } catch (Exception ignored) {}
  }

  /* Texto → búsqueda de YouTube Music; los enlaces YouTube se abren tal cual. */
  void go(String input) {
    String t = input == null ? "" : input.trim();
    if (t.isEmpty()) return;
    String url = null;
    if (!t.contains(" ") && t.contains(".")) {
      Uri u = Uri.parse(t.contains("://") ? t : "https://" + t);
      if (ytHost(u.getHost())) url = u.buildUpon().scheme("https").build().toString();
    }
    if (url == null) {
      try { url = HOME + "search?q=" + URLEncoder.encode(t, "UTF-8"); } catch (Exception e) { return; }
    }
    web.loadUrl(url);
    bar.clearFocus();
    InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (im != null) im.hideSoftInputFromWindow(bar.getWindowToken(), 0);
  }

  void showUrl(String url) {
    if (bar == null || bar.hasFocus() || url == null) return;
    Uri u = Uri.parse(url);
    String q = u.getQueryParameter("q");
    bar.setText(q != null ? q : (u.getHost() == null ? "" : u.getHost()));
  }

  public void onBackPressed() {
    if (customView != null) { chrome.onHideCustomView(); return; }
    if (web.canGoBack()) web.goBack(); else finish();
  }

  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    if (web != null) web.saveState(out);
  }

  protected void onDestroy() {
    setAudioPlaying(false);
    if (web != null) { root.removeView(web); web.destroy(); web = null; }
    try { if (playbackLock != null && playbackLock.isHeld()) playbackLock.release(); } catch (Exception ignored) {}
    super.onDestroy();
  }
}
