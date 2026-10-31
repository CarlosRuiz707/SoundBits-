package app.tunebox;
import android.app.PendingIntent;import android.appwidget.AppWidgetManager;import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;import android.content.Context;import android.content.Intent;import android.widget.RemoteViews;
public class PlayWidget extends AppWidgetProvider {
  static final String P = "app.tunebox.W_";
  public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
  public void onReceive(Context c, Intent i) {
    String a = i.getAction();
    if (a != null && a.startsWith(P)) { if (PlayService.inst != null) PlayService.cmd(a.substring(P.length())); }
    else super.onReceive(c, i);
  }
  static PendingIntent bc(Context c, String cmd) {
    return PendingIntent.getBroadcast(c, cmd.hashCode(), new Intent(c, PlayWidget.class).setAction(P + cmd), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }
  static PendingIntent ac(Context c, String cmd) {
    Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    if (cmd != null) i.putExtra("cmd", cmd);
    return PendingIntent.getActivity(c, ("a" + cmd).hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }
  static void refresh(Context c) {
    try {
      AppWidgetManager m = AppWidgetManager.getInstance(c);
      int[] ids = m.getAppWidgetIds(new ComponentName(c, PlayWidget.class));
      if (ids == null || ids.length == 0) return;
      boolean live = PlayService.inst != null;
      RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
      v.setTextViewText(R.id.w_title, live ? PlayService.title : "SoundBits");
      v.setTextViewText(R.id.w_artist, live ? PlayService.artist : "Toca para reproducir");
      if (live && PlayService.art != null) v.setImageViewBitmap(R.id.w_art, PlayService.art); else v.setImageViewResource(R.id.w_art, R.drawable.ic_launcher);
      v.setImageViewResource(R.id.w_play, (live && PlayService.playing) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
      v.setOnClickPendingIntent(R.id.w_root, ac(c, null));
      v.setOnClickPendingIntent(R.id.w_art, ac(c, null));
      v.setOnClickPendingIntent(R.id.w_prev, live ? bc(c, "prev") : ac(c, "prev"));
      v.setOnClickPendingIntent(R.id.w_play, live ? bc(c, "toggle") : ac(c, "toggle"));
      v.setOnClickPendingIntent(R.id.w_next, live ? bc(c, "next") : ac(c, "next"));
      m.updateAppWidget(ids, v);
    } catch (Exception e) {}
  }
}
