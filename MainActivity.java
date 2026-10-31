package app.tunebox;
import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Size;
import android.view.View;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
  static MainActivity inst;
  String pendingCmd;
  WebView w;
  ValueCallback<Uri[]> cb;
  volatile boolean wifiOnly = true;
  String artKey = "";
  Bitmap artBm;

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    inst = this;
    boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    w = new WebView(this);
    w.setBackgroundColor(dark ? Color.BLACK : Color.WHITE);
    setContentView(w);
    w.setOverScrollMode(View.OVER_SCROLL_NEVER);
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
    s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); s.setTextZoom(100);
    w.addJavascriptInterface(new Br(), "Android");
    w.setWebViewClient(new WebViewClient() {
      public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return serve(r); }
      public void onPageFinished(WebView v, String url) {
        if (pendingCmd != null) {
          final String c = pendingCmd; pendingCmd = null;
          v.postDelayed(new Runnable() { public void run() { js("window.tbCmd&&tbCmd('" + c + "')"); } }, 1500);
        }
      }
    });
    w.setWebChromeClient(new WebChromeClient() {
      public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
        if (cb != null) cb.onReceiveValue(null);
        cb = c;
        try { startActivityForResult(p.createIntent(), 1); } catch (Exception e) { cb = null; return false; }
        return true;
      }
    });
    pendingCmd = getIntent() != null ? getIntent().getStringExtra("cmd") : null;
    w.loadUrl("file:///android_asset/index.html");
    java.util.ArrayList<String> pl = new java.util.ArrayList<String>();
    if (Build.VERSION.SDK_INT >= 33) { pl.add("android.permission.READ_MEDIA_AUDIO"); pl.add("android.permission.POST_NOTIFICATIONS"); } else pl.add("android.permission.READ_EXTERNAL_STORAGE");
    if (Build.VERSION.SDK_INT >= 31) pl.add("android.permission.BLUETOOTH_CONNECT");
    String[] ps = pl.toArray(new String[0]);
    requestPermissions(ps, 2);
  }

  protected void onDestroy() {
    inst = null;
    try { stopService(new Intent(this, PlayService.class)); } catch (Exception e) {}
    super.onDestroy();
  }

  void js(final String code) {
    runOnUiThread(new Runnable() { public void run() { if (w != null) w.evaluateJavascript(code, null); } });
  }

  public void onRequestPermissionsResult(int rc, String[] p, int[] g) { w.evaluateJavascript("window.scanNative&&scanNative()", null); }
  protected void onActivityResult(int rc, int res, Intent d) {
    if (rc == 1 && cb != null) { cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, d)); cb = null; }
  }
  public void onBackPressed() {
    w.evaluateJavascript("window.goBack?goBack():false", new ValueCallback<String>() { public void onReceiveValue(String v) { if (!"true".equals(v)) moveTaskToBack(true); } });
  }
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i); setIntent(i);
    String c = i.getStringExtra("cmd");
    if (c != null) js("window.tbCmd&&tbCmd('" + c + "')");
  }

  boolean wifi() {
    try {
      ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
      Network n = cm.getActiveNetwork();
      NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
      return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    } catch (Exception e) { return false; }
  }
  File imgFile(String src) { File d = new File(getFilesDir(), "img"); d.mkdirs(); return new File(d, Integer.toHexString(src.hashCode()) + "_" + src.length()); }
  boolean cached(Uri u) { String s = u.getQueryParameter("u"); return s != null && imgFile(s).exists(); }

  WebResourceResponse err(Map<String, String> hd, int code) {
    return new WebResourceResponse("application/json", "utf-8", code, code == 503 ? "Blocked" : "Not Found", hd, new ByteArrayInputStream("{}".getBytes()));
  }

  WebResourceResponse serve(WebResourceRequest r) {
    Uri u = r.getUrl();
    if (!"tb.local".equals(u.getHost())) return null;
    String path = u.getPath() == null ? "" : u.getPath();
    Map<String, String> hd = new HashMap<>();
    hd.put("Access-Control-Allow-Origin", "*");
    if ((path.equals("/q") || path.equals("/d") || path.equals("/g")) && wifiOnly && !wifi() && !(path.equals("/g") && cached(u))) return err(hd, 503);
    try {
      if (path.equals("/q") || path.equals("/d")) {
        String url = path.equals("/q")
          ? "https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + URLEncoder.encode(u.getQueryParameter("term"), "UTF-8")
          : "https://api.deezer.com/search/artist?limit=1&q=" + URLEncoder.encode(u.getQueryParameter("q"), "UTF-8");
        HttpURLConnection hc = (HttpURLConnection) new URL(url).openConnection();
        hc.setConnectTimeout(8000); hc.setReadTimeout(8000);
        return new WebResourceResponse("application/json", "utf-8", 200, "OK", hd, hc.getInputStream());
      }
      if (path.equals("/g")) {
        String src = u.getQueryParameter("u");
        if (src == null) return err(hd, 404);
        String host = Uri.parse(src).getHost();
        if (!src.startsWith("https://") || host == null || !(host.endsWith("mzstatic.com") || host.endsWith("dzcdn.net") || host.endsWith("deezer.com"))) return err(hd, 404);
        File f = imgFile(src);
        if (!f.exists()) {
          HttpURLConnection hc = (HttpURLConnection) new URL(src).openConnection();
          hc.setConnectTimeout(8000); hc.setReadTimeout(12000);
          File tmp = new File(f.getPath() + ".tmp");
          try (InputStream in = hc.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] bf = new byte[8192]; int n;
            while ((n = in.read(bf)) > 0) out.write(bf, 0, n);
          }
          tmp.renameTo(f);
        }
        hd.put("Cache-Control", "max-age=31536000");
        return new WebResourceResponse("image/jpeg", null, 200, "OK", hd, new FileInputStream(f));
      }
      Uri c = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(u.getLastPathSegment()));
      if (path.startsWith("/c/")) {
        byte[] art = null;
        MediaMetadataRetriever mr = new MediaMetadataRetriever();
        try { mr.setDataSource(this, c); art = mr.getEmbeddedPicture(); } catch (Exception e) {}
        try { mr.release(); } catch (Exception e) {}
        if (art == null && Build.VERSION.SDK_INT >= 29) {
          try {
            Bitmap bm = getContentResolver().loadThumbnail(c, new Size(512, 512), null);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 85, bo);
            art = bo.toByteArray();
          } catch (Exception e) {}
        }
        if (art == null) return err(hd, 404);
        hd.put("Cache-Control", "max-age=86400");
        return new WebResourceResponse("image/jpeg", null, 200, "OK", hd, new ByteArrayInputStream(art));
      }
      AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(c, "r");
      long len = fd.getLength(), st = 0, en = len - 1;
      int code = 200;
      String rg = r.getRequestHeaders().get("Range");
      if (rg != null && rg.startsWith("bytes=")) {
        String[] p = rg.substring(6).split("-");
        st = Long.parseLong(p[0]);
        if (p.length > 1 && !p[1].isEmpty()) en = Long.parseLong(p[1]);
        code = 206;
      }
      InputStream in = fd.createInputStream();
      in.skip(st);
      hd.put("Accept-Ranges", "bytes");
      hd.put("Content-Length", String.valueOf(en - st + 1));
      if (code == 206) hd.put("Content-Range", "bytes " + st + "-" + en + "/" + len);
      String mime = getContentResolver().getType(c);
      if (mime == null) mime = "audio/mpeg";
      return new WebResourceResponse(mime, null, code, code == 206 ? "Partial Content" : "OK", hd, in);
    } catch (Exception e) { return err(hd, 404); }
  }

  Bitmap small(byte[] d, String path) {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    if (d != null) BitmapFactory.decodeByteArray(d, 0, d.length, o); else BitmapFactory.decodeFile(path, o);
    int sm = 1;
    while (o.outWidth / sm > 320) sm *= 2;
    o = new BitmapFactory.Options();
    o.inSampleSize = sm;
    return d != null ? BitmapFactory.decodeByteArray(d, 0, d.length, o) : BitmapFactory.decodeFile(path, o);
  }

  Bitmap loadArt(long mid, String url) {
    try {
      if (url != null && url.length() > 0) { File f = imgFile(url); if (f.exists()) return small(null, f.getPath()); }
      if (mid > 0) {
        Uri cu = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mid);
        MediaMetadataRetriever mr = new MediaMetadataRetriever();
        try {
          mr.setDataSource(this, cu);
          byte[] b = mr.getEmbeddedPicture();
          if (b != null) return small(b, null);
        } finally { try { mr.release(); } catch (Exception e) {} }
        if (Build.VERSION.SDK_INT >= 29) return getContentResolver().loadThumbnail(cu, new Size(256, 256), null);
      }
    } catch (Exception e) {}
    return null;
  }

  class Br {
    @JavascriptInterface public String scan() {
      JSONArray a = new JSONArray();
      try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          Build.VERSION.SDK_INT >= 30
            ? new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, "genre"}
            : new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED},
          MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
        while (c != null && c.moveToNext()) {
          JSONObject o = new JSONObject();
          o.put("mid", c.getLong(0)); o.put("title", c.getString(1)); o.put("artist", c.getString(2)); o.put("album", c.getString(3));
          o.put("dur", c.getLong(4) / 1000); o.put("added", c.getLong(5));
          if (c.getColumnCount() > 6) o.put("genre", c.getString(6));
          a.put(o);
        }
      } catch (Exception e) {}
      return a.toString();
    }
    @JavascriptInterface public void now(final String t, final String a, final String b, final boolean pl, final long mid, final String raw, final long dur, final long pos) {
      new Thread(new Runnable() { public void run() {
        String key = mid + "|" + raw;
        if (!key.equals(PlayService.artKey)) { PlayService.art = loadArt(mid, raw); PlayService.artKey = key; }
        PlayService.title = t; PlayService.artist = a; PlayService.album = b; PlayService.playing = pl; PlayService.durMs = dur; PlayService.posMs = pos;
        if (PlayService.inst == null) {
          try { Intent i = new Intent(MainActivity.this, PlayService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch (Exception e) {}
        } else PlayService.inst.refresh();
      } }).start();
    }
    @JavascriptInterface public String device() {
      try {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        JSONArray arr = new JSONArray(); String cur = null, curT = "speaker";
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
          int ty = d.getType(); String type;
          if (ty == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || ty == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || ty == 26) type = "bt";
          else if (ty == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || ty == AudioDeviceInfo.TYPE_WIRED_HEADSET || ty == AudioDeviceInfo.TYPE_USB_HEADSET) type = "wired";
          else if (ty == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) type = "speaker";
          else continue;
          String nm = d.getProductName() == null ? "" : d.getProductName().toString();
          JSONObject o = new JSONObject(); o.put("name", nm); o.put("type", type); arr.put(o);
          if (!type.equals("speaker") && (cur == null || (type.equals("bt") && !curT.equals("bt")))) { cur = nm; curT = type; }
        }
        JSONObject r = new JSONObject(); r.put("cur", cur); r.put("type", curT); r.put("list", arr);
        return r.toString();
      } catch (Exception e) { return "{}"; }
    }
    @JavascriptInterface public void openOutput() {
      try {
        Intent i = Build.VERSION.SDK_INT >= 29 ? new Intent(Settings.Panel.ACTION_VOLUME) : new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
      } catch (Exception e) {
        try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception x) {}
      }
    }
    @JavascriptInterface public void openUrl(String u) {
      try {
        Uri x = Uri.parse(u); String h = x.getHost();
        if (!"https".equals(x.getScheme()) || h == null || !(h.equals("github.com") || h.endsWith(".github.com") || h.endsWith("githubusercontent.com"))) return;
        startActivity(new Intent(Intent.ACTION_VIEW, x).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      } catch (Exception e) {}
    }
    @JavascriptInterface public String version() {
      try {
        android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
        long code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        return "{\"name\":\"" + pi.versionName + "\",\"code\":" + code + "}";
      } catch (Exception e) { return "{}"; }
    }
    @JavascriptInterface public boolean wifi() { return MainActivity.this.wifi(); }
    @JavascriptInterface public void setWifiOnly(boolean b) { wifiOnly = b; }
    @JavascriptInterface public double cacheMB() {
      long t = 0; File[] fs = new File(getFilesDir(), "img").listFiles();
      if (fs != null) for (File x : fs) t += x.length();
      return Math.round(t / 104857.6) / 10.0;
    }
    @JavascriptInterface public void clearCache() {
      File[] fs = new File(getFilesDir(), "img").listFiles();
      if (fs != null) for (File x : fs) x.delete();
    }
    @JavascriptInterface public int getVol() {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      return am.getStreamVolume(3) * 100 / am.getStreamMaxVolume(3);
    }
    @JavascriptInterface public void setVol(int p) {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      am.setStreamVolume(3, Math.round(p * am.getStreamMaxVolume(3) / 100f), 0);
    }
  }
}
