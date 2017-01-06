package com.example.apple.huanxin.presenter;

import android.content.Context;
import android.util.Log;

import com.hyphenate.EMCallBack;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMMessage;

/**
 * Created by apple on 16/12/23.
 */

public class ChatPresenter {

    private Context context;
    private IChatPresenter iChatPresenter;
    private final String TAG = "ChatPresenter";

    public ChatPresenter(Context context, IChatPresenter iChatPresenter) {
        this.context = context;
        this.iChatPresenter = iChatPresenter;
    }

    public void sendMessage(String content,String userName){

        //创建一条文本消息，content为消息文字内容，toChatUsername为对方用户或者群聊的id，后文皆是如此
        final EMMessage message = EMMessage.createTxtSendMessage(content, userName);
        //  text_content.setText(content);
       // text_content.setText(text_content.getText() + "\n"+ message.getFrom()+":"+ content + " - time: " + message.getMsgTime());
        //如果是群聊，设置chattype，默认是单聊
        // if (chatType == CHATTYPE_GROUP)
        //message.setChatType(EMMessage.ChatType.GroupChat);
        //发送消息
        EMClient.getInstance().chatManager().sendMessage(message);

        // 为消息设置回调
        message.setMessageStatusCallback(new EMCallBack() {
            @Override
            public void onSuccess() {
                // 消息发送成功，打印下日志，正常操作应该去刷新ui
                Log.e(TAG, "send message on success");
                iChatPresenter.setMessage(1+"");
            }

            @Override
            public void onError(int i, String s) {
                // 消息发送失败，打印下失败的信息，正常操作应该去刷新ui
                 Log.e(TAG, "send message on error " + i + " - " + s);
            }

            @Override
            public void onProgress(int i, String s) {
                // 消息发送进度，一般只有在发送图片和文件等消息才会有回调，txt不回调
            }
        });
    }


}
