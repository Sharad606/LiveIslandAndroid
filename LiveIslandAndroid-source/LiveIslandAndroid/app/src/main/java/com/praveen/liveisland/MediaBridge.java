package com.praveen.liveisland;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;

public final class MediaBridge {
    private static volatile MediaController active;
    private MediaBridge() {}

    public static void refresh(Context context) {
        try {
            MediaSessionManager m = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = m.getActiveSessions(new ComponentName(context, NotificationBridgeService.class));
            MediaController chosen = null;
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { chosen = c; break; }
                if (chosen == null) chosen = c;
            }
            active = chosen;
            if (chosen != null) {
                MediaMetadata md = chosen.getMetadata();
                String title = md == null ? "Media" : md.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = md == null ? chosen.getPackageName() : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                IslandService.pushEvent(context, new IslandEvent(IslandEvent.Type.MEDIA, title, artist));
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isPlaying() { PlaybackState s = active == null ? null : active.getPlaybackState(); return s != null && s.getState() == PlaybackState.STATE_PLAYING; }
    public static void toggle() { try { if (active == null) return; if (isPlaying()) active.getTransportControls().pause(); else active.getTransportControls().play(); } catch (Throwable ignored) {} }
    public static void next() { try { if (active != null) active.getTransportControls().skipToNext(); } catch (Throwable ignored) {} }
    public static void previous() { try { if (active != null) active.getTransportControls().skipToPrevious(); } catch (Throwable ignored) {} }
}
