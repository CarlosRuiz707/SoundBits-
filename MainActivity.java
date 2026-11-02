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
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.xmlpull.v1.XmlPullParser;

public class MainActivity extends Activity {
  static MainActivity inst;
  String pendingCmd;
  WebView w;
  ValueCallback<Uri[]> cb;
  volatile boolean wifiOnly = true;
  String artKey = "";
  Bitmap artBm;
  FirebaseAuth firebaseAuth;
  CredentialManager credentialManager;

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    inst = this;
    initAuthentication();
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
        publishAuthState();
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

  private void initAuthentication() {
    try {
      if (!FirebaseApp.getApps(this).isEmpty()) {
        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(getBaseContext());
      }
    } catch (Exception e) {
      firebaseAuth = null;
      credentialManager = null;
    }
  }

  private boolean authConfigured() {
    return firebaseAuth != null && credentialManager != null;
  }

  private JSONObject authStateJson() {
    JSONObject out = new JSONObject();
    try {
      FirebaseUser u = authConfigured() ? firebaseAuth.getCurrentUser() : null;
      out.put("configured", authConfigured());
      out.put("signedIn", u != null);
      if (u != null) {
        out.put("uid", u.getUid());
        out.put("name", u.getDisplayName() == null ? "" : u.getDisplayName());
        out.put("email", u.getEmail() == null ? "" : u.getEmail());
        out.put("photo", u.getPhotoUrl() == null ? "" : u.getPhotoUrl().toString());
        out.put("provider", "google.com");
      }
    } catch (Exception e) {
      try { out.put("configured", false); out.put("signedIn", false); } catch (Exception ignored) {}
    }
    return out;
  }

  private void publishAuthState() {
    js("window.onNativeAuthChanged&&window.onNativeAuthChanged(" + authStateJson().toString() + ")");
  }

