package com.example.apple.huanxin.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apple.huanxin.R;
import com.example.apple.huanxin.presenter.ChatPresenter;
import com.example.apple.huanxin.presenter.IChatPresenter;
import com.example.apple.huanxin.utils.MyListView;
import com.example.apple.huanxin.utils.PopWindow_image;
import com.facebook.drawee.view.DraweeView;
import com.hyphenate.EMError;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMChatManager;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMCmdMessageBody;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMImageMessageBody;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMTextMessageBody;
import com.hyphenate.chat.EMVoiceMessageBody;
import com.hyphenate.util.ImageUtils;
import com.hyphenate.util.VoiceRecorder;

import net.tsz.afinal.FinalBitmap;

import java.io.File;
import java.util.List;


public class Chat extends AppCompatActivity implements View.OnClickListener,EMMessageListener,IChatPresenter{

    private EditText input_text;
    private Button send;
    private TextView text_content;
    private Button sendImage;
    private Button recording;
    private MyListView listView;

    private VoiceRecorder voiceRecorder;// 环信封装的录音功能类

    private String userName;// 当前聊天的 ID

    // 当前会话对象
    private EMConversation mConversation;

    // 消息监听器
    private EMMessageListener mMessageListener;

    private final String TAG = "Chat";

    private ChatAdapter adapter;
    private boolean message_more = true;// 聊天记录是否还有更多
    private String startMsgId;// 获取聊天记录时的第一条信息id

    private ChatPresenter chatPresenter;

    protected static final int REQUEST_CODE_LOCAL = 3;

    private FinalBitmap fb;//显示图片

    public static boolean isPlaying = false;

    private MediaPlayer mPlayer = null;// 播放语音的对象（播放器）
    private String playMsgId = null;// 正在播放的语音信息id,用于判断播放的是哪一个语音信息
    private AnimationDrawable voiceAnimation = null;
    private ImageView playIv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Intent intent = getIntent();
        userName = intent.getStringExtra("userName");
        mMessageListener = this;

        listView = (MyListView)findViewById(R.id.listview);

        chatPresenter = new ChatPresenter(this,this);

