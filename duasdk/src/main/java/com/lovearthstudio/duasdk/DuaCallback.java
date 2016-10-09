package com.lovearthstudio.duasdk;

/**
 * Author：Mingyu Yi on 2016/8/7 16:48
 * Email：461072496@qq.com
 */
public interface DuaCallback {
    void onSuccess(Object result);
    void onError(int status, String reason);
}
