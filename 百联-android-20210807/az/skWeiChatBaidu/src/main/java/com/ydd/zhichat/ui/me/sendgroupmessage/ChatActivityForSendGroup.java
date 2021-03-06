package com.ydd.zhichat.ui.me.sendgroupmessage;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.ydd.zhichat.AppConstant;
import com.ydd.zhichat.MyApplication;
import com.ydd.zhichat.R;
import com.ydd.zhichat.Reporter;
import com.ydd.zhichat.audio_x.VoicePlayer;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.PrivacySetting;
import com.ydd.zhichat.bean.VideoFile;
import com.ydd.zhichat.bean.message.ChatMessage;
import com.ydd.zhichat.bean.message.XmppMessage;
import com.ydd.zhichat.broadcast.MsgBroadcast;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.dao.ChatMessageDao;
import com.ydd.zhichat.downloader.Downloader;
import com.ydd.zhichat.helper.DialogHelper;
import com.ydd.zhichat.helper.FileDataHelper;
import com.ydd.zhichat.helper.PrivacySettingHelper;
import com.ydd.zhichat.helper.UploadEngine;
import com.ydd.zhichat.ui.base.BaseActivity;
import com.ydd.zhichat.ui.map.MapPickerActivity;
import com.ydd.zhichat.ui.me.LocalVideoActivity;
import com.ydd.zhichat.ui.me.sendgroupmessage.ChatBottomForSendGroup.ChatBottomListener;
import com.ydd.zhichat.util.TimeUtils;
import com.ydd.zhichat.util.ToastUtil;
import com.ydd.zhichat.video.EasyCameraActivity;
import com.ydd.zhichat.video.MessageEventGpu;
import com.ydd.zhichat.view.SelectCardPopupWindow;
import com.ydd.zhichat.view.SelectFileDialog;
import com.ydd.zhichat.view.photopicker.PhotoPickerActivity;
import com.ydd.zhichat.view.photopicker.SelectModel;
import com.ydd.zhichat.view.photopicker.intent.PhotoPickerIntent;
import com.ydd.zhichat.xmpp.CoreService;
import com.ydd.zhichat.xmpp.CoreService.CoreServiceBinder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;
import de.greenrobot.event.ThreadMode;
import fm.jiecao.jcvideoplayer_lib.MessageEvent;
import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;


/**
 * ????????????
 */