        initView();
        initConversation();//获取历史记录数据
    }

    private void initView() {

        fb = FinalBitmap.create(Chat.this);
        fb.configLoadfailImage(R.drawable.ic_launcher);
        fb.configLoadingImage(R.drawable.ic_launcher);

        input_text = (EditText)findViewById(R.id.input_text);
        send = (Button)findViewById(R.id.send);
        send.setOnClickListener(this);

        sendImage = (Button)findViewById(R.id.sendImage);
        sendImage.setOnClickListener(this);

        recording = (Button)findViewById(R.id.recording);

        //录音时的动画
        voiceRecorder = new VoiceRecorder(new Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                // 切换msg切换图片
                recording.setBackgroundResource(R.drawable.record_animate_01);
            }
        });

        text_content = (TextView)findViewById(R.id.text_content);
        // 设置textview可滚动，需配合xml布局设置
        text_content.setMovementMethod(new ScrollingMovementMethod());

        listView.setonRefreshListener(new MyListView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Thread(new LoadMoreMsgRun()).start();// 加载聊天记录
            }
        });

        /**
         * 录音
         */
        recording.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(MotionEvent.ACTION_DOWN == event.getAction()){
                    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                        Toast.makeText(getApplication(),"内存卡不存在",Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    v.setPressed(true);

                    try {
                        voiceRecorder.startRecording(null,userName,getApplicationContext());

                    }catch (Exception e){
                        e.printStackTrace();
                        v.setPressed(false);
                        Toast.makeText(getApplication(),"录音失败，请重试",Toast.LENGTH_SHORT).show();

                        if (voiceRecorder!=null){
                            voiceRecorder.discardRecording();
                        }
                    }
                }else if (MotionEvent.ACTION_MOVE == event.getAction()){

                    if(event.getY()<0){
                        Toast.makeText(getApplication(),"松开手指,取消发送",Toast.LENGTH_SHORT).show();
                    }else {
                        Toast.makeText(getApplication(),"手指上滑,取消发送",Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }else if (MotionEvent.ACTION_UP ==event.getAction()){

                    v.setPressed(false);
                    if (event.getY()<0){//如果已经上滑后
                        voiceRecorder.discardRecording();
                        return true;
                    }
                    try {
                        int length = voiceRecorder.stopRecoding(); // 停止录音
                        if (length > 0) {

                            EMMessage message = EMMessage.createVoiceSendMessage(voiceRecorder.getVoiceFilePath(), length, userName);
                            EMClient.getInstance().chatManager().sendMessage(message);
                            recording.setBackgroundResource(R.drawable.record_animate_01);

                            Message msg3 = mHandler.obtainMessage();
                            msg3.what = 3;
                            mHandler.sendMessage(msg3);

                        } else if (length == EMError.FILE_INVALID) {
                            Toast.makeText(getApplication(),"无录音权限",Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplication(),"录音时间太短",Toast.LENGTH_SHORT).show();

                        }
                    }catch (Exception e){

                    }

                }

                return false;
            }
        });

    }

    /**
     *
     * @param msg
     */
    @Override
    public void setMessage(String msg) {
        Message msg1 = mHandler.obtainMessage();
        msg1.what = 1;
        msg1.obj = msg;
        mHandler.sendMessage(msg1);
    }

    class LoadMoreMsgRun implements Runnable {
        public void run() {
            // 判断是否还有更多
            if (message_more == true) {
                // 获取startMsgId之前的pagesize条消息，此方法获取的messages
                // sdk会自动存入到此会话中，app中无需再次把获取到的messages添加到会话中

                mConversation.loadMoreMsgFromDB(mConversation.getAllMessages().get(0).getMsgId(), 10);// 加载更多记录

                if (mConversation.getUnreadMsgCount()>mConversation.getAllMsgCount()) {// 表示获取到更多信息了

//                    loadFinish(1, mConversation.get - messageCount);// 重新设置适配器
//                    messageCount = conversation.getMsgCount();
                    startMsgId = mConversation.getAllMessages().get(0).getMsgId();// 设置第一条信息的id

                } else { // 没有获取到
                    loadFinish(2, 0); // 提示没有更多
                    message_more = false;// 设置没有更多标记
                }
            } else if (message_more == false) {
                loadFinish(2, 0); // 提示没有更多
            }

            loadFinish(3, 0);// lisetview下拉刷新完成
        }
    }

    // 加载完成后的ui处理
    private void loadFinish(final int tag, final int index) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (tag == 1) {
                    // 重新设置适配器
                    listView.setAdapter(adapter);
                    listView.setSelection(index);
                } else if (tag == 2) {
                    // toast("没有更多记录了");
                }else if (tag == 3)
                    listView.onRefreshComplete(); // 刷新完成
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){

            case R.id.send:
                String content = input_text.getText().toString();
                chatPresenter.sendMessage(content,userName);
                break;

            case R.id.sendImage:
                selectPicFromLocal();

                break;

//            case R.id.recording:
//
//                break;

            default:

                break;

        }
    }

    protected void selectPicFromLocal() {
        Intent intent;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");

        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_LOCAL);
    }

    /**
     * 初始化会话对象，并且根据需要加载更多消息
     */
    private void initConversation() {

        /**
         * 初始化会话对象，这里有三个参数么，
         * 第一个表示会话的当前聊天的 useranme 或者 groupid
         * 第二个是绘画类型可以为空
         * 第三个表示如果会话不存在是否创建
         */
        mConversation = EMClient.getInstance().chatManager().getConversation(userName, null, true);

//        for (int i = 0; i<mConversation.getAllMessages().size();i++){
//            Log.e(TAG,"mConversation="+mConversation.getAllMessages().get(i).toString()+"\n");
//        }
        // 设置当前会话未读数为 0
        mConversation.markAllMessagesAsRead();
       // startMsgId = mConversation.getAllMessages().get(0).getMsgId();// 设置第一条信息的id
        Log.e(TAG,"mConversation.getAllMessages()="+mConversation.getAllMessages());
        if (mConversation.getAllMessages()!=null) {
            adapter = new ChatAdapter(mConversation.getAllMessages());
            listView.setAdapter(adapter);
            listView.setSelection(listView.getBottom());
        }

        int count = mConversation.getAllMessages().size();
        if (count < mConversation.getAllMsgCount() && count < 20) {
            // 获取已经在列表中的最上边的一条消息id
            String msgId = mConversation.getAllMessages().get(0).getMsgId();
            // 分页加载更多消息，需要传递已经加载的消息的最上边一条消息的id，以及需要加载的消息的条数
            mConversation.loadMoreMsgFromDB(msgId, 20 - count);
        }
        // 打开聊天界面获取最后一条消息内容并显示
        if (mConversation.getAllMessages().size() > 0) {
            EMMessage messge = mConversation.getLastMessage();
          //  EMTextMessageBody body = (EMTextMessageBody) messge.getBody();
            // 将消息内容和时间显示出来
            //text_content.setText("聊天记录：" + body.getMessage() + " - time: " + mConversation.getLastMessage().getMsgTime());
        }
    }

    /**
     * 自定义实现Handler，主要用于刷新UI操作
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                 //   EMMessage message = (EMMessage) msg.obj;
                    // 这里只是简单的demo，也只是测试文字消息的收发，所以直接将body转为EMTextMessageBody去获取内容
                  //  EMTextMessageBody body = (EMTextMessageBody) message.getBody();
                    // 将新的消息内容和时间加入到下边

                  //  Log.e("nsc", body.getMessage());
                    //text_content.setText(body.getMessage());
                    mConversation = EMClient.getInstance().chatManager().getConversation(userName, null, true);
                    adapter.emMessages.add(mConversation.getLastMessage());
                    adapter.notifyDataSetChanged();
                    listView.setSelection(listView.getBottom());
                 //   text_content.setText(text_content.getText() + "\n"+message.getTo()+":" + body.getMessage() + " - time: " + message.getMsgTime());
                    break;

                case 1://
                    mConversation = EMClient.getInstance().chatManager().getConversation(userName, null, true);
                    adapter.emMessages.add(mConversation.getLastMessage());
                    adapter.notifyDataSetChanged();
                    listView.setSelection(listView.getBottom());
                    Log.e(TAG, "mConversation.getLastMessage()000000="+mConversation.getLastMessage());
                    break;

                case 2://刷新图片
                    mConversation = EMClient.getInstance().chatManager().getConversation(userName, null, true);
                    adapter.emMessages.add(mConversation.getLastMessage());
                    adapter.notifyDataSetChanged();
                    listView.setSelection(listView.getBottom());
                    Log.e(TAG, "mConversation.getLastMessage()000000="+mConversation.getLastMessage());

                    break;

                case 3://刷新语音
                    mConversation = EMClient.getInstance().chatManager().getConversation(userName, null, true);
                    adapter.emMessages.add(mConversation.getLastMessage());
                    adapter.notifyDataSetChanged();
                    listView.setSelection(listView.getBottom());

                    break;
            }
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        // 添加消息监听
        EMClient.getInstance().chatManager().addMessageListener(mMessageListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 移除消息监听
        EMClient.getInstance().chatManager().removeMessageListener(mMessageListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try{
            //停止语音播放
            if(mPlayer.isPlaying()){

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 收到新消息
     * @param messages
     */
    @Override
    public void onMessageReceived(List<EMMessage> messages) {
        //收到消息
        // 循环遍历当前收到的消息
        for (EMMessage message : messages) {
            if (message.getFrom().equals(userName)) {
                // 设置消息为已读
                mConversation.markMessageAsRead(message.getMsgId());

                // 因为消息监听回调这里是非ui线程，所以要用handler去更新ui
                Message msg = mHandler.obtainMessage();
                msg.what = 0;
                msg.obj = message;
                mHandler.sendMessage(msg);
            } else {
                // 如果消息不是当前会话的消息发送通知栏通知
            }
        }
    }

    /**
     * 收到新的 CMD 消息
     * @param list
     */
    @Override
    public void onCmdMessageReceived(List<EMMessage> list) {
        for (int i = 0; i < list.size(); i++) {
            // 透传消息
            EMMessage cmdMessage = list.get(i);
            EMCmdMessageBody body = (EMCmdMessageBody) cmdMessage.getBody();
            Log.e("nsc", body.action());
        }
    }

    /**
     * 收到新的已读回执
     * @param list
     */
    @Override
    public void onMessageReadAckReceived(List<EMMessage> list) {

    }

    /**
     * 收到新的发送回执
     * @param list
     */
    @Override
    public void onMessageDeliveryAckReceived(List<EMMessage> list) {

    }

    /**
     * 消息的状态改变
     * @param emMessage
     * @param o
     */
    @Override
    public void onMessageChanged(EMMessage emMessage, Object o) {

    }

    class ChatAdapter extends BaseAdapter{
        List<EMMessage> emMessages;

        ChatAdapter(List<EMMessage> emMessages){
            this.emMessages = emMessages;
        }

        @Override
        public int getCount() {
            return emMessages.size();
        }

        @Override
        public Object getItem(int i) {
            return emMessages.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            final EMMessage message = emMessages.get(i);//每条消息
            if(message.getFrom().equals(userName)){
                view = getLayoutInflater().inflate(R.layout.chat_item1,null);
            }else {
                view = getLayoutInflater().inflate(R.layout.chat_item2,null);
            }

            final TextView time = (TextView)view.findViewById(R.id.time);
            ImageView head = (ImageView)view.findViewById(R.id.head);
//            Bitmap b = message.getBody()
//            head.setImageBitmap();

            TextView tv = (TextView)view.findViewById(R.id.tv);
            final DraweeView image = (DraweeView)view.findViewById(R.id.image);

            long msgTime = message.getMsgTime(); // 信息时间
            if (i == 0 || msgTime - emMessages.get(i-1).getMsgTime() > 120000) {
                time.setVisibility(View.VISIBLE);

                long dateTaken = System.currentTimeMillis(); // 当前系统时间
                String currentDate = (String) DateFormat.format("yyyy/MM/dd",
                        dateTaken); // 当前系统日期
                String messageDate = (String) DateFormat.format("yyyy/MM/dd",
                        msgTime); // 信息的日期
                if (currentDate.equals(messageDate)) { // 如果是当天

                    time.setText(DateFormat.format("kk:mm:ss", msgTime));// 只显示时间
                }else {
                    time.setText(DateFormat.format("yyyy/MM/dd kk:mm:ss",// 显示日期加时间
                            msgTime));
                }
            }

            if (message.getType()==EMMessage.Type.TXT){//文本显示
                String mess = ((EMTextMessageBody)message.getBody()).getMessage();
                tv.setText(mess);
            }else if (message.getType()==EMMessage.Type.IMAGE){//图片显示
                tv.setVisibility(View.GONE);
                image.setVisibility(View.VISIBLE);

                if (message.getFrom().equals(userName)){ // 对方发的消息
                    String ThumbnailUrl = ((EMImageMessageBody) message.getBody()).getThumbnailUrl(); // 获取缩略图片地址
                    String thumbnailPath = ImageUtils.getScaledImage(getApplication(),ThumbnailUrl);
                    String imageRemoteUrl = ((EMImageMessageBody) message.getBody()).getRemoteUrl();// 获取远程原图片地址

                    fb.display(image,thumbnailPath);//显示图片
                    imageClick(image, imageRemoteUrl);//图片添加监听
                } else {
                    // 自己发的消息
                    String LocalUrl = ((EMImageMessageBody) message.getBody()).getLocalUrl(); // 获取本地图片地址
                    Bitmap bm = ImageUtils.decodeScaleImage(LocalUrl,160,160);//获取缩略图
                    image.setImageBitmap(bm);//显示图片
                    Log.e(TAG,"bm="+bm+"==LocalUrl="+LocalUrl);

                    imageClick(image, LocalUrl);//图片添加监听
                }
            }else if (message.getType() == EMMessage.Type.VOICE){//语音
                tv.setVisibility(View.GONE);
                tv.setGravity(Gravity.TOP);
                image.setVisibility(View.VISIBLE);
                tv.setText(((EMVoiceMessageBody) message.getBody()).getLength() + "”"); // 设置语音的时长
                // 设置为语音的图片
                if (message.direct() == EMMessage.Direct.RECEIVE){
                    image.setImageResource(R.drawable.chatfrom_voice_playing);
                }else {
                    image.setImageResource(R.drawable.chatto_voice_playing);
                }

                image.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        // 开始播放录音
                        startPlay(message, image);
                    }
                });

            }

            return view;
        }

        // 图片点击监听
        private void imageClick(ImageView image, final String imageUrl) {
            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {

                    //new PopWindow_Image(Chat.this, imageUrl).showAtLocation(arg0, 0, 0, 0);
                    new PopWindow_image(fb,Chat.this,imageUrl).showAtLocation(arg0,0,0,0);
                }
            });
        }
    }

    // 开始播放
    private void startPlay(final EMMessage message, ImageView image) {

        EMVoiceMessageBody voiceBody = (EMVoiceMessageBody) message.getBody();

        if (message.direct() == EMMessage.Direct.SEND) {
            // for sent msg, we will try to play the voice file directly
            playVoice(voiceBody.getLocalUrl(), message, image);
        } else {
            if (message.status() == EMMessage.Status.SUCCESS) {
                playVoice(voiceBody.getLocalUrl(), message, image);

            } else if (message.status() == EMMessage.Status.INPROGRESS) {
                //toast("信息还在发送中");
                Toast.makeText(getApplication(),"信息还在发送中",Toast.LENGTH_SHORT).show();
            } else if (message.status() == EMMessage.Status.FAIL) {
               // toast("接收失败");
                Toast.makeText(getApplication(),"接收失败",Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 播放录音
    public void playVoice(String filePath, final EMMessage message, final ImageView image) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("file not exist");
           // toast("语音文件不存在");
            return;
        }
        playMsgId = message.getMsgId();
        mPlayer = new MediaPlayer();

        try {
            mPlayer.setDataSource(filePath);
            mPlayer.prepare();
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    if (mPlayer == null) // 表示因为要播放其他语音时已经被停止了,所以不需要再次调用停止
                        return;
                    stopPlayVoice(message); // stop animation
                }
            });
            isPlaying = true;
            mPlayer.start();
            playIv = image;
            showAnimation(message);

        } catch (Exception e) {
        }
    }

    /**
     * 停止语音
     * @param message
     */
    public void stopPlayVoice(final EMMessage message) {
        voiceAnimation.stop();
        if (message.direct() == EMMessage.Direct.RECEIVE) {
            playIv.setImageResource(R.drawable.chatfrom_voice_playing);
        } else {
            playIv.setImageResource(R.drawable.chatto_voice_playing);
        }
        // stop play voice
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
        }
        isPlaying = false;
        playMsgId = null;
        adapter.notifyDataSetChanged();
    }

    // show the voice playing animation
    private void showAnimation(final EMMessage message) {

        // play voice, and start animation
        if (message.direct() == EMMessage.Direct.RECEIVE) {
            playIv.setImageResource(R.anim.voice_from_icon);
        } else {
            playIv.setImageResource(R.anim.voice_to_icon);
        }
        voiceAnimation = (AnimationDrawable) playIv.getDrawable();
        voiceAnimation.start();
    }


    /**
     * 选取图片
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode== Activity.RESULT_OK){
            if (requestCode==REQUEST_CODE_LOCAL){//发送图片

                if (data != null) {
                    Uri selectedImage = data.getData();
                    if (selectedImage != null) {
                        sendPicByUri(selectedImage);//发送图片
                    }
                }

            }

        }
    }

    /**
     * send image
     *
     * @param selectedImage
     */
    protected void sendPicByUri(Uri selectedImage) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        Cursor cursor = getApplication().getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            cursor = null;

            if (picturePath == null || picturePath.equals("null")) {
                Toast toast = Toast.makeText(getApplication(), R.string.cant_find_pictures, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                return;
            }
            sendImageMessage(picturePath);
        } else {
            File file = new File(selectedImage.getPath());
            if (!file.exists()) {
                Toast toast = Toast.makeText(getApplication(), R.string.cant_find_pictures, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                return;

            }
            sendImageMessage(file.getAbsolutePath());
        }

    }

    /**
     * 发送图片
     * @param imagePath
     */
    protected void sendImageMessage(String imagePath) {
        EMMessage message = EMMessage.createImageSendMessage(imagePath, false, userName);
        if (message==null){
            return;
        }
        EMClient.getInstance().chatManager().sendMessage(message);
        Message msg2 = mHandler.obtainMessage();
        msg2.what = 2;
        mHandler.sendMessage(msg2);
    }
}
