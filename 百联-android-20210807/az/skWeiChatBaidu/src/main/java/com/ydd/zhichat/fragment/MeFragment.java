package com.ydd.zhichat.fragment;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.ydd.zhichat.AppConstant;
import com.ydd.zhichat.R;
import com.ydd.zhichat.Reporter;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.User;
import com.ydd.zhichat.broadcast.OtherBroadcast;
import com.ydd.zhichat.course.LocalCourseActivity;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.dao.FriendDao;
import com.ydd.zhichat.helper.AvatarHelper;
import com.ydd.zhichat.ui.MainActivity;
import com.ydd.zhichat.ui.base.EasyFragment;
import com.ydd.zhichat.ui.circle.BusinessCircleActivity;
import com.ydd.zhichat.ui.circle.DiscoverActivity;
import com.ydd.zhichat.ui.circle.range.NewZanActivity;
import com.ydd.zhichat.ui.contacts.RoomActivity;
import com.ydd.zhichat.ui.me.BasicInfoEditActivity;
import com.ydd.zhichat.ui.me.MyCollection;
import com.ydd.zhichat.ui.me.SettingActivity;
import com.ydd.zhichat.ui.me.ShareActivity;
import com.ydd.zhichat.ui.me.redpacket.WxPayBlance;
import com.ydd.zhichat.ui.message.ChatActivity;
import com.ydd.zhichat.ui.other.QRcodeActivity;
import com.ydd.zhichat.ui.tool.SingleImagePreviewActivity;
import com.ydd.zhichat.util.AsyncUtils;
import com.ydd.zhichat.util.Constants;
import com.ydd.zhichat.util.PreferenceUtils;
import com.ydd.zhichat.util.ToastUtil;
import com.ydd.zhichat.util.UiUtils;

public class MeFragment extends EasyFragment implements View.OnClickListener {

    private ImageView mAvatarImg;
    private ImageView imageView3;
    private TextView mNickNameTv;
    private TextView mPhoneNumTv;
    private TextView skyTv, setTv;
    String zs="{\n" +
            "\t\"_id\": 1,\n" +
            "\t\"chatRecordTimeOut\": -1.0,\n" +
            "\t\"companyId\": 0,\n" +
            "\t\"content\": \"????????????????????????\",\n" +
            "\t\"downloadTime\": 0,\n" +
            "\t\"groupStatus\": 0,\n" +
            "\t\"isAtMe\": 0,\n" +
            "\t\"isDevice\": 0,\n" +
            "\t\"nickName\": \"????????????\",\n" +
            "\t\"offlineNoPushMsg\": 0,\n" +
            "\t\"ownerId\": \"10000014\",\n" +
            "\t\"remarkName\": \"????????????\",\n" +
            "\t\"roomFlag\": 0,\n" +
            "\t\"roomTalkTime\": 0,\n" +
            "\t\"status\": 8,\n" +
            "\t\"timeCreate\": 0,\n" +
            "\t\"timeSend\": 0,\n" +
            "\t\"topTime\": 0,\n" +
            "\t\"type\": 0,\n" +
            "\t\"unReadNum\": 0,\n" +
            "\t\"userId\": \"10000\",\n" +
            "\t\"version\": 1\n" +
            "}";
    private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.equals(action, OtherBroadcast.SYNC_SELF_DATE_NOTIFY)) {
                updateUI();
            }
        }
    };

    public MeFragment() {
    }

    @Override
    protected int inflateLayoutId() {
        return R.layout.fragment_me;
    }

    @Override
    protected void onActivityCreated(Bundle savedInstanceState, boolean createView) {
        if (createView) {
            initView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mUpdateReceiver);
    }

    private void initView() {
//        if (coreManager.getConfig().newUi) {
//            findViewById(R.id.iv_title_left).setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    requireActivity().finish();
//                }
//            });
//        } else {
//            findViewById(R.id.iv_title_left).setVisibility(View.GONE);
//        }
//        TextView mTvTitle = (TextView) findViewById(R.id.tv_title_center);
//        mTvTitle.setText("??????");
        skyTv = (TextView) findViewById(R.id.MySky);
        setTv = (TextView) findViewById(R.id.SettingTv);
        skyTv.setText("????????????");
        setTv.setText(InternationalizationHelper.getString("JXSettingVC_Set"));
        findViewById(R.id.info_rl).setOnClickListener(this);
        findViewById(R.id.live_rl).setOnClickListener(this);
        findViewById(R.id.douyin_rl).setOnClickListener(this);

        findViewById(R.id.correlation_rl).setOnClickListener(this);
        findViewById(R.id.customer_rl).setOnClickListener(this);

        findViewById(R.id.ll_more).setVisibility(View.GONE);

        findViewById(R.id.my_monry).setOnClickListener(this);
        // ???????????????????????????????????????
        if (coreManager.getConfig().displayRedPacket) { // ??????????????????ui??????????????????????????????????????????????????????????????????
            findViewById(R.id.my_monry).setVisibility(View.GONE);
        }
        findViewById(R.id.my_space_rl).setOnClickListener(this);
        findViewById(R.id.my_collection_rl).setOnClickListener(this);
        findViewById(R.id.local_course_rl).setOnClickListener(this);
        findViewById(R.id.setting_rl).setOnClickListener(this);

        mAvatarImg = (ImageView) findViewById(R.id.avatar_img);
        imageView3 = (ImageView) findViewById(R.id.imageView3);
        mNickNameTv = (TextView) findViewById(R.id.nick_name_tv);
        mPhoneNumTv = (TextView) findViewById(R.id.phone_number_tv);
        String loginUserId = coreManager.getSelf().getUserId();
        AvatarHelper.getInstance().displayAvatar(coreManager.getSelf().getNickName(), loginUserId, mAvatarImg, false);
        mNickNameTv.setText(coreManager.getSelf().getNickName());

        mAvatarImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), SingleImagePreviewActivity.class);
                intent.putExtra(AppConstant.EXTRA_IMAGE_URI, coreManager.getSelf().getUserId());
                startActivity(intent);
            }
        });
        imageView3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                User mUser = coreManager.getSelf();
                Intent intent2 = new Intent(getActivity(), QRcodeActivity.class);
                intent2.putExtra("isgroup", false);
                if (!TextUtils.isEmpty(mUser.getAccount())) {
                    intent2.putExtra("userid", mUser.getAccount());
                } else {
                    intent2.putExtra("userid", mUser.getUserId());
                }
                intent2.putExtra("userAvatar", mUser.getUserId());
                intent2.putExtra("userName", mUser.getNickName());
                startActivity(intent2);
            }
        });

        findViewById(R.id.llFriend).setOnClickListener(v -> {
            MainActivity activity = (MainActivity) requireActivity();
            activity.changeTab(R.id.rb_tab_2);
        });

        findViewById(R.id.llGroup).setOnClickListener(v -> RoomActivity.start(requireContext()));

