package com.lovearthstudio.duasdk;


import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import com.lovearthstudio.duasdk.util.DuaPermissionUtil;
import com.lovearthstudio.duasdk.util.LogUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class DuaCollector {
    public Context context;
    public PackageManager pm;
    public PackageInfo pkg;
    public ApplicationInfo app;
    public ConnectivityManager cm;
    public TelephonyManager tm;


    public LocationManager lm;
    public Location curLocation;
    public boolean locationListened;
    public class MyLocationListener implements LocationListener{
        @Override
        public void onLocationChanged(Location location) {

            if(isBetterLocation(curLocation,location)){
                curLocation=location;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            if(!locationListened){
                locationListened=registerLocationListener();
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            lm.removeUpdates(this);
        }
    };
    public boolean registerLocationListener(){
        if(lm!=null&& DuaPermissionUtil.checkLocationPermissions(context)){
            boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if(isNetworkEnabled){
                MyLocationListener networkListner=new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0, networkListner);
            }
            if(isGPSEnabled){
                MyLocationListener gpsListener=new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, gpsListener);
            }
            return isNetworkEnabled||isGPSEnabled;
        }
        return false;
    }
    public Location getCurrentLocation() {
        if(tm==null||!DuaPermissionUtil.checkLocationPermissions(context)) {
            return null;
        }
        if(locationListened){
            return curLocation;
        }else{
            locationListened=registerLocationListener();
            Criteria criteria = new Criteria();
            return lm.getLastKnownLocation(lm.getBestProvider(criteria, false));
        }
    }
    public boolean isBetterLocation(Location currentBestLocation,Location location){
        final int CHECK_INTERVAL = 1000 * 30;
        if (currentBestLocation == null) {
            return true;
        }
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > CHECK_INTERVAL;
        boolean isSignificantlyOlder = timeDelta < -CHECK_INTERVAL;
        boolean isNewer = timeDelta > 0;
        if (isSignificantlyNewer) {
            return true;
        } else if (isSignificantlyOlder) {
            return false;
        }
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;
        boolean isFromSameProvider = location.getProvider().equals(currentBestLocation.getProvider());
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate
                && isFromSameProvider) {
            return true;
        }
        return false;
    }


    private List<WirelessDevice> bt;
    private long scanBtTimeout=6000;
    private int scanProgress=0;
    private BluetoothAdapter mBluetoothAdapter= BluetoothAdapter.getDefaultAdapter();;
    private MyBroadcastReceiver mReceiver;
    public class MyBroadcastReceiver extends BroadcastReceiver{
        public DuaCallback mcb;
        public MyBroadcastReceiver(DuaCallback mcb){
            this.mcb=mcb;
        }
        public void setMyCallBack(DuaCallback mcb){
            this.mcb=mcb;
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                scanProgress=1;
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi=0;
                int conn=1;
                int status=device.getBondState();
                if (status != BluetoothDevice.BOND_BONDED) {
                    rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                    if(status==BluetoothDevice.BOND_NONE) conn=0;
                }
                bt.add(new WirelessDevice("B",device.getAddress(),device.getName(),rssi,conn));
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                scanProgress=0;
                context.unregisterReceiver(mReceiver);
//                mReceiver=null;
                mcb.onSuccess(bt);
            }
        }
    }
    public void scanBlueTeeth(final DuaCallback duaCallback){
        if(mBluetoothAdapter==null){//通常是虚拟机
            duaCallback.onError(12306,"没有蓝牙适配器");
            return;
        }
        bt=new ArrayList<WirelessDevice>();
        if(mReceiver==null){
            mReceiver = new MyBroadcastReceiver(duaCallback);
            IntentFilter mFilter = new IntentFilter();
            mFilter.addAction(BluetoothDevice.ACTION_FOUND);
            mFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            context.registerReceiver(mReceiver, mFilter);
        }else{
            mReceiver.setMyCallBack(duaCallback);
            //或者直接返回当前已找到的bt
//            duaCallback.onSuccess(new Gson().toJson(bt));
        }
        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }
        mBluetoothAdapter.startDiscovery();
        Timer timer=new Timer();
        TimerTask task=new TimerTask() {
            @Override
            public void run() {
                if(scanProgress==1){
                    mReceiver.setMyCallBack(null);
                    duaCallback.onSuccess(bt);
                    unregisterReciver();
                    scanProgress=0;
                }
            }
        };
        timer.schedule(task,scanBtTimeout);
    }
    public void scanBluetooth(final DuaCallback duaCallback){
        if(mBluetoothAdapter==null){//通常是虚拟机
            duaCallback.onError(12306,"没有蓝牙适配器");
            return;
        }
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final List<WirelessDevice> bts=new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder().build();
        List<ScanFilter> filters = new ArrayList<>();
        final ScanCallback scanCallback=new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, no.nordicsemi.android.support.v18.scanner.ScanResult result) {
                BluetoothDevice device=result.getDevice();
                int conn=device.getBondState()==BluetoothDevice.BOND_NONE?0:1;
                WirelessDevice wld=new WirelessDevice("B",device.getAddress(),device.getName(),result.getRssi(),conn);
                if(!bts.contains(wld)){
                    bts.add(wld);
                }
            }

            public void onScanFailed(int errorCode) {
                duaCallback.onError(errorCode,"蓝牙扫描失败");
            }
        };
        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }
        try {
            scanner.startScan(filters, settings, scanCallback);
            Timer timer=new Timer();
            TimerTask task=new TimerTask() {
                @Override
                public void run() {
                    scanner.stopScan(scanCallback);
                    duaCallback.onSuccess(bts);
                }
            };
            timer.schedule(task,scanBtTimeout);
        }catch (Exception e){
            e.printStackTrace();
            duaCallback.onError(100,e.toString());
        }
    }
    public List<WirelessDevice> getBoundBlueTeeth(){
        List<WirelessDevice> bt=new ArrayList<WirelessDevice>();
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        if (devices.size() > 0) {
            for (BluetoothDevice device : devices) {
               bt.add(new WirelessDevice("B",device.getAddress(),device.getName(),0,1));
            }
        }
        return bt;
    }
    public class WirelessDevice{
        String type;
        String id;
        String name;
        int dbm;
        int reg;
        public WirelessDevice(String type,String id,String name,int dbm,int reg){
            this.type=type;
            this.id=id;
            if(name==null||name.equals("")){
                this.name=id;
            }else{
                this.name=name;
            }
            this.dbm=dbm;
            this.reg=reg;
        }

        @Override
        public String toString() {
            return "WirelessDevice{" +
                    "type='" + type + '\'' +
                    ", id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", dbm=" + dbm +
                    ", reg=" + reg +
                    '}';
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof WirelessDevice) {
                if (this.id.equals(((WirelessDevice) obj).id)) {
                    return true;
                }
                else {
                    return false;
                }
            }else if(obj instanceof BluetoothDevice){
                if (this.id.equals(((BluetoothDevice) obj).getAddress())) {
                    return true;
                }
                else {
                    return false;
                }
            }else{
                if (this.id.equals(obj)) {
                    return true;
                }
                else {
                    return false;
                }
            }
        }
    }



    public DuaCollector(Context context){
        this.context = context;
        this.pm = context.getPackageManager();
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.lm= (LocationManager) context.getSystemService(context.LOCATION_SERVICE);
        this.locationListened=registerLocationListener();
        try {
            this.pkg = pm.getPackageInfo(getPackageName(), 0);
            this.app = pm.getApplicationInfo(getPackageName(), 0);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void unregisterReciver(){
        if(mReceiver!=null){
            try {
                context.unregisterReceiver(mReceiver);
            }catch (Exception e){
            }
        }
    }
    public String getDuaAppKey(){
        try {
            ApplicationInfo appInfo=pm.getApplicationInfo(getPackageName(),PackageManager.GET_META_DATA);
            return appInfo.metaData.getString("DuaAppKey");
        }catch (Exception e) {
            e.printStackTrace();
            return "NoAppKey";
        }
    }

    public String getPackageName() {
        return context.getPackageName();
    }

    public String getAppName() {
        return (String) pm.getApplicationLabel(app);
    }

    public String getVersionNumber() {
        return pkg.versionName;
    }

    public int getVersionCode() {
        return pkg.versionCode;
    }

    public String getUuid() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public String getOSVersion() {
        return Build.VERSION.RELEASE;
    }

    public String getModel() {
        return Build.MODEL;
    }

    public String getManufacturer() {
        return Build.MANUFACTURER;
    }

    public String getPlatform() {
        if (Build.MANUFACTURER.equals("Amazon")) {
            return "Amazon-fireos";
        } else {
            return "Android";
        }
    }

    public JSONArray getAppLists() throws JSONException {
        List<PackageInfo> ps = pm.getInstalledPackages(0);
        JSONArray ja = new JSONArray();
        for (PackageInfo p : ps) {
            JSONObject jo = new JSONObject();
            jo.put("aname", p.applicationInfo.loadLabel(pm).toString());
            jo.put("pname", p.packageName);
            jo.put("version", p.versionName == null ? "na" : p.versionName);
            jo.put("code", p.versionCode);
            jo.put("system", ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1) ? 1 : 0);
//            Bitmap bitmap=((BitmapDrawable)p.applicationInfo.loadIcon(pm)).getBitmap();
//            jo.put("icon",encodeToBase64(bitmap));
            ja.put(jo);
        }
        return ja;
    }

    public List<JSONObject> getAppStats(long startTime, long endTime, int type) throws JSONException {
        List<JSONObject> jl = new ArrayList<JSONObject>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // UsageStatsManager usm = getUsageStatsManager(context);
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            int interval;
            switch (type) {
                case 1:
                    interval = UsageStatsManager.INTERVAL_DAILY;
                    break;
                case 2:
                    interval = UsageStatsManager.INTERVAL_WEEKLY;
                    break;
                case 3:
                    interval = UsageStatsManager.INTERVAL_MONTHLY;
                    break;
                case 4:
                    interval = UsageStatsManager.INTERVAL_YEARLY;
                    break;
                default:
                    interval = UsageStatsManager.INTERVAL_DAILY;
                    break;
            }
            List<UsageStats> usageStatsList = usm.queryUsageStats(interval, startTime, endTime);
            for (UsageStats app : usageStatsList) {
                JSONObject jo = new JSONObject();
                jo.put("pname", app.getPackageName());
                jo.put("fts", app.getFirstTimeStamp());
                jo.put("lts", app.getLastTimeStamp());
                jo.put("ltu", app.getLastTimeUsed());
                jo.put("ttf", app.getTotalTimeInForeground());
                jo.put("cnt", -1);
                jo.put("type", type);
                jl.add(jo);
            }
        }
        return jl;
    }

    public List<WirelessDevice> getNCI(){
        List<WirelessDevice> nci = new ArrayList<WirelessDevice>();
        if(!DuaPermissionUtil.checkLocationPermissions(context)) return nci;
        if (Build.VERSION.SDK_INT >= 18) {
            List<CellInfo> list = tm.getAllCellInfo();
            if(tm!=null&&list!=null&&list.size()>0)  {
                for (int i = 0; i < list.size(); i++) {
                    int mcc = 0;
                    int mnc = 0;
                    int lac = 0;
                    int cid = 0;
                    int dbm = 0;
                    int reg = list.get(i).isRegistered() ? 1 : 0;//这个网络是否在这个SIM卡上注册：如果注册，说明这个网络是附近最好的.isRegistered(): True if this cell is registered to the mobile network

                    CellInfo cell=list.get(i);
                    if (cell instanceof CellInfoGsm) {
                        CellInfoGsm info = (CellInfoGsm) list.get(i);
                        mcc = info.getCellIdentity().getMcc();
                        mnc = info.getCellIdentity().getMnc();
                        lac = info.getCellIdentity().getLac();
                        cid = info.getCellIdentity().getCid();//CID Either 16-bit GSM Cell Identity described in TS 27.007, 0..65535, Integer.MAX_VALUE if unknown
                        dbm = info.getCellSignalStrength().getDbm();//getDbm() Get the signal strength as dBm
                    } else if (cell instanceof CellInfoCdma) {
                        CellInfoCdma info = (CellInfoCdma) list.get(i);
                        mcc = Integer.parseInt(tm.getNetworkOperator().substring(0, 3));//从网上得知cdma需要如此得到mcc: http://blog.csdn.net/lancees/article/details/7616735
                        mnc = Integer.parseInt(tm.getNetworkOperator().substring(3, 5));
                        lac = info.getCellIdentity().getNetworkId();
                        cid = info.getCellIdentity().getBasestationId();//Base Station Id 0..65535, Integer.MAX_VALUE if unknown
                        dbm = info.getCellSignalStrength().getDbm();//getDbm() Get the signal strength as dBm
                    } else if (cell instanceof CellInfoLte) {
                        CellInfoLte info = (CellInfoLte) list.get(i);
                        mcc = info.getCellIdentity().getMcc(); //返回整形：3-digit Mobile Country Code, 0..999, Integer.MAX_VALUE if unknown
                        mnc = info.getCellIdentity().getMnc(); //返回整形：2 or 3-digit Mobile Network Code, 0..999, Integer.MAX_VALUE if unknown
                        lac = info.getCellIdentity().getTac(); //我们不清楚是不是lac,文档中标明：16-bit Tracking Area Code, Integer.MAX_VALUE if unknown
                        cid = info.getCellIdentity().getCi(); //我们也不清楚ci是否就是cid:lte中的ci竟然是28bit的：28-bit Cell Identity, Integer.MAX_VALUE if unknown
                        dbm = info.getCellSignalStrength().getDbm();//getDbm() Get the signal strength as dBm
                    } else if (cell instanceof CellInfoWcdma) {
                        CellInfoWcdma info = (CellInfoWcdma) list.get(i);
                        mcc = info.getCellIdentity().getMcc(); //返回整形：3-digit Mobile Country Code, 0..999, Integer.MAX_VALUE if unknown
                        mnc = info.getCellIdentity().getMnc(); //返回整形：2 or 3-digit Mobile Network Code, 0..999, Integer.MAX_VALUE if unknown
                        lac = info.getCellIdentity().getLac(); //16-bit Location Area Code, 0..65535, Integer.MAX_VALUE if unknown
                        cid = info.getCellIdentity().getCid(); //竟然是28bit:CID 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455, Integer.MAX_VALUE if unknown
                        dbm = info.getCellSignalStrength().getDbm();//getDbm() Get the signal strength as dBm
                    }
                    String id=mcc + ":" + mnc + ":" + lac + ":" + cid;
                    nci.add(new WirelessDevice("T",id,"N/A",dbm,reg));
                }
            }
        }
        return nci;
    }

    public List<WirelessDevice> getWifiList(){
        List<WirelessDevice> wifis = new ArrayList<WirelessDevice>();
        if(!DuaPermissionUtil.checkLocationPermissions(context)) return wifis;
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> scanResults = wifiManager.getScanResults();
        String connectedWifi="";
        try {
            connectedWifi = wifiManager.getConnectionInfo().getBSSID();
        }catch (Exception e){
        }
        for (ScanResult scan : scanResults) {
            int conn=scan.BSSID.equals(connectedWifi)?1:0;
            wifis.add(new WirelessDevice("F",scan.BSSID,scan.SSID,scan.level,conn));
        }
        return wifis;
    }

    public String getNetWorkTye() {
        switch (tm.getNetworkType()) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";

            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";

            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";

            default:
                return "unknown";
        }
    }

    public String getNetWorkStatus() {
        String netWorkType = "offline";
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            int type = networkInfo.getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                netWorkType = "wifi";
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                netWorkType = getNetWorkTye();
            }
        }
        return netWorkType;
    }

    public int getWifiLevel(int strength,int... numLevels){
        int defaultLevel = 5; //default value;
        int level;
        if (numLevels.length == 1) {
            if (numLevels[0] == 0) {  //no need calculate
                level = strength;
            } else {
                level = WifiManager.calculateSignalLevel(strength, numLevels[0]);
            }
        } else {
            level = WifiManager.calculateSignalLevel(strength, defaultLevel);
        }
        return level;
    }
}
