package com.android.server;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.storage.StorageVolume;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.view.WindowManager;
import android.os.SystemProperties;

import java.io.IOException;
import java.io.File;
import com.android.internal.R;

public class AutoOTAReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoOTAReceiver";
    private StorageVolume mVolume = null;
    private final String fileName = "/update.zip";
    private final String SdcardDir = "/storage/emulated/0";
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if("homlet".equals(SystemProperties.get("ro.product.platform", null))){
            if (intent.getAction().equals(intent.ACTION_MEDIA_MOUNTED)){
                String Dir = intent.getData().getPath();
                String path = Dir + fileName;
                if(new File(path).exists() && !Dir.equals(SdcardDir)){
                    AlertDialog alertDialog = new AlertDialog.Builder(context).
                        setTitle(R.string.ota_title).
                        setMessage(R.string.ota_message).
                        setPositiveButton(R.string.ota_accept, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub
                                Intent mIntent = new Intent("android.intent.action.otaactivity");
                                mIntent.putExtra("updateUSBPath", path);
                                mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try{
                                    context.startActivity(mIntent);
                                }
                                catch(ActivityNotFoundException e){
                                    e.printStackTrace();
                                }
                            }
                        }).
                    setNegativeButton(R.string.ota_reject, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //do nothing
                        }
                    }).
                    create();
                    alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                    alertDialog.show();
                }
            }
        }
    }
}
