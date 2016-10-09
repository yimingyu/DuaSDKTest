package com.lovearthstudio.duasdk.util;

import android.os.Environment;
import android.util.Log;

import com.kymjs.rxvolley.RxVolley;
import com.kymjs.rxvolley.client.HttpCallback;
import com.kymjs.rxvolley.client.HttpParams;

import java.io.File;

/**
 * Author：Mingyu Yi on 2016/7/29 16:15
 * Email：461072496@qq.com
 */
public class RxVolleyUtil {
    static{
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + "RxVolley"
                + File.separator);
        if(!file.exists()) file.mkdirs();
    }
    public static HttpCallback defaultCallback=new HttpCallback() {
        @Override
        public void onSuccess(String t) {
            Log.i("RxVolleyUtil", "数据：" + t);
        }

        @Override
        public void onFailure(int errorNo, String strMsg) {
            Log.e("RxVolleyUtil", "错误:" + errorNo + " 原因：" + strMsg);
        }
    };
    public static void asyncGet(String url, HttpCallback callback){
        if(callback==null){
            callback=defaultCallback;
        }
        try {
            new RxVolley.Builder()
                    .url(url)
                    .httpMethod(RxVolley.Method.GET)
//                .useServerControl(false)
                    .shouldCache(false)
                    .contentType(RxVolley.ContentType.JSON)
                    .callback(callback)
                    .encoding("UTF-8") //default
                    .doTask();
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    public static void asyncPost(String url, HttpParams params, HttpCallback callback){
        if(callback==null){
            callback=defaultCallback;
        }
        try {
            new RxVolley.Builder()
                .url(url)
                .httpMethod(RxVolley.Method.POST) //default GET or POST/PUT/DELETE/HEAD/OPTIONS/TRACE/PATCH
//                .useServerControl(false)
                .shouldCache(false)
                .contentType(RxVolley.ContentType.JSON)//default FORM or JSON
                .params(params)
                .callback(callback)
                .encoding("UTF-8") //default
                .doTask();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void uploadFile(String url, File file, HttpCallback callback,String...extra){
        HttpParams params=new HttpParams();
        params.put("files",file);
        for (int i = 0; i <extra.length-1 ; i=i+2) {
            params.put(extra[i],extra[i+1]);
        }
        try {
            new RxVolley.Builder()
                    .url(url)
                    .httpMethod(RxVolley.Method.POST)
                    .shouldCache(false)
                    .contentType(RxVolley.ContentType.FORM)
                    .timeout(60 * 1000)
                    .params(params)
                    .callback(callback)
                    .encoding("UTF-8") //default
                    .doTask();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
