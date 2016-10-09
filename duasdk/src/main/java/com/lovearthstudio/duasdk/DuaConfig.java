package com.lovearthstudio.duasdk;


import java.util.HashMap;
import java.util.Map;

/**
 * Author：Mingyu Yi on 2016/5/12 17:05
 * Email：461072496@qq.com
 */
public class DuaConfig {
    public static final String DUA_LAUNCH_MODE_LOGIN="Login";
    public static final String DUA_LAUNCH_MODE_REGISTER="Register";
    public static final String DUA_LAUNCH_MODE_RESET_PWD="ResetPassword";
    public static boolean keepLogon=true;
    public static boolean keepLoginHistory=true;
    public static boolean debugLog=false;

    public static  String serverUrl="http://api.xdua.org";
    public static  long APP_EVENT_TIME_GAP=5000;
    public static  int MAX_APP_EVENT_LENGTH=2;

    public static final Map<Integer,String> errCode=new HashMap<>();
    static {
        errCode.put(200016,"手机号已经注册");
        errCode.put(200017,"手机验证码错误");
        errCode.put(200025,"手机号没有注册");
        errCode.put(200026,"密码错误");
        errCode.put(200027,"没有权限");
        errCode.put(200002,"服务器错误");
        errCode.put(200101,"服务器错误");
        errCode.put(500,"服务器错误");
        errCode.put(404,"服务器错误");
        errCode.put(502,"网关错误，请稍后再试");
        errCode.put(-1,"网络连接超时");
        errCode.put(4,"不支持的手机号");
    }
    public static String getErrStr(int status,String reason){
        String str=errCode.get(status);
        if(str==null) str=reason;
        return str;
    }
}
