package com.lovearthstudio.duademo;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;


import com.lovearthstudio.duaui.DuaActivityLogin;
import com.lovearthstudio.duaui.DuaActivityProfile;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{


    private Button button;
    private Button profile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button=(Button)findViewById(R.id.button);
        button.setOnClickListener(this);
        profile=(Button)findViewById(R.id.profile);
        profile.setOnClickListener(this);
//        DuaPermissionUtil.requestDuaPermissions(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.button:
                String path= Environment.getExternalStorageDirectory()+ File.separator+"FileTest"+File.separator;
                String fileName=path+"test.txt";
                File file=new File(fileName);
//                Log.e("mkdir","mkdir结果 "+file.mkdir());
//                Log.e("mkdir","mkdir结果 "+file.mkdirs());

                new File(path).mkdirs();
                try {
                    Log.e("mkdir","mkdir结果 "+file.createNewFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }


                startActivityForResult(new Intent(this, DuaActivityLogin.class),10086);
                break;
            case R.id.profile:
                startActivity(new Intent(this, DuaActivityProfile.class));
                break;
        }
    }
}
