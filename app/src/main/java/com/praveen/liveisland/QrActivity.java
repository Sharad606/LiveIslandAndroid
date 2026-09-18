package com.praveen.liveisland;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class QrActivity extends Activity {
    private LinearLayout root;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.screenBrightness=1f; getWindow().setAttributes(lp);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(dp(24),dp(24),dp(24),dp(24)); root.setBackgroundColor(Color.BLACK); setContentView(root);
        IslandPreferences p=new IslandPreferences(this);
        if(p.qrBiometric()) BiometricHelper.authenticate(this,"Bank QR","Unlock your payment QR", ok->{if(ok)showQr();else finish();}); else showQr();
    }
    private void showQr(){
        root.removeAllViews(); IslandPreferences p=new IslandPreferences(this); String u=p.qrUri();
        if(u==null||u.isEmpty()){TextView t=new TextView(this);t.setText("No QR configured. Choose a bank/UPI QR in Live Island settings.");t.setTextColor(Color.WHITE);t.setTextSize(18);t.setGravity(Gravity.CENTER);root.addView(t);return;}
        ImageView image=new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try{image.setImageURI(Uri.parse(u));}catch(Throwable ignored){}
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1);root.addView(image,ip);
        TextView hint=new TextView(this);hint.setText("Tap anywhere to close");hint.setTextColor(Color.LTGRAY);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(12),0,0);root.addView(hint);
        root.setOnClickListener(v->finish());
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
