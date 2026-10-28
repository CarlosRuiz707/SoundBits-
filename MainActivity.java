package app.tunebox;
import android.app.Activity;import android.content.*;import android.database.Cursor;import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;import android.media.AudioManager;import android.media.MediaMetadataRetriever;import android.util.Size;import java.net.*;import android.net.ConnectivityManager;import android.net.Network;import android.net.NetworkCapabilities;import android.net.Uri;import android.os.*;import android.provider.MediaStore;import android.webkit.*;
import org.json.*;import java.io.*;import java.util.*;
public class MainActivity extends Activity {
  WebView w; ValueCallback<Uri[]> cb; volatile boolean wifiOnly = true;
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    w = new WebView(this); setContentView(w);
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
    w.addJavascriptInterface(new Br(), "Android");
    w.setWebViewClient(new WebViewClient() {
      public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        if (!"tb.local".equals(u.getHost())) return null;
        String path = u.getPath() == null ? "" : u.getPath();
        Map<String, String> hd = new HashMap<>(); hd.put("Access-Control-Allow-Origin", "*");
        if ((path.equals("/q") || path.equals("/d") || path.equals("/g")) && wifiOnly && !wifi() && !(path.equals("/g") && cached(u)))
          return new WebResourceResponse("application/json", "utf-8", 503, "Blocked", hd, new ByteArrayInputStream("{}".getBytes()));
        if (path.equals("/q")) {
          try {
            HttpURLConnection hc = (HttpURLConnection) new URL("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + URLEncoder.encode(u.getQueryParameter("term"), "UTF-8")).openConnection();
            hc.setConnectTimeout(8000); hc.setReadTimeout(8000);
            return new WebResourceResponse("application/json", "utf-8", 200, "OK", hd, hc.getInputStream());
          } catch (Exception e) { return new WebResourceResponse("application/json", "utf-8", 404, "Not Found", hd, new ByteArrayInputStream("{}".getBytes())); }
        }
        if (path.equals("/d")) {
          try {
            HttpURLConnection hc = (HttpURLConnection) new URL("https://api.deezer.com/search/artist?limit=1&q=" + URLEncoder.encode(u.getQueryParameter("q"), "UTF-8")).openConnection();
            hc.setConnectTimeout(8000); hc.setReadTimeout(8000);
            return new WebResourceResponse("application/json", "utf-8", 200, "OK", hd, hc.getInputStream());
          } catch (Exception e) { return new WebResourceResponse("application/json", "utf-8", 404, "Not Found", hd, new ByteArrayInputStream("{}".getBytes())); }
        }
        if (path.equals("/g")) {
          try {
            String src = u.getQueryParameter("u");
            if (src == null) throw new Exception("u");
            String host = Uri.parse(src).getHost();
            if (!src.startsWith("https://") || host == null || !(host.endsWith("mzstatic.com") || host.endsWith("dzcdn.net") || host.endsWith("deezer.com"))) throw new Exception("host");
            File f = imgFile(src);
            if (!f.exists()) {
              HttpURLConnection hc = (HttpURLConnection) new URL(src).openConnection();
              hc.setConnectTimeout(8000); hc.setReadTimeout(12000);
              File tmp = new File(f.getPath() + ".tmp");
              try (InputStream in = hc.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) { byte[] bf = new byte[8192]; int n; while ((n = in.read(bf)) > 0) out.write(bf, 0, n); }
              tmp.renameTo(f);
            }
            hd.put("Cache-Control", "max-age=31536000");
            return new WebResourceResponse("image/jpeg", null, 200, "OK", hd, new FileInputStream(f));
          } catch (Exception e) { return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", hd, new ByteArrayInputStream(new byte[0])); }
        }
        if (path.startsWith("/c/")) {
          byte[] art = null;
          Uri cu = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(u.getLastPathSegment()));
          MediaMetadataRetriever mr = new MediaMetadataRetriever();
          try { mr.setDataSource(MainActivity.this, cu); art = mr.getEmbeddedPicture(); } catch (Exception e) {}
          try { mr.release(); } catch (Exception e) {}
          if (art == null && Build.VERSION.SDK_INT >= 29) {
            try { Bitmap bm = getContentResolver().loadThumbnail(cu, new Size(512, 512), null); ByteArrayOutputStream bo = new ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.JPEG, 85, bo); art = bo.toByteArray(); } catch (Exception e) {}
          }
          if (art == null) return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", hd, new ByteArrayInputStream(new byte[0]));
          hd.put("Cache-Control", "max-age=86400");
          return new WebResourceResponse("image/jpeg", null, 200, "OK", hd, new ByteArrayInputStream(art));
        }
        try {
          Uri c = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(u.getLastPathSegment()));
          AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(c, "r");
          long len = fd.getLength(), st = 0, en = len - 1; int code = 200;
          String rg = r.getRequestHeaders().get("Range");
          if (rg != null && rg.startsWith("bytes=")) { String[] p = rg.substring(6).split("-"); st = Long.parseLong(p[0]); if (p.length > 1 && !p[1].isEmpty()) en = Long.parseLong(p[1]); code = 206; }
          InputStream in = fd.createInputStream(); in.skip(st);
          Map<String, String> h = new HashMap<>();
          h.put("Accept-Ranges", "bytes"); h.put("Access-Control-Allow-Origin", "*"); h.put("Content-Length", String.valueOf(en - st + 1));
          if (code == 206) h.put("Content-Range", "bytes " + st + "-" + en + "/" + len);
          String mime = getContentResolver().getType(c); if (mime == null) mime = "audio/mpeg";
          return new WebResourceResponse(mime, null, code, code == 206 ? "Partial Content" : "OK", h, in);
        } catch (Exception e) { return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])); }
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
    w.loadUrl("file:///android_asset/index.html");
    String[] ps = Build.VERSION.SDK_INT >= 33 ? new String[]{"android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS"} : new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
    requestPermissions(ps, 2);
  }
  public void onRequestPermissionsResult(int rc, String[] p, int[] g) { w.evaluateJavascript("window.scanNative&&scanNative()", null); }
  protected void onActivityResult(int rc, int res, Intent d) {
    if (rc == 1 && cb != null) { cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, d)); cb = null; }
  }
  public void onBackPressed() { w.evaluateJavascript("window.goBack&&goBack()", null); }
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
  class Br {
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
    @JavascriptInterface public String scan() {
      JSONArray a = new JSONArray();
      try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          Build.VERSION.SDK_INT >= 30 ? new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, "genre"} : new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED},
          MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
        while (c != null && c.moveToNext()) {
          JSONObject o = new JSONObject();
          o.put("mid", c.getLong(0)); o.put("title", c.getString(1)); o.put("artist", c.getString(2)); o.put("album", c.getString(3)); o.put("dur", c.getLong(4) / 1000); o.put("added", c.getLong(5)); if (c.getColumnCount() > 6) o.put("genre", c.getString(6));
          a.put(o);
        }
      } catch (Exception e) {}
      return a.toString();
    }
    @JavascriptInterface public int getVol() {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      return am.getStreamVolume(3) * 100 / am.getStreamMaxVolume(3);
    }
    @JavascriptInterface public void setVol(int p) {
      AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
      am.setStreamVolume(3, Math.round(p * am.getStreamMaxVolume(3) / 100f), 0);
    }
    @JavascriptInterface public void keep(boolean on, String t) {
      try {
        Intent i = new Intent(MainActivity.this, PlayService.class).putExtra("t", t);
        if (on) { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } else stopService(i);
      } catch (Exception e) {}
    }
  }
}
