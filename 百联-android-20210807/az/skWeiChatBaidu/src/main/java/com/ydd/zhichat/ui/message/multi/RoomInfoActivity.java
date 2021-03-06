package com.ydd.zhichat.ui.message.multi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.ydd.zhichat.AppConstant;
import com.ydd.zhichat.MyApplication;
import com.ydd.zhichat.R;
import com.ydd.zhichat.Reporter;
import com.ydd.zhichat.bean.EventRoomNotice;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.Report;
import com.ydd.zhichat.bean.RoomMember;
import com.ydd.zhichat.bean.message.MucRoom;
import com.ydd.zhichat.bean.message.MucRoom.Notice;
import com.ydd.zhichat.bean.message.MucRoomMember;
import com.ydd.zhichat.broadcast.MsgBroadcast;
import com.ydd.zhichat.broadcast.MucgroupUpdateUtil;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.dao.ChatMessageDao;
import com.ydd.zhichat.db.dao.FriendDao;
import com.ydd.zhichat.db.dao.RoomMemberDao;
import com.ydd.zhichat.helper.AvatarHelper;
import com.ydd.zhichat.helper.DialogHelper;
import com.ydd.zhichat.ui.MainActivity;
import com.ydd.zhichat.ui.base.BaseActivity;
import com.ydd.zhichat.ui.message.search.SearchChatHistoryActivity;
import com.ydd.zhichat.ui.mucfile.MucFileListActivity;
import com.ydd.zhichat.ui.other.BasicInfoActivity;
import com.ydd.zhichat.ui.other.QRcodeActivity;
import com.ydd.zhichat.util.AsyncUtils;
import com.ydd.zhichat.util.CameraUtil;
import com.ydd.zhichat.util.CharUtils;
import com.ydd.zhichat.util.Constants;
import com.ydd.zhichat.util.ExpandView;
import com.ydd.zhichat.util.LogUtils;
import com.ydd.zhichat.util.PreferenceUtils;
import com.ydd.zhichat.util.TimeUtils;
import com.ydd.zhichat.util.ToastUtil;
import com.ydd.zhichat.view.MsgSaveDaysDialog;
import com.ydd.zhichat.view.ReportDialog;
import com.ydd.zhichat.view.SelectionFrame;
import com.ydd.zhichat.view.TipDialog;
import com.ydd.zhichat.view.VerifyDialog;
import com.ydd.zhichat.volley.Result;
import com.ydd.zhichat.xmpp.ListenerManager;
import com.suke.widget.SwitchButton;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.BaseCallback;
import com.xuan.xuanhttplibrary.okhttp.result.ObjectResult;

import org.apache.http.Header;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;
import de.greenrobot.event.ThreadMode;
import in.srain.cube.views.GridViewWithHeaderAndFooter;
import okhttp3.Call;

import static com.ydd.zhichat.broadcast.MsgBroadcast.ACTION_MSG_UPDATE_ROOM_INVITE;
import static com.ydd.zhichat.broadcast.MsgBroadcast.EXTRA_ENABLED;

/**
 * ????????????
 */
