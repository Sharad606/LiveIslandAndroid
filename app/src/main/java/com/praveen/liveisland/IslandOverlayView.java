package com.praveen.liveisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class IslandOverlayView extends View {
    public interface Listener {
        void onCollapsedTap();
        void onExpandedTap();
        void onMediaPrevious();
        void onMediaToggle();
        void onMediaNext();
        void onCallAccept();
        void onCallDecline();
        void onSwipeCollapse();
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private IslandEvent event = IslandEvent.idle();
    private float expansion;
    private float downX, downY;
    private long downAt;

    public IslandOverlayView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    }

    public void setListener(Listener l) { listener = l; }
    public void setEvent(IslandEvent e) { event = e == null ? IslandEvent.idle() : e; invalidate(); }
    public void setExpansion(float f) { expansion = Math.max(0f, Math.min(1f, f)); invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        p.setColor(Color.BLACK);
        float radius = Math.min(w, h) * (expansion < .35f ? .48f : .26f);
        c.drawRoundRect(new RectF(0,0,w,h), radius, radius, p);

        if (expansion < .58f) {
            if (event.type != IslandEvent.Type.IDLE) {
                p.setColor(accent(event.type));
                float r = Math.max(dp(3), Math.min(w,h)*.10f);
                c.drawCircle(w*.16f, h*.5f, r, p);
            }
            return;
        }

        float left = dp(18), top = dp(22);
        p.setColor(accent(event.type));
        c.drawCircle(left + dp(5), top + dp(5), dp(5), p);

        text.setColor(Color.WHITE); text.setTextSize(dp(15)); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String title = ellipsize(event.title.isEmpty() ? label(event.type) : event.title, 31);
        c.drawText(title, left + dp(18), top + dp(6), text);
        text.setTypeface(android.graphics.Typeface.DEFAULT); text.setColor(Color.LTGRAY); text.setTextSize(dp(12));
        String sub = ellipsize(event.subtitle, 45);
        if (!sub.isEmpty()) c.drawText(sub, left + dp(18), top + dp(26), text);

        if (event.progress >= 0) {
            float y = h - dp(18), x1 = left, x2 = w-left;
            p.setStrokeWidth(dp(4)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.DKGRAY); c.drawLine(x1,y,x2,y,p);
            p.setColor(Color.WHITE); c.drawLine(x1,y,x1+(x2-x1)*(event.progress/100f),y,p);
        }

        if (event.type == IslandEvent.Type.MEDIA) drawMedia(c,w,h);
        else if (event.type == IslandEvent.Type.TIMER) drawTimer(c,w,h);
        else if (event.type == IslandEvent.Type.CALL) drawCall(c,w,h);
        else if (event.type == IslandEvent.Type.NAVIGATION) drawNav(c,w,h);
    }

    private void drawMedia(Canvas c, float w, float h) {
        float y = h - dp(24);
        text.setColor(Color.WHITE); text.setTextSize(dp(21)); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("‹", w*.23f, y, text);
        c.drawText(MediaBridge.isPlaying() ? "Ⅱ" : "▶", w*.48f, y, text);
        c.drawText("›", w*.76f, y, text);
    }
    private void drawTimer(Canvas c,float w,float h) {
        text.setColor(Color.WHITE); text.setTextSize(dp(13)); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("Tap to pause / resume", dp(18), h-dp(17), text);
    }
    private void drawCall(Canvas c,float w,float h) {
        text.setTextSize(dp(12)); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setColor(Color.rgb(45,180,90)); c.drawCircle(w*.78f,h-dp(24),dp(14),p); text.setColor(Color.WHITE); c.drawText("✓",w*.76f,h-dp(20),text);
        p.setColor(Color.rgb(220,55,65)); c.drawCircle(w*.90f,h-dp(24),dp(14),p); c.drawText("×",w*.885f,h-dp(20),text);
    }
    private void drawNav(Canvas c,float w,float h) {
        text.setColor(Color.WHITE); text.setTextSize(dp(24)); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("↑", w-dp(42), dp(45), text);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX=e.getX(); downY=e.getY(); downAt=SystemClock.uptimeMillis(); return true;
            case MotionEvent.ACTION_UP:
                float dx=e.getX()-downX, dy=e.getY()-downY;
                if (Math.hypot(dx,dy) > dp(38)) { if (listener!=null) listener.onSwipeCollapse(); return true; }
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (expansion < .5f) { if (listener!=null) listener.onCollapsedTap(); }
                else if (event.type == IslandEvent.Type.MEDIA && e.getY() > getHeight()*.52f) {
                    if (e.getX() < getWidth()/3f) { if(listener!=null) listener.onMediaPrevious(); }
                    else if (e.getX() > getWidth()*2f/3f) { if(listener!=null) listener.onMediaNext(); }
                    else { if(listener!=null) listener.onMediaToggle(); }
                } else if (event.type == IslandEvent.Type.CALL && e.getY() > getHeight()*.55f) {
                    if (e.getX() > getWidth()*.84f) { if(listener!=null) listener.onCallDecline(); }
                    else if (e.getX() > getWidth()*.68f) { if(listener!=null) listener.onCallAccept(); }
                    else { if(listener!=null) listener.onExpandedTap(); }
                } else { if(listener!=null) listener.onExpandedTap(); }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int accent(IslandEvent.Type t) {
        switch (t) {
            case CALL: return Color.rgb(48,209,88);
            case NAVIGATION: return Color.rgb(10,132,255);
            case TIMER: case ALARM: return Color.rgb(255,159,10);
            case PAYMENT: return Color.rgb(50,215,75);
            case BLUETOOTH: return Color.rgb(80,150,255);
            case BIOMETRIC: return Color.rgb(100,210,255);
            case CHARGING: return Color.rgb(48,209,88);
            default: return Color.WHITE;
        }
    }
    private String label(IslandEvent.Type t) { return t.name().toLowerCase().replace('_',' '); }
    private String ellipsize(String s, int n) { if (s==null) return ""; return s.length()<=n?s:s.substring(0,Math.max(0,n-1))+"…"; }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
}
