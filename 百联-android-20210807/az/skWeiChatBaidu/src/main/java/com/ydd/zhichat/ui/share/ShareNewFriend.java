package com.ydd.zhichat.ui.share;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.ydd.zhichat.R;
import com.ydd.zhichat.Reporter;
import com.ydd.zhichat.adapter.FriendSortAdapter;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.SKShareBean;
import com.ydd.zhichat.bean.message.ChatMessage;
import com.ydd.zhichat.bean.message.XmppMessage;
import com.ydd.zhichat.broadcast.MsgBroadcast;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.dao.ChatMessageDao;
import com.ydd.zhichat.db.dao.FriendDao;
import com.ydd.zhichat.sortlist.BaseComparator;
import com.ydd.zhichat.sortlist.BaseSortModel;
import com.ydd.zhichat.sortlist.SideBar;
import com.ydd.zhichat.sortlist.SortHelper;
import com.ydd.zhichat.ui.MainActivity;
import com.ydd.zhichat.ui.base.BaseActivity;
import com.ydd.zhichat.ui.message.InstantMessageConfirm;
import com.ydd.zhichat.util.AsyncUtils;
import com.ydd.zhichat.util.TimeUtils;
import com.ydd.zhichat.util.ToastUtil;
import com.ydd.zhichat.view.LoadFrame;
import com.ydd.zhichat.xmpp.ListenerManager;
import com.ydd.zhichat.xmpp.listener.ChatMessageListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 分享 选择 好友
 */
public class ShareNewFriend extends BaseActivity implements OnClickListener, ChatMessageListener {
    private PullToRefreshListView mPullToRefreshListView;
    private FriendSortAdapter mAdapter;
    private TextView mTextDialog;
    private SideBar mSideBar;
    private List<BaseSortModel<Friend>> mSortFriends;
    private BaseComparator<Friend> mBaseComparator;
    private String mLoginUserId;

    private Handler mHandler = new Handler();

    private InstantMessageConfirm menuWindow;
    private LoadFrame mLoadFrame;

