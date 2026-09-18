package com.praveen.liveisland;

import android.animation.ValueAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import java.util.List;
import java.util.Locale;

public class IslandService extends Service implements IslandOverlayView.Listener {
    public enum AnchorEdge { TOP, LEFT, RIGHT, BOTTOM }
    public static final String ACTION_EVENT = "com.praveen.liveisland.EVENT";
    public static final String ACTION_CLEAR = "com.praveen.liveisland.CLEAR";
    public static final String ACTION_REPOSITION = "com.praveen.liveisland.REPOSITION";
    public static final String ACTION_START_TIMER = "com.praveen.liveisland.START_TIMER";
    private static final String CHANNEL="live_island_service";

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private IslandOverlayView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private StatusBarController statusBar;
    private IslandPreferences prefs;
    private IslandEvent current = IslandEvent.idle();
    private boolean expanded;
    private float expansion;
    private int screenW, screenH;
    private int anchorX, anchorY;
    private AnchorEdge edge = AnchorEdge.TOP;
    private int collapsedW, collapsedH, expandedW, expandedH;
    private long timerEnd;
    private boolean timerPaused;
    private long timerRemain;
    private final Runnable timerTick = this::tickTimer;

    public static void start(Context c) {
        Intent i = new Intent(c, IslandService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void pushEvent(Context c, IslandEvent e) {
        IslandPreferences p = new IslandPreferences(c);
        if (!p.enabled()) return;
        Intent i = new Intent(c, IslandService.class).setAction(ACTION_EVENT);
        i.putExtra("type", e.type.name()).putExtra("title",e.title).putExtra("subtitle",e.subtitle).putExtra("progress",e.progress).putExtra("key",e.notificationKey);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void clearEvent(Context c, String key) {
        IslandPreferences p = new IslandPreferences(c); if (!p.enabled()) return;
        Intent i = new Intent(c, IslandService.class).setAction(ACTION_CLEAR).putExtra("key",key);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = new IslandPreferences(this);
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        statusBar = new StatusBarController(this);
        createChannel();
        android.app.Notification n = new android.app.Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Live Island active")
                .setContentText("Anchored to the camera cutout").setOngoing(true).build();
        startForeground(4107,n);
        ShizukuBridge.ensureBound(this, null);
        registerReceiver(systemReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter pwr = new IntentFilter(); pwr.addAction(Intent.ACTION_POWER_CONNECTED); pwr.addAction(Intent.ACTION_POWER_DISCONNECTED); registerReceiver(systemReceiver,pwr);
        if (Settings.canDrawOverlays(this)) addOverlay();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL,"Live Island service",NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private void addOverlay() {
        if (view != null) return;
        view = new IslandOverlayView(this); view.setListener(this); view.setEvent(current);
        lp = new WindowManager.LayoutParams(dp(88),dp(34),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        recalcGeometry();
        try { wm.addView(view, lp); } catch (Throwable ignored) { view = null; }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.enabled()) { stopSelf(); return START_NOT_STICKY; }
        if (view == null && Settings.canDrawOverlays(this)) addOverlay();
        if (intent == null || intent.getAction() == null) return START_STICKY;
        String a=intent.getAction();
        if (ACTION_EVENT.equals(a)) {
            IslandEvent.Type t; try { t=IslandEvent.Type.valueOf(intent.getStringExtra("type")); } catch(Throwable x){t=IslandEvent.Type.OTHER;}
            IslandEvent incoming = new IslandEvent(t,intent.getStringExtra("title"),intent.getStringExtra("subtitle"),intent.getIntExtra("progress",-1),intent.getStringExtra("key"));
            if (current.type == IslandEvent.Type.IDLE || priority(incoming.type) >= priority(current.type) || (incoming.notificationKey != null && incoming.notificationKey.equals(current.notificationKey))) {
                current = incoming;
                if (view!=null) view.setEvent(current);
                if (t==IslandEvent.Type.CHARGING || t==IslandEvent.Type.BIOMETRIC || t==IslandEvent.Type.NFC || t==IslandEvent.Type.PAYMENT) autoClear(3500);
            }
        } else if (ACTION_CLEAR.equals(a)) {
            String key=intent.getStringExtra("key"); if (current.notificationKey!=null && current.notificationKey.equals(key)) setIdle();
        } else if (ACTION_REPOSITION.equals(a)) recalcGeometry();
        else if (ACTION_START_TIMER.equals(a)) startTimer(intent.getLongExtra("seconds",300));
        return START_STICKY;
    }

    private void setIdle() { current=IslandEvent.idle(); if(view!=null)view.setEvent(current); if(expanded) animateExpanded(false); }
    private void autoClear(long ms) { main.removeCallbacks(autoClearRunnable); main.postDelayed(autoClearRunnable,ms); }
    private final Runnable autoClearRunnable = () -> { if(current.type!=IslandEvent.Type.TIMER && current.type!=IslandEvent.Type.MEDIA && current.type!=IslandEvent.Type.CALL && current.type!=IslandEvent.Type.NAVIGATION) setIdle(); };

    private void startTimer(long sec) {
        timerPaused=false; timerRemain=Math.max(1,sec)*1000L; timerEnd=android.os.SystemClock.elapsedRealtime()+timerRemain;
        main.removeCallbacks(timerTick); main.post(timerTick);
    }
    private void tickTimer() {
        if (timerPaused) return;
        long left=Math.max(0,timerEnd-android.os.SystemClock.elapsedRealtime()); timerRemain=left;
        long s=(left+999)/1000; String f=String.format(Locale.US,"%02d:%02d",s/60,s%60);
        current=new IslandEvent(IslandEvent.Type.TIMER,"Timer",f); if(view!=null)view.setEvent(current);
        if(left<=0){
            try { Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE); if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(600,VibrationEffect.DEFAULT_AMPLITUDE)); else v.vibrate(600); }catch(Throwable ignored){}
            current=new IslandEvent(IslandEvent.Type.ALARM,"Timer finished","Tap to dismiss"); if(view!=null)view.setEvent(current); return;
        }
        main.postDelayed(timerTick,1000);
    }
    private void toggleTimer() {
        if(current.type!=IslandEvent.Type.TIMER) return;
        if(timerPaused){timerPaused=false; timerEnd=android.os.SystemClock.elapsedRealtime()+timerRemain; main.post(timerTick);} else {timerPaused=true; timerRemain=Math.max(0,timerEnd-android.os.SystemClock.elapsedRealtime()); main.removeCallbacks(timerTick); current=new IslandEvent(IslandEvent.Type.TIMER,"Timer paused",formatMs(timerRemain)); if(view!=null)view.setEvent(current);}
    }
    private String formatMs(long ms){long s=(ms+999)/1000;return String.format(Locale.US,"%02d:%02d",s/60,s%60);}

    private void recalcGeometry() {
        if (wm==null || lp==null) return;
        try {
            WindowMetrics m=wm.getCurrentWindowMetrics(); Rect b=m.getBounds(); screenW=b.width(); screenH=b.height();
            DisplayCutout cut=m.getWindowInsets().getDisplayCutout(); Rect hole=null;
            if(cut!=null){List<Rect> r=cut.getBoundingRects(); if(r!=null&&!r.isEmpty()) hole=r.get(0);}
            if(hole!=null && !hole.isEmpty()) {
                anchorX=hole.centerX(); anchorY=hole.centerY();
                int dt=hole.top, dl=hole.left, dr=screenW-hole.right, db=screenH-hole.bottom;
                int min=Math.min(Math.min(dt,db),Math.min(dl,dr));
                edge=min==dl?AnchorEdge.LEFT:min==dr?AnchorEdge.RIGHT:min==db?AnchorEdge.BOTTOM:AnchorEdge.TOP;
            } else fallbackAnchor();
            anchorX += dp(prefs.xOffsetDp()); anchorY += dp(prefs.yOffsetDp());
            if(edge==AnchorEdge.TOP||edge==AnchorEdge.BOTTOM){collapsedW=dp(88);collapsedH=dp(34);expandedW=Math.min(dp(370),screenW-dp(12));expandedH=dp(118);}
            else {collapsedW=dp(34);collapsedH=dp(88);expandedW=Math.min(dp(285),screenW-dp(8));expandedH=Math.min(dp(124),screenH-dp(8));}
            applyGeometry(expansion);
        } catch(Throwable ignored) {}
    }

    private void fallbackAnchor(){
        Display d=wm.getDefaultDisplay(); int rotation=d.getRotation();
        if(rotation==android.view.Surface.ROTATION_90){edge=AnchorEdge.LEFT;anchorX=dp(16);anchorY=screenH/2;}
        else if(rotation==android.view.Surface.ROTATION_270){edge=AnchorEdge.RIGHT;anchorX=screenW-dp(16);anchorY=screenH/2;}
        else if(rotation==android.view.Surface.ROTATION_180){edge=AnchorEdge.BOTTOM;anchorX=screenW/2;anchorY=screenH-dp(16);}
        else {edge=AnchorEdge.TOP;anchorX=screenW/2;anchorY=dp(17);}
    }

    private void applyGeometry(float f) {
        if(view==null||lp==null)return;
        int w=Math.round(collapsedW+(expandedW-collapsedW)*f), h=Math.round(collapsedH+(expandedH-collapsedH)*f);
        int x,y;
        if(edge==AnchorEdge.LEFT){x=Math.max(0,anchorX-collapsedW/2); y=anchorY-h/2;}
        else if(edge==AnchorEdge.RIGHT){x=Math.min(screenW-w,anchorX+collapsedW/2-w); y=anchorY-h/2;}
        else if(edge==AnchorEdge.BOTTOM){x=anchorX-w/2; y=Math.min(screenH-h,anchorY+collapsedH/2-h);}
        else {x=anchorX-w/2; y=Math.max(0,anchorY-collapsedH/2);}
        x=Math.max(0,Math.min(screenW-w,x)); y=Math.max(0,Math.min(screenH-h,y));
        lp.width=w;lp.height=h;lp.x=x;lp.y=y;
        try{wm.updateViewLayout(view,lp);}catch(Throwable ignored){}
        view.setExpansion(f);
        Rect island=new Rect(x,y,x+w,y+h); statusBar.update(island,screenW,f>.35f,edge);
    }

    private void animateExpanded(boolean target) {
        if(view==null) return; expanded=target;
        float from=expansion,to=target?1f:0f;
        ValueAnimator a=ValueAnimator.ofFloat(from,to); a.setDuration(280); a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f));
        a.addUpdateListener(v->{expansion=(float)v.getAnimatedValue();applyGeometry(expansion);});
        a.start();
    }

    @Override public void onCollapsedTap() {
        if(current.type==IslandEvent.Type.IDLE){
            String q=prefs.qrUri(); if(q!=null&&!q.isEmpty()){Intent i=new Intent(this,QrActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);} else animateExpanded(true);
        } else animateExpanded(true);
    }
    @Override public void onExpandedTap() {
        if(current.type==IslandEvent.Type.TIMER){toggleTimer();return;}
        if(current.notificationKey!=null && NotificationActionRegistry.open(current.notificationKey)){animateExpanded(false);return;}
        if(current.type==IslandEvent.Type.ALARM){setIdle();return;}
        animateExpanded(false);
    }
    @Override public void onMediaPrevious(){MediaBridge.previous();}
    @Override public void onMediaToggle(){MediaBridge.toggle();main.postDelayed(()->MediaBridge.refresh(this),250);}
    @Override public void onMediaNext(){MediaBridge.next();}
    @Override public void onCallAccept(){ if(current.notificationKey!=null) NotificationActionRegistry.actionByWords(current.notificationKey,"answer","accept"); }
    @Override public void onCallDecline(){ if(current.notificationKey!=null) NotificationActionRegistry.actionByWords(current.notificationKey,"decline","reject","hang up","end"); }
    @Override public void onSwipeCollapse(){animateExpanded(false);}

    private int priority(IslandEvent.Type t){
        switch(t){
            case CALL:return 100; case NAVIGATION:return 90; case ALARM:return 88; case TIMER:return 85; case PAYMENT:return 82;
            case MEDIA:return 75; case BLUETOOTH:return 68; case DOWNLOAD:return 65; case BIOMETRIC:return 62; case NFC:return 60;
            case CHARGING:return 55; case MESSAGE:return 40; case OTHER:return 20; default:return 0;
        }
    }

    private final BroadcastReceiver systemReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        String a=i.getAction(); if(Intent.ACTION_POWER_CONNECTED.equals(a)||Intent.ACTION_BATTERY_CHANGED.equals(a)){
            int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100); int pct=scale>0?Math.round(level*100f/scale):-1;
            int plugged=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0); if(plugged!=0 && Intent.ACTION_POWER_CONNECTED.equals(a)) pushEvent(IslandService.this,new IslandEvent(IslandEvent.Type.CHARGING,"Charging",pct>=0?pct+"%":"Connected"));
        }
    }};

    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig){super.onConfigurationChanged(newConfig);main.postDelayed(this::recalcGeometry,120);}
    @Override public void onTaskRemoved(Intent rootIntent){statusBar.restore();super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);statusBar.restore();try{unregisterReceiver(systemReceiver);}catch(Throwable ignored){} if(view!=null){try{wm.removeView(view);}catch(Throwable ignored){}} super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
