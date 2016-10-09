package com.lovearthstudio.duasdk;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kymjs.rxvolley.RxVolley;
import com.kymjs.rxvolley.client.HttpCallback;
import com.kymjs.rxvolley.client.HttpParams;
import com.kymjs.rxvolley.http.RequestQueue;
import com.kymjs.rxvolley.toolbox.FileUtils;
import com.lovearthstudio.duasdk.util.DuaPermissionUtil;
import com.lovearthstudio.duasdk.util.FileUtil;
import com.lovearthstudio.duasdk.util.JsonUtil;
import com.lovearthstudio.duasdk.util.LogUtil;
import com.lovearthstudio.duasdk.util.RxVolleyUtil;
import com.lovearthstudio.duasdk.util.SharedPreferenceUtil;
import com.lovearthstudio.duasdk.util.TimeUtil;
import com.lovearthstudio.duasdk.util.encryption.Des3;
import com.lovearthstudio.duasdk.util.encryption.MD5;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.lovearthstudio.duasdk.DuaConfig.MAX_APP_EVENT_LENGTH;

/**
 * Author：Mingyu Yi on 2016/8/7 16:26
 * Email：461072496@qq.com
 */
public class Dua {
    private static Dua instance=null;
    public static Dua getInstance(){
        return instance;
    }
    public static Dua init(Application context){
        if(instance==null){
            String path=context.getExternalCacheDir().getPath()+File.separator+"RxVolley"+File.separator;
            RxVolley.setRequestQueue(RequestQueue.newRequestQueue(FileUtil.makeDir(path)));
            DuaAppHandler handler=new DuaAppHandler();
            context.registerActivityLifecycleCallbacks(handler);
            context.registerComponentCallbacks(handler);
            instance=new Dua(context);
        }
        return instance;
    }
    private Dua(Context context){
        this.context=context;
        this.duaCollector =new DuaCollector(context);
    }
    private Context context;
    private String DUA_LOCAL_STORAGE="duaLocalStorage_0d82839cf42a298708e70c1f5a9f5872";
    public static final int JSON_ERR=10010;
    public static final int NULL_ERR=10086;
    public static final String NULL_ERR_STR="服务器无返回或为空";
    public static final int NET_ERR=1024;
    public static final String NET_ERR_STR ="请检查网络设置";
    public static final String NET_OFFLINE="offline";
    private DuaLocalStorage duaLocalStorage;
    public DuaCollector duaCollector;
    private Gson gson = new Gson();
    public  DuaUser duaUser;


