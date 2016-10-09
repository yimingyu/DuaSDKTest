package com.lovearthstudio.duasdk.util;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Author：Mingyu Yi on 2016/9/14 10:05
 * Email：461072496@qq.com
 */
public class ComUtil {
    public static JSONArray listToJSONArray(Object...list){
        JSONArray ja=new JSONArray();
        for (Object o:list) {
            ja.put(o);
        }
        return ja;
    }
    public static List newIncArray(int start, int count){
        List array=new ArrayList<>();
        for (int i = 0; i <count ; i++) {
            array.add(start+i);
        }
        return array;
    }
    public static int[] newIncList(int start, int count){
        int[] list=new int[count];
        for (int i = 0; i <count ; i++) {
            list[i]=start+i;
        }
        return list;
    }
}
