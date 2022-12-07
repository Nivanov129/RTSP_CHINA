package org.easydarwin.easyscreenlive.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.tencent.bugly.Bugly;

import org.easydarwin.easyscreenlive.R;
import org.easydarwin.easyscreenlive.databinding.ActivityScreenLiveBinding;
import org.easydarwin.easyscreenlive.screen_live.CapScreenService;
import org.easydarwin.easyscreenlive.screen_live.EasyScreenLiveAPI;
import org.easydarwin.easyscreenlive.ui.base.BaseActivity;
import org.easydarwin.easyscreenlive.ui.playlist.PlayListFragment;
import org.easydarwin.easyscreenlive.ui.playlist.PlayListPresenter;
import org.easydarwin.easyscreenlive.ui.pusher.PusherFragment;
import org.easydarwin.easyscreenlive.ui.pusher.PusherPresenter;
import org.easydarwin.easyscreenlive.ui.setting.SettingActivity;


public class ScreenLiveActivity extends BaseActivity {

    private static final String TAG = "ScreenLiveActivity";
    private ActivityScreenLiveBinding mBinding;
    static public  final int REQUEST_MEDIA_PROJECTION = 1002;
    private static final int REQUEST_CAMERA_RESULT = 1;
    private Context context;
    PusherFragment pusherFragment;
    PlayListFragment playListFragment;
    static public int mDgree= 0;

    PowerManager.WakeLock wakeLock = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        acquireWakeLock();
        Bugly.init(getApplicationContext(), "f6e6bd386f", false);
//        CrashReport.initCrashReport(getApplicationContext(), "f6e6bd386f", false);

//        ViewDataBinding mBinder = DataBindingUtil.setContentView(this, R.layout.activity_screen_live);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_screen_live);
        setSupportActionBar(mBinding.toolbar);
        context = this;

        if (savedInstanceState == null) {
            startActivity(new Intent(this, SplashActivity.class));
        }

        pusherFragment = new PusherFragment();
        PusherPresenter.getInterface().setPusherView(pusherFragment);

        playListFragment = new PlayListFragment();
        PlayListPresenter.getInterface().setPlayListView(playListFragment);
        getFragmentManager().beginTransaction().replace(R.id.fragmeng_main_layout, pusherFragment).commit();


        mBinding.toolbarSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ScreenLiveActivity.this, SettingActivity.class));
            }
        });

        mBinding.toolbarAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ScreenLiveActivity.this, AboutActivity.class));
            }
        });

        mBinding.fragmengPlayList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!playListFragment.isActive()) {
                    Log.e(TAG, "-------------" + EasyScreenLiveAPI.getPushStatus());
                    if (EasyScreenLiveAPI.getPushStatus()
                            == EasyScreenLiveAPI.EASY_PUSH_SERVICE_STATUS.STATUS_PUSH_CAMREA_BACK
                        || EasyScreenLiveAPI.getPushStatus()
                            == EasyScreenLiveAPI.EASY_PUSH_SERVICE_STATUS.STATUS_PUSH_CAMREA_FRONT) {
                        showToast("Сначала остановите потоковую передачу с камеры.");
                    } else {
                        getFragmentManager().beginTransaction().replace(R.id.fragmeng_main_layout, playListFragment).commit();
                        mBinding.fragmengPush.setTextColor(getResources().getColor(R.color.colorText));
                        mBinding.fragmengPlayList.setTextColor(getResources().getColor(R.color.colorAccent));
                    }
                }
            }
        });
        mBinding.fragmengPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!pusherFragment.isActive()) {
                    getFragmentManager().beginTransaction().replace(R.id.fragmeng_main_layout, pusherFragment).commit();
                    PusherPresenter.getInterface().setPusherView(pusherFragment);
                    mBinding.fragmengPush.setTextColor(getResources().getColor(R.color.colorAccent));
                    mBinding.fragmengPlayList.setTextColor(getResources().getColor(R.color.colorText));
                }
            }
        });

        requestCamreatPermissions();
        requestAudioPermissions();
        mDgree = getDgree();

        Intent pushintent = new Intent(getApplicationContext(), CapScreenService.class);
        startService(pushintent);
    }

    void requestCamreatPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
            } else {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "No Permission to use the Camera services", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_RESULT);
            }
        }
    }

    void requestAudioPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
            } else {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
                    Toast.makeText(this, "No Permission to use the audio services", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_RESULT);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode){
            case  REQUEST_CAMERA_RESULT:
//                if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
//                    Toast.makeText(this, "Cannot run application because camera service permission have not been granted", Toast.LENGTH_SHORT).show();
//                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    private int getDgree() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break; // Natural orientation
            case Surface.ROTATION_90:
                degrees = 90;
                break; // Landscape left
            case Surface.ROTATION_180:
                degrees = 180;
                break;// Upside down
            case Surface.ROTATION_270:
                degrees = 270;
                break;// Landscape right
        }
        return degrees;
    }

    //获取电源锁，保持该服务在屏幕熄灭时仍然获取CPU时，保持运行
    private void acquireWakeLock()
    {
        if (null == wakeLock)
        {
            PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "PostLocationService");
            if (null != wakeLock)
            {
                wakeLock.acquire();
            }
        }
    }

    //释放设备电源锁
    private void releaseWakeLock()
    {
        if (null != wakeLock)
        {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        Log.d(TAG, "onDestroy");
    }
}