    public  void duaSleep(){
        long now= TimeUtil.getCurrentTimeStamp();
        duaLocalStorage_load();
        duaLocalStorage.curEventDuration +=now-duaLocalStorage.curEventLastStart;
        duaLocalStorage.curEventCount +=1;
        duaLocalStorage.curEventLastPause=now;
        duaLocalStorage_save();
    }
    public  void duaAwake(){
        final long t,d,c;
        duaLocalStorage_load();
        long now= TimeUtil.getCurrentTimeStamp();
        duaLocalStorage.lastOpenTime=now;
        if(now-duaLocalStorage.curEventLastPause> DuaConfig.APP_EVENT_TIME_GAP){
            t=duaLocalStorage.curEventStart;
            d=duaLocalStorage.curEventDuration;
            c=duaLocalStorage.curEventCount;

            duaLocalStorage.curEventStart=now;
            duaLocalStorage.curEventDuration=0;
            duaLocalStorage.curEventCount=0;
            duaLocalStorage.curEventLastPause=0;
            duaLocalStorage.curEventLastStart=now;
        }else{
            t=0;
            d=0;
            c=0;
            duaLocalStorage.curEventLastStart=now;
        }
        duaLocalStorage_save();
        new DuaIdRequest(null){
            @Override
            public void doWithDuaId(long dua_id) {
                if(DuaConfig.debugLog) LogUtil.e("当前DuaId",dua_id+"");
                saveAppEvent(dua_id, t, d, c,TimeUtil.getTimeZoneGMT());
                if (!duaCollector.getNetWorkStatus().equals(NET_OFFLINE)){
                    uploadAppEvents(dua_id);
                    uploadAppLists(dua_id);
                    uploadAppStats(dua_id);
                    uploadWirelessDevices2(dua_id);
                }
            }
        }.doRequest();
    }
    public  void duaExit(){//扫描蓝牙中有一个广播接收器，可能出现内存泄露
        duaCollector.unregisterReciver();
    }
    public long getCurrentDuaId(){
        duaLocalStorage_load();
        if(getCurrentDuaUser().logon&&duaLocalStorage.currentDuaId!=-1){
            return duaLocalStorage.currentDuaId;
        }else if(duaLocalStorage.anonymousDuaId!=-1){
            return duaLocalStorage.anonymousDuaId;
        }else{
            applyDuaId(null);
            return 0;
        }
    }
    public  void getCurrentDuaId(DuaCallback mcb){
        duaLocalStorage_load();
        if(getCurrentDuaUser().logon&&duaLocalStorage.currentDuaId!=-1){
            callbackOnSuccess(mcb,"获取DuaId",duaLocalStorage.currentDuaId+"");
        }else if(duaLocalStorage.anonymousDuaId!=-1){
            callbackOnSuccess(mcb,"获取DuaId",duaLocalStorage.anonymousDuaId+"");
        }else{
            applyDuaId(mcb);
        }
    }
    public String getNetworkStatus(){
        return duaCollector.getNetWorkStatus();
    }
    public void logout(){
        getCurrentDuaUser().logon=false;
        if(!DuaConfig.keepLoginHistory){
            duaUser.ustr="";
            duaUser.avatar="";
        }
        duaUser.pwd="";
        duaUser.tel="";
        duaUser.rules=new ArrayList<String>();
        duaUser_save();
    }
    public void resetPwd(final String ustr, final String vfcode, final String pwd, final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try {
                    JSONObject jo = new JSONObject();
                    jo.put("ustr", ustr);
                    jo.put("vfcode",vfcode);
                    jo.put("pwd", MD5.md5(pwd));
                    jo.put("dua_id", dua_id);
                    jo.put("action", "reset_pwd");
                    String jstr = jo.toString();
                    new DuaNetRequest("重设密码", DuaConfig.serverUrl + "/users", jstr, mcb).doRequest();
                }catch (Exception e){
                    callbackOnError(mcb,"重设密码",JSON_ERR, e.toString());
                }
            }
        }.doRequest();
    }
    public void login(String ustr, String pwd, String role, DuaCallback mcb){
        cookieLogin(ustr, MD5.md5(pwd),role,mcb);
    }
    private void cookieLogin(final String ustr, final String pwd, final String role, final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try {
                    JSONObject jo = new JSONObject();
                    jo.put("ustr", ustr);
                    jo.put("pwd", pwd);
                    jo.put("dua_id", dua_id);
                    jo.put("action", "login");
                    jo.put("role", role);
                    String jstr = jo.toString();

                    new DuaNetRequest("登录", DuaConfig.serverUrl + "/users", jstr, mcb){
                        @Override
                        public void doSuccessExtra(Object result) {
                            try {
                                JSONObject resultContent=(JSONObject)result;
                                duaLocalStorage.currentDuaId=resultContent.getLong("dua_id");
                                String rules=resultContent.getString("rules");
                                duaUser.rules=gson.fromJson(rules,new TypeToken<ArrayList<String>>() {}.getType());
                                if(DuaConfig.keepLogon){
                                    duaUser.logon=true;
                                    duaUser.ustr=ustr;
                                    int index=ustr.indexOf("-");
                                    duaUser.zone=ustr.substring(0,index);
                                    duaUser.tel=ustr.substring(index+1);
                                    duaUser.pwd=pwd;
                                    duaUser.role=role;
                                }
                                duaLocalStorage_save();
                                duaUser_save();
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void doErrorExtra(int status,String reason) {
                            logout();
                        }
                    }.doRequest();
                }catch (Exception e){
                    callbackOnError(mcb,"登录",JSON_ERR, e.toString());
                }
            }
        }.doRequest();
    }
    public void checkVfCode(final String ustr,final String vfcode,final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try {
                    JSONObject jo = new JSONObject();
                    jo.put("ustr", ustr);
                    jo.put("type","T");
                    jo.put("vfcode", vfcode);
                    jo.put("dua_id", dua_id);
                    jo.put("action", "vfcode");
                    String jstr = jo.toString();
                    new DuaNetRequest("检查验证码", DuaConfig.serverUrl + "/users", jstr, mcb){}.doRequest();
                }catch (Exception e){
                    callbackOnError(mcb,"检查验证码",JSON_ERR, e.toString());
                }
            }
        }.doRequest();
    }
    public void register(final String ustr, final String pwd, final String role, final String vfcode, final String incode,
                         final String type, final String name, final String sex, final String bday, final String avatar, final DuaCallback mcb){
        try {
            JSONObject jo=new JSONObject();
            jo.put("ustr",ustr);
            jo.put("pwd", MD5.md5(pwd));
            jo.put("dua_id",duaLocalStorage.anonymousDuaId);
            jo.put("action","register");
            jo.put("type",type);
            jo.put("role", role);
            jo.put("vfcode",vfcode);
            jo.put("incode",incode);
            jo.put("name",name);
            jo.put("sex",sex);
            jo.put("bday", bday);
            jo.put("avatar",avatar);
            String jstr=jo.toString();
            new DuaNetRequest("注册", DuaConfig.serverUrl + "/users", jstr, mcb){
                @Override
                protected void doSuccessExtra(Object result) {
                    try {
                        duaLocalStorage.currentDuaId=((JSONObject)result).getLong("dua_id");
                        duaLocalStorage_save();
                    }catch (Exception e){
                        doErrorExtra(JSON_ERR,e.toString());
                    }
                }
            }.doRequest();
        }catch (Exception e){
            callbackOnError(mcb,"注册",JSON_ERR, e.toString());
        }
    }
    public void getVfCode(final String ustr, final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try {
                    JSONObject jo=new JSONObject();
                    jo.put("ustr",ustr);
                    jo.put("dua_id",dua_id);
                    jo.put("action", "get_vfcode");
                    String jstr=jo.toString();

                    new DuaNetRequest("验证码", DuaConfig.serverUrl + "/auth", jstr, mcb){}.doRequest();
                }catch (Exception e){
                    callbackOnError(mcb,"验证码",JSON_ERR, e.toString());
                }
            }
        }.doRequest();
    }
    public void auth(final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try{
                    JSONObject jo=new JSONObject();
                    jo.put("rule", "");
                    jo.put("dua_id",dua_id);
                    jo.put("action","dua_auth");
                    String jstr=jo.toString();
                    new DuaNetRequest("Auth", DuaConfig.serverUrl + "/auth",jstr,mcb).doRequest();
                }catch (Exception e){
                    callbackOnError(mcb,"Auth",JSON_ERR,e.toString());
                }
            }
        }.doRequest();
    }

    public void setUserProfile(final JSONObject fields, final DuaCallback mcb){
        new DuaIdRequest(mcb) {
            @Override
            public void doWithDuaId(long dua_id) {
                JSONObject jo = new JSONObject();
                try {
                    jo.put("dua_id", dua_id);
                    jo.put("action", "set_profile");
                    jo.put("fields", fields);
                    new DuaNetRequest("设置用户资料", DuaConfig.serverUrl + "/users", jo.toString(), mcb).doRequest();
                } catch (JSONException e) {
                    callbackOnError(mcb,"设置用户资料",JSON_ERR,e.toString());
                }
            }
        }.doRequest();
    }
    public void getUserProfile(final JSONArray fields,final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                JSONObject jo=new JSONObject();
                try {
                    jo.put("dua_id",dua_id);
                    jo.put("action","get_profile");
                    jo.put("fields",fields);
                    new DuaNetRequest("获取用户资料", DuaConfig.serverUrl+"/users",jo.toString(),mcb){
                        @Override
                        protected void doSuccessExtra(Object result) {
                            try {
                                JSONObject user= (JSONObject) result;
                                duaUser.avatar=user.getString("avatar");
                                duaUser.sex=user.getString("sex");
                                duaUser.bday=user.getString("bday");
                                duaUser.name=user.getString("name");
                                duaUser_save();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }.doRequest();
                } catch (JSONException e) {
                    callbackOnError(mcb,"获取用户资料",JSON_ERR,e.toString());
                }
            }
        }.doRequest();
    }
    public void setAppPmc(final String event, final int param, final String punit, final long stats, final String sunit){
        final DuaCallback mcb=null;
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try {
                    JSONObject jo=new JSONObject();
                    jo.put("dua_id",dua_id);
                    jo.put("action", "set_pmc");
                    jo.put("event",event);
                    jo.put("param",param);
                    jo.put("punit",punit);
                    jo.put("stats",stats);
                    jo.put("sunit",sunit);
                    String jstr=jo.toString();

                    new DuaNetRequest("App性能", DuaConfig.serverUrl + "/apps",jstr, mcb){}.doRequest();
                }catch (Exception e){
                    callbackOnError(mcb,"App性能",JSON_ERR, e.toString());
                }
            }
        }.doRequest();
    }

    public void updateAvatar(String imgFullName, final DuaCallback callback){
        DuaCallback dcb=new DuaCallback() {
            @Override
            public void onSuccess(Object result) {
                try {
                    JSONObject jo=(JSONObject)result;
                    final String url=jo.getString("url");
                    duaUser.avatar=url;
                    duaUser_save();
                    JSONObject fields=new JSONObject();
                    fields.put("avatar",url);
                    setUserProfile(fields,callback);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            @Override
            public void onError(int status, String reason) {
                callbackOnError(callback,"图片上传",status,"图片上传失败:"+reason);
            }
        };
        uploadAvatar(imgFullName,dcb);
    }
    public void uploadAvatar(String imgFullName, final DuaCallback callback){
        HttpCallback httpCallback=new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                JSONObject jo=null;
                String err=null;
                String url="";
                try {
                    jo= JsonUtil.toJsonObject(response).getJSONArray("files").getJSONObject(0);
                    url=jo.getString("url");
                    err=jo.getString("error");
                } catch (Exception e) {
                }
                if (jo==null|| TextUtils.isEmpty(url)){ //此处暂时不知上传成功的标准
                    callbackOnError(callback,"图片上传",NULL_ERR,response);
                }else {
                    callbackOnSuccess(callback,"图片上传",jo);
                }
            }
            @Override
            public void onFailure(int errorNo, String strMsg) {
                callbackOnError(callback,"图片上传",errorNo,strMsg);
            }
        };
        RxVolleyUtil.uploadFile("http://files.xdua.org/index.php",new File(imgFullName),
                httpCallback,"dua_id",duaLocalStorage.currentDuaId+"","subdir","avatar/");
    }

    public void uploadSleepData(final String data, final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try{
                    JSONObject jo=new JSONObject();
                    jo.put("dua_id",dua_id);
                    jo.put("data",data);
                    jo.put("gmt",TimeUtil.getTimeZoneGMT());
                    String jstr=jo.toString();
                    new DuaNetRequest("睡眠数据","http://api.ivita.org/sleep",jstr,mcb){}.doRequest();
                }catch (Exception e){
                    e.printStackTrace();
                    callbackOnError(mcb,"睡眠数据",JSON_ERR,e.toString());
                }
            }
        }.doRequest();
    }
    public void uploadSleepFeature(final JSONObject jo, final DuaCallback mcb){
        new DuaIdRequest(mcb){
            @Override
            public void doWithDuaId(long dua_id) {
                try{
                    jo.put("dua_id",dua_id);
                    jo.put("timestamp", TimeUtil.getCurrentTimeStamp());
                    jo.put("gmt",TimeUtil.getTimeZoneGMT());
                    String jstr=jo.toString();
                    new DuaNetRequest("睡眠特征","http://api.ivita.org/sleepFeature",jstr,mcb){}.doRequest();
                }catch (Exception e){
                    e.printStackTrace();
                    callbackOnError(mcb,"睡眠特征",JSON_ERR,e.toString());
                }
            }
        }.doRequest();
    }

    public void uploadSingleBle(String mac,String name,int rssi){
        if(name==null) name="";
        uploadSingleWld("B",mac,name,rssi,1);
    }
    public void uploadSingleWld(String type,String mac,String name,int rssi,int reg){
        List<DuaCollector.WirelessDevice> list=new ArrayList<>();
        list.add(duaCollector.new WirelessDevice(type,mac,name,rssi,reg));
        uploadWirelessDevices(list,getCurrentDuaId(),TimeUtil.getCurrentTimeStamp(),TimeUtil.getTimeZoneGMT());
    }

    private void uploadWirelessDevices2(final long dua_id){
        final long curTime= TimeUtil.getCurrentTimeStamp();
        if(curTime-duaLocalStorage.lastWldUploadTime>=5*60*1000){
            final List<DuaCollector.WirelessDevice> jl=new ArrayList<DuaCollector.WirelessDevice>();
            jl.addAll(duaCollector.getNCI());
            jl.addAll(duaCollector.getWifiList());
            DuaCallback mcb=new DuaCallback() {
                @Override
                public void onSuccess(Object result) {
                    try {
                        jl.addAll((List<DuaCollector.WirelessDevice>)result);
                        uploadWirelessDevices(jl,dua_id,curTime,TimeUtil.getTimeZoneGMT());
                    }catch (Exception e){
                    }
                }

                @Override
                public void onError(int status,String reason) {
                    uploadWirelessDevices(jl,dua_id,curTime,TimeUtil.getTimeZoneGMT());
                }
            };
            duaCollector.scanBluetooth(mcb);
        }else {
            if(DuaConfig.debugLog)
                LogUtil.e("Wld", "last upload time " + TimeUtil.toTimeString(duaLocalStorage.lastWldUploadTime));
        }

    }
    private void uploadWirelessDevices(List<DuaCollector.WirelessDevice> list, long dua_id, final long time,final double gmt){
        try{
            JSONObject jo=new JSONObject();
            jo.put("dua_id",dua_id);
            jo.put("action","add_wlds");
            jo.put("wlds",new JSONArray(gson.toJson(list)));
            jo.put("time", 0);
            jo.put("gmt",gmt);
            Location location= duaCollector.getCurrentLocation();
            if(location!=null){
                jo.put("gps",1);
                jo.put("lat",location.getLatitude());
                jo.put("lon",location.getLongitude());
                jo.put("acc",location.getAccuracy());
            }else{
                jo.put("gps",0);
                jo.put("lat",0);
                jo.put("lon",0);
                jo.put("acc",0);
                if(DuaConfig.debugLog) LogUtil.e("WirelessDevices","无法获取gps信息");
            }
            String info=jo.toString();
            new DuaNetRequest("上传Wld", DuaConfig.serverUrl + "/wlds", info, null){
                @Override
                protected void doSuccessExtra(Object result) {
                    duaLocalStorage.lastWldUploadTime = time;
                    duaLocalStorage_save();
                }
            }.doRequest();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private  void uploadWirelessDevices(long dua_id){
        final long curTime= TimeUtil.getCurrentTimeStamp();
        if(curTime-duaLocalStorage.lastWldUploadTime>=5*60*1000){
            List<DuaCollector.WirelessDevice> jl=new ArrayList<DuaCollector.WirelessDevice>();
            jl.addAll(duaCollector.getNCI());
            jl.addAll(duaCollector.getWifiList());
            jl.addAll(duaCollector.getBoundBlueTeeth());
            uploadWirelessDevices(jl,dua_id,TimeUtil.getCurrentTimeStamp(),TimeUtil.getTimeZoneGMT());
        }else {
            if(DuaConfig.debugLog)
                LogUtil.e("Wld", "last upload time " + TimeUtil.toTimeString(duaLocalStorage.lastWldUploadTime));
        }
    }
    private  void uploadAppStats(long dua_id){
        try {
            final long curTime= TimeUtil.getCurrentTimeStamp();
            long difTime=(curTime-duaLocalStorage.lastAppStatUploadTime)/1000;
            List<JSONObject> jl=new ArrayList<JSONObject>();
            if(difTime>3600*24*7){
                long startTime= TimeUtil.getYearsAgo(-3);
                jl.addAll(duaCollector.getAppStats(startTime, curTime, 1));
                jl.addAll(duaCollector.getAppStats(startTime, curTime, 2));
                jl.addAll(duaCollector.getAppStats(startTime, curTime, 3));
                jl.addAll(duaCollector.getAppStats(startTime, curTime, 4));
            }else if(difTime >= 3600 * 24 * 1 && difTime <= 3600 * 24 * 7){
                long startTime= TimeUtil.getDaysAgo(-7);
                jl.addAll(duaCollector.getAppStats(startTime, curTime, 1));
                jl.addAll(duaCollector.getAppStats(startTime, curTime, 2));
            }else{
                if(DuaConfig.debugLog) LogUtil.e("AppStats", "last upload time " + TimeUtil.toTimeString(duaLocalStorage.lastAppStatUploadTime));
                return;
            }
            JSONArray ja=new JSONArray();
            for(JSONObject stat:jl){
                long ttf=stat.getLong("ttf");
                if(ttf>0){
                    stat.put("ttf",ttf/1000);
                    long fts=stat.getLong("fts");
                    long lts=stat.getLong("lts");
                    switch (stat.getInt("type")){
                        case 1: {
                            stat.put("date", TimeUtil.toTimeString(fts,"yyyyMMdd"));
                            break;
                        }
                        case 2: {
                            int[] index= TimeUtil.rangeGetIndex(fts,lts,2);
                            stat.put("date",""+(index[2] * 10000 + index[1] * 100 + index[0]));
                            break;
                        }
                        case 3: {
                            int[] index= TimeUtil.rangeGetIndex(fts,lts, 3);
                            stat.put("date",""+(index[1] * 100 + index[0]));
                            break;
                        }
                        case 4: {
                            stat.put("date",""+ TimeUtil.rangeGetIndex(fts,lts,4)[0]);
                            break;
                        }
                    }
                    ja.put(stat);
                }
            }
            if(ja.length()!=0){
                JSONObject jo=new JSONObject();
                jo.put("dua_id",dua_id);
                jo.put("action","dev_stat");
                jo.put("data", ja);
                String str=jo.toString();
                new DuaNetRequest("上传AppStat", DuaConfig.serverUrl + "/duas", str, null){
                    @Override
                    protected void doSuccessExtra(Object result) {
                        duaLocalStorage.lastAppStatUploadTime = curTime;
                        duaLocalStorage_save();
                    }
                }.doRequest();
            }else{
                if(DuaConfig.debugLog) LogUtil.e("AppStats", "got no app usage stats");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private  void uploadAppLists(long dua_id){
        final long curTime= TimeUtil.getCurrentTimeStamp();
        if(curTime-duaLocalStorage.lastAppListUploadTime>=7*24*3600*1000){
            try {
                JSONObject jo=new JSONObject();
                jo.put("dua_id",dua_id);
                jo.put("action", "add_rats");
                jo.put("data", duaCollector.getAppLists());
                String str=jo.toString();
                new DuaNetRequest("上传AppList", DuaConfig.serverUrl + "/apps", str, null){
                    @Override
                    protected void doSuccessExtra(Object result) {
                        duaLocalStorage.lastAppListUploadTime = curTime;
                        duaLocalStorage_save();
                    }
                }.doRequest();
            }catch (Exception e){
                e.printStackTrace();
            }
        }else{
            if(DuaConfig.debugLog) LogUtil.e("AppLists", "last upload time " + TimeUtil.toTimeString(duaLocalStorage.lastAppListUploadTime));
        }
    }
    private  void uploadAppEvents(long dua_id){
        if(duaLocalStorage.needUploadAppEvent==1){
            JSONArray events=getAppEvents();
            if(events!=null&&events.length()>=MAX_APP_EVENT_LENGTH){//此处判断可无
                try {
                    JSONObject jo=new JSONObject();
                    jo.put("dua_id",dua_id);
                    jo.put("action","dua_active");
                    jo.put("channel","Debug");
                    jo.put("model", duaCollector.getModel());
                    jo.put("os", duaCollector.getPlatform() + " " + duaCollector.getOSVersion());
                    jo.put("version", duaCollector.getVersionNumber() + " " + duaCollector.getVersionCode());
                    jo.put("event", events);
                    String str=jo.toString();
                    new DuaNetRequest("上传AppEvent", DuaConfig.serverUrl + "/duas", str, null){
                        @Override
                        protected void doSuccessExtra(Object result) {
                            duaLocalStorage.needUploadAppEvent=0;
                            duaLocalStorage.lastAppEventUploadTime= TimeUtil.getCurrentTimeStamp();
                            duaLocalStorage_save();
                            SharedPreferenceUtil.prefSetKey(context, DUA_LOCAL_STORAGE, "AppEvent", new JSONArray().toString());
                        }
                    }.doRequest();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }else{
            if(duaLocalStorage.lastAppEventUploadTime!=0){
                if(DuaConfig.debugLog) LogUtil.e("AppEvent","last upload time " + TimeUtil.toTimeString(duaLocalStorage.lastAppEventUploadTime));
            }
        }
    }
    private  void saveAppEvent(long dua_id,long timestamp,long duration,long count,double gmt){
        if(dua_id==0||(timestamp==0&&duration==0&&count==0)) return; //如果dua_id为0或第一次打开不保存
        JSONArray events=getAppEvents();
        JSONObject event=new JSONObject();
        try {
            event.put("dua_id",dua_id);
            event.put("t",timestamp);
            event.put("d",duration);
            event.put("c", count);
            event.put("gmt",gmt);
        }catch (Exception e){
            e.printStackTrace();
        }
        events.put(event);
        if(events.length()>=MAX_APP_EVENT_LENGTH){
            duaLocalStorage.needUploadAppEvent=1;
        }
        SharedPreferenceUtil.prefSetKey(context, DUA_LOCAL_STORAGE, "AppEvent", events.toString());
    }
    private JSONArray getAppEvents(){
        JSONArray events;
        String objStr= SharedPreferenceUtil.prefGetKey(context, DUA_LOCAL_STORAGE, "AppEvent", null);
        if(objStr==null){
            events=new JSONArray();
        }else{
            try {
                events=new JSONArray(objStr);
            }catch (Exception e){
                events=null;
                e.printStackTrace();
            }
        }
        return events;
    }

    private  class BornInfo{
        public long initime;
        public long lastime;
        public String avn;
        public int avc;
        public String aname;
        public String pname;
        public String dsn;
        public String model;
        public String os;
        public String man;
        public String channel="Debug";
        public String action="dua_born";
        public String key;
    }
    private BornInfo getBornInfo(){
        BornInfo bornInfo=new BornInfo();
        String appKey=duaCollector.getDuaAppKey();
        if(appKey==null) appKey="NoAppKey";
        bornInfo.key=appKey;
        bornInfo.avn= duaCollector.getVersionNumber();
        bornInfo.avc= duaCollector.getVersionCode();
        bornInfo.aname= duaCollector.getAppName();
        bornInfo.pname= duaCollector.getPackageName();
        bornInfo.dsn= duaCollector.getUuid();
        bornInfo.model= duaCollector.getModel();
        bornInfo.os= duaCollector.getPlatform()+" "+ duaCollector.getOSVersion();
        bornInfo.man= duaCollector.getManufacturer();
        bornInfo.initime=(TimeUtil.getCurrentTimeStamp()-duaLocalStorage.firtOpenTime)/1000;
        bornInfo.lastime=(TimeUtil.getCurrentTimeStamp()-duaLocalStorage.lastOpenTime)/1000;
        if (bornInfo.model == null)
            bornInfo.model = "Unknown";
        if (bornInfo.avn == null)
            bornInfo.avn = "1.1.1";
        if (bornInfo.avc == 0)
            bornInfo.avc = 110;
        if (bornInfo.aname == null)
            bornInfo.aname = "unknown";
        if (bornInfo.pname == null)
            bornInfo.pname = "com.lovearthstudio.unknown";
        if (bornInfo.model == null)
            bornInfo.model = "Unknown";
        return bornInfo;
    }
    private  void applyDuaId(final DuaCallback dcb){
        final long now= TimeUtil.getCurrentTimeStamp();
        String str=gson.toJson(getBornInfo());
        new DuaNetRequest("申请DuaId", DuaConfig.serverUrl + "/duas",str, dcb){
            @Override
            protected void doSuccessExtra(Object result) {
                long dua_id=Long.parseLong(result.toString());
                duaLocalStorage.anonymousDuaId=dua_id;
                duaLocalStorage.lastOpenTime =now;
                if(duaLocalStorage.firtOpenTime ==0){
                    duaLocalStorage.firtOpenTime =now;
                }
                duaLocalStorage_save();
            }
        }.doRequest();
    }
    public class DuaUser{
        public boolean logon=false;
        public String zone="+86";
        public String tel;
        public String ustr;
        public String pwd;
        public String avatar;
        public String sex;
        public String bday;
        public String name;
        public String role="member";
        public List<String> rules=new ArrayList<String>();
    }
    public DuaUser getCurrentDuaUser(){
        if(duaUser==null){
            String objStr= SharedPreferenceUtil.prefGetKey(context, DUA_LOCAL_STORAGE, "DuaUser", null);
            if (objStr != null) {
                try {
                    duaUser=gson.fromJson(Des3.decode(objStr), DuaUser.class);
                } catch (Exception e) {
                    e.printStackTrace();
                    duaUser=new DuaUser();
                }
            }else {
                duaUser=new DuaUser();
            }
        }
        return duaUser;
    }
    public void duaUser_save(){
        try{
            SharedPreferenceUtil.prefSetKey(context, DUA_LOCAL_STORAGE, "DuaUser", Des3.encode(gson.toJson(duaUser)));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private  class DuaLocalStorage{
        public long curEventStart=0;
        public long curEventDuration=0;
        public long curEventCount=0;
        public long curEventLastStart=0;
        public long curEventLastPause=0;

        public int needUploadAppEvent=0;

        public long lastAppListUploadTime=0;
        public long lastAppStatUploadTime=0;
        public long lastAppEventUploadTime=0;
        public long lastWldUploadTime=0;
        public long anonymousDuaId=-1;
        public long currentDuaId=-1;

        public long lastOpenTime = TimeUtil.getCurrentTimeStamp();
        public long firtOpenTime = TimeUtil.getCurrentTimeStamp();
    }
    private DuaLocalStorage duaLocalStorage_load(){
        if(duaLocalStorage==null){
            String objStr= SharedPreferenceUtil.prefGetKey(context, DUA_LOCAL_STORAGE, "DuaLocalStorage", null);
            if (objStr != null) {
                try {
                    //LogUtil.e(Des3.decode(objStr));
                    duaLocalStorage=gson.fromJson(Des3.decode(objStr), DuaLocalStorage.class);
                } catch (Exception e) {
                    e.printStackTrace();
                    duaLocalStorage=new DuaLocalStorage();
                }
            }else {
                duaLocalStorage=new DuaLocalStorage();
                duaLocalStorage_save();
            }
        }
        return duaLocalStorage;
    }
    private  void duaLocalStorage_save(){
        try{
            SharedPreferenceUtil.prefSetKey(context, DUA_LOCAL_STORAGE, "DuaLocalStorage", Des3.encode(gson.toJson(duaLocalStorage)));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void callbackOnSuccess(DuaCallback dcb, String tag, Object result){
        if(dcb !=null){
            dcb.onSuccess(result);
        }else{
            if(DuaConfig.debugLog) LogUtil.e(tag+"成功",result.toString());
        }
    }
    private void callbackOnError(DuaCallback dcb, String tag, int status, String reason){
        if(dcb !=null){
            dcb.onError(status,reason);
        }else{
            if(DuaConfig.debugLog) LogUtil.e(tag+"失败",status+" "+reason);
        }
    }

    public abstract class DuaIdRequest {
        private DuaCallback dcb;
        private DuaIdRequest(DuaCallback duaCallback){
            this.dcb =duaCallback;
        }
        public abstract void doWithDuaId(long dua_id);
        public void doRequest(){
            DuaCallback duaCallback =new DuaCallback(){
                @Override
                public void onSuccess(Object result) {
                    doWithDuaId(Long.parseLong(result.toString()));
                }
                @Override
                public void onError(int status, String reason) {
                    callbackOnError(dcb,"获取DuaId",status,reason);
                }
            };
            getCurrentDuaId(duaCallback);
        }
    }
    public class DuaNetRequest {
        private String tag;
        private String url;
        private HttpParams params;
        private DuaCallback dcb;

        public DuaNetRequest(String tag, String url, HttpParams params, DuaCallback dcb) {
            this.tag = tag;
            this.url = url;
            this.params = params;
            this.dcb = dcb;
        }
        public DuaNetRequest(String tag, String url, String json, DuaCallback dcb) {
            this.tag = tag;
            this.url = url;
            this.params =new HttpParams() ;
            this.params.putJsonParams(json);
            this.dcb = dcb;
        }

        public final void doRequest(){
            if(!getNetworkStatus().equals(NET_OFFLINE)){
                HttpCallback callback=new HttpCallback() {
                    @Override
                    public void onSuccess(String t) {
                        if(DuaConfig.debugLog){
                            LogUtil.e(tag,"参数："+params.getJsonParams()+"，结果："+t);
                        }
                        try {
                            JSONObject result=new JSONObject(t);
                            int status=result.getInt("status");
                            if(status==0){
                                Object data=result.get("result");  //如果返回result:null结果很奇怪
                                if(data==null||data.equals("null")||data.equals(null)) {
                                    doErrorExtra(NULL_ERR,NULL_ERR_STR);
                                    callbackOnError(dcb,tag,NULL_ERR,NULL_ERR_STR);
                                }else {
                                    doSuccessExtra(data);
                                    callbackOnSuccess(dcb,tag,data);
                                }
                            }else {
                                String reason=result.getString("reason");
                                doErrorExtra(status,reason);
                                callbackOnError(dcb,tag,status,reason);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            String str=e.toString();
                            if(str.contains("<!DOCTYPE")){
                                doErrorExtra(NET_ERR, "当前网络可能需要登录");
                                callbackOnError(dcb, tag, NET_ERR, "当前网络可能需要登录");
                            }else {
                                doErrorExtra(JSON_ERR, e.toString());
                                callbackOnError(dcb, tag, JSON_ERR, e.toString());
                            }
                        }
                    }

                    @Override
                    public void onFailure(int errorNo, String strMsg) {
                        if(strMsg==null) strMsg="null";    //500错误时,strMsg是null
                        if(DuaConfig.debugLog){
                            LogUtil.e(tag,"参数："+params.getJsonParams()+"，失败："+errorNo+" "+strMsg);
                        }
                        doErrorExtra(errorNo,strMsg);
                        callbackOnError(dcb,tag,errorNo,strMsg);
                    }
                };
                RxVolleyUtil.asyncPost(url,params,callback);
            }else {
                doErrorExtra(NET_ERR,NET_ERR_STR);
                callbackOnError(dcb,tag,NET_ERR,NET_ERR_STR);
            }
        }
        protected void doSuccessExtra(Object result){}
        protected void doErrorExtra(int status,String reason){}
    }
}