public class RoomInfoActivity extends BaseActivity {
    private static final int RESULT_FOR_ADD_MEMBER = 1;
    private static final int RESULT_FOR_MODIFY_NOTICE = 5;
    private static final int REQUEST_CODE_CAPTURE_CROP_PHOTO = 4;
    private static final int REQUEST_CODE_PICK_CROP_PHOTO = 2;
    private static final int REQUEST_CODE_CROP_PHOTO = 3;
    MucRoom mucRoom;
    RefreshBroadcastReceiver receiver = new RefreshBroadcastReceiver();
    private String mRoomJid;
    private Friend mRoom;
    private Context mContext = RoomInfoActivity.this;
    // ???????????????????????????
    private boolean isMucChatComing;
    private String mLoginUserId;
    private GridViewWithHeaderAndFooter mGridView;
    private GridViewAdapter mAdapter;
    private TextView mRoomNameTv;
    private TextView mRoomDescTv;
    private TextView mNoticeTv;
    private TextView mNickNameTv;
    private TextView romNameTv, romDesTv, gongGaoTv, myGroupName, shieldGroupMesTv, jinyanTv;
    private RelativeLayout room_qrcode;
    // ????????????
    private SwitchButton mSbTopChat;
    private SwitchButton mSbDisturb;
    private SwitchButton mSbShield;
    // ????????????
    private SwitchButton mSbAllShutUp;
    private Button mBtnQuitRoom;
    private ImageView mExpandIv;
    private ExpandView mExpandView;
    private TextView mCreatorTv;
    private TextView buileTimetv;
    private TextView mCreateTime;
    private TextView numberTopTv;
    private TextView mCountTv;
    private TextView mCountTv2;
    private TextView mMsgSaveDays;
    MsgSaveDaysDialog.OnMsgSaveDaysDialogClickListener onMsgSaveDaysDialogClickListener = new MsgSaveDaysDialog.OnMsgSaveDaysDialogClickListener() {
        @Override
        public void tv1Click() {
            updateChatRecordTimeOut(-1);
        }

        @Override
        public void tv2Click() {
            updateChatRecordTimeOut(0.04);
            // updateChatRecordTimeOut(0.00347); // ???????????????
        }

        @Override
        public void tv3Click() {
            updateChatRecordTimeOut(1);
        }

        @Override
        public void tv4Click() {
            updateChatRecordTimeOut(7);
        }

        @Override
        public void tv5Click() {
            updateChatRecordTimeOut(30);
        }

        @Override
        public void tv6Click() {
            updateChatRecordTimeOut(90);
        }

        @Override
        public void tv7Click() {
            updateChatRecordTimeOut(365);
        }
    };
    private Uri mNewPhotoUri;
    private File mCurrentFile;
    private TextView tvMemberLimit;
    // ???????????? && ????????????
    SwitchButton.OnCheckedChangeListener onCheckedChangeMessageListener = new SwitchButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(SwitchButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.sb_top_chat:// ????????????
                    updateDisturbState(1, isChecked ? 1 : 0);
                    break;
                case R.id.sb_no_disturb:// ???????????????
                    updateDisturbState(0, isChecked ? 1 : 0);
                    break;
                case R.id.sb_shield_chat:// ???????????????
                    if (isChecked) {
                        if (mRoom.getOfflineNoPushMsg() == 0) {
                            mSbDisturb.setChecked(true);
                        }
                    }
                    PreferenceUtils.putBoolean(mContext, Constants.SHIELD_GROUP_MSG + mRoomJid + mLoginUserId, isChecked);
                    mSbShield.setChecked(isChecked);
                    break;
                case R.id.sb_banned:// ????????????
                    if (isChecked) {
                        updateSingleAttribute("talkTime", String.valueOf(TimeUtils.sk_time_current_time() + 24 * 60 * 60 * 15));
                    } else {
                        updateSingleAttribute("talkTime", String.valueOf(0));
                    }
                    break;
            }
        }
    };
    private int add_minus_count = 2;
    private int role;
    // ?????????????????????????????????????????????
    private String creator;  // ??????id
    private int isNeedVerify;// ????????????????????????
    // ??????????????????????????????
    private LinearLayout llOp;
    private ImageView mOpenMembers;
    // false?????????????????????
    private boolean flag;
    private List<MucRoomMember> mMembers;
    private List<MucRoomMember> mCurrentMembers = new ArrayList<>();
    private MucRoomMember mGroupOwner;// ??????
    private MucRoomMember myself;// ??????
    private Map<String, String> mRemarksMap = new HashMap<>();
    private View header;
    private View footer;
    private int mMemberSize;

    /**
     * ???????????????
     * ????????????????????????????????????????????????????????? MucRoomMember
     */
    public static void saveMucLastRoamingTime(String ownerId, String roomId, long time, boolean reset) {
        if (reset) {
            PreferenceUtils.putLong(MyApplication.getContext(), Constants.MUC_MEMBER_LAST_JOIN_TIME + ownerId + roomId, time);
        } else {
            long lastRoamingTime = PreferenceUtils.getLong(MyApplication.getContext(), Constants.MUC_MEMBER_LAST_JOIN_TIME + ownerId + roomId, 0);
            if (lastRoamingTime < time) {
                PreferenceUtils.putLong(MyApplication.getContext(), Constants.MUC_MEMBER_LAST_JOIN_TIME + ownerId + roomId, time);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_info);
        if (getIntent() != null) {
            mRoomJid = getIntent().getStringExtra(AppConstant.EXTRA_USER_ID);
            isMucChatComing = getIntent().getBooleanExtra(AppConstant.EXTRA_IS_GROUP_CHAT, false);
        }
        if (TextUtils.isEmpty(mRoomJid)) {
            LogUtils.log(getIntent());
            Reporter.post("?????????RoomJid?????????");
            Toast.makeText(this, R.string.tip_group_message_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mLoginUserId = coreManager.getSelf().getUserId();
        mRoom = FriendDao.getInstance().getFriend(mLoginUserId, mRoomJid);
        if (mRoom == null || TextUtils.isEmpty(mRoom.getRoomId())) {
            LogUtils.log(getIntent());
            LogUtils.log("mLoginUserId = " + mLoginUserId);
            LogUtils.log("mRoomJid = " + mRoomJid);
            // ??????toString??????????????????json?????????????????????
            LogUtils.log("mRoom = " + JSON.toJSONString(mRoom));
            Reporter.post("?????????RoomJid?????????Room???");
            Toast.makeText(this, R.string.tip_group_message_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        initActionBar();
        initView();
        registerRefreshReceiver();
        loadMembers();
        initEvent();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception e) {
                // onCreate??????????????????????????????Receiver,
                // ???????????????????????????destroy????????????
                // ???????????????????????????boolean??????????????????????????????
                Reporter.post("??????Receiver?????????", e);
            }
        }
        // ????????????????????????????????????????????????
        EventBus.getDefault().unregister(this);
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView mTitleTv = (TextView) findViewById(R.id.tv_title_center);
        mTitleTv.setText(InternationalizationHelper.getString("JXRoomMemberVC_RoomInfo"));
    }

    private void initView() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Friend> mFriendList = FriendDao.getInstance().getAllFriends(mLoginUserId);
                for (int i = 0; i < mFriendList.size(); i++) {
                    if (!TextUtils.isEmpty(mFriendList.get(i).getRemarkName())) {// ??????????????????????????????
                        mRemarksMap.put(mFriendList.get(i).getUserId(), mFriendList.get(i).getRemarkName());
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mAdapter != null) {
                            mAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }).start();

        mGridView = findViewById(R.id.grid_view);
        // ??????????????????footer??????
        header = getLayoutInflater().inflate(R.layout.activity_room_info_header, null);
        footer = getLayoutInflater().inflate(R.layout.activity_room_info_footer, null);
        mGridView.addHeaderView(header);
        mGridView.addFooterView(footer);
        llOp = (LinearLayout) footer.findViewById(R.id.ll_op);
        mOpenMembers = (ImageView) footer.findViewById(R.id.open_members);
        mRoomNameTv = (TextView) footer.findViewById(R.id.room_name_tv);
        mRoomDescTv = (TextView) footer.findViewById(R.id.room_desc_tv);
        mNoticeTv = (TextView) footer.findViewById(R.id.notice_tv);
        mNickNameTv = (TextView) footer.findViewById(R.id.nick_name_tv);
        room_qrcode = (RelativeLayout) footer.findViewById(R.id.room_qrcode);

        mSbTopChat = (SwitchButton) footer.findViewById(R.id.sb_top_chat);
        mSbDisturb = (SwitchButton) footer.findViewById(R.id.sb_no_disturb);
        mSbShield = (SwitchButton) footer.findViewById(R.id.sb_shield_chat);

        mSbAllShutUp = (SwitchButton) footer.findViewById(R.id.sb_banned);

        gongGaoTv = (TextView) footer.findViewById(R.id.notice_text);
        romNameTv = (TextView) footer.findViewById(R.id.room_name_text);
        romDesTv = (TextView) footer.findViewById(R.id.room_desc_text);
        myGroupName = (TextView) footer.findViewById(R.id.nick_name_text);
        shieldGroupMesTv = (TextView) footer.findViewById(R.id.shield_chat_text_title);
        jinyanTv = (TextView) footer.findViewById(R.id.banned_voice_text);
        gongGaoTv.setText(InternationalizationHelper.getString("JXRoomMemberVC_RoomAdv"));
        romNameTv.setText(InternationalizationHelper.getString("JX_RoomName"));
        /*romDesTv.setText(InternationalizationHelper.getString("JX_RoomExplain"));*/
        myGroupName.setText(InternationalizationHelper.getString("JXRoomMemberVC_NickName"));
        shieldGroupMesTv.setText(InternationalizationHelper.getString("JXRoomMemberVC_NotMessage"));
        jinyanTv.setText(InternationalizationHelper.getString("GAG"));
      /*  TextView qrCode = (TextView) footer.findViewById(R.id.qr_code_tv);
        qrCode.setText(InternationalizationHelper.getString("JXQR_QRImage"));*/
        TextView mGroupFile = (TextView) footer.findViewById(R.id.tv_file_name);
        mGroupFile.setText(InternationalizationHelper.getString("JXRoomMemberVC_ShareFile"));
       /* TextView isGroupReadTv = (TextView) footer.findViewById(R.id.iskaiqiqun);
        isGroupReadTv.setText(InternationalizationHelper.getString("JX_RoomShowRead"));*/

        mBtnQuitRoom = (Button) footer.findViewById(R.id.room_info_quit_btn);
//        mBtnQuitRoom.setBackground(new ColorDrawable(MyApplication.getContext().getResources().getColor(R.color.redpacket_bg)));
        mBtnQuitRoom.setText(InternationalizationHelper.getString("JXRoomMemberVC_OutPutRoom"));

        // ExpandView And His Sons
        mExpandIv = (ImageView) footer.findViewById(R.id.room_info_iv);
        mExpandView = (ExpandView) footer.findViewById(R.id.expandView);
        mExpandView.setContentView(R.layout.layout_expand);
        mCreatorTv = (TextView) footer.findViewById(R.id.creator_tv);
        buileTimetv = (TextView) footer.findViewById(R.id.create_time_text);
        buileTimetv.setText(InternationalizationHelper.getString("JXRoomMemberVC_CreatTime"));
        mCreateTime = (TextView) footer.findViewById(R.id.create_timer);
        numberTopTv = (TextView) footer.findViewById(R.id.count_text);
        numberTopTv.setText(InternationalizationHelper.getString("MEMBER_CAP"));
        mCountTv = (TextView) footer.findViewById(R.id.count_tv);
        mCountTv2 = (TextView) header.findViewById(R.id.member_count_tv);
        mMsgSaveDays = (TextView) footer.findViewById(R.id.msg_save_days_tv);

        // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????ui
        mRoomNameTv.setText(mRoom.getNickName());
        mRoomDescTv.setText(mRoom.getDescription());

        mNickNameTv.setText(mRoom.getRoomMyNickName() != null
                ? mRoom.getRoomMyNickName() : coreManager.getSelf().getNickName());

        mSbDisturb.setChecked(mRoom.getOfflineNoPushMsg() == 1);// ???????????????
        mMsgSaveDays.setText(conversion(mRoom.getChatRecordTimeOut()));// ??????????????????

        boolean isAllShutUp = PreferenceUtils.getBoolean(mContext, Constants.GROUP_ALL_SHUP_UP + mRoom.getUserId(), false);
        mSbAllShutUp.setChecked(isAllShutUp);

        tvMemberLimit = footer.findViewById(R.id.member_limit_tv);
    }

    /**
     * ??????????????????????????????
     */
    private void initEvent() {
        footer.findViewById(R.id.room_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpandView.isExpand()) {
                    mExpandView.collapse();
                    mExpandIv.setBackgroundResource(R.drawable.open_member);
                } else {
                    mExpandView.expand();
                    mExpandIv.setBackgroundResource(R.drawable.close_member);
                }
            }
        });

        // ?????????
        room_qrcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RoomInfoActivity.this, QRcodeActivity.class);
                intent.putExtra("isgroup", true);
                intent.putExtra("userid", mRoom.getRoomId());
                intent.putExtra("roomJid", mRoom.getUserId());
                intent.putExtra("roomName", mRoom.getNickName());
                startActivity(intent);
            }
        });

        // ??????
        footer.findViewById(R.id.notice_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mucRoom != null) {
                    List<String> mNoticeIdList = new ArrayList<>();
                    List<String> mNoticeUserIdList = new ArrayList<>();
                    List<String> mNoticeNickNameIdList = new ArrayList<>();
                    List<Long> mNoticeTimeList = new ArrayList<>();
                    List<String> mNoticeTextList = new ArrayList<>();
                    for (Notice notice : mucRoom.getNotices()) {
                        mNoticeIdList.add(notice.getId());
                        mNoticeUserIdList.add(notice.getUserId());
                        mNoticeNickNameIdList.add(notice.getNickname());
                        mNoticeTimeList.add(notice.getTime());
                        mNoticeTextList.add(notice.getText());
                    }
                    Intent intent = new Intent(RoomInfoActivity.this, NoticeListActivity.class);
                    intent.putExtra("mNoticeIdList", JSON.toJSONString(mNoticeIdList));
                    intent.putExtra("mNoticeUserIdList", JSON.toJSONString(mNoticeUserIdList));
                    intent.putExtra("mNoticeNickNameIdList", JSON.toJSONString(mNoticeNickNameIdList));
                    intent.putExtra("mNoticeTimeList", JSON.toJSONString(mNoticeTimeList));
                    intent.putExtra("mNoticeTextList", JSON.toJSONString(mNoticeTextList));
                    intent.putExtra("mRole", myself.getRole());
                    intent.putExtra("mRoomId", mRoom.getRoomId());

                    startActivityForResult(intent, RESULT_FOR_MODIFY_NOTICE);
                }
            }
        });

        // ??????????????????
        footer.findViewById(R.id.nick_name_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangeNickNameDialog(mNickNameTv.getText().toString().trim());
            }
        });
        // ???????????????
        footer.findViewById(R.id.picture_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePictureDialog();
            }
        });

        // ???????????????
        footer.findViewById(R.id.file_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myself != null && mucRoom != null) {
                    Intent intent = new Intent(RoomInfoActivity.this, MucFileListActivity.class);
                    intent.putExtra("roomId", mRoom.getRoomId());
                    intent.putExtra("role", myself.getRole());
                    intent.putExtra("allowUploadFile", mucRoom.getAllowUploadFile());
                    startActivity(intent);
                }
            }
        });

        // ??????????????????
        footer.findViewById(R.id.chat_history_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RoomInfoActivity.this, SearchChatHistoryActivity.class);
                intent.putExtra("isSearchSingle", false);
                intent.putExtra(AppConstant.EXTRA_USER_ID, mRoomJid);
                startActivity(intent);
            }
        });

        mSbTopChat.setOnCheckedChangeListener(onCheckedChangeMessageListener);
        mSbDisturb.setOnCheckedChangeListener(onCheckedChangeMessageListener);
        mSbShield.setOnCheckedChangeListener(onCheckedChangeMessageListener);

        // ??????????????????
        footer.findViewById(R.id.chat_history_empty).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectionFrame selectionFrame = new SelectionFrame(mContext);
                selectionFrame.setSomething(null, getString(R.string.tip_confirm_clean_history), new SelectionFrame.OnSelectionFrameClickListener() {
                    @Override
                    public void cancelClick() {

                    }

                    @Override
                    public void confirmClick() {
                        // ??????????????????
                        FriendDao.getInstance().resetFriendMessage(mLoginUserId, mRoomJid);
                        ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, mRoomJid);
                        sendBroadcast(new Intent(Constants.CHAT_HISTORY_EMPTY));// ??????????????????
                        MsgBroadcast.broadcastMsgUiUpdate(RoomInfoActivity.this);
                        Toast.makeText(RoomInfoActivity.this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show();
                    }
                });
                selectionFrame.show();
            }
        });

        footer.findViewById(R.id.report_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReportDialog mReportDialog = new ReportDialog(RoomInfoActivity.this, true, new ReportDialog.OnReportListItemClickListener() {
                    @Override
                    public void onReportItemClick(Report report) {
                        report(mRoom.getRoomId(), report);
                    }
                });
                mReportDialog.show();
            }
        });

        // ????????????
        mBtnQuitRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mucRoom == null) {
                    return;
                }

                String desc;
                String url;
                Map<String, String> params = new HashMap<>();
                params.put("access_token", coreManager.getSelfStatus().accessToken);
                params.put("roomId", mRoom.getRoomId());
                if (mucRoom.getUserId().equals(mLoginUserId)) {// ????????????
                    desc = getString(R.string.tip_disband);
                    url = coreManager.getConfig().ROOM_DELETE;
                } else {// ????????????
                    params.put("userId", mLoginUserId);
                    desc = getString(R.string.tip_exit);
                    url = coreManager.getConfig().ROOM_MEMBER_DELETE;
                }
                quitRoom(desc, url, params);
            }
        });

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (add_minus_count == 1) {
                    // ???????????????????????????????????????,+ -???????????????????????????????????????????????????????????????add_minus_count????????????????????????
                    if (position == mCurrentMembers.size() - 2) {
                        if (myself.disallowInvite()) {
                            tip(getString(R.string.tip_disallow_invite_role_place_holder, getString(myself.getRoleName())));
                        } else if (mucRoom.getAllowInviteFriend() == 1 || myself.getRole() == 1 || myself.getRole() == 2) {
                            List<String> existIds = new ArrayList<>();
                            for (int i = 0; i < mMembers.size() - 2; i++) {
                                existIds.add(mMembers.get(i).getUserId());
                            }
                            // ??????
                            Intent intent = new Intent(RoomInfoActivity.this, AddContactsActivity.class);
                            intent.putExtra("roomId", mRoom.getRoomId());
                            intent.putExtra("roomJid", mRoomJid);
                            intent.putExtra("roomName", mRoomNameTv.getText().toString());
                            intent.putExtra("roomDes", mRoomDescTv.getText().toString());
                            intent.putExtra("exist_ids", JSON.toJSONString(existIds));
                            intent.putExtra("roomCreator", creator);
                            startActivityForResult(intent, RESULT_FOR_ADD_MEMBER);
                        } else {
                            tip(getString(R.string.tip_disable_invite));
                        }
                    } else if (position == mCurrentMembers.size() - 1) {
                        // ????????????????????????????????????
                        Toast.makeText(RoomInfoActivity.this, InternationalizationHelper.getString("JXRoomMemberVC_NotAdminCannotDoThis"), Toast.LENGTH_SHORT).show();
                    } else {
                        boolean isAllowSecretlyChat = PreferenceUtils.getBoolean(mContext, Constants.IS_SEND_CARD + mRoom.getUserId(), true);
                        if (isAllowSecretlyChat) {
                            MucRoomMember member = mCurrentMembers.get(position);
                            if (member != null) {
                                startBasicInfo(member.getUserId());
                            }
                        } else {
                            tip(getString(R.string.tip_member_disable_privately_chat));
                        }
                    }
                } else if (add_minus_count == 2) {// ??????????????????
                    if (position == mCurrentMembers.size() - 2) {
                        List<String> existIds = new ArrayList<>();
                        for (int i = 0; i < mMembers.size() - 2; i++) {
                            existIds.add(mMembers.get(i).getUserId());
                        }
                        // ??????
                        Intent intent = new Intent(RoomInfoActivity.this, AddContactsActivity.class);
                        intent.putExtra("roomId", mRoom.getRoomId());
                        intent.putExtra("roomJid", mRoomJid);
                        intent.putExtra("roomName", mRoomNameTv.getText().toString());
                        intent.putExtra("roomDes", mRoomDescTv.getText().toString());
                        intent.putExtra("exist_ids", JSON.toJSONString(existIds));
                        intent.putExtra("roomCreator", creator);
                        startActivityForResult(intent, RESULT_FOR_ADD_MEMBER);
                    } else if (position == mCurrentMembers.size() - 1) {
                        Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                        intent.putExtra("roomId", mucRoom.getId());
                        intent.putExtra("isDelete", true);
                        startActivity(intent);
                    } else {
                        MucRoomMember member = mCurrentMembers.get(position);
                        if (member != null) {
                            startBasicInfo(member.getUserId());
                        }
                    }
                }
            }
        });
    }

    private void startBasicInfo(String userId) {
        BasicInfoActivity.start(mContext, userId, BasicInfoActivity.FROM_ADD_TYPE_GROUP);
    }

    private void loadMembers() {
        HashMap<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("pageSize", Constants.MUC_MEMBER_PAGE_SIZE);

        HttpUtils.get().url(coreManager.getConfig().ROOM_GET)
                .params(params)
                .build()
                .execute(new BaseCallback<MucRoom>(MucRoom.class) {

                             @Override
                             public void onResponse(ObjectResult<MucRoom> result) {
                                 if (result.getResultCode() == 1 && result.getData() != null) {
                                     mucRoom = result.getData();
                                     tvMemberLimit.setText(String.valueOf(mucRoom.getMaxUserSize()));

                                     MyApplication.getInstance().saveGroupPartStatus(mucRoom.getJid(), mucRoom.getShowRead(), mucRoom.getAllowSendCard(),
                                             mucRoom.getAllowConference(), mucRoom.getAllowSpeakCourse(), mucRoom.getTalkTime());
                                     FriendDao.getInstance().updateRoomCreateUserId(mLoginUserId, mRoom.getUserId(), mucRoom.getUserId());
                                     PreferenceUtils.putBoolean(MyApplication.getContext(),
                                             Constants.IS_NEED_OWNER_ALLOW_NORMAL_INVITE_FRIEND + mucRoom.getJid(), mucRoom.getIsNeedVerify() == 1);
                                     PreferenceUtils.putBoolean(MyApplication.getContext(),
                                             Constants.IS_ALLOW_NORMAL_SEND_UPLOAD + mucRoom.getJid(), mucRoom.getAllowUploadFile() == 1);

                                     AsyncUtils.doAsync(this, (AsyncUtils.Function<AsyncUtils.AsyncContext<BaseCallback<MucRoom>>>) baseCallbackAsyncContext -> {
                                         for (int i = 0; i < mucRoom.getMembers().size(); i++) {// ????????????????????????
                                             RoomMember roomMember = new RoomMember();
                                             roomMember.setRoomId(mucRoom.getId());
                                             roomMember.setUserId(mucRoom.getMembers().get(i).getUserId());
                                             roomMember.setUserName(mucRoom.getMembers().get(i).getNickName());
                                             if (TextUtils.isEmpty(mucRoom.getMembers().get(i).getRemarkName())) {
                                                 roomMember.setCardName(mucRoom.getMembers().get(i).getNickName());
                                             } else {
                                                 roomMember.setCardName(mucRoom.getMembers().get(i).getRemarkName());
                                             }
                                             roomMember.setRole(mucRoom.getMembers().get(i).getRole());
                                             roomMember.setCreateTime(mucRoom.getMembers().get(i).getCreateTime());
                                             RoomMemberDao.getInstance().saveSingleRoomMember(mucRoom.getId(), roomMember);
                                         }
                                     });

                                     saveMucLastRoamingTime(mLoginUserId, mucRoom.getId(), mucRoom.getMembers().get(mucRoom.getMembers().size() - 1).getCreateTime(), false);

                                     // ??????????????????
                                     MsgBroadcast.broadcastMsgUiUpdate(RoomInfoActivity.this);
                                     // ??????????????????
                                     MucgroupUpdateUtil.broadcastUpdateUi(RoomInfoActivity.this);
                                     // ??????ui
                                     updateUI(result.getData());
                                 } else {
                                     ToastUtil.showErrorData(RoomInfoActivity.this);
                                 }
                             }

                             @Override
                             public void onError(Call call, Exception e) {
                                 ToastUtil.showErrorNet(RoomInfoActivity.this);
                             }
                         }
                );
    }

    private void updateUI(final MucRoom mucRoom) {
        mMemberSize = mucRoom.getUserSize();
        mMembers = mucRoom.getMembers();

        creator = mucRoom.getUserId();
        isNeedVerify = mucRoom.getIsNeedVerify();

        if (mMembers != null) {
            for (int i = 0; i < mMembers.size(); i++) {
                String userId = mMembers.get(i).getUserId();
                if (mucRoom.getUserId().equals(userId)) {
                    mGroupOwner = mMembers.get(i);
                }
            }

            // ????????????????????????????????????
            if (mGroupOwner != null) {
                mMembers.remove(mGroupOwner);
                mMembers.add(0, mGroupOwner);
            }
        }
        myself = mucRoom.getMember();

        if (myself == null) {
            ToastUtil.showToast(mContext, R.string.tip_kick_room);
            finish();
            return;
        }

        mAdapter = new GridViewAdapter();
        mGridView.setAdapter(mAdapter);

        mRoomNameTv.setText(mucRoom.getName());
        mRoomDescTv.setText(mucRoom.getDesc());

        mCreatorTv.setText(mucRoom.getNickName());
        mCreateTime.setText(TimeUtils.s_long_2_str(mucRoom.getCreateTime() * 1000));
        mCountTv.setText(mucRoom.getUserSize() + "/" + mucRoom.getMaxUserSize());
        mCountTv2.setText(getString(R.string.total_count_place_holder, mucRoom.getUserSize()));

        List<Notice> notices = mucRoom.getNotices();
        if (notices != null && !notices.isEmpty()) {
            String text = getLastNoticeText(notices);
            mNoticeTv.setText(text);
            EventBus.getDefault().post(new EventRoomNotice(text));
        } else {
            mNoticeTv.setText(InternationalizationHelper.getString("JX_NotAch"));
        }
        String mGroupName = coreManager.getSelf().getNickName();
        if (mRoom != null) {
            mGroupName = mRoom.getRoomMyNickName() != null ?
                    mRoom.getRoomMyNickName() : mGroupName;
        }
        mNickNameTv.setText(mGroupName);

        // ????????????????????????????????????
        mRoom.setOfflineNoPushMsg(myself.getOfflineNoPushMsg());
        FriendDao.getInstance().updateOfflineNoPushMsgStatus(mRoom.getUserId(), myself.getOfflineNoPushMsg());
        mRoom.setTopTime(myself.getOpenTopChatTime());
        if (myself.getOpenTopChatTime() > 0) {
            FriendDao.getInstance().updateTopFriend(mRoom.getUserId(), myself.getOpenTopChatTime());
        } else {
            FriendDao.getInstance().resetTopFriend(mRoom.getUserId());
        }

        // ????????????????????????
        updateMessageStatus();

        // ????????????????????????
        mMsgSaveDays.setText(conversion(mucRoom.getChatRecordTimeOut()));
        FriendDao.getInstance().updateChatRecordTimeOut(mRoom.getUserId(), mucRoom.getChatRecordTimeOut());

        // ??????????????????????????????UI??????
        role = myself.getRole();
        /*for (int i = 0; i < mMembers.size(); i++) {
            if (mMembers.get(i).getUserId().equals(mLoginUserId)) {
                role = mMembers.get(i).getRole();
            }
        }*/

        if (role == 1) {// ?????????????????????????????????
            mBtnQuitRoom.setText(InternationalizationHelper.getString("DISSOLUTION_GROUP"));
            footer.findViewById(R.id.room_name_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomNameDialog(mRoomNameTv.getText().toString().trim());
                }
            });
            // ???????????????
            footer.findViewById(R.id.picture_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangePictureDialog();
                }
            });
            footer.findViewById(R.id.room_desc_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomDesDialog(mRoomDescTv.getText().toString().trim());
                }
            });

            footer.findViewById(R.id.msg_save_days_rl).setVisibility(View.VISIBLE);
            footer.findViewById(R.id.msg_save_days_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MsgSaveDaysDialog msgSaveDaysDialog = new MsgSaveDaysDialog(RoomInfoActivity.this, onMsgSaveDaysDialogClickListener);
                    msgSaveDaysDialog.show();
                }
            });

            footer.findViewById(R.id.banned_voice_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                    intent.putExtra("roomId", mucRoom.getId());
                    intent.putExtra("isBanned", true);
                    startActivity(intent);
                }
            });

            footer.findViewById(R.id.rl_manager).setVisibility(View.VISIBLE);
            footer.findViewById(R.id.rl_manager).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int status_lists[] = {mucRoom.getShowRead(), mucRoom.getIsLook(), mucRoom.getIsNeedVerify(),
                            mucRoom.getShowMember(), mucRoom.getAllowSendCard(),
                            mucRoom.getAllowInviteFriend(), mucRoom.getAllowUploadFile(),
                            mucRoom.getAllowConference(), mucRoom.getAllowSpeakCourse(),
                            mucRoom.getIsAttritionNotice()};
                    Intent intent = new Intent(mContext, GroupManager.class);
                    intent.putExtra("roomId", mucRoom.getId());
                    intent.putExtra("roomJid", mucRoom.getJid());
                    intent.putExtra("roomRole", myself.getRole());
                    intent.putExtra("GROUP_STATUS_LIST", status_lists);
                    startActivity(intent);
                }
            });

            mSbAllShutUp.setOnCheckedChangeListener(onCheckedChangeMessageListener);

            enableGroupMore(mucRoom);

            updateMemberLimit(true);
        } else if (role == 2) {// ??????????????????????????????
            mBtnQuitRoom.setText(InternationalizationHelper.getString("JXRoomMemberVC_OutPutRoom"));
            footer.findViewById(R.id.room_name_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomNameDialog(mRoomNameTv.getText().toString().trim());
                }
            });
            // ???????????????
            footer.findViewById(R.id.picture_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangePictureDialog();
                }
            });
            footer.findViewById(R.id.room_desc_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomDesDialog(mRoomDescTv.getText().toString().trim());
                }
            });

            footer.findViewById(R.id.banned_voice_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                    intent.putExtra("roomId", mucRoom.getId());
                    intent.putExtra("isBanned", true);
                    startActivity(intent);
                }
            });

            mSbAllShutUp.setOnCheckedChangeListener(onCheckedChangeMessageListener);

            enableGroupMore(mucRoom);

            updateMemberLimit(true);
        } else {
            add_minus_count = 1;
            mBtnQuitRoom.setText(InternationalizationHelper.getString("JXRoomMemberVC_OutPutRoom"));
            footer.findViewById(R.id.room_name_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tip(getString(R.string.tip_cannot_change_name));
                }
            });
            // ???????????????
            footer.findViewById(R.id.picture_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tip(getString(R.string.tip_cannot_change_avatar));
                }
            });
            footer.findViewById(R.id.room_desc_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tip(getString(R.string.tip_cannot_change_description));
                }
            });

            // ?????? ?????? ??? ????????????
            footer.findViewById(R.id.banned_voice_rl).setVisibility(View.GONE);
            footer.findViewById(R.id.banned_all_voice_rl).setVisibility(View.GONE);

            footer.findViewById(R.id.msg_save_days_rl).setVisibility(View.GONE);
            footer.findViewById(R.id.rl_manager).setVisibility(View.GONE);

            boolean isAllowSecretlyChat = PreferenceUtils.getBoolean(mContext, Constants.IS_SEND_CARD + mRoom.getUserId(), true);
            if (isAllowSecretlyChat) {
                enableGroupMore(mucRoom);
            }
            updateMemberLimit(false);
        }

        // ??????????????????????????????????????????+ -??????????????????
        mMembers.add(null);// ??????+???
        mMembers.add(null);// ??????-???

        mCurrentMembers.clear();
        if (mucRoom.getShowMember() == 0 && role != 1 && role != 2) {// ??????????????? ??????????????????????????? (????????????????????????) ??????????????????????????????+ -
            header.findViewById(R.id.ll_all_member).setVisibility(View.GONE);
            llOp.setVisibility(View.GONE);
            mCurrentMembers.add(mGroupOwner);
            mCurrentMembers.add(myself);
            mCurrentMembers.add(null);// +
            mCurrentMembers.add(null);// _
        } else {// ????????????
            header.findViewById(R.id.ll_all_member).setVisibility(View.VISIBLE);
            // ??????+-???????????????
            if (mMembers.size() - 2 > getDefaultCount()) {
                // ????????????
                llOp.setVisibility(View.VISIBLE);
                // ???????????????
                // ????????????????????????????????????????????????????????????flag????????????
                flag = false;
                mOpenMembers.setImageResource(R.drawable.open_member);
                minimalMembers();
            } else {
                // ???????????????????????????
                llOp.setVisibility(View.GONE);
                mCurrentMembers.addAll(mMembers);
            }

            llOp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    op();
                }
            });
        }
    }

    /**
     * @param isGroupManager ????????????????????????
     */
    private void updateMemberLimit(boolean isGroupManager) {
        View rlMemberLimit = footer.findViewById(R.id.member_limit_rl);
        if (isGroupManager && coreManager.getSelf().isSuperManager()) {
            rlMemberLimit.setVisibility(View.VISIBLE);
            rlMemberLimit.setOnClickListener(v -> {
                DialogHelper.input(this, "?????????????????????", "???????????????", new VerifyDialog.VerifyClickListener() {
                    @Override
                    public void cancel() {

                    }

                    @Override
                    public void send(String str) {
                        if (TextUtils.isDigitsOnly(str)) {
                            updateSingleAttribute("maxUserSize", str);
                        } else {
                            Reporter.unreachable();
                            ToastUtil.showToast(RoomInfoActivity.this, "?????????????????????");
                        }
                    }
                });
            });
        } else {
            rlMemberLimit.setVisibility(View.GONE);
        }
    }

    /**
     * ????????????????????????????????????????????????????????????
     */
    private void enableGroupMore(MucRoom mucRoom) {
        header.findViewById(R.id.ll_all_member).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                intent.putExtra("roomId", mucRoom.getId());
                intent.putExtra("isLoadByService", true);
                startActivity(intent);
            }
        });
    }

    private void minimalMembers() {
        int count = getDefaultCount();
        for (int i = 0; i < count; i++) {
            mCurrentMembers.add(mMembers.get(i));
        }
        mCurrentMembers.add(null);
        mCurrentMembers.add(null);
    }

    private int getDefaultCount() {
        return mGridView.getNumColumns() * 3 - 2;
    }

    /**
     * ???????????????????????????
     * mMembers.size > getDefaultCount() + 2
     * mMembers.size?????????+-???????????????
     */
    public void op() {
        Log.e("RoomInfoActivity", System.currentTimeMillis() + "start");
        flag = !flag;
        mCurrentMembers.clear();
        if (flag) {
            // ??????
            mCurrentMembers.addAll(mMembers);
            mAdapter.notifyDataSetChanged();
            mOpenMembers.setImageResource(R.drawable.close_member);
        } else {
            // ??????
            minimalMembers();
            mAdapter.notifyDataSetChanged();
            scrollToTop();
            mOpenMembers.setImageResource(R.drawable.open_member);
        }
        Log.e("RoomInfoActivity", System.currentTimeMillis() + "end");
    }

    public void tip(String tip) {

        ToastUtil.showToast(RoomInfoActivity.this, tip);
    }

    private String getLastNoticeText(List<Notice> notices) {
        Notice notice = new Notice();
        notice.setTime(0);
        for (Notice no : notices) {
            if (no.getTime() > notice.getTime())
                notice = no;
        }
        return notice.getText();
    }

    // ??????????????????
    private void showChangeRoomNameDialog(final String roomName) {
        DialogHelper.showLimitSingleInputDialog(this, InternationalizationHelper.getString("JXRoomMemberVC_UpdateRoomName"), roomName,
                2, 2, 20, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String text = ((EditText) v).getText().toString().trim();
                        if (TextUtils.isEmpty(text) || text.equals(roomName)) {
                            return;
                        }
                        int length = 0;
                        for (int i = 0; i < text.length(); i++) {
                            String substring = text.substring(i, i + 1);
                            boolean flag = CharUtils.isChinese(substring);
                            if (flag) {
                                // ?????????????????????
                                length += 2;
                            } else {
                                length += 1;
                            }
                        }
                        if (length > 20) {
                            ToastUtil.showToast(mContext, getString(R.string.tip_name_too_long));
                            return;
                        }
                        updateRoom(text, null);
                    }
                });
    }

    // ??????????????????
    private void showChangeRoomDesDialog(final String roomDes) {
        DialogHelper.showLimitSingleInputDialog(this, InternationalizationHelper.getString("JXRoomMemberVC_UpdateExplain"), roomDes,
                7, 2, 100, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String text = ((EditText) v).getText().toString().trim();
                        if (TextUtils.isEmpty(text) || text.equals(roomDes)) {
                            return;
                        }
                        int length = 0;
                        for (int i = 0; i < text.length(); i++) {
                            String substring = text.substring(i, i + 1);
                            boolean flag = CharUtils.isChinese(substring);
                            if (flag) {
                                length += 2;
                            } else {
                                length += 1;
                            }
                        }
                        if (length > 100) {
                            ToastUtil.showToast(mContext, getString(R.string.tip_description_too_long));
                            return;
                        }
                        updateRoom(null, text);
                    }
                });
    }

    // ???????????????
    private void showChangePictureDialog() {
        String[] items = new String[]{InternationalizationHelper.getString("PHOTOGRAPH"), InternationalizationHelper.getString("ALBUM")};
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(InternationalizationHelper.getString("SELECT_AVATARS"))
                .setSingleChoiceItems(items, 0,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    takePhoto();
                                } else {
                                    selectPhoto();
                                }
                                dialog.dismiss();
                            }
                        });
        builder.show();
    }

    private void takePhoto() {
        mNewPhotoUri = CameraUtil.getOutputMediaFileUri(this, CameraUtil.MEDIA_TYPE_IMAGE);
        CameraUtil.captureImage(this, mNewPhotoUri, REQUEST_CODE_CAPTURE_CROP_PHOTO);
    }

    private void selectPhoto() {
        CameraUtil.pickImageSimple(this, REQUEST_CODE_PICK_CROP_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_FOR_ADD_MEMBER && resultCode == RESULT_OK) {// ??????????????????
            loadMembers();
        } else if (requestCode == RESULT_FOR_MODIFY_NOTICE && resultCode == RESULT_OK) {// ??????????????????
            if (data != null) {
                boolean isNeedUpdate = data.getBooleanExtra("isNeedUpdate", false);
                if (isNeedUpdate) {
                    loadMembers();
                }
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE_CROP_PHOTO) {// ????????????????????????
            if (resultCode == Activity.RESULT_OK) {
                if (mNewPhotoUri != null) {
                    Uri o = mNewPhotoUri;
                    mNewPhotoUri = CameraUtil.getOutputMediaFileUri(this, CameraUtil.MEDIA_TYPE_IMAGE);
                    mCurrentFile = new File(mNewPhotoUri.getPath());
                    CameraUtil.cropImage(this, o, mNewPhotoUri, REQUEST_CODE_CROP_PHOTO, 1, 1, 300, 300);
                } else {
                    ToastUtil.showToast(this, R.string.c_photo_album_failed);
                }
            }
        } else if (requestCode == REQUEST_CODE_PICK_CROP_PHOTO) {// ??????????????????,????????????????????????
            Log.e("zx", "onActivityResult: ??????????????????");
            if (resultCode == Activity.RESULT_OK) {
                Log.e("zx", "onActivityResult: RESULT_OK ??????????????????");

                if (data != null && data.getData() != null) {
                    Uri o = data.getData();
                    mNewPhotoUri = CameraUtil.getOutputMediaFileUri(this, CameraUtil.MEDIA_TYPE_IMAGE);
                    mCurrentFile = new File(mNewPhotoUri.getPath());
                    CameraUtil.cropImage(this, o, mNewPhotoUri, REQUEST_CODE_CROP_PHOTO, 1, 1, 300, 300);
                } else {
                    ToastUtil.showToast(this, R.string.c_photo_album_failed);
                }
            }
        } else if (requestCode == REQUEST_CODE_CROP_PHOTO) {
            if (resultCode == Activity.RESULT_OK) {
                if (mNewPhotoUri != null) {
                    mCurrentFile = new File(mNewPhotoUri.getPath());
                    // ????????????
                    uploadAvatar(mCurrentFile);
                } else {
                    ToastUtil.showToast(this, R.string.c_crop_failed);
                }
            }
        }
    }

    private void uploadAvatar(File file) {
        if (!file.exists()) {
            // ???????????????
            return;
        }
        // ?????????????????????ProgressDialog
        DialogHelper.showDefaulteMessageProgressDialog(this);
        RequestParams params = new RequestParams();
        params.put("jid", mRoomJid);
        try {
            params.put("file", file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        AsyncHttpClient client = new AsyncHttpClient();
        client.post(coreManager.getConfig().ROOM_UPDATE_PICTURE, params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
                DialogHelper.dismissProgressDialog();
                boolean success = false;
                if (arg0 == 200) {
                    Result result = null;
                    try {
                        result = JSON.parseObject(new String(arg2), Result.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (result != null && result.getResultCode() == Result.CODE_SUCCESS) {
                        success = true;
                    }
                }

                if (success) {
                    ToastUtil.showToast(mContext, R.string.upload_avatar_success);
                    AvatarHelper.getInstance().updateAvatar(mRoomJid);// ????????????
                } else {
                    ToastUtil.showToast(mContext, R.string.upload_avatar_failed);
                }
            }

            @Override
            public void onFailure(int arg0, Header[] arg1, byte[] arg2, Throwable arg3) {
                DialogHelper.dismissProgressDialog();
                ToastUtil.showToast(mContext, R.string.upload_avatar_failed);
            }
        });
    }

    // ????????????
    private void showChangeNickNameDialog(final String nickName) {
        DialogHelper.showLimitSingleInputDialog(this, InternationalizationHelper.getString("JXRoomMemberVC_UpdateNickName"), nickName, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String text = ((EditText) v).getText().toString().trim();
                if (TextUtils.isEmpty(text) || text.equals(nickName)) {
                    return;
                }
                updateNickName(text);
            }
        });
    }

    // ?????????????????? ?????? ?????????????????????
    private void updateMessageStatus() {
        mSbTopChat.setChecked(mRoom.getTopTime() != 0);
        mSbDisturb.setChecked(mRoom.getOfflineNoPushMsg() == 1);
        boolean mShieldStatus = PreferenceUtils.getBoolean(mContext, Constants.SHIELD_GROUP_MSG + mRoomJid + mLoginUserId, false);
        mSbShield.setChecked(mShieldStatus);
    }

    private String conversion(double outTime) {
        String outTimeStr;
        if (outTime == -1 || outTime == 0) {
            outTimeStr = getString(R.string.permanent);
        } else if (outTime == 0.04) {
            outTimeStr = getString(R.string.one_hour);
        } else if (outTime == 1) {
            outTimeStr = getString(R.string.one_day);
        } else if (outTime == 7) {
            outTimeStr = getString(R.string.one_week);
        } else if (outTime == 30) {
            outTimeStr = getString(R.string.one_month);
        } else if (outTime == 90) {
            outTimeStr = getString(R.string.one_season);
        } else {
            outTimeStr = getString(R.string.one_year);
        }
        return outTimeStr;
    }

    /**
     * ScrollView??????????????????
     */
    private void scrollToTop() {
        mGridView.post(() -> {
            mGridView.smoothScrollToPosition(0);
        });
    }

    @Subscribe(threadMode = ThreadMode.MainThread)
    public void helloEventBus(EventGroupStatus eventGroupStatus) {
        if (eventGroupStatus.getWhichStatus() == 0) {
            mucRoom.setShowRead(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 1) {
            mucRoom.setIsLook(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 2) {
            mucRoom.setIsNeedVerify(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 3) {
            mucRoom.setShowMember(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 4) {
            mucRoom.setAllowSendCard(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 5) {
            mucRoom.setAllowInviteFriend(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 6) {
            mucRoom.setAllowUploadFile(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 7) {
            mucRoom.setAllowConference(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 8) {
            mucRoom.setAllowSpeakCourse(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 9) {
            mucRoom.setIsAttritionNotice(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 10000) {// ??????/?????? ?????????, ????????????????????????
            loadMembers();
        } else if (eventGroupStatus.getWhichStatus() == 10001) {// ???????????????
            mMemberSize = mMemberSize - 1;
            mCountTv.setText(mMemberSize + "/" + mucRoom.getMaxUserSize());
            mCountTv2.setText(getString(R.string.total_count_place_holder, mMemberSize));
            for (int i = 0; i < mMembers.size(); i++) {
                if (mMembers.get(i).getUserId().equals(String.valueOf(eventGroupStatus.getGroupManagerStatus()))) {
                    mCurrentMembers.remove(mMembers.get(i));
                    mMembers.remove(mMembers.get(i));
                    mAdapter.notifyDataSetInvalidated();
                }
            }
        } else if (eventGroupStatus.getWhichStatus() == 10002) {// ?????????
            loadMembers();
        } else if (eventGroupStatus.getWhichStatus() == 10003) {// ??????
            loadMembers();
            // ???????????????????????????
            MsgBroadcast.broadcastMsgRoomUpdate(mContext);
        }
    }

    private void registerRefreshReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(com.ydd.zhichat.broadcast.OtherBroadcast.REFRESH_MANAGER);
        intentFilter.addAction(ACTION_MSG_UPDATE_ROOM_INVITE);
        registerReceiver(receiver, intentFilter);
    }


    /**
     * Todo Http Get
     * <p>
     * ????????????????????????
     */
    private void updateRoom(final String roomName, final String roomDes) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        if (!TextUtils.isEmpty(roomName)) {
            params.put("roomName", roomName);
        }

        if (!TextUtils.isEmpty(roomDes)) {
            params.put("desc", roomDes);
        }
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Toast.makeText(RoomInfoActivity.this, getString(R.string.update_success), Toast.LENGTH_SHORT).show();
                            if (!TextUtils.isEmpty(roomName)) {
                                mRoomNameTv.setText(roomName);
                                mRoom.setNickName(roomName);
                                FriendDao.getInstance().updateNickName(mLoginUserId, mRoom.getUserId(), roomName);
                            }

                            if (!TextUtils.isEmpty(roomDes)) {
                                mRoomDescTv.setText(roomDes);
                                mRoom.setDescription(roomDes);
                            }
                        } else {
                            Toast.makeText(RoomInfoActivity.this, result.getResultMsg(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /**
     * ??????????????????
     */
    private void updateNickName(final String nickName) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("userId", mLoginUserId);
        params.put("nickname", nickName);
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_MEMBER_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showToast(mContext, R.string.update_success);
                        mNickNameTv.setText(nickName);
                        String loginUserId = coreManager.getSelf().getUserId();
                        FriendDao.getInstance().updateRoomName(loginUserId, mRoom.getUserId(), nickName);
                        ChatMessageDao.getInstance().updateNickName(loginUserId, mRoom.getUserId(), loginUserId, nickName);
                        mRoom.setRoomMyNickName(nickName);
                        FriendDao.getInstance().updateRoomMyNickName(mRoom.getUserId(), nickName);
                        ListenerManager.getInstance().notifyNickNameChanged(mRoom.getUserId(), loginUserId, nickName);
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /**
     * ??????????????? ??????
     */
    private void updateDisturbState(final int type, final int disturb) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("userId", mLoginUserId);
        params.put("type", String.valueOf(type));
        params.put("offlineNoPushMsg", String.valueOf(disturb));
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_DISTURB)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            if (type == 0) {
                                mRoom.setOfflineNoPushMsg(disturb);
                                FriendDao.getInstance().updateOfflineNoPushMsgStatus(mRoom.getUserId(), disturb);
                            } else {
                                if (disturb == 1) {
                                    mRoom.setTopTime(TimeUtils.sk_time_current_time());
                                    FriendDao.getInstance().updateTopFriend(mRoomJid, mRoom.getTimeSend());
                                } else {
                                    mRoom.setTopTime(0);
                                    FriendDao.getInstance().resetTopFriend(mRoomJid);
                                }
                                if (!isMucChatComing) {// ????????????????????????????????????????????????
                                    MsgBroadcast.broadcastMsgUiUpdate(RoomInfoActivity.this);
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /**
     * ??????????????????????????????
     */
    private void updateSingleAttribute(final String attributeKey, final String attributeValue) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put(attributeKey, attributeValue);
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Toast.makeText(mContext, R.string.modify_succ, Toast.LENGTH_SHORT).show();
                            if (attributeKey.equals("talkTime")) {// ????????????
                            }
                            switch (attributeKey) {
                                case "talkTime":
                                    if (Long.parseLong(attributeValue) > 0) {// ??????????????????
                                        PreferenceUtils.putBoolean(mContext, Constants.GROUP_ALL_SHUP_UP + mRoom.getUserId(), true);
                                    } else {// ??????????????????
                                        PreferenceUtils.putBoolean(mContext, Constants.GROUP_ALL_SHUP_UP + mRoom.getUserId(), false);
                                    }
                                    break;
                                case "maxUserSize":
                                    mucRoom.setMaxUserSize(Integer.valueOf(attributeValue));
                                    tvMemberLimit.setText(attributeValue);
                                    break;
                            }
                        } else {
                            Toast.makeText(mContext, R.string.modify_fail, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                    }
                });
    }

    /**
     * ????????????????????????
     */
    private void updateChatRecordTimeOut(final double outTime) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("chatRecordTimeOut", String.valueOf(outTime));

        HttpUtils.get().url(coreManager.getConfig().ROOM_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Toast.makeText(RoomInfoActivity.this, getString(R.string.update_success), Toast.LENGTH_SHORT).show();
                            mMsgSaveDays.setText(conversion(outTime));
                            FriendDao.getInstance().updateChatRecordTimeOut(mRoom.getUserId(), outTime);

                            Intent intent = new Intent();
                            intent.setAction(Constants.CHAT_TIME_OUT_ACTION);
                            intent.putExtra("friend_id", mRoom.getUserId());
                            intent.putExtra("time_out", outTime);
                            mContext.sendBroadcast(intent);
                        } else {
                            Toast.makeText(RoomInfoActivity.this, result.getResultMsg(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /*
    ??????
     */
    private void report(String roomId, Report report) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", roomId);
        params.put("reason", String.valueOf(report.getReportId()));
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().USER_REPORT)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            ToastUtil.showToast(RoomInfoActivity.this, "????????????");
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                    }
                });
    }

    /**
     * ????????????
     */
    private void quitRoom(String desc, final String url, final Map<String, String> params) {
        SelectionFrame selectionFrame = new SelectionFrame(mContext);
        selectionFrame.setSomething(null, desc, new SelectionFrame.OnSelectionFrameClickListener() {
            @Override
            public void cancelClick() {

            }

            @Override
            public void confirmClick() {
                DialogHelper.showDefaulteMessageProgressDialog(RoomInfoActivity.this);
                HttpUtils.get().url(url)
                        .params(params)
                        .build()
                        .execute(new BaseCallback<Void>(Void.class) {

                            @Override
                            public void onResponse(ObjectResult<Void> result) {
                                DialogHelper.dismissProgressDialog();
                                if (result.getResultCode() == 1) {
                                    deleteFriend();
                                    if (isMucChatComing) {// ???????????????????????????????????? / ?????? ??????????????????????????????
                                        Intent intent = new Intent(RoomInfoActivity.this, MainActivity.class);
                                        startActivity(intent);
                                    }
                                    finish();
                                } else {
                                    Toast.makeText(RoomInfoActivity.this, result.getResultMsg() + "", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(Call call, Exception e) {
                                DialogHelper.dismissProgressDialog();
                                ToastUtil.showErrorNet(RoomInfoActivity.this);
                            }
                        });
            }
        });
        selectionFrame.show();
    }

    private void deleteFriend() {
        // ??????????????????
        FriendDao.getInstance().deleteFriend(mLoginUserId, mRoom.getUserId());
        // ??????????????????
        ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, mRoom.getUserId());
        RoomMemberDao.getInstance().deleteRoomMemberTable(mRoom.getRoomId());
        // ??????????????????
        MsgBroadcast.broadcastMsgNumReset(this);
        MsgBroadcast.broadcastMsgUiUpdate(this);
        // ??????????????????
        MucgroupUpdateUtil.broadcastUpdateUi(this);
        coreManager.exitMucChat(mRoom.getUserId());
    }

    public class RefreshBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(com.ydd.zhichat.broadcast.OtherBroadcast.REFRESH_MANAGER)) {
                String roomId = intent.getStringExtra("roomId");
                String toUserId = intent.getStringExtra("toUserId");
                boolean isSet = intent.getBooleanExtra("isSet", false);
                if (roomId.equals(mRoomJid) && toUserId.equals(mLoginUserId)) {
                    TipDialog tipDialog = new TipDialog(RoomInfoActivity.this);
                    tipDialog.setmConfirmOnClickListener(isSet ? getString(R.string.tip_became_manager) : getString(R.string.tip_be_cancel_manager)
                            , new TipDialog.ConfirmOnClickListener() {
                                @Override
                                public void confirm() {
                                    finish();
                                }
                            });
                    tipDialog.show();
                }
            } else if (action.equals(ACTION_MSG_UPDATE_ROOM_INVITE)) {
                if (mucRoom != null) {
                    int enabled = intent.getIntExtra(EXTRA_ENABLED, -1);
                    if (enabled != -1) {
                        mucRoom.setAllowInviteFriend(enabled);
                    }
                }
            }
        }
    }

    class GridViewAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mCurrentMembers.size();
        }

        @Override
        public Object getItem(int position) {
            return mCurrentMembers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_room_info_view, parent, false);
                GridViewHolder vh = new GridViewHolder(convertView);
                convertView.setTag(vh);
            }
            GridViewHolder vh = (GridViewHolder) convertView.getTag();
            ImageView imageView = vh.imageView;
            TextView memberName = vh.memberName;
            int GAT5;
            if (add_minus_count == 1) {
                GAT5 = add_minus_count + 2;
            } else {
                GAT5 = add_minus_count + 1;
            }
            if (position > mCurrentMembers.size() - GAT5) {// + -
                memberName.setText("");
                if (position == mCurrentMembers.size() - 2) {
                    imageView.setImageResource(R.drawable.bg_room_info_add_btn);
                }
                if (position == mCurrentMembers.size() - 1) {
                    imageView.setImageResource(R.drawable.bg_room_info_minus_btn);
                }
            } else {
                MucRoomMember mMucRoomMember = mCurrentMembers.get(position);
                String name;
                if (role == 1) {// ?????? ????????????>????????????>????????????
                    if (!TextUtils.isEmpty(mMucRoomMember.getRemarkName())) {
                        name = mMucRoomMember.getRemarkName();
                    } else {
                        if (mRemarksMap.containsKey(mCurrentMembers.get(position).getUserId())) {// ????????? ???????????? ?????? ????????????????????????
                            name = mRemarksMap.get(mMucRoomMember.getUserId());
                        } else {
                            name = mMucRoomMember.getNickName();
                        }
                    }
                } else {
                    if (mRemarksMap.containsKey(mCurrentMembers.get(position).getUserId())) {// ????????? ???????????? ?????? ????????????????????????
                        name = mRemarksMap.get(mMucRoomMember.getUserId());
                    } else {
                        name = mMucRoomMember.getNickName();

                    }
                }
                AvatarHelper.getInstance().displayAvatar(name, mMucRoomMember.getUserId(), imageView, true);
                memberName.setText(name);
            }
            return convertView;
        }
    }

    class GridViewHolder {
        ImageView imageView;
        TextView memberName;

        GridViewHolder(View itemView) {
            imageView = itemView.findViewById(R.id.content);
            memberName = itemView.findViewById(R.id.member_name);
        }
    }
}
