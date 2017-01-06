package com.example.apple.huanxin.ui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.apple.huanxin.R;
import com.hyphenate.EMContactListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.exceptions.HyphenateException;

public class FriendActivity extends AppCompatActivity implements View.OnClickListener{
    private Button addFriend;
    private EditText edit;
    private final String TAG = "FriendActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);

        initView();
    }

    private void initView() {
        addFriend = (Button)findViewById(R.id.addFriend);
        addFriend.setOnClickListener(this);

        edit = (EditText)findViewById(R.id.edit);

        EMClient.getInstance().contactManager().setContactListener(new EMContactListener() {
            @Override
            public void onContactAdded(String username) {
                //增加了联系人时回调此方法
                Log.e(TAG,"增加了联系人时回调此方法："+username);
            }

            @Override
            public void onContactDeleted(String username) {
                Log.e(TAG,"被删除："+username);
            }

            @Override
            public void onContactInvited(String username, String reason) {
                //收到好友邀请
                Log.e(TAG,"收到好友邀请："+username+"=="+reason);
            }

            @Override
            public void onContactAgreed(String username) {
                //好友请求被同意
                Log.e(TAG,"同意："+username);
            }

            @Override
            public void onContactRefused(String username) {
                //好友请求被拒绝
                Log.e(TAG,"拒绝："+username);
            }
        });

    }




    @Override
    public void onClick(View v) {
        switch (v.getId()){

            case R.id.addFriend://添加好友
                String addUserName = edit.getText().toString();
                //参数为要添加的好友的username和添加理由
                try {
                    EMClient.getInstance().contactManager().addContact(addUserName, "添加原因");

                    Toast.makeText(getApplicationContext(),"发送成功！",Toast.LENGTH_SHORT).show();
                } catch (HyphenateException e) {
                    Toast.makeText(getApplicationContext(),"发送错误！",Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

                break;

        }
    }
}
