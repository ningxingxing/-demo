package com.example.apple.huanxin.ui;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.apple.huanxin.R;
import com.hyphenate.EMCallBack;
import com.hyphenate.EMConnectionListener;
import com.hyphenate.EMError;
import com.hyphenate.chat.EMClient;
import com.hyphenate.util.NetUtils;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private Button button;
    private Button logout;
    private Button friend;
    private EditText userName;
    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 判断sdk是否登录成功过，并没有退出和被踢，否则跳转到登陆界面
        if (!EMClient.getInstance().isLoggedInBefore()) {
            Intent intent = new Intent(MainActivity.this, Login.class);
            startActivity(intent);
            finish();
            return;
        }

        initView();
    }

    private void initView() {

        button = (Button)findViewById(R.id.btn);
        button.setOnClickListener(this);

        logout = (Button)findViewById(R.id.logout);
        logout.setOnClickListener(this);

        friend = (Button)findViewById(R.id.friend);
        friend.setOnClickListener(this);

        userName = (EditText)findViewById(R.id.userName);

        //注册一个监听连接状态的listener
        EMClient.getInstance().addConnectionListener(new MyConnectionListener());

    }

    //实现ConnectionListener接口
    private class MyConnectionListener implements EMConnectionListener {
        @Override
        public void onConnected() {
        }
        @Override
        public void onDisconnected(final int error) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if(error == EMError.USER_REMOVED){
                        // 显示帐号已经被移除
                    }else if (error == EMError.USER_LOGIN_ANOTHER_DEVICE) {
                        // 显示帐号在其他设备登录
                    } else {
                        if (NetUtils.hasNetwork(MainActivity.this)){
                            Toast.makeText(getApplication(),"连接不到聊天服务器",Toast.LENGTH_SHORT).show();
                            Log.e(TAG,"连接不到聊天服务器");
                            //连接不到聊天服务器
                        } else{
                            Toast.makeText(getApplication(),"当前网络不可用，请检查网络设置",Toast.LENGTH_SHORT).show();
                            Log.e(TAG,"当前网络不可用，请检查网络设置");
                            //当前网络不可用，请检查网络设置
                        }

                    }
                }
            });
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.logout:
                setLogout();

                break;

            case R.id.btn:
                if (!userName.getText().toString().equals("")) {
                    Intent intent = new Intent(this, Chat.class);
                    intent.putExtra("userName", userName.getText().toString());
                    startActivity(intent);
                }else {
                    Toast.makeText(getApplication(),"用户名不能为空！",Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.friend:
                Intent intent = new Intent(this,FriendActivity.class);
                startActivity(intent);
                break;
        }
    }

    /**
     * 退出登陆
     */
    private void setLogout(){
      //  EMClient.getInstance().logout(true);//同步方法

        //异步方法
        // 调用sdk的退出登录方法，第一个参数表示是否解绑推送的token，没有使用推送或者被踢都要传false
        EMClient.getInstance().logout(false, new EMCallBack() {
            @Override
            public void onSuccess() {
                Log.e("lzan13", "logout success");
                // 调用退出成功，结束app
                finish();
            }

            @Override
            public void onError(int i, String s) {
                Log.e("lzan13", "logout error " + i + " - " + s);
            }

            @Override
            public void onProgress(int i, String s) {

            }
        });
    }




}
