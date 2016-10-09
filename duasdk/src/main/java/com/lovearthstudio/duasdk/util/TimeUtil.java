package com.lovearthstudio.duasdk.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;


public class TimeUtil {
    public static TimeZone timeZone=TimeZone.getDefault();
    public static String getCurrentTimeString(){
        return toTimeString(getCurrentTimeStamp());
    }
    public static String getCurrentTimeString(String fmt){
        return toTimeString(getCurrentTimeStamp(),fmt);
    }
    public static long getCurrentTimeStamp(){
        return Calendar.getInstance(timeZone).getTimeInMillis();
    }
    public static double getTimeZoneGMT(){
        return (double) timeZone.getRawOffset()/(60*60*1000);
    }

    public static long getYearsAgo(int ys){
        Calendar calendar=Calendar.getInstance(timeZone);
        calendar.add(Calendar.YEAR, ys);
        return calendar.getTimeInMillis();
    }
    public static long getDaysAgo(int ds){
        Calendar calendar=Calendar.getInstance(timeZone);
        calendar.add(Calendar.DATE, ds);
        return calendar.getTimeInMillis();
    }
    public static String toTimeString(long ts){
        return toTimeString(ts,null);
    }
    public static String toTimeString(long ts,String fmt){
        if(fmt==null||fmt.equals("")) fmt="yyyy-MM-dd HH:mm:ss:SS";
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(fmt);
            Calendar calendar=Calendar.getInstance(timeZone);
            calendar.setTimeInMillis(ts);
            String timeStr = dateFormat.format(calendar.getTime());
            return timeStr;
        } catch (Exception e) {
            e.printStackTrace();
            return "Invalid timestamp or format";
        }
    }
    public static int[] getWeekOfMonthInfo(long ts){
        Calendar calendar=Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(ts);

        int day=calendar.get(Calendar.DAY_OF_WEEK);  //1-7
        int wn=calendar.get(Calendar.WEEK_OF_MONTH); //0-6
        int month=calendar.get(Calendar.MONTH);      //0-11
        int year=calendar.get(Calendar.YEAR);
        return new int[]{day,wn,month,year};
    }
    public static int[] getMonthOfYearInfo(long ts){
        Calendar calendar=Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(ts);
        int dm=calendar.get(Calendar.DAY_OF_MONTH);   //1-31
        int month=calendar.get(Calendar.MONTH);
        int year=calendar.get(Calendar.YEAR);
        return new int[]{dm,month,year};
    }
    public static int[] getYearRangeInfo(long ts){
        Calendar calendar=Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(ts);
        int dn=calendar.get(Calendar.DAY_OF_YEAR);   //1-366
        int year=calendar.get(Calendar.YEAR);
        return new int[]{dn,year};
    }
    public static int[] rangeGetIndex(long ts1,long ts2,int type){
        int[] index={};
        switch (type){
            case 2 :{    //七天大部分处于哪一月哪一周
                int[] p1=getWeekOfMonthInfo(ts1);
                int[] p2=getWeekOfMonthInfo(ts2);
                int wn,month,year;
                if(p1[0]+p2[0]<7){
                    wn=p1[1]+1;
                    month=p1[2]+1;
                    year=p1[3];
                }else{
                    wn=p2[1]+1;
                    month=p2[2]+1;
                    year=p2[3];
                }
                index=new int[]{wn,month,year};
                break;
            }
            case 3 : {       //30天大部分处于哪一年哪一月
                int[] p1=getMonthOfYearInfo(ts1);
                int[] p2=getMonthOfYearInfo(ts2);
                int month,year;
                if(p1[0]+p2[0]<30){
                    month=p1[1]+1;
                    year=p1[2];
                }else{
                    month=p2[1]+1;
                    year=p2[2];
                }
                index=new int[]{month,year};
                break;
            }
            case 4 : {      //365天大部分处于哪一年
                int[] p1=getYearRangeInfo(ts1);
                int[] p2=getYearRangeInfo(ts2);
                int year=p1[0]+p2[0]<365?p1[1]:p2[1];
                index=new int[]{year};
            }
        }
        return index;
    }

    public static long getUnixTimestamp(){
        return getCurrentTimeStamp()/1000;
    }
    public static long getPastSeconds(long unix, int days){
        Calendar calendar=Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(unix*1000);
        int hours=calendar.get(Calendar.HOUR_OF_DAY);
        int minutes=calendar.get(Calendar.MINUTE);
        int seconds=calendar.get(Calendar.SECOND);
        return hours*3600+minutes*60+seconds+days*86400;
    }

    public static String toPromptDate(long unix){
        long cur=getUnixTimestamp();
        long today= getPastSeconds(cur,0);
        long yesterday= getPastSeconds(cur,1);
        long lastday= getPastSeconds(cur,2);
        long time = cur - unix;
        if (time < 60 && time >= 0) {
            return time>5?time+"秒前":"刚刚";
        } else if (time >= 60 && time < 3600) {      //1-60 min
            return time / 60 + "分钟前";
        } else if (time >= 3600 && time < today) {   //今天
            return toTimeString(unix*1000,"HH:mm:ss");
        } else if (time >=today && time < yesterday) {       //昨天
            return "昨天"+toTimeString(unix*1000,"HH:mm:ss");
        } else if (time >=yesterday && time < lastday) {       //前天
            return "前天"+toTimeString(unix*1000,"HH:mm:ss");
        } else if (time >= lastday && time < 3600 * 24 * 30 * 12) {  //今年
            return toTimeString(unix*1000,"MM-dd HH:mm:ss");
        } else {
            return toTimeString(unix*1000,"yyyy-MM-dd HH:mm:ss");
        }
    }


    public static String toPromptPeriod(long unixPeriod){
        long min=unixPeriod/60;
        long sec=unixPeriod-min*60;
        if(min==0){
            return sec+"秒";
        }else if(min<5){
            return min+"分"+sec+"秒";
        }else {
            long hour=min/60;
            min=min-hour*60;
            if(hour==0){
                return min+"分";
            }else if(hour<24){
                return hour+"小时"+min+"分";
            }else {
                long day=hour/24;
                hour=hour-day*24;
                return day+"天"+hour+"小时";
            }
        }
    }

    public static String toPromptStr(long unix){
        Calendar cal=Calendar.getInstance(timeZone);
        long cur=cal.getTimeInMillis()/1000;
        cal.setTimeInMillis(unix*1000);
        int mon=cal.get(Calendar.MONTH)+1;
        int day=cal.get(Calendar.DATE);
        int hour=cal.get(Calendar.HOUR_OF_DAY);
        int min=cal.get(Calendar.MINUTE);


        long today= getPastSeconds(cur,0);
        long yesterday= getPastSeconds(cur,1);
        long time = cur - unix;
        if (time < 60 && time >= 0) {
            return "刚刚";
        } else if (time >= 60 && time < 3600) {      //1-60 min
            return time / 60 + "分钟前";
        } else if (time >= 3600 && time < today) {   //今天
            return getHourStr(hour)+hour+":"+min;
        } else if (time >=today && time < yesterday) {       //昨天
            return "昨天 "+getHourStr(hour)+hour+":"+min;
        } else if (time >= yesterday && time < 3600 * 24 * 30 * 12) {  //今年
            return mon+"月"+day+"日 "+getHourStr(hour)+hour+":"+min;
        } else {
            return cal.get(Calendar.YEAR)+"年"+mon+"月"+day+"日 "+getHourStr(hour)+hour+":"+min;
        }
    }

    public static String getHourStr(int hour){
        if(hour>=0 && hour<5){
            return "凌晨";
        }else if(hour>=5 && hour<8){
            return "早晨";
        }else if(hour>=8 && hour<12){
            return "上午";
        }else if(hour>=12 && hour<14){
            return "中午";
        }else if(hour>=14 && hour<18){
            return "下午";
        }else if(hour>=18 && hour<22){
            return "晚上";
        }else {
            return "深夜";
        }
    }
}
