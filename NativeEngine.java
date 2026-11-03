package app.tunebox;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de audio nativo (Media3 ExoPlayer).
 *
 * Vive en el proceso, no en la actividad: si Android destruye la interfaz (WebView),
 * la reproducción continúa y la notificación sigue controlándola. La interfaz envía la cola
 * como filas [src, título, artista, álbum, mid, carátula, clave]; en modo "lista" el índice del
 * motor coincide con la posición en la cola de la interfaz.
 *
 * Todo el acceso al ExoPlayer se hace en el hilo principal.
 */
final class NativeEngine {
  static final Handler H = new Handler(Looper.getMainLooper());
  static ExoPlayer p;
  static Context app;
  static volatile boolean on;                       // hay una cola cargada
  static boolean list;                              // true: el motor posee la cola completa
  static final List<String[]> tracks = new ArrayList<String[]>();   // [src,title,artist,album,mid,raw,k]
  static int errs;
  static boolean shuf, rep, lastP, ticking;
  static float speed = 1f;
  static float eqLo, eqMi, eqHi;
  static Equalizer eq;
  static int eqSid;
  // Instantánea legible desde otros hilos (el puente de la WebView no corre en el hilo principal)
  static volatile String sKey = "";
  static volatile int sIdx, sN;
  static volatile boolean sPlaying, sList;
  static volatile long sPos, sDur;

  static void run(Runnable r) { if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else H.post(r); }
  static boolean active() { return on; }

  /* ---------- puente de estado ---------- */
  static String state() {
    if (!on) return "";
    return "{\"on\":1,\"l\":" + (sList ? 1 : 0) + ",\"i\":" + sIdx + ",\"n\":" + sN + ",\"k\":" + JSONObject.quote(sKey)
      + ",\"p\":" + (sPlaying ? 1 : 0) + ",\"t\":" + sPos + ",\"d\":" + sDur + "}";
  }
  static void emit(String json) {
    MainActivity m = MainActivity.inst;
    if (m != null) m.js("window.npEv&&npEv(" + JSONObject.quote(json) + ")");
  }

