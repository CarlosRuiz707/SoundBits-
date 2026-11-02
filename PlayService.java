package app.tunebox;
import android.app.*;import android.content.Intent;import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;import android.graphics.drawable.Icon;import android.media.MediaMetadata;
import android.media.session.MediaSession;import android.media.session.PlaybackState;import android.os.*;
public class PlayService extends Service {
  static PlayService inst;
  static String title = "Tunebox", artist = "", album = "", artKey = "";
  static boolean playing = false; static Bitmap art; static long posMs = 0, durMs = 0;
  MediaSession ms; NotificationManager nm; Handler h = new Handler(Looper.getMainLooper()); boolean fg = false;
  public void onCreate() {
    super.onCreate(); inst = this;
    nm = getSystemService(NotificationManager.class);
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("tb", "Reproducción", NotificationManager.IMPORTANCE_LOW));
    ms = new MediaSession(this, "Tunebox");
    ms.setCallback(new MediaSession.Callback() {
      public void onPlay() { cmd("play"); }
      public void onPause() { cmd("pause"); }
      public void onSkipToNext() { cmd("next"); }
      public void onSkipToPrevious() { cmd("prev"); }
      public void onSeekTo(long p) { MainActivity m = MainActivity.inst; if (m != null) m.js("window.tbSeek&&tbSeek(" + (p / 1000.0) + ")"); }
    });
    ms.setActive(true);
  }
  static void cmd(String c) { MainActivity m = MainActivity.inst; if (m != null) m.js("window.tbCmd&&tbCmd('" + c + "')"); }
  public int onStartCommand(Intent i, int f, int s) {
    String a = i == null ? null : i.getAction();
    if (a == null) push();
    else if (a.equals("stop")) { cmd("pause"); stopSelf(); }
    else cmd(a);
    return START_NOT_STICKY;
  }
  void refresh() { h.post(new Runnable() { public void run() { push(); } }); }
  PendingIntent act(String a) { return PendingIntent.getService(this, a.hashCode(), new Intent(this, PlayService.class).setAction(a), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT); }
  Notification.Action na(int ic, String t, String a) { return new Notification.Action.Builder(Icon.createWithResource(this, ic), t, act(a)).build(); }
  void push() {
    MediaMetadata.Builder mb = new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, artist).putString(MediaMetadata.METADATA_KEY_ALBUM, album).putLong(MediaMetadata.METADATA_KEY_DURATION, durMs);
    if (art != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
    ms.setMetadata(mb.build());
    ms.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
      .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, posMs, playing ? 1f : 0f).build());
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "tb") : new Notification.Builder(this);
    PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
    b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(artist).setSubText(album).setContentIntent(open)
      .setDeleteIntent(act("stop")).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC).setShowWhen(false);
    if (art != null) b.setLargeIcon(art);
    b.addAction(na(android.R.drawable.ic_media_previous, "Anterior", "prev"))
     .addAction(na(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pausa" : "Reproducir", "toggle"))
     .addAction(na(android.R.drawable.ic_media_next, "Siguiente", "next"));
    b.setStyle(new Notification.MediaStyle().setMediaSession(ms.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
    Notification n = b.build();
    try {
      if (playing || !fg) { if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(1, n); fg = true; }
      if (!playing) { stopForeground(false); fg = false; nm.notify(1, n); }
    } catch (Exception e) { nm.notify(1, n); }
    PlayWidget.refresh(this);
  }
  public void onDestroy() { inst = null; try { ms.release(); } catch (Exception e) {} PlayWidget.refresh(this); super.onDestroy(); }
  public IBinder onBind(Intent i) { return null; }
}