  private void startGoogleSignIn() {
    if (!authConfigured()) {
      js("window.onNativeAuthError&&window.onNativeAuthError('Configura Firebase y agrega google-services.json para activar Google Sign-In.')");
      return;
    }
    try {
      GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
          .setFilterByAuthorizedAccounts(false)
          .setServerClientId(getString(getResources().getIdentifier("default_web_client_id", "string", getPackageName())))
          .build();
      GetCredentialRequest request = new GetCredentialRequest.Builder()
          .addCredentialOption(googleOption)
          .build();
      credentialManager.getCredentialAsync(this, request, new CancellationSignal(),
          Executors.newSingleThreadExecutor(), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override public void onResult(GetCredentialResponse result) {
              handleGoogleCredential(result.getCredential());
            }
            @Override public void onError(@NonNull GetCredentialException e) {
              js("window.onNativeAuthError&&window.onNativeAuthError(" + JSONObject.quote(authErrorMessage(e)) + ")");
            }
          });
    } catch (Exception e) {
      js("window.onNativeAuthError&&window.onNativeAuthError(" + JSONObject.quote(authErrorMessage(e)) + ")");
    }
  }

  private String authErrorMessage(Exception e) {
    String m = e == null ? "No se pudo iniciar sesión." : e.getLocalizedMessage();
    return m == null || m.trim().isEmpty() ? "No se pudo iniciar sesión con Google." : m;
  }

  private void handleGoogleCredential(Credential credential) {
    try {
      if (!(credential instanceof CustomCredential) ||
          !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
        js("window.onNativeAuthError&&window.onNativeAuthError('Google no devolvió una credencial válida.')");
        return;
      }
      GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
      AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(google.getIdToken(), null);
      firebaseAuth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
        if (task.isSuccessful()) publishAuthState();
        else js("window.onNativeAuthError&&window.onNativeAuthError(" + JSONObject.quote(authErrorMessage(task.getException())) + ")");
      });
    } catch (Exception e) {
      js("window.onNativeAuthError&&window.onNativeAuthError(" + JSONObject.quote(authErrorMessage(e)) + ")");
    }
  }

  private void signOutGoogle() {
    if (!authConfigured()) return;
    firebaseAuth.signOut();
    ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
    credentialManager.clearCredentialStateAsync(clearRequest, new CancellationSignal(),
        Executors.newSingleThreadExecutor(), new CredentialManagerCallback<Void, ClearCredentialException>() {
          @Override public void onResult(@NonNull Void result) { publishAuthState(); }
          @Override public void onError(@NonNull ClearCredentialException e) { publishAuthState(); }
        });
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

  static final String UA = "SoundBits/1.8";

  String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
  String httpGet(String url) throws IOException {
    HttpURLConnection hc = (HttpURLConnection) new URL(url).openConnection();
    hc.setConnectTimeout(8000); hc.setReadTimeout(10000);
    hc.setRequestProperty("User-Agent", UA);
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try (InputStream in = hc.getInputStream()) { byte[] bf = new byte[8192]; int n; while ((n = in.read(bf)) > 0) bo.write(bf, 0, n); }
    return bo.toString("UTF-8");
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

  /* ---------- Offline storage (internal app storage) ---------- */
  static final String UA2 = "SoundBits/1.9 (https://github.com/CarlosRuiz707/Tunebox-v-0.1)";
  final Set<String> offBusy = Collections.synchronizedSet(new HashSet<String>());
  final ExecutorService offPool = Executors.newSingleThreadExecutor();

  File offDir() { File d = new File(getFilesDir(), "off"); d.mkdirs(); return d; }
  boolean okId(String id) { return id != null && id.matches("[a-z0-9]{4,24}"); }
  String readAll(InputStream in) throws IOException {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    byte[] bf = new byte[8192]; int n;
    while ((n = in.read(bf)) > 0) bo.write(bf, 0, n);
    return bo.toString("UTF-8");
  }
  String enc2(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
  String httpGet2(String url) throws IOException {
    HttpURLConnection hc = (HttpURLConnection) new URL(url).openConnection();
    hc.setConnectTimeout(8000); hc.setReadTimeout(12000);
    hc.setRequestProperty("User-Agent", UA2);
    try (InputStream in = hc.getInputStream()) { return readAll(in); } finally { hc.disconnect(); }
  }
  /* Follows redirects by hand and only over https */
  HttpURLConnection openFollow(String raw, String range) throws IOException {
    String cur = raw;
    for (int hop = 0; hop < 6; hop++) {
      if (!cur.startsWith("https://")) throw new IOException("https only");
      HttpURLConnection hc = (HttpURLConnection) new URL(cur).openConnection();
      hc.setInstanceFollowRedirects(false);
      hc.setConnectTimeout(12000); hc.setReadTimeout(20000);
      hc.setRequestProperty("User-Agent", UA2);
      if (range != null) hc.setRequestProperty("Range", range);
      int code = hc.getResponseCode();
      if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
        String loc = hc.getHeaderField("Location");
        hc.disconnect();
        if (loc == null) throw new IOException("redirect");
        cur = new URL(new URL(cur), loc).toString();
        continue;
      }
      return hc;
    }
    throw new IOException("too many redirects");
  }
  void offEvt(String id, String st, long v) {
    js("window.tbOff&&tbOff('" + (okId(id) ? id : "") + "','" + st + "'," + v + ")");
  }
  void downloadOff(final String id, final String url) {
    if (!okId(id) || url == null || !url.startsWith("https://")) { offEvt(id, "err", 0); return; }
    if (wifiOnly && !wifi()) { offEvt(id, "wifi", 0); return; }
    if (!offBusy.add(id)) return;
    offPool.execute(new Runnable() { public void run() {
      File dir = offDir();
      File fin = new File(dir, id);
      File tmp = new File(dir, id + ".tmp");
      HttpURLConnection hc = null;
      try {
        if (fin.exists()) { offEvt(id, "ok", fin.length()); return; }
        hc = openFollow(url, null);
        if (hc.getResponseCode() != 200) throw new IOException("http");
        long total = hc.getContentLengthLong(), got = 0, last = 0, cap = 600L * 1024 * 1024;
        if (total > cap) throw new IOException("big");
        try (InputStream in = hc.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
          byte[] bf = new byte[16384]; int n;
          while ((n = in.read(bf)) > 0) {
            out.write(bf, 0, n); got += n;
            if (got > cap) throw new IOException("big");
            long now = System.currentTimeMillis();
            if (total > 0 && now - last > 700) { last = now; offEvt(id, "p", got * 100 / total); }
          }
        }
        if (got <= 0 || !tmp.renameTo(fin)) throw new IOException("save");
        offEvt(id, "ok", fin.length());
      } catch (Exception e) {
        tmp.delete(); offEvt(id, "err", 0);
      } finally {
        if (hc != null) hc.disconnect();
        offBusy.remove(id);
      }
    } });
  }
  WebResourceResponse serveFile(WebResourceRequest r, Map<String, String> hd, File f) throws IOException {
    long len = f.length(), st = 0, en = len - 1;
    int code = 200;
    String rg = r.getRequestHeaders().get("Range");
    if (rg != null && rg.startsWith("bytes=")) {
      String[] p = rg.substring(6).split("-");
      st = Long.parseLong(p[0].trim());
      if (p.length > 1 && p[1].trim().length() > 0) en = Long.parseLong(p[1].trim());
      if (en >= len) en = len - 1;
      code = 206;
    }
    if (len <= 0 || st > en) return err(hd, 404);
    FileInputStream fi = new FileInputStream(f);
    long skipped = 0;
    while (skipped < st) { long k = fi.skip(st - skipped); if (k <= 0) break; skipped += k; }
    final long remain = en - st + 1;
    InputStream lim = new FilterInputStream(fi) {
      long left = remain;
      public int read() throws IOException { if (left <= 0) return -1; int b = super.read(); if (b >= 0) left--; return b; }
      public int read(byte[] b, int o, int l) throws IOException { if (left <= 0) return -1; int n = super.read(b, o, (int) Math.min(l, left)); if (n > 0) left -= n; return n; }
    };
    hd.put("Accept-Ranges", "bytes");
    hd.put("Content-Length", String.valueOf(remain));
    if (code == 206) hd.put("Content-Range", "bytes " + st + "-" + en + "/" + len);
    return new WebResourceResponse("audio/mpeg", null, code, code == 206 ? "Partial Content" : "OK", hd, lim);
  }
  /* Podcast enclosures: any https host, streamed through the app so the audio element gets CORS headers */
  WebResourceResponse proxyMedia(WebResourceRequest req, Map<String, String> headers) throws IOException {
    String raw = req.getUrl().getQueryParameter("u");
    if (raw == null || !raw.startsWith("https://")) return err(headers, 404);
    final HttpURLConnection hc = openFollow(raw, req.getRequestHeaders().get("Range"));
    int code = hc.getResponseCode();
    if (code != 200 && code != 206) { hc.disconnect(); return err(headers, 503); }
    String type = hc.getContentType();
    type = type == null ? "audio/mpeg" : type.split(";")[0].trim();
    if (!type.startsWith("audio/")) type = "audio/mpeg";
    String length = hc.getHeaderField("Content-Length"), cr = hc.getHeaderField("Content-Range");
    headers.put("Accept-Ranges", "bytes"); headers.put("Cache-Control", "no-store");
    if (length != null) headers.put("Content-Length", length);
    if (cr != null) headers.put("Content-Range", cr);
    InputStream body = new FilterInputStream(hc.getInputStream()) {
      public void close() throws IOException { try { super.close(); } finally { hc.disconnect(); } }
    };
    return new WebResourceResponse(type, null, code, code == 206 ? "Partial Content" : "OK", headers, body);
  }
  WebResourceResponse jsonResp(Map<String, String> hd, String body) throws IOException {
    return new WebResourceResponse("application/json", "utf-8", 200, "OK", hd, new ByteArrayInputStream(body.getBytes("UTF-8")));
  }
  /* Official embedded players only (YouTube, Mixcloud). Ads and controls stay untouched. */
  WebResourceResponse embedPage(String url, Map<String, String> hd) throws IOException {
    String[] ok = {"https://www.youtube-nocookie.com/embed/", "https://www.youtube.com/embed/", "https://www.mixcloud.com/widget/iframe/"};
    boolean good = false;
    if (url != null) for (String p : ok) if (url.startsWith(p)) good = true;
    if (!good) return err(hd, 404);
    String safe = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>html,body{margin:0;height:100%;background:#000}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head><body><iframe src=\"" + safe + "\" allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\" allowfullscreen></iframe></body></html>";
    hd.put("Cache-Control", "no-store");
    return new WebResourceResponse("text/html", "utf-8", 200, "OK", hd, new ByteArrayInputStream(html.getBytes("UTF-8")));
  }
  int parseDur(String s) {
    try {
      if (s.contains(":")) { int t = 0; for (String x : s.split(":")) t = t * 60 + Integer.parseInt(x.trim().split("\\.")[0]); return t; }
      return (int) Double.parseDouble(s);
    } catch (Exception e) { return 0; }
  }
  JSONObject parseFeed(String feedUrl) throws Exception {
    HttpURLConnection hc = openFollow(feedUrl, null);
    if (hc.getResponseCode() != 200) throw new IOException("feed");
    JSONObject out = new JSONObject();
    JSONArray eps = new JSONArray();
    String showTitle = "", showArt = "", author = "", tag = "";
    boolean inItem = false, inImage = false;
    JSONObject cur = null;
    try (InputStream in = hc.getInputStream()) {
      XmlPullParser xp = android.util.Xml.newPullParser();
      xp.setInput(in, null);
      int ev = xp.getEventType();
      while (ev != XmlPullParser.END_DOCUMENT && eps.length() < 60) {
        if (ev == XmlPullParser.START_TAG) {
          tag = xp.getName();
          if (tag.equals("item")) { inItem = true; cur = new JSONObject(); }
          else if (tag.equals("image") && !inItem) inImage = true;
          else if (tag.equals("itunes:image") && xp.getAttributeValue(null, "href") != null) {
            String h = xp.getAttributeValue(null, "href");
            if (inItem && cur != null) cur.put("a", h); else if (showArt.isEmpty()) showArt = h;
          } else if (tag.equals("enclosure") && inItem && cur != null) {
            String eu = xp.getAttributeValue(null, "url");
            if (eu != null) cur.put("u", eu);
          }
        } else if (ev == XmlPullParser.TEXT) {
          String tx = xp.getText() == null ? "" : xp.getText().trim();
          if (!tx.isEmpty()) {
            if (inItem && cur != null) {
              if (tag.equals("title") && !cur.has("t")) cur.put("t", tx);
              else if (tag.equals("itunes:duration")) cur.put("d", parseDur(tx));
              else if (tag.equals("pubDate")) cur.put("p", tx);
              else if (tag.equals("link") && !cur.has("l")) cur.put("l", tx);
            } else if (!inItem) {
              if (tag.equals("title") && showTitle.isEmpty() && !inImage) showTitle = tx;
              else if (tag.equals("url") && inImage && showArt.isEmpty()) showArt = tx;
              else if (tag.equals("itunes:author") && author.isEmpty()) author = tx;
            }
          }
        } else if (ev == XmlPullParser.END_TAG) {
          String nm = xp.getName();
          if (nm.equals("item")) { inItem = false; if (cur != null && cur.has("u") && cur.has("t")) eps.put(cur); cur = null; }
          else if (nm.equals("image")) inImage = false;
          tag = "";
        }
        ev = xp.next();
      }
    } finally { hc.disconnect(); }
    out.put("title", showTitle); out.put("author", author); out.put("art", showArt); out.put("eps", eps);
    return out;
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
      if (path.equals("/pf")) return proxyMedia(r, hd);
      if (path.startsWith("/o/")) {
        String oid = u.getLastPathSegment();
        File of = okId(oid) ? new File(offDir(), oid) : null;
        if (of == null || !of.isFile()) return err(hd, 404);
        return serveFile(r, hd, of);
      }
      if (path.equals("/em")) return embedPage(u.getQueryParameter("u"), hd);
      if (path.equals("/is")) return jsonResp(hd, httpGet2("https://itunes.apple.com/search?media=music&entity=song&limit=25&term=" + enc2(u.getQueryParameter("q"))));
      if (path.equals("/ps")) return jsonResp(hd, httpGet2("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=20&term=" + enc2(u.getQueryParameter("q"))));
      if (path.equals("/mc")) return jsonResp(hd, httpGet2("https://api.mixcloud.com/search/?type=cloudcast&limit=10&q=" + enc2(u.getQueryParameter("q"))));
      if (path.equals("/pod")) {
        String fu = u.getQueryParameter("u");
        if (fu == null) return err(hd, 404);
        return jsonResp(hd, parseFeed(fu.replaceFirst("^http://", "https://")).toString());
      }
      if (path.equals("/s")) {
        String yurl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true&videoCategoryId=10&maxResults=15&q="
          + enc2(u.getQueryParameter("q")) + "&key=" + enc2(u.getQueryParameter("key"));
        HttpURLConnection yc = (HttpURLConnection) new URL(yurl).openConnection();
        yc.setConnectTimeout(8000); yc.setReadTimeout(12000);
        int ycode = yc.getResponseCode();
        InputStream yis = ycode >= 400 ? yc.getErrorStream() : yc.getInputStream();
        if (yis == null) yis = new ByteArrayInputStream("{}".getBytes());
        return new WebResourceResponse("application/json", "utf-8", ycode, ycode >= 400 ? "Error" : "OK", hd, yis);
      }
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
    @JavascriptInterface public String getAuthState() { return authStateJson().toString(); }
    @JavascriptInterface public void signInGoogle() { runOnUiThread(new Runnable() { public void run() { startGoogleSignIn(); } }); }
    @JavascriptInterface public void signOutGoogle() { runOnUiThread(new Runnable() { public void run() { MainActivity.this.signOutGoogle(); } }); }
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
    @JavascriptInterface public void download(String id, String url) { downloadOff(id, url); }
    @JavascriptInterface public boolean hasOffline(String id) { return okId(id) && new File(offDir(), id).isFile(); }
    @JavascriptInterface public void removeOffline(String id) { if (okId(id)) { new File(offDir(), id).delete(); new File(offDir(), id + ".tmp").delete(); } }
    @JavascriptInterface public double offlineMB() {
      long t = 0; File[] fs = offDir().listFiles();
      if (fs != null) for (File x : fs) t += x.length();
      return Math.round(t / 104857.6) / 10.0;
    }
    @JavascriptInterface public void syncOffline(String json) {
      try {
        JSONArray a = new JSONArray(json);
        HashSet<String> keep = new HashSet<String>();
        for (int i = 0; i < a.length(); i++) keep.add(a.getString(i));
        File[] fs = offDir().listFiles();
        if (fs != null) for (File x : fs) {
          String n = x.getName();
          boolean tmp = n.endsWith(".tmp");
          if (tmp ? !offBusy.contains(n.substring(0, n.length() - 4)) : !keep.contains(n)) x.delete();
        }
      } catch (Exception e) {}
    }
    @JavascriptInterface public void clearOffline() { File[] fs = offDir().listFiles(); if (fs != null) for (File x : fs) x.delete(); }
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
