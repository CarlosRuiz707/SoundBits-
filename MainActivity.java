package app.tunebox;
import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
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
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        if (!r.isForMainFrame()) return false;
        Uri u = r.getUrl();
        if ("tb.local".equals(u.getHost()) || "file".equals(u.getScheme())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
        return true;
      }
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
    ArrayList<String> ps = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= 33) { ps.add("android.permission.READ_MEDIA_AUDIO"); ps.add("android.permission.POST_NOTIFICATIONS"); }
    else ps.add("android.permission.READ_EXTERNAL_STORAGE");
    if (Build.VERSION.SDK_INT >= 31) ps.add("android.permission.BLUETOOTH_CONNECT");
    requestPermissions(ps.toArray(new String[0]), 2);
  }

  protected void onStop() {
    super.onStop();
    js("window.tbBg&&tbBg()");
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

  static final String UA = "SoundBits/2.0";

  String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
  String httpGet(String url) throws IOException {
    HttpURLConnection hc = (HttpURLConnection) new URL(url).openConnection();
    hc.setConnectTimeout(8000); hc.setReadTimeout(10000);
    hc.setRequestProperty("User-Agent", UA);
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try (InputStream in = hc.getInputStream()) { byte[] bf = new byte[8192]; int n; while ((n = in.read(bf)) > 0) bo.write(bf, 0, n); }
    return bo.toString("UTF-8");
  }
  String archiveCreator(JSONObject o) {
    Object v = o.opt("creator");
    if (v instanceof JSONArray) {
      JSONArray a = (JSONArray) v; ArrayList<String> out = new ArrayList<>();
      for (int i = 0; i < a.length() && i < 3; i++) { String x = a.optString(i, "").trim(); if (!x.isEmpty()) out.add(x); }
      return android.text.TextUtils.join(", ", out);
    }
    return v == null || v == JSONObject.NULL ? "Artista desconocido" : String.valueOf(v).trim();
  }
  boolean allowedArchiveLicense(String license) {
    String x = license == null ? "" : license.toLowerCase(Locale.ROOT);
    return x.contains("creativecommons.org/licenses/by/") || x.contains("creativecommons.org/licenses/by-sa/")
      || x.contains("creativecommons.org/publicdomain/zero/") || x.contains("creativecommons.org/publicdomain/mark/");
  }
  String archiveAudioFile(JSONArray files) {
    String best = ""; int bestScore = -1;
    for (int i = 0; i < files.length(); i++) {
      JSONObject file = files.optJSONObject(i); if (file == null || file.optBoolean("private", false)) continue;
      String name = file.optString("name", ""); String low = name.toLowerCase(Locale.ROOT);
      if (!(low.endsWith(".mp3") || low.endsWith(".m4a") || low.endsWith(".ogg") || low.endsWith(".opus") || low.endsWith(".flac") || low.endsWith(".wav"))) continue;
      if (low.contains("_spectrogram") || low.contains("_thumb") || low.endsWith("_sample.mp3")) continue;
      long size = 0; try { size = Long.parseLong(file.optString("size", "0")); } catch (Exception e) {}
      if (size <= 0) continue;
      int score = low.endsWith(".mp3") ? 6 : low.endsWith(".m4a") ? 5 : low.endsWith(".ogg") ? 4 : low.endsWith(".opus") ? 3 : low.endsWith(".flac") ? 2 : 1;
      if (score > bestScore) { bestScore = score; best = name; }
    }
    return best;
  }
  JSONArray searchArchive(String query) {
    JSONArray out = new JSONArray();
    try {
      String clean = query == null ? "" : query.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll("\\s+", " ");
      if (clean.length() < 2) return out;
      String term = "\"" + clean + "\"";
      String q = "mediatype:audio AND licenseurl:http*creativecommons.org* AND (title:" + term + " OR creator:" + term + " OR subject:" + term + ")";
      String url = "https://archive.org/advancedsearch.php?q=" + enc(q)
        + "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&fl[]=licenseurl&rows=12&page=1&output=json";
      JSONObject root = new JSONObject(httpGet(url));
      JSONArray docs = root.optJSONObject("response") == null ? new JSONArray() : root.getJSONObject("response").optJSONArray("docs");
      if (docs == null) return out;
      for (int i = 0; i < docs.length() && i < 12; i++) {
        JSONObject doc = docs.optJSONObject(i); if (doc == null) continue;
        String id = doc.optString("identifier", ""), license = doc.optString("licenseurl", "");
        if (id.isEmpty() || !allowedArchiveLicense(license)) continue;
        try {
          JSONObject detail = new JSONObject(httpGet("https://archive.org/metadata/" + Uri.encode(id)));
          JSONObject md = detail.optJSONObject("metadata"); JSONArray files = detail.optJSONArray("files");
          if (md == null || files == null) continue;
          String itemLicense = md.optString("licenseurl", license);
          if (!allowedArchiveLicense(itemLicense)) continue;
          String file = archiveAudioFile(files); if (file.isEmpty()) continue;
          String title = md.optString("title", doc.optString("title", "Sin título"));
          String artist = archiveCreator(md); if (artist.isEmpty()) artist = archiveCreator(doc);
          JSONObject track = new JSONObject();
          track.put("id", id); track.put("title", title); track.put("artist", artist);
          track.put("album", md.optString("album", ""));
          track.put("license", itemLicense);
          track.put("sourceUrl", "https://archive.org/details/" + Uri.encode(id));
          track.put("url", "https://archive.org/download/" + Uri.encode(id) + "/" + Uri.encode(file));
          track.put("art", "https://archive.org/services/img/" + Uri.encode(id));
          track.put("dur", md.optDouble("runtime", 0));
          out.put(track);
        } catch (Exception ignored) {}
      }
    } catch (Exception ignored) {}
    return out;
  }
  WebResourceResponse streamArchive(WebResourceRequest request, Map<String, String> headers) throws IOException {
    String raw = request.getUrl().getQueryParameter("u");
    if (raw == null) return err(headers, 404);
    Uri target = Uri.parse(raw); String host = target.getHost();
    if (!"https".equals(target.getScheme()) || host == null || !(host.equals("archive.org") || host.endsWith(".archive.org"))) return err(headers, 404);
    HttpURLConnection hc = (HttpURLConnection) new URL(raw).openConnection();
    hc.setConnectTimeout(12000); hc.setReadTimeout(20000); hc.setInstanceFollowRedirects(true);
    hc.setRequestProperty("User-Agent", UA);
    String range = request.getRequestHeaders().get("Range"); if (range != null) hc.setRequestProperty("Range", range);
    int code = hc.getResponseCode();
    if (code != 200 && code != 206) { InputStream es = hc.getErrorStream(); if (es != null) es.close(); return err(headers, code == 416 ? 404 : 503); }
    String type = hc.getContentType(); if (type == null) type = "audio/mpeg";
    String length = hc.getHeaderField("Content-Length"), contentRange = hc.getHeaderField("Content-Range");
    headers.put("Accept-Ranges", "bytes"); headers.put("Cache-Control", "no-store");
    if (length != null) headers.put("Content-Length", length);
    if (contentRange != null) headers.put("Content-Range", contentRange);
    InputStream upstream = hc.getInputStream();
    InputStream body = new FilterInputStream(upstream) { public void close() throws IOException { try { super.close(); } finally { hc.disconnect(); } } };
    return new WebResourceResponse(type, null, code, code == 206 ? "Partial Content" : "OK", headers, body);
  }
  String f(JSONObject o, String k) { return o.isNull(k) ? "" : o.optString(k, "").trim(); }
  boolean hasLy(JSONObject o) { return f(o, "syncedLyrics").length() > 0 || f(o, "plainLyrics").length() > 0; }
  /* Lyrics from LRCLIB (needs internet; nothing is stored on the device) */
  JSONObject lyrics(String ar, String ti, String al, String du) {
    try {
      if (ar.isEmpty() || ti.isEmpty()) return null;
      try {
        String url = "https://lrclib.net/api/get?artist_name=" + enc(ar) + "&track_name=" + enc(ti)
          + (al.isEmpty() ? "" : "&album_name=" + enc(al)) + (du.isEmpty() || du.equals("0") ? "" : "&duration=" + enc(du));
        JSONObject o = new JSONObject(httpGet(url));
        if (hasLy(o)) return o;
      } catch (Exception e) {}
      JSONArray a = new JSONArray(httpGet("https://lrclib.net/api/search?track_name=" + enc(ti) + "&artist_name=" + enc(ar)));
      JSONObject plain = null;
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        if (f(o, "syncedLyrics").length() > 0) return o;
        if (plain == null && hasLy(o)) plain = o;
      }
      return plain;
    } catch (Exception e) { return null; }
  }

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
      if (path.equals("/l")) {
        String a = u.getQueryParameter("a"), t = u.getQueryParameter("t"), b = u.getQueryParameter("b"), d = u.getQueryParameter("d");
        JSONObject o = lyrics(a == null ? "" : a, t == null ? "" : t, b == null ? "" : b, d == null ? "" : d);
        if (o == null) return err(hd, 404);
        return new WebResourceResponse("application/json", "utf-8", 200, "OK", hd, new ByteArrayInputStream(o.toString().getBytes("UTF-8")));
      }
      if (path.equals("/stream")) {
        JSONArray results = searchArchive(u.getQueryParameter("q"));
        hd.put("Cache-Control", "no-store");
        return new WebResourceResponse("application/json", "utf-8", 200, "OK", hd, new ByteArrayInputStream(results.toString().getBytes("UTF-8")));
      }
      if (path.equals("/streamfile")) return streamArchive(r, hd);
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
      if (url != null && url.length() > 0) {
        File f = imgFile(url);
        if (!f.exists() && url.startsWith("https://archive.org/services/img/")) {
          HttpURLConnection hc = (HttpURLConnection) new URL(url).openConnection();
          hc.setConnectTimeout(7000); hc.setReadTimeout(9000); hc.setRequestProperty("User-Agent", UA);
          File tmp = new File(f.getPath() + ".tmp");
          try (InputStream in = hc.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] bf = new byte[8192]; int n; while ((n = in.read(bf)) > 0) out.write(bf, 0, n);
          } finally { hc.disconnect(); }
          tmp.renameTo(f);
        }
        if (f.exists()) return small(null, f.getPath());
      }
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
    @JavascriptInterface public boolean wifi() { return MainActivity.this.wifi(); }
    @JavascriptInterface public void openUrl(final String url) {
      runOnUiThread(new Runnable() { public void run() {
        try { if (url != null && url.startsWith("https://")) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
      } });
    }
    @JavascriptInterface public String outputDevice() {
      try {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        String other = null;
        for (android.media.AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
          int t = d.getType();
          if (t == 8 || t == 7 || t == 26 || t == 27) { CharSequence n = d.getProductName(); return n != null && n.length() > 0 ? n.toString() : "Bluetooth"; }
          if (t == 3 || t == 4) other = "Auriculares con cable"; else if (t == 22 || t == 11) other = "Audio USB"; else if (t == 9) other = "HDMI";
        }
        if (other != null) return other;
      } catch (Exception e) {}
      return "Este teléfono";
    }
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
