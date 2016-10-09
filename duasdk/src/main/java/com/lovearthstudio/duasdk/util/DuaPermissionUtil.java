package com.lovearthstudio.duasdk.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Author：Mingyu Yi on 2016/8/2 10:36
 * Email：461072496@qq.com
 */
public class DuaPermissionUtil {
    public static final boolean apiM=Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    public static final int DUA_PERMISSIONS_REQ_CODE=255;
    public static final String DUA_PERMISSIONS_PREF="DuaPermissions";
    public static final String[] DUA_PERMISSIONS= new String[]{
            Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE
    };
    public static Map<String,String> DUA_PWEMIAAIONS_CN=new HashMap<>();
    static {
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[0],"蓝牙");
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[1],"蓝牙开关");
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[2],"网络");
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[3],"定位");
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[4],"定位");
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[5],"网络状态");
        DUA_PWEMIAAIONS_CN.put(DUA_PERMISSIONS[6],"WIFI状态");
        DUA_PWEMIAAIONS_CN.put(Manifest.permission.CAMERA,"相机");
        DUA_PWEMIAAIONS_CN.put(Manifest.permission.WRITE_EXTERNAL_STORAGE,"存储空间");
    }
    public static String getPromptStr(ArrayList<String> permissions){
        ArrayList names=new ArrayList();
        for (String pm:permissions) {
            String name=DUA_PWEMIAAIONS_CN.get(pm);
            if(name==null) name=pm;
            names.add(name);
        }
        String promt=Arrays.toString(new HashSet<>(names).toArray());
        return promt.substring(1,promt.length()-1);
    }
    public static boolean checkPermissions(Context context,String...permissions){
        for(String permission:permissions){
            if(ContextCompat.checkSelfPermission( context, permission ) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
    public static boolean requestDuaPermissions(Activity context){
        return requestPermissions(context,DUA_PERMISSIONS_REQ_CODE,DUA_PERMISSIONS);
    }
    public static boolean requestPermissions(final Activity context, int code,String...permissions){
        if(!apiM) return false;
        ArrayList<String> shouldRequest=new ArrayList();
        ArrayList<String> shouldRationale=new ArrayList();
        for(String permission:permissions){
            if(ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED){
                shouldRequest.add(permission);
                if (ActivityCompat.shouldShowRequestPermissionRationale(context,permission)) {
                    shouldRationale.add(permission);
                }else {
                    String everRequested=SharedPreferenceUtil.prefGetKey(context,DUA_PERMISSIONS_PREF,permission,null);
                    if(everRequested==null){
                        shouldRationale.add(permission);
                    }
                }
                SharedPreferenceUtil.prefSetKey(context,DUA_PERMISSIONS_PREF,permission,"requested");
            }
        }
        int size=shouldRequest.size();
        if(size==0) return true;
        int len=shouldRationale.size();
        if(len!=0){
            ActivityCompat.requestPermissions(context,shouldRequest.toArray(new String[size]),code);
            return true;
        }else {
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setMessage("应用缺少必需权限:"+getPromptStr(shouldRequest)+"\n请进入“应用信息”设置界面点击“权限”并启用")
                    .setPositiveButton("立即设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
                            intent.setData(uri);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                            context.startActivity(intent);
                        }
                    })
                    .setNegativeButton("忽略", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();
            dialog.show();
            return false;
        }
    }

    public static boolean checkLocationPermissions(Context context){
        return checkPermissions(context,DUA_PERMISSIONS[3])||checkPermissions(context,DUA_PERMISSIONS[4]);
    }
    public static boolean isInManifest(Context context, String permissionName) {
        final String packageName = context.getPackageName();
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] declaredPermisisons = packageInfo.requestedPermissions;
            if (declaredPermisisons != null && declaredPermisisons.length > 0) {
                for (String p : declaredPermisisons) {
                    if (p.equals(permissionName)) {
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    public static boolean inManifestButDenied(Context context,String permission){
        return isInManifest(context,permission)&&!checkPermissions(context,permission);
    }
    public static boolean invalidSdAndCamera(Context context){
        return inManifestButDenied(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)||
                inManifestButDenied(context,Manifest.permission.CAMERA);
    }
    public static void requestSdAndCamera(Activity activity,int code){
        requestPermissions(activity,code,Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean checkAlertWindow(Activity activity){
        if(!isInManifest(activity,Manifest.permission.SYSTEM_ALERT_WINDOW)){
            return false;
        }
        if(apiM && !Settings.canDrawOverlays(activity)){
            return false;
        }
        return true;
    }
    public static void requestAlertWindow(Activity activity,int code){
        if(apiM) startSettingsActivity(activity,Settings.ACTION_MANAGE_OVERLAY_PERMISSION,code);
    }
    @TargetApi(Build.VERSION_CODES.M)
    public static boolean checkWriteSettings(Activity activity){
        if(!isInManifest(activity,Manifest.permission.SYSTEM_ALERT_WINDOW)){
            return false;
        }
        if(apiM && !Settings.System.canWrite(activity)){
            return false;
        }
        return true;
    }
    public static void requestWriteSettings(Activity activity,int code){
        if(apiM) startSettingsActivity(activity,Settings.ACTION_MANAGE_WRITE_SETTINGS,code);
    }

    public static void startSettingsActivity(Activity activity,String action,int code){
        Intent intent = new Intent(action);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivityForResult(intent, code);
    }
}
