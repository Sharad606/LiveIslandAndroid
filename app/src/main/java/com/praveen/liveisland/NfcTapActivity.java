package com.praveen.liveisland;

import android.app.Activity;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NfcTapActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter adapter; private TextView text;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state); LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);r.setBackgroundColor(Color.BLACK);text=new TextView(this);text.setTextColor(Color.WHITE);text.setTextSize(26);text.setGravity(Gravity.CENTER);r.addView(text);setContentView(r);
        adapter=NfcAdapter.getDefaultAdapter(this); if(adapter==null)text.setText("NFC is not supported on this phone");else if(!adapter.isEnabled()){text.setText("Turn on NFC, then return here");text.setOnClickListener(v->startActivity(new android.content.Intent(Settings.ACTION_NFC_SETTINGS)));}else{text.setText("Hold an NFC tag near the phone\n\nLive Island is ready");IslandService.pushEvent(this,new IslandEvent(IslandEvent.Type.NFC,"NFC ready","Hold near a tag"));}
    }
    @Override protected void onResume(){super.onResume();if(adapter!=null&&adapter.isEnabled())adapter.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V,null);}
    @Override protected void onPause(){if(adapter!=null)try{adapter.disableReaderMode(this);}catch(Throwable ignored){}super.onPause();}
    @Override public void onTagDiscovered(Tag tag){new Handler(Looper.getMainLooper()).post(()->{text.setText("NFC detected ✓");IslandService.pushEvent(this,new IslandEvent(IslandEvent.Type.NFC,"NFC detected","Tap complete"));});}
}