//        initTitleBackground();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(OtherBroadcast.SYNC_SELF_DATE_NOTIFY);
        getActivity().registerReceiver(mUpdateReceiver, intentFilter);


    }

//    private void initTitleBackground() {
//        SkinUtils.Skin skin = SkinUtils.getSkin(requireContext());
//        int primaryColor = skin.getPrimaryColor();
//        findViewById(R.id.tool_bar).setBackgroundColor(primaryColor);
//    }

    @Override
    public void onClick(View v) {
        if (!UiUtils.isNormalClick(v)) {
            return;
        }
        int id = v.getId();
        switch (id) {
            case R.id.info_rl:
                // ????????????
                startActivityForResult(new Intent(getActivity(), BasicInfoEditActivity.class), 1);
                break;
            case R.id.my_monry:
                // ????????????
                startActivity(new Intent(getActivity(), WxPayBlance.class));
                break;
            case R.id.my_space_rl:
                // ????????????
                Intent intent = new Intent(getActivity(), BusinessCircleActivity.class);
                intent.putExtra(AppConstant.EXTRA_CIRCLE_TYPE, AppConstant.CIRCLE_TYPE_PERSONAL_SPACE);
                startActivity(intent);
                break;
            case R.id.my_collection_rl:
                // ????????????
                startActivity(new Intent(getActivity(), MyCollection.class));
                break;
            case R.id.local_course_rl:
                // ????????????
                startActivity(new Intent(getActivity(), LocalCourseActivity.class));
                break;
            case R.id.setting_rl:
                // ??????
                startActivity(new Intent(getActivity(), SettingActivity.class));
                break;
            case R.id.correlation_rl:
                // ????????????
                Intent intent2 = new Intent(getActivity(), NewZanActivity.class);
                intent2.putExtra("OpenALL", true);
                startActivity(intent2);
                break;
            case R.id.customer_rl:
                // ????????????
                // ????????????
                Friend friend=new Gson().fromJson(zs,Friend.class);
                Intent intent1 = new Intent();
                intent1.setClass(getActivity(), ChatActivity.class);
                intent1.putExtra(ChatActivity.FRIEND, friend);
                startActivity(intent1);
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 || resultCode == Activity.RESULT_OK) {// ?????????????????????
            updateUI();
        }
    }

    /**
     * ?????????????????????????????????ui??????
     */
    private void updateUI() {
        if (mAvatarImg != null) {
            AvatarHelper.getInstance().displayAvatar(coreManager.getSelf().getUserId(), mAvatarImg, true);
        }
        if (mNickNameTv != null) {
            mNickNameTv.setText(coreManager.getSelf().getNickName());
        }

        if (mPhoneNumTv != null) {
            String phoneNumber = coreManager.getSelf().getTelephone();
            int mobilePrefix = PreferenceUtils.getInt(getContext(), Constants.AREA_CODE_KEY, -1);
            String sPrefix = String.valueOf(mobilePrefix);
            // ????????????????????????
            if (phoneNumber.startsWith(sPrefix)) {
                phoneNumber = phoneNumber.substring(sPrefix.length());
            }
           mPhoneNumTv.setText("???????????????????????????");
        }

        AsyncUtils.doAsync(this, t -> {
            Reporter.post("???????????????????????????", t);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    ToastUtil.showToast(requireContext(), R.string.tip_me_query_friend_failed);
                });
            }
        }, ctx -> {
            long count = FriendDao.getInstance().getFriendsCount(coreManager.getSelf().getUserId());
            ctx.uiThread(ref -> {
                TextView tvColleague = findViewById(R.id.tvFriend);
                tvColleague.setText(String.valueOf(count));
            });
        });

        AsyncUtils.doAsync(this, t -> {
            Reporter.post("???????????????????????????", t);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    ToastUtil.showToast(requireContext(), R.string.tip_me_query_friend_failed);
                });
            }
        }, ctx -> {
            long count = FriendDao.getInstance().getGroupsCount(coreManager.getSelf().getUserId());
            ctx.uiThread(ref -> {
                TextView tvGroup = findViewById(R.id.tvGroup);
                tvGroup.setText(String.valueOf(count));
            });
        });

    }
}
