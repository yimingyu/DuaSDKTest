package com.lovearthstudio.duasdk.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

public class FileUtil {
    public String local_image_path;
    public FileUtil(String path){
        this.local_image_path=path;
        makeDir(local_image_path);
    }
    public boolean saveBitmap(String filename, Bitmap bitmap){
        return saveBitmap(local_image_path,filename,bitmap);
    }
    public boolean isFileExists(String filename) {
        return isFileExists(filename,local_image_path);
    }
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
    public static boolean saveBitmap(String path,String filename, Bitmap bitmap) {
        if (!isExternalStorageWritable()) {
            Log.e("DuaFileUtil","SD卡不可用，保存失败");
            return false;
        }
        if (bitmap == null) {
            return false;
        }
        try {
            
            File file = new File(path,filename);
            FileOutputStream fos = new FileOutputStream(file);
            if((filename.contains("png"))||(filename.contains("PNG")))
            {  
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }  else{
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            }
            fos.flush();
            fos.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } 
    }
    public static boolean isFileExists(String filename,String path) {
        return new File(path,filename).exists();
    }

    public static File newFile(String fullName){
        int index=fullName.lastIndexOf(File.separator);
        String dir=fullName.substring(0,index);
        File file=new File(makeDir(dir),fullName.substring(index+1));
        if(!file.exists()) try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
    public static File makeDir(String path){
        File dir =new File(path);
        dir.mkdirs();
        return dir;
    }
}
