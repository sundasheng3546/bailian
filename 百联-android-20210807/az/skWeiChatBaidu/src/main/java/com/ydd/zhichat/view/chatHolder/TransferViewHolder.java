package com.ydd.zhichat.view.chatHolder;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.ydd.zhichat.AppConstant;
import com.ydd.zhichat.R;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.Transfer;
import com.ydd.zhichat.bean.message.ChatMessage;
import com.ydd.zhichat.db.dao.FriendDao;
import com.ydd.zhichat.pay.TransferMoneyDetailActivity;
import com.ydd.zhichat.ui.base.CoreManager;
import com.ydd.zhichat.view.NoDoubleClickListener;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.BaseCallback;
import com.xuan.xuanhttplibrary.okhttp.result.ObjectResult;

import java.util.HashMap;

import okhttp3.Call;

class TransferViewHolder extends AChatHolderInterface {

    TextView mTvContent;
    TextView mTvMoney;

    @Override
    public int itemLayoutId(boolean isMysend) {
        return isMysend ? R.layout.chat_from_item_transfer : R.layout.chat_to_item_transfer;
    }

    @Override
    public void initView(View view) {
        mTvContent = view.findViewById(R.id.chat_text_desc);
        mTvMoney = view.findViewById(R.id.chat_text_money);
        mRootView = view.findViewById(R.id.chat_warp_view);
    }

    @Override
    public void fillData(ChatMessage message) {
        if (mdata.getFileSize() == 2) {// 已领取
            mRootView.setAlpha(0.6f);
        } else {
            mRootView.setAlpha(1f);
        }

        if (TextUtils.isEmpty(message.getFilePath())) {
            if (message.getFromUserId().equals(mLoginUserId)) {// 发送方 显示 转账给对方
                Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, message.getToUserId());
                if (friend != null) {
                    mTvContent.setText(getString(R.string.transfer_money_to_someone2,
                            TextUtils.isEmpty(friend.getRemarkName()) ? friend.getNickName() : friend.getRemarkName()));
                }
            } else {// 接收方 显示 转账给你
                mTvContent.setText(getString(R.string.transfer_money_to_someone3));
            }
        } else {// 转账说明
            mTvContent.setText(message.getFilePath());
        }
        mTvMoney.setText("￥" + message.getContent());

        mRootView.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                TransferViewHolder.super.onClick(view);
            }
        });
    }

    @Override
    public boolean isOnClick() {
        return false; // 红包消息点击后回去请求接口，所以要做一个多重点击替换
    }

    @Override
    protected void onRootClick(View v) {
        getTransferInfo();
    }

    private void getTransferInfo() {
        final String token = CoreManager.requireSelfStatus(mContext).accessToken;
        final String redId = mdata.getObjectId();
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("access_token", token);
        params.put("id", redId);

        HttpUtils.get().url(CoreManager.requireConfig(mContext).SKTRANSFER_GET_TRANSFERINFO)
                .params(params)
                .build()
                .execute(new BaseCallback<Transfer>(Transfer.class) {

                    @Override
                    public void onResponse(ObjectResult<Transfer> result) {
                        if (result.getResultCode() == 1 && result.getData() != null) {
                            Intent intent = new Intent(mContext, TransferMoneyDetailActivity.class);
                            intent.putExtra(AppConstant.EXTRA_MESSAGE_ID, mdata.getPacketId());
                            intent.putExtra(TransferMoneyDetailActivity.TRANSFER_DETAIL, JSON.toJSONString(result.getData()));
                            mContext.startActivity(intent);
                        } else {
                            Toast.makeText(mContext, result.getResultMsg(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                    }
                });
    }

    @Override
    public boolean enableSendRead() {
        return true;
    }
}
