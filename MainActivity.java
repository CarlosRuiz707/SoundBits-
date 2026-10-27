package app.tunebox;
import android.app.Activity;import android.content.*;import android.database.Cursor;import android.content.res.AssetFileDescriptor;
import android.net.Uri;import android.os.*;import android.provider.MediaStore;import android.webkit.*;
import org.json.*;import java.io.*;import java.util.*;
public class MainActivity extends Activity {
  WebView w; ValueCallback<Uri[]> cb;
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
  class Br {
    @JavascriptInterface public String scan() {
      JSONArray a = new JSONArray();
      try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION},
          MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
        while (c != null && c.moveToNext()) {
          JSONObject o = new JSONObject();
          o.put("mid", c.getLong(0)); o.put("title", c.getString(1)); o.put("artist", c.getString(2)); o.put("album", c.getString(3)); o.put("dur", c.getLong(4) / 1000);
          a.put(o);
        }
      } catch (Exception e) {}
      return a.toString();
    }
    @JavascriptInterface public void keep(boolean on, String t) {
      try {
        Intent i = new Intent(MainActivity.this, PlayService.class).putExtra("t", t);
        if (on) { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } else stopService(i);
      } catch (Exception e) {}
    }
  }
}
