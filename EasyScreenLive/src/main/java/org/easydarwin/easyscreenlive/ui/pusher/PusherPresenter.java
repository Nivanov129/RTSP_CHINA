package org.easydarwin.easyscreenlive.ui.pusher;

import android.content.Context;
import android.content.Intent;
import android.view.SurfaceView;

import org.easydarwin.easyscreenlive.config.Config;
import org.easydarwin.easyscreenlive.screen_live.LiveRtspConfig;
import org.easydarwin.easyscreenlive.screen_live.EasyScreenLiveAPI;
import org.easydarwin.rtspservice.JniEasyScreenLive;

/**
 * Created by gavin on 2018/1/23.
 */

public class PusherPresenter implements PusherContract.Presenter {
    private PusherContract.View view;
    static private PusherPresenter pusherPresenter = null;

    static public PusherPresenter getInterface() {
        if (pusherPresenter == null) {
            pusherPresenter = new PusherPresenter();
        }
        return pusherPresenter;
    }

    private PusherPresenter() {
    }

    public void setPusherView(PusherContract.View view) {
        this.view = view;
        this.view.setPresenter(this);
    }

    @Override
    public void initView(Context context) {
        if (view != null && view.isActive()) {
            if (EasyScreenLiveAPI.getPushStatus() == EasyScreenLiveAPI.EASY_PUSH_SERVICE_STATUS.STATUS_LEISURE) {
                view.changeViewStatus(getPushStatus(), "");
            } else {
                view.changeViewStatus(getPushStatus(), Config.getRtspUrl(context));
            }
        }
    }

    @Override
    public int getPushStatus() {
        return EasyScreenLiveAPI.getPushStatus();
    }

    @Override
    public void onStartPush(Context context, int pushDev, Intent capScreenIntent,
             int capScreenCode, SurfaceView mSurfaceView) {
        LiveRtspConfig config = new LiveRtspConfig();
        config.pushdev = pushDev;
        config.capScreenIntent = capScreenIntent;
        config.capScreenCode = capScreenCode;


        config.initLiveRtspConfig(context);
        EasyScreenLiveAPI.startPush(config, mSurfaceView);
    }

    @Override
    public void onStopPush() {
        EasyScreenLiveAPI.stopPush();
    }


    @Override
    public void onStartPushSuccess(Context context, int isEnableMulticast,String URL) {
        if(view != null && view.isActive()) {
            view.showTip("??????????????????"+
                    (isEnableMulticast==1?"(????????????) ":"??????????????????")+"\n"
                    + "URL:"+URL);
            view.changeViewStatus(getPushStatus(),URL);
        }
    }

    @Override
    public void onStartPushFail(Context context, int result) {
        if(view != null && view.isActive()) {
            view.changeViewStatus(getPushStatus(),"");
            if (result == JniEasyScreenLive.EasyErrorCode.EASY_SDK_ACTIVE_FAIL) {
                view.showTip("????????????????????????????????????");
            } else {
                view.showTip("??????????????????:" + result + ", RTSP??????????????????1");
            }
            view.changeViewStatus(getPushStatus(),"");
        }
    }

    @Override
    public void onStartPushFail(Context context, String str) {
        if(view != null && view.isActive()) {
            view.changeViewStatus(getPushStatus(),"");
            view.showTip(str);
            view.changeViewStatus(getPushStatus(),"");
        }
    }

    @Override
    public void onStopPushSuccess(Context context) {
        if(view != null && view.isActive()) {
            view.changeViewStatus(getPushStatus(),"");
        }
    }


}
