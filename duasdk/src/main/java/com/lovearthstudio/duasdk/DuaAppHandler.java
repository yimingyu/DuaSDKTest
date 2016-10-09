package com.lovearthstudio.duasdk;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;

import com.lovearthstudio.duasdk.util.LogUtil;

/**
 * Author：Mingyu Yi on 2016/6/24 10:43
 * Email：461072496@qq.com
 */
public class DuaAppHandler implements Application.ActivityLifecycleCallbacks,ComponentCallbacks2{
    private static boolean isInBackground = false;
    private static int resumeCount=0;
    private static int foregroundTimes=0;
    private static int backgroundTimes=0;
    private static boolean isExiting=false;
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        if(isInBackground||resumeCount==0){
            foregroundTimes++;
            isInBackground = false;
            Dua.getInstance().duaAwake();
            if(DuaConfig.debugLog) {
                LogUtil.e("DuaApp", "进入前台,总次数" + foregroundTimes);
            }
        }
        resumeCount++;
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if(isExiting){
//            Dua.getInstance().duaExit();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    @Override
    public void onTrimMemory(int i) {
        switch (i){
            case TRIM_MEMORY_UI_HIDDEN:
                backgroundTimes++;
                isInBackground = true;
                Dua.getInstance().duaSleep();
                if(DuaConfig.debugLog) {
                    LogUtil.e("DuaApp", "进入后台，总次数" + backgroundTimes);
                }
                break;
            case TRIM_MEMORY_COMPLETE:
                isExiting=true;
                break;
        }

    }

    @Override
    public void onLowMemory() {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

    }


}