public class ChatActivityForSendGroup extends BaseActivity implements
        ChatBottomListener, SelectCardPopupWindow.SendCardS {
    // ????????????????????????
    private static final int REQUEST_CODE_PICK_PHOTO = 2;
    private static final int REQUEST_CODE_SELECT_VIDE0 = 3;
    private static final int REQUEST_CODE_SELECT_Locate = 5;
    private TextView mCountTv;
    private TextView mNameTv;
    private ChatBottomForSendGroup mChatBottomView;
    private CoreService mService;
    private String mLoginUserId;
    private String mLoginNickName;
    private List<String> userIds;
    private List<String> mCloneUserIds;
    private String userNames;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((CoreServiceBinder) service).getService();
        }
    };
    private UploadEngine.ImFileUploadResponse mUploadResponse = new UploadEngine.ImFileUploadResponse() {

        @Override
        public void onSuccess(String toUserId, ChatMessage message) {
            message.setUpload(true);
            message.setUploadSchedule(100);
            send(message);
        }

        @Override
        public void onFailure(String toUserId, ChatMessage message) {
            Toast.makeText(mContext, getString(R.string.upload_failed), Toast.LENGTH_SHORT).show();
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_for_sg);

        String ids = getIntent().getStringExtra("USERIDS");
        userIds = JSON.parseArray(ids, String.class);
        userNames = getIntent().getStringExtra("USERNAMES");
        mCloneUserIds = new ArrayList<>();
        mCloneUserIds.addAll(userIds);

        mLoginUserId = coreManager.getSelf().getUserId();
        mLoginNickName = coreManager.getSelf().getNickName();

        bindService(CoreService.getIntent(), mConnection, BIND_AUTO_CREATE);
        EventBus.getDefault().register(this);
        Downloader.getInstance().init(MyApplication.getInstance().mAppDir + File.separator + mLoginUserId
                + File.separator + Environment.DIRECTORY_MUSIC);

        initActionBar();
        initView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unbindService(mConnection);
        EventBus.getDefault().unregister(this);
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        TextView tvTitle = (TextView) findViewById(R.id.tv_title_center);
        tvTitle.setText(getString(R.string.mass));
    }

    private void initView() {
        mCountTv = (TextView) findViewById(R.id.send_size_tv);
        mNameTv = (TextView) findViewById(R.id.send_name_tv);
        mCountTv.setText(getString(R.string.you_will_send_a_message_to) + userIds.size() + getString(R.string.bit) + getString(R.string.friend));
        mNameTv.setText(userNames);

        mChatBottomView = (ChatBottomForSendGroup) findViewById(R.id.chat_bottom_view);
        mChatBottomView.setChatBottomListener(this);
    }

    private void setSameParams(ChatMessage message) {
        DialogHelper.showDefaulteMessageProgressDialogAddCancel(this, dialog -> dialog.dismiss());

        message.setFromUserId(mLoginUserId);
        message.setFromUserName(mLoginNickName);
        message.setIsReadDel(0);

        PrivacySetting privacySetting = PrivacySettingHelper.getPrivacySettings(this);
        boolean isEncrypt = privacySetting.getIsEncrypt() == 1;
        if (isEncrypt) {
            message.setIsEncrypt(1);
        } else {
            message.setIsEncrypt(0);
        }
        message.setReSendCount(ChatMessageDao.fillReCount(message.getType()));
        sendMessage(message);
    }

    private void sendMessage(ChatMessage message) {
        if (message.getType() == XmppMessage.TYPE_VOICE || message.getType() == XmppMessage.TYPE_IMAGE
                || message.getType() == XmppMessage.TYPE_VIDEO || message.getType() == XmppMessage.TYPE_FILE
                || message.getType() == XmppMessage.TYPE_LOCATION) {
            if (!message.isUpload()) {
                UploadEngine.uploadImFile(coreManager.getSelfStatus().accessToken, coreManager.getSelf().getUserId(), userIds.get(0), message, mUploadResponse);
            } else {
                message.setUpload(true);
                message.setUploadSchedule(100);
                send(message);
            }
        } else {
            send(message);
        }
    }

    private void send(ChatMessage message) {
        new Thread(() -> {
            for (int i = 0; i < userIds.size(); i++) {
                try {
                    Thread.sleep(100);// ????????????????????????100ms???????????????
                    message.setToUserId(userIds.get(i));
                    message.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                    message.setDoubleTimeSend(TimeUtils.sk_time_current_time_double());
                    if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, userIds.get(i), message)) {
                        mService.sendChatMessage(userIds.get(i), message);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void stopVoicePlay() {
        VoicePlayer.instance().stop();
    }

    @Override
    public void sendVoice(String filePath, int timeLen) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        File file = new File(filePath);
        long fileSize = file.length();
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_VOICE);
        message.setContent("");
        message.setFilePath(filePath);
        message.setFileSize((int) fileSize);
        message.setTimeLen(timeLen);
        setSameParams(message);
    }

    @Override
    public void sendText(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_TEXT);
        message.setContent(text);
        setSameParams(message);
    }

    @Override
    public void sendGif(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_GIF);
        message.setContent(text);
        setSameParams(message);
    }

    @Override
    public void sendCollection(String collection) {
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_IMAGE);
        message.setContent(collection);
        message.setUpload(true);// ??????????????????
        setSameParams(message);
    }

    public void sendImage(File file) {
        if (!file.exists()) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_IMAGE);
        message.setContent("");
        String filePath = file.getAbsolutePath();
        message.setFilePath(filePath);
        long fileSize = file.length();
        message.setFileSize((int) fileSize);
        int[] imageParam = FileDataHelper.getImageParamByIntsFile(filePath);
        message.setLocation_x(String.valueOf(imageParam[0]));
        message.setLocation_y(String.valueOf(imageParam[1]));
        setSameParams(message);
    }

    public void sendVideo(File file) {
        if (!file.exists()) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_VIDEO);
        message.setContent("");
        String filePath = file.getAbsolutePath();
        message.setFilePath(filePath);
        long fileSize = file.length();
        message.setFileSize((int) fileSize);
        setSameParams(message);
    }

    public void sendFile(File file) {
        if (!file.exists()) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_FILE);
        message.setContent("");
        String filePath = file.getAbsolutePath();
        message.setFilePath(filePath);
        long fileSize = file.length();
        message.setFileSize((int) fileSize);
        setSameParams(message);
    }

    public void sendLocate(double latitude, double longitude, String address, String snapshot) {
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_LOCATION);
        message.setContent("");
        message.setFilePath(snapshot);
        message.setLocation_x(latitude + "");
        message.setLocation_y(longitude + "");
        message.setObjectId(address);
        setSameParams(message);
    }

    public void sendCard(Friend friend) {
        ChatMessage message = new ChatMessage();
        message.setType(XmppMessage.TYPE_CARD);
        message.setContent(friend.getNickName());
        message.setObjectId(friend.getUserId());
        setSameParams(message);
    }

    @Override
    public void clickPhoto() {
        ArrayList<String> imagePaths = new ArrayList<>();
        PhotoPickerIntent intent = new PhotoPickerIntent(ChatActivityForSendGroup.this);
        intent.setSelectModel(SelectModel.SINGLE);
        intent.setSelectedPaths(imagePaths);
        startActivityForResult(intent, REQUEST_CODE_PICK_PHOTO);
        mChatBottomView.reset();
    }

    @Override
    public void clickCamera() {
        Intent intent = new Intent(this, EasyCameraActivity.class);
        startActivity(intent);
        mChatBottomView.reset();
    }

    @Override
    public void clickVideo() {
        Intent intent = new Intent(mContext, LocalVideoActivity.class);
        intent.putExtra(AppConstant.EXTRA_ACTION, AppConstant.ACTION_SELECT);
        intent.putExtra(AppConstant.EXTRA_MULTI_SELECT, false);
        startActivityForResult(intent, REQUEST_CODE_SELECT_VIDE0);
    }

    @Override
    public void clickFile() {
        SelectFileDialog dialog = new SelectFileDialog(this, new SelectFileDialog.OptionFileListener() {
            @Override
            public void option(List<File> files) {
                if (files != null && files.size() > 0) {
                    for (int i = 0; i < files.size(); i++) {
                        sendFile(files.get(i));
                    }
                }
            }

            @Override
            public void intent() {

            }
        });
        dialog.show();
    }

    @Override
    public void clickLocation() {
        Intent intent = new Intent(mContext, MapPickerActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SELECT_Locate);
    }

    @Override
    public void clickCard() {
        SelectCardPopupWindow mSelectCardPopupWindow = new SelectCardPopupWindow(this, this);
        mSelectCardPopupWindow.showAtLocation(findViewById(R.id.root_view),
                Gravity.CENTER, 0, 0);
    }

    @Override
    public void sendCardS(List<Friend> friends) {
        for (int i = 0; i < friends.size(); i++) {
            sendCard(friends.get(i));
        }
    }

    @Subscribe(threadMode = ThreadMode.MainThread)
    public void helloEventBus(final MessageEventGpu message) {// ????????????
        photograph(new File(message.event));
    }

    @Subscribe(threadMode = ThreadMode.MainThread)
    public void helloEventBus(final MessageEvent message) {
        for (String s : mCloneUserIds) {
            if (message.message.equals(s)) {// ????????????????????????
                mCloneUserIds.remove(s);
                if (mCloneUserIds.size() == 0) {// ????????????????????????????????? ??????????????????
                    Log.e("TAG", "over: " + s);
                    MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                    DialogHelper.dismissProgressDialog();

                    sendBroadcast(new Intent(com.ydd.zhichat.broadcast.OtherBroadcast.SEND_MULTI_NOTIFY));
                    finish();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_PHOTO && resultCode == RESULT_OK) {// ????????????
            if (data != null) {
                boolean isOriginal = data.getBooleanExtra(PhotoPickerActivity.EXTRA_RESULT_ORIGINAL, false);
                album(data.getStringArrayListExtra(PhotoPickerActivity.EXTRA_RESULT), isOriginal);
            } else {
                ToastUtil.showToast(this, R.string.c_photo_album_failed);
            }
        } else if (requestCode == REQUEST_CODE_SELECT_VIDE0 && resultCode == RESULT_OK) {// ??????????????????
            if (data == null) {
                return;
            }
            String json = data.getStringExtra(AppConstant.EXTRA_VIDEO_LIST);
            List<VideoFile> fileList = JSON.parseArray(json, VideoFile.class);
            if (fileList == null || fileList.size() == 0) {
                // ???????????????????????????????????????
                Reporter.unreachable();
            } else {
                for (VideoFile videoFile : fileList) {
                    String filePath = videoFile.getFilePath();
                    if (TextUtils.isEmpty(filePath)) {
                        // ???????????????????????????????????????
                        Reporter.unreachable();
                    } else {
                        File file = new File(filePath);
                        if (!file.exists()) {
                            // ???????????????????????????????????????
                            Reporter.unreachable();
                        } else {
                            sendVideo(file);
                        }
                    }
                }
            }
        } else if (requestCode == REQUEST_CODE_SELECT_Locate && resultCode == RESULT_OK) {// ?????????????????????
            double latitude = data.getDoubleExtra(AppConstant.EXTRA_LATITUDE, 0);
            double longitude = data.getDoubleExtra(AppConstant.EXTRA_LONGITUDE, 0);
            String address = data.getStringExtra(AppConstant.EXTRA_ADDRESS);
            String snapshot = data.getStringExtra(AppConstant.EXTRA_SNAPSHOT);

            if (latitude != 0 && longitude != 0 && !TextUtils.isEmpty(address)
                    && !TextUtils.isEmpty(snapshot)) {
                sendLocate(latitude, longitude, address, snapshot);
            } else {
                ToastUtil.showToast(mContext, InternationalizationHelper.getString("JXLoc_StartLocNotice"));
            }
        }
    }

    // ?????????????????? ??????
    private void photograph(final File file) {
        Log.e("zq", "?????????????????????:" + file.getPath() + "?????????????????????:" + file.length() / 1024 + "KB");
        // ?????????????????????Luban???????????????
        Luban.with(this)
                .load(file)
                .ignoreBy(100)     // ????????????100kb ?????????
                // .putGear(2)     // ?????????????????????????????????
                // .setTargetDir() // ??????????????????????????????
                .setCompressListener(new OnCompressListener() { // ????????????
                    @Override
                    public void onStart() {
                        Log.e("zq", "????????????");
                    }

                    @Override
                    public void onSuccess(File file) {
                        Log.e("zq", "????????????????????????????????????:" + file.getPath() + "?????????????????????:" + file.length() / 1024 + "KB");
                        sendImage(file);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e("zq", "????????????,????????????");
                        sendImage(file);
                    }
                }).launch();// ????????????
    }

    // ?????????????????? ??????
    private void album(ArrayList<String> stringArrayListExtra, boolean isOriginal) {
        if (isOriginal) {// ????????????????????????
            Log.e("zq", "???????????????????????????????????????");
            for (int i = 0; i < stringArrayListExtra.size(); i++) {
                sendImage(new File(stringArrayListExtra.get(i)));
            }
            Log.e("zq", "???????????????????????????????????????");
            return;
        }

        List<File> fileList = new ArrayList<>();
        for (int i = 0; i < stringArrayListExtra.size(); i++) {
            // gif??????????????????
            if (stringArrayListExtra.get(i).endsWith("gif")) {
                fileList.add(new File(stringArrayListExtra.get(i)));
                stringArrayListExtra.remove(i);
            } else {
                // Luban????????????????????????????????????????????????????????????????????????
                // ???????????????????????????
                List<String> lubanSupportFormatList = Arrays.asList("jpg", "jpeg", "png", "webp", "gif");
                boolean support = false;
                for (int j = 0; j < lubanSupportFormatList.size(); j++) {
                    if (stringArrayListExtra.get(i).endsWith(lubanSupportFormatList.get(j))) {
                        support = true;
                        break;
                    }
                }
                if (!support) {
                    fileList.add(new File(stringArrayListExtra.get(i)));
                    stringArrayListExtra.remove(i);
                }
            }
        }

        if (fileList.size() > 0) {
            for (File file : fileList) {// ?????????????????????????????????
                sendImage(file);
            }
        }

        Luban.with(this)
                .load(stringArrayListExtra)
                .ignoreBy(100)// ????????????100kb ?????????
                .setCompressListener(new OnCompressListener() {
                    @Override
                    public void onStart() {

                    }

                    @Override
                    public void onSuccess(File file) {
                        sendImage(file);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                }).launch();// ????????????
    }
}