  /* ---------- fuentes ---------- */
  static Uri resolve(String src) {
    if (src == null || src.length() == 0) return null;
    Uri u = Uri.parse(src);
    if ("tb.local".equals(u.getHost())) {
      String path = u.getPath() == null ? "" : u.getPath();
      try {
        if (path.startsWith("/a/")) return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(u.getLastPathSegment()));
        if (path.startsWith("/o/")) {
          String id = u.getLastPathSegment();
          if (id != null && id.matches("[a-z0-9]{4,24}")) {
            File f = new File(new File(app.getFilesDir(), "off"), id);
            if (f.isFile()) return Uri.fromFile(f);
          }
          return null;
        }
        if (path.equals("/pf")) {
          String o = u.getQueryParameter("u");
          if (o != null && o.startsWith("https://")) return Uri.parse(o);
        }
      } catch (Exception ignored) {}
      return null;
    }
    if (src.startsWith("https://")) return u;
    if (src.startsWith("http://")) return Uri.parse("https://" + src.substring(7));
    return null;
  }
  static MediaItem item(String[] t) {
    Uri u = resolve(t[0]);
    if (u == null) u = Uri.parse("file:///tunebox/missing");
    return new MediaItem.Builder().setUri(u).setMediaId(t[6]).build();
  }
  static List<String[]> parse(JSONArray a) {
    List<String[]> r = new ArrayList<String[]>();
    for (int i = 0; i < a.length(); i++) {
      JSONArray x = a.optJSONArray(i);
      String[] t = new String[7];
      for (int j = 0; j < 7; j++) t[j] = x == null ? "" : x.optString(j, "");
      r.add(t);
    }
    return r;
  }
  static List<MediaItem> items(List<String[]> ts) {
    List<MediaItem> r = new ArrayList<MediaItem>();
    for (String[] t : ts) r.add(item(t));
    return r;
  }
  static String[] cur() {
    if (p == null) return null;
    int i = p.getCurrentMediaItemIndex();
    return i >= 0 && i < tracks.size() ? tracks.get(i) : null;
  }

  /* ---------- reproductor ---------- */
  static void ensure(Context c) {
    if (p != null) return;
    app = c.getApplicationContext();
    DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true).setUserAgent("Mozilla/5.0 (Linux; Android) TuneBox")
      .setConnectTimeoutMs(10000).setReadTimeoutMs(15000);
    p = new ExoPlayer.Builder(app)
      .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app, http)))
      .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .build();
    p.addListener(new Player.Listener() {
      public void onPlayWhenReadyChanged(boolean pw, int reason) { statePush(); }
      public void onPlaybackStateChanged(int st) {
        if (st == Player.STATE_READY) {
          errs = 0;
          long d = p.getDuration();
          emit("{\"e\":\"meta\",\"d\":" + (d > 0 ? String.valueOf(d / 1000.0) : "0") + "}");
        } else if (st == Player.STATE_ENDED) emit("{\"e\":\"ended\"}");
        statePush();
      }
      public void onMediaItemTransition(MediaItem mi, int reason) { trackChanged(); }
      public void onPlayerError(PlaybackException e) { fail(); }
      public void onAudioSessionIdChanged(int sid) { applyEq(); }
    });
    startTicker();
  }
  static void applyOpts() {
    if (p == null) return;
    p.setShuffleModeEnabled(shuf);
    p.setRepeatMode(rep ? Player.REPEAT_MODE_ONE : (list ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF));
    p.setPlaybackSpeed(speed);
  }
  static void snap() {
    if (p == null) return;
    sIdx = p.getCurrentMediaItemIndex();
    sN = p.getMediaItemCount();
    sList = list;
    sPos = Math.max(0L, p.getCurrentPosition());
    long d = p.getDuration();
    sDur = d > 0 ? d : 0L;
    String[] t = cur();
    sKey = t == null ? "" : t[6];
    sPlaying = lastP;
  }
  static void kick() {
    if (app == null) return;
    if (PlayService.inst == null) {
      try {
        Intent i = new Intent(app, PlayService.class);
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i); else app.startService(i);
      } catch (Exception ignored) {}
    } else PlayService.inst.refresh();
  }
  static void statePush() {
    if (p == null) return;
    int st = p.getPlaybackState();
    boolean pl = p.getPlayWhenReady() && (st == Player.STATE_BUFFERING || st == Player.STATE_READY);
    boolean changed = pl != lastP;
    lastP = pl;
    snap();
    PlayService.playing = pl;
    PlayService.posMs = sPos;
    PlayService.durMs = sDur;
    if (changed) emit("{\"e\":\"st\",\"p\":" + (pl ? "true" : "false") + "}");
    kick();
  }
  static void trackChanged() {
    if (p == null) return;
    snap();
    emit("{\"e\":\"track\",\"i\":" + p.getCurrentMediaItemIndex() + "}");
    String[] t = cur();
    // Si la interfaz no está viva, la notificación se alimenta desde aquí
    if (t != null && MainActivity.inst == null) {
      PlayService.title = t[1]; PlayService.artist = t[2]; PlayService.album = t[3];
      final String key = "n|" + t[4];
      if (!key.equals(PlayService.artKey)) {
        PlayService.artKey = key; PlayService.art = null;
        loadArt(t[4], key);
      }
    }
    statePush();
  }
  static void loadArt(final String mid, final String key) {
    if (Build.VERSION.SDK_INT < 29 || app == null) return;
    new Thread(new Runnable() { public void run() {
      try {
        long id = Long.parseLong(mid);
        if (id <= 0) return;
        Uri u = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        Bitmap b = app.getContentResolver().loadThumbnail(u, new Size(256, 256), null);
        if (b != null && key.equals(PlayService.artKey)) { PlayService.art = b; kick(); }
      } catch (Exception ignored) {}
    } }, "np-art").start();
  }
  static void fail() {
    errs++;
    if (MainActivity.inst != null) { emit("{\"e\":\"err\"}"); return; }
    if (list && errs < 6) next();
  }
  static final Runnable TICK = new Runnable() { public void run() {
    if (p == null) { ticking = false; return; }
    snap();
    PlayService.posMs = sPos; PlayService.durMs = sDur;
    if (lastP && MainActivity.inst != null && MainActivity.vis)
      emit("{\"e\":\"time\",\"t\":" + (sPos / 1000.0) + ",\"d\":" + (sDur / 1000.0) + "}");
    H.postDelayed(this, 500);
  } };
  static void startTicker() { if (!ticking) { ticking = true; H.postDelayed(TICK, 500); } }

  /* ---------- ecualizador (graves / medios / agudos) ---------- */
  static void applyEq() {
    try {
      if (p == null) return;
      int sid = p.getAudioSessionId();
      if (sid == C.AUDIO_SESSION_ID_UNSET) return;
      boolean flat = eqLo == 0f && eqMi == 0f && eqHi == 0f;
      if (eq == null || eqSid != sid) {
        if (flat) return;
        if (eq != null) { try { eq.release(); } catch (Exception ignored) {} }
        eq = new Equalizer(0, sid);
        eqSid = sid;
      }
      eq.setEnabled(!flat);
      short[] r = eq.getBandLevelRange();
      short n = eq.getNumberOfBands();
      for (short b = 0; b < n; b++) {
        int hz = eq.getCenterFreq(b) / 1000;
        float db = hz < 300 ? eqLo : (hz < 4000 ? eqMi : eqHi);
        int lvl = Math.max(r[0], Math.min(r[1], Math.round(db * 100f)));
        eq.setBandLevel(b, (short) lvl);
      }
    } catch (Throwable ignored) {}
  }

  /* ---------- API usada por el puente y por la notificación ---------- */
  static void load(final Context c, final String json) {
    run(new Runnable() { public void run() {
      try {
        JSONObject o = new JSONObject(json);
        ensure(c);
        List<String[]> ts = parse(o.getJSONArray("l"));
        if (ts.isEmpty()) return;
        int idx = o.optInt("i", 0);
        if (idx < 0 || idx >= ts.size()) idx = 0;
        boolean lm = o.optInt("m", 1) == 1;
        shuf = o.optInt("s") == 1;
        rep = o.optInt("r") == 1;
        speed = (float) o.optDouble("v", 1.0);
        JSONArray e = o.optJSONArray("e");
        if (e != null && e.length() == 3) { eqLo = (float) e.optDouble(0, 0); eqMi = (float) e.optDouble(1, 0); eqHi = (float) e.optDouble(2, 0); }
        tracks.clear();
        list = lm;
        if (lm) { tracks.addAll(ts); p.setMediaItems(items(ts), idx, 0L); }
        else { tracks.add(ts.get(idx)); p.setMediaItem(item(ts.get(idx))); }
        errs = 0;
        on = true;
        applyOpts();
        p.prepare();
        p.play();
        applyEq();
        trackChanged();
      } catch (Exception ex) { emit("{\"e\":\"err\"}"); }
    } });
  }
  /* Cambios en la cola mientras suena: se conserva el elemento actual sin cortar el audio */
  static void sync(final String json) {
    run(new Runnable() { public void run() {
      try {
        if (p == null || !on) return;
        JSONObject o = new JSONObject(json);
        int cur = p.getCurrentMediaItemIndex(), n = p.getMediaItemCount();
        if (o.optInt("m", 1) == 0) {          // pasar a modo individual: solo queda el actual
          String[] t = cur();
          if (cur + 1 < n) p.removeMediaItems(cur + 1, n);
          if (cur > 0) p.removeMediaItems(0, cur);
          tracks.clear();
          if (t != null) tracks.add(t);
          list = false;
          applyOpts();
          snap();
          return;
        }
        List<String[]> ts = parse(o.getJSONArray("l"));
        int idx = o.optInt("i", 0);
        if (idx < 0 || idx >= ts.size()) return;
        List<MediaItem> its = items(ts);
        MediaItem ci = p.getCurrentMediaItem();
        if (list && ci != null && cur >= 0 && ci.mediaId.equals(ts.get(idx)[6])) {
          if (cur + 1 < n) p.removeMediaItems(cur + 1, n);
          if (cur > 0) p.removeMediaItems(0, cur);
          if (idx > 0) p.addMediaItems(0, new ArrayList<MediaItem>(its.subList(0, idx)));
          if (idx + 1 < its.size()) p.addMediaItems(new ArrayList<MediaItem>(its.subList(idx + 1, its.size())));
        } else {
          boolean pw = p.getPlayWhenReady();
          p.setMediaItems(its, idx, 0L);
          p.prepare();
          p.setPlayWhenReady(pw);
        }
        tracks.clear();
        tracks.addAll(ts);
        list = true;
        applyOpts();
        snap();
      } catch (Exception ignored) {}
    } });
  }
  static void jump(final int i) {
    run(new Runnable() { public void run() {
      if (p == null || !on || !list || i < 0 || i >= p.getMediaItemCount()) return;
      p.seekTo(i, 0L);
      if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
      p.play();
    } });
  }
  static void play() {
    run(new Runnable() { public void run() {
      if (p == null || !on) return;
      if (p.getPlaybackState() == Player.STATE_ENDED) p.seekTo(0L);
      if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
      p.play();
    } });
  }
  static void pause() { run(new Runnable() { public void run() { if (p != null) p.pause(); } }); }
  static void seek(final long ms) {
    run(new Runnable() { public void run() { if (p != null && on) { p.seekTo(Math.max(0L, ms)); snap(); } } });
  }
  static void next() {
    run(new Runnable() { public void run() {
      if (p == null || !on) return;
      if (list) {
        if (p.hasNextMediaItem()) p.seekToNextMediaItem(); else p.seekTo(0, 0L);
        if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
        p.play();
      } else if (MainActivity.inst != null) MainActivity.inst.js("window.tbCmd&&tbCmd('next')");
    } });
  }
  static void prev() {
    run(new Runnable() { public void run() {
      if (p == null || !on) return;
      if (list) {
        if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem(); else p.seekTo(Math.max(0, p.getMediaItemCount() - 1), 0L);
        if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
        p.play();
      } else if (MainActivity.inst != null) MainActivity.inst.js("window.tbCmd&&tbCmd('prev')");
    } });
  }
  static void opts(final boolean s, final boolean r, final double sp) {
    run(new Runnable() { public void run() { shuf = s; rep = r; speed = (float) sp; applyOpts(); } });
  }
  static void eq(final double lo, final double mi, final double hi) {
    run(new Runnable() { public void run() { eqLo = (float) lo; eqMi = (float) mi; eqHi = (float) hi; applyEq(); } });
  }
  /* Órdenes de la notificación, auriculares y Bluetooth */
  static void cmd(final String c) {
    run(new Runnable() { public void run() {
      if (p == null || !on) return;
      if ("play".equals(c)) play();
      else if ("pause".equals(c)) pause();
      else if ("toggle".equals(c)) { if (lastP) pause(); else play(); }
      else if ("next".equals(c)) next();
      else if ("prev".equals(c)) { if (p.getCurrentPosition() > 3000) p.seekTo(0L); else prev(); }
    } });
  }
  static void stop() {
    run(new Runnable() { public void run() {
      boolean was = on;
      on = false; list = false; lastP = false; sPlaying = false; sKey = "";
      tracks.clear();
      if (eq != null) { try { eq.release(); } catch (Exception ignored) {} eq = null; }
      if (p != null) { try { p.stop(); p.release(); } catch (Exception ignored) {} p = null; }
      if (was) {
        PlayService.playing = false;
        if (PlayService.inst != null) PlayService.inst.refresh();
        emit("{\"e\":\"st\",\"p\":false}");
      }
    } });
  }
}