    private SKShareBean mSKShareBean;
    private String mShareContent;
    private ChatMessage mShareChatMessage;
    private BroadcastReceiver mShareBroadCast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.isEmpty(intent.getAction())
                    && intent.getAction().equals(ShareBroadCast.ACTION_FINISH_ACTIVITY)) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newchat_person_selected);
        mSortFriends = new ArrayList<BaseSortModel<Friend>>();
        mBaseComparator = new BaseComparator<Friend>();
        mLoginUserId = coreManager.getSelf().getUserId();

        mShareContent = getIntent().getStringExtra(ShareConstant.EXTRA_SHARE_CONTENT);
        Log.e("zq", mShareContent);
        mSKShareBean = JSON.parseObject(mShareContent, SKShareBean.class);

        initActionBar();
        initView();
        loadData();

        ListenerManager.getInstance().addChatMessageListener(this);
        registerReceiver(mShareBroadCast, new IntentFilter(ShareBroadCast.ACTION_FINISH_ACTIVITY));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ListenerManager.getInstance().removeChatMessageListener(this);
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView tvTitle = (TextView) findViewById(R.id.tv_title_center);
        tvTitle.setText(InternationalizationHelper.getString("SELECT_CONTANTS"));
    }

    private void initView() {
        mPullToRefreshListView = (PullToRefreshListView) findViewById(R.id.pull_refresh_list);
        View headView = View.inflate(this, R.layout.item_headview_creategroup_chat, null);
        mPullToRefreshListView.getRefreshableView().addHeaderView(headView);
        headView.setOnClickListener(this);
        mPullToRefreshListView.setMode(Mode.PULL_FROM_START);
        mAdapter = new FriendSortAdapter(this, mSortFriends);
        mPullToRefreshListView.getRefreshableView().setAdapter(mAdapter);
        mPullToRefreshListView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                loadData();
            }
        });

        mPullToRefreshListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Friend friend = mSortFriends.get((int) id).getBean();
                showPopuWindow(view, friend);
            }
        });

        mTextDialog = (TextView) findViewById(R.id.text_dialog);
        mSideBar = (SideBar) findViewById(R.id.sidebar);
        mSideBar.setTextView(mTextDialog);

        mSideBar.setOnTouchingLetterChangedListener(new SideBar.OnTouchingLetterChangedListener() {
            @Override
            public void onTouchingLetterChanged(String s) {
                // 该字母首次出现的位置
                int position = mAdapter.getPositionForSection(s.charAt(0));
                if (position != -1) {
                    mPullToRefreshListView.getRefreshableView().setSelection(position);
                }
            }
        });
    }

    private void showPopuWindow(View view, Friend friend) {
        menuWindow = new InstantMessageConfirm(this, new ClickListener(friend), friend);
        menuWindow.showAtLocation(view, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
    }

    private void loadData() {
        AsyncUtils.doAsync(this, e -> {
            Reporter.post("加载数据失败，", e);
            AsyncUtils.runOnUiThread(this, ctx -> {
                ToastUtil.showToast(ctx, R.string.data_exception);
            });
        }, c -> {
            long startTime = System.currentTimeMillis();
            final List<Friend> friends = FriendDao.getInstance().getAllFriends(mLoginUserId);
            Map<String, Integer> existMap = new HashMap<>();
            List<BaseSortModel<Friend>> sortedList = SortHelper.toSortedModelList(friends, existMap, Friend::getShowName);

            long delayTime = 200 - (startTime - System.currentTimeMillis());// 保证至少200ms的刷新过程
            if (delayTime < 0) {
                delayTime = 0;
            }
            c.postDelayed(r -> {
                mSideBar.setExistMap(existMap);
                mSortFriends = sortedList;
                mAdapter.setData(sortedList);
                mPullToRefreshListView.onRefreshComplete();
            }, delayTime);
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rl_headview_instant_group:
                Intent intent = new Intent(this, ShareNewGroup.class);
                intent.putExtra(ShareConstant.EXTRA_SHARE_CONTENT, mShareContent);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    @Override
    public void onMessageSendStateChange(int messageState, String msgId) {
        if (TextUtils.isEmpty(msgId)) {
            return;
        }
        // 更新消息Fragment的广播
        MsgBroadcast.broadcastMsgUiUpdate(mContext);
        if (mShareChatMessage != null && mShareChatMessage.getPacketId().equals(msgId)) {
            if (messageState == ChatMessageListener.MESSAGE_SEND_SUCCESS) {// 发送成功
                if (mLoadFrame != null) {
                    mLoadFrame.change();
                }
            }
        }
    }

    @Override
    public boolean onNewMessage(String fromUserId, ChatMessage message, boolean isGroupMsg) {
        return false;
    }

    /**
     * 事件的监听
     */
    class ClickListener implements OnClickListener {
        private Friend friend;

        public ClickListener(Friend friend) {
            this.friend = friend;
        }

        @Override
        public void onClick(View v) {
            menuWindow.dismiss();
            switch (v.getId()) {
                case R.id.btn_send:
                    mLoadFrame = new LoadFrame(ShareNewFriend.this);
                    mLoadFrame.setSomething(getString(R.string.back_app, mSKShareBean.getAppName()), new LoadFrame.OnLoadFrameClickListener() {
                        @Override
                        public void cancelClick() {
                            ShareBroadCast.broadcastFinishActivity(ShareNewFriend.this);
                        }

                        @Override
                        public void confirmClick() {
                            ShareBroadCast.broadcastFinishActivity(ShareNewFriend.this);
                            startActivity(new Intent(ShareNewFriend.this, MainActivity.class));
                            finish();
                        }
                    });
                    mLoadFrame.show();

                    mShareChatMessage = new ChatMessage();
                    mShareChatMessage.setType(XmppMessage.TYPE_SHARE_LINK);
                    mShareChatMessage.setFromUserId(mLoginUserId);
                    mShareChatMessage.setFromUserName(coreManager.getSelf().getNickName());
                    mShareChatMessage.setToUserId(friend.getUserId());
                    mShareChatMessage.setObjectId(mShareContent);
                    mShareChatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                    mShareChatMessage.setDoubleTimeSend(TimeUtils.sk_time_current_time_double());
                    ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), mShareChatMessage);
                    coreManager.sendChatMessage(friend.getUserId(), mShareChatMessage);
                    break;
                case R.id.btn_cancle:// 取消
                    break;
                default:
                    break;
            }
        }
    }
}
