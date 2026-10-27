package app.tunebox;
import android.app.*;import android.content.Intent;import android.content.pm.ServiceInfo;import android.os.*;
public class PlayService extends Service {
  public int onStartCommand(Intent i, int f, int s) {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("tb", "Reproducción", NotificationManager.IMPORTANCE_LOW));
    PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "tb") : new Notification.Builder(this);
    String t = i != null && i.getStringExtra("t") != null ? i.getStringExtra("t") : "Reproduciendo";
    Notification n = b.setContentTitle("Tunebox").setContentText(t).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setOngoing(true).build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(1, n);
    return START_STICKY;
  }
  public IBinder onBind(Intent i) { return null; }
}
