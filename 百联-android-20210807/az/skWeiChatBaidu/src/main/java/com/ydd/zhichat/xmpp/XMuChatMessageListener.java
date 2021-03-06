package com.ydd.zhichat.xmpp;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.ydd.zhichat.MyApplication;
import com.ydd.zhichat.R;
import com.ydd.zhichat.audio.NoticeVoicePlayer;
import com.ydd.zhichat.bean.EventNewNotice;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.MsgRoamTask;
import com.ydd.zhichat.bean.RoomMember;
import com.ydd.zhichat.bean.message.ChatMessage;
import com.ydd.zhichat.bean.message.XmppMessage;
import com.ydd.zhichat.broadcast.MsgBroadcast;
import com.ydd.zhichat.broadcast.MucgroupUpdateUtil;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.dao.ChatMessageDao;
import com.ydd.zhichat.db.dao.FriendDao;
import com.ydd.zhichat.db.dao.MsgRoamTaskDao;
import com.ydd.zhichat.db.dao.RoomMemberDao;
import com.ydd.zhichat.fragment.MessageFragment;
import com.ydd.zhichat.ui.base.CoreManager;
import com.ydd.zhichat.ui.mucfile.XfileUtils;
import com.ydd.zhichat.util.Constants;
import com.ydd.zhichat.util.DateFormatUtil;
import com.ydd.zhichat.util.PreferenceUtils;
import com.ydd.zhichat.xmpp.listener.ChatMessageListener;
import com.ydd.zhichat.xmpp.util.XmppStringUtil;

import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.json.JSONException;

import java.util.Date;
import java.util.UUID;

import de.greenrobot.event.EventBus;

import static com.ydd.zhichat.db.InternationalizationHelper.getString;

/**
 * Created by Administrator on 2017/11/24.
 */

public class XMuChatMessageListener implements MessageListener {

    private CoreService mService;
    private String mLoginUserId;

    public XMuChatMessageListener(CoreService coreService) {
        mService = coreService;
        mLoginUserId = CoreManager.requireSelf(mService).getUserId();
    }

    @Override
    public void processMessage(Message message) {
        mService.sendReceipt(message.getPacketID());
        Log.e("msg_muc", "from:" + message.getFrom() + " ,to:" + message.getTo());
        if (TextUtils.isEmpty(message.getFrom()) || TextUtils.isEmpty(message.getTo())) {
            Log.e("msg_muc", "Return 1");
            return;
        }
        String from = message.getFrom().toString();
        String to = message.getTo().toString();
        if (!XmppStringUtil.isJID(from) || !XmppStringUtil.isJID(to)) {
            Log.e("msg_muc", "Return 2");
            return;
        }

        String content = message.getBody();
        if (TextUtils.isEmpty(content)) {
            Log.e("msg_muc", "Return 3");
            return;
        }
        Log.e("msg_muc", content);

        // DelayInformation delayInformation = (DelayInformation) message.getExtension("delay", "jabber:x:delay");
        DelayInformation delayInformation = (DelayInformation) message.getExtension("delay", "urn:xmpp:delay");
        if (com.ydd.zhichat.util.StringUtils.strEquals(message.getPacketID(), "") || message.getPacketID() == null) {
            /**
             * ????????????packetId???????????????????????????????????????????????????????????????????????????body???????????????messageId,????????????
             * ??????????????????????????????packetId???????????????????????????messageId
             * ??????id?????????
             * @see {@link  ChatMessage#toJsonString(boolean)}
             * @see {@link  com.ydd.zhichat.bean.message.NewFriendMessage#toJsonString(boolean)}
             * */
            try {
                JSONObject jsonObject = JSONObject.parseObject(message.getBody());
                message.setPacketID(jsonObject.getString("messageId"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (delayInformation != null) {// ??????????????????
            Log.e("delay", "??????????????????........" + message.getBody() + "delay:" + delayInformation.getStamp().getTime());
            Date date = delayInformation.getStamp();
            if (date != null) {
                saveGroupMessage(content, from, message.getPacketID(), true);
                return;
            }
        }
        saveGroupMessage(content, from, message.getPacketID(), false);
    }

    /**
     * ??????????????????????????????(??????)
     */
    private void saveGroupMessage(String body, String from, String packetId, boolean isDelay) {
        String fromId = XmppStringUtil.getRoomJID(from);
        String roomJid = XmppStringUtil.getRoomJIDPrefix(fromId);

        ChatMessage chatMessage = new ChatMessage(body);

        if (TextUtils.equals(chatMessage.getFromUserId(), mLoginUserId)
                && chatMessage.getType() == XmppMessage.TYPE_READ
                && TextUtils.isEmpty(chatMessage.getFromUserName())) {
            chatMessage.setFromUserName(CoreManager.requireSelf(mService).getNickName());
        }

        if (!chatMessage.validate()) {
            return;
        }
        ChatMessageDao.getInstance().decryptDES(chatMessage);// ??????
        int type = chatMessage.getType();

        chatMessage.setGroup(true);
        chatMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
        if (TextUtils.isEmpty(packetId)) {
            if (isDelay) {
                Log.e("msg_muc", "???????????????packetId??????????????????????????????????????????????????????????????????Return");
            }
            packetId = UUID.randomUUID().toString().replaceAll("-", "");
        }
        chatMessage.setPacketId(packetId);

        // ??????????????????
        if (isDelay) {
            if (chatMessage.isExpired()) {// ???????????????????????????????????????????????????Return ????????????
                Log.e("msg_muc", "// ???????????????????????????????????????????????????Return ????????????");
                chatMessage.setIsExpired(1);
                ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, roomJid, chatMessage);
                return;
            }
            // ???????????? ???????????????????????????????????????????????????100???????????????100?????????????????????????????????endTime????????????????????????????????????
            // ???????????? ?????????????????????????????????msgId?????????????????????startMsgId ??????????????????100???
            MsgRoamTask mLastMsgRoamTask = MsgRoamTaskDao.getInstance().getFriendLastMsgRoamTask(mLoginUserId, roomJid); // ?????????????????????????????????
            if (mLastMsgRoamTask == null) {
            } else if (mLastMsgRoamTask.getEndTime() == 0) {// ???????????????EndTime?????? ???????????????????????????
                MsgRoamTaskDao.getInstance().updateMsgRoamTaskEndTime(mLoginUserId, roomJid, mLastMsgRoamTask.getTaskId(), chatMessage.getTimeSend());
            } else if (packetId.equals(mLastMsgRoamTask.getStartMsgId())) {
                MsgRoamTaskDao.getInstance().deleteMsgRoamTask(mLoginUserId, roomJid, mLastMsgRoamTask.getTaskId());
            }
        }

        boolean isShieldGroupMsg = PreferenceUtils.getBoolean(MyApplication.getContext(), Constants.SHIELD_GROUP_MSG + roomJid + mLoginUserId, false);
        if (isShieldGroupMsg) {// ?????????
            return;
        }

        if (type == XmppMessage.TYPE_TEXT
                && !TextUtils.isEmpty(chatMessage.getObjectId())) {// ?????????@??????
            Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, roomJid);
            if (friend != null) {
                if (friend.getIsAtMe() == 0
                        && !TextUtils.equals(MyApplication.IsRingId, roomJid)) {// ?????????@?????? && ?????????????????????????????????????????????????????????
                    if (chatMessage.getObjectId().equals(roomJid)) {// @????????????
                        FriendDao.getInstance().updateAtMeStatus(roomJid, 2);
                    } else if (chatMessage.getObjectId().contains(mLoginUserId)) {// @???
                        FriendDao.getInstance().updateAtMeStatus(roomJid, 1);
                    }
                }
            }
        }

        // ?????????
        if (type == XmppMessage.TYPE_READ) {
            packetId = chatMessage.getContent();
            ChatMessage chat = ChatMessageDao.getInstance().findMsgById(mLoginUserId, roomJid, packetId);
            if (chat != null) {
                String fromUserId = chatMessage.getFromUserId();
                boolean repeat = ChatMessageDao.getInstance().checkRepeatRead(mLoginUserId, roomJid, fromUserId, packetId);
                if (!repeat) {
                    int count = chat.getReadPersons();// ????????????+1
                    chat.setReadPersons(count + 1);
                    // ??????????????????
                    chat.setReadTime(chatMessage.getTimeSend());
                    // ??????????????????
                    ChatMessageDao.getInstance().updateMessageRead(mLoginUserId, roomJid, chat);
                    // ???????????????
                    ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, roomJid, chatMessage);
                    // ????????????
                    MsgBroadcast.broadcastMsgReadUpdate(MyApplication.getInstance(), packetId);
                }
            }
            return;
        }

        // ????????????
        if (type == XmppMessage.TYPE_BACK) {
            // ?????????????????????
            packetId = chatMessage.getContent();
            if (chatMessage.getFromUserId().equals(mLoginUserId)) {// ????????????????????????
                ChatMessageDao.getInstance().updateMessageBack(mLoginUserId, roomJid, packetId, MyApplication.getContext().getString(R.string.you));
            } else {
                ChatMessageDao.getInstance().updateMessageBack(mLoginUserId, roomJid, packetId, chatMessage.getFromUserName());
            }

            Intent intent = new Intent();
            intent.putExtra("packetId", packetId);
            intent.setAction(com.ydd.zhichat.broadcast.OtherBroadcast.MSG_BACK);
            mService.sendBroadcast(intent);

            // ??????UI??????
            ChatMessage chat = ChatMessageDao.getInstance().getLastChatMessage(mLoginUserId, roomJid);
            if (chat != null) {
                if (chat.getPacketId().equals(packetId)) {
                    // ??????????????????????????????????????????????????????
                    if (chatMessage.getFromUserId().equals(mLoginUserId)) {// ????????????????????????
                        FriendDao.getInstance().updateFriendContent(mLoginUserId, roomJid,
                                MyApplication.getContext().getString(R.string.you) + " " + getString("JX_OtherWithdraw"), XmppMessage.TYPE_TEXT, chatMessage.getTimeSend());
                    } else {
                        FriendDao.getInstance().updateFriendContent(mLoginUserId, roomJid,
                                chatMessage.getFromUserName() + " " + getString("JX_OtherWithdraw"), XmppMessage.TYPE_TEXT, chatMessage.getTimeSend());
                    }
                    MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                }
            }
            return;
        }

        if ((type >= XmppMessage.TYPE_MUCFILE_ADD && type <= XmppMessage.TYPE_MUCFILE_DOWN)
                || (type >= XmppMessage.TYPE_CHANGE_NICK_NAME && type <= XmppMessage.NEW_MEMBER)
                || type == XmppMessage.TYPE_SEND_MANAGER
                || (type >= XmppMessage.TYPE_CHANGE_SHOW_READ && type <= XmppMessage.TYPE_GROUP_TRANSFER)) {
            if (TextUtils.isEmpty(chatMessage.getObjectId())) {
                Log.e("msg_muc", "Return 4");
                return;
            }
            if (ChatMessageDao.getInstance().hasSameMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage.getPacketId())) {// ?????????????????????????????????????????????
                Log.e("msg_muc", "Return 5");
                return;
            }
            Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getObjectId());
            if (friend != null) {
                chatGroup(body, chatMessage, friend);
            }
            return;
        }

        if (type == XmppMessage.TYPE_GROUP_UPDATE_MSG_AUTO_DESTROY_TIME) {
            if (TextUtils.isEmpty(chatMessage.getObjectId())) {
                Log.e("msg_muc", "Return 4");
                return;
            }
            if (ChatMessageDao.getInstance().hasSameMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage.getPacketId())) {// ?????????????????????????????????????????????
                Log.e("msg_muc", "Return 5");
                return;
            }
            FriendDao.getInstance().updateChatRecordTimeOut(chatMessage.getObjectId(), Double.parseDouble(chatMessage.getContent()));
            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_group_owner_update_msg_auto_destroy_time, DateFormatUtil.timeStr(Double.parseDouble(chatMessage.getContent()))));
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
            }
        }

        if (chatMessage.getFromUserId().equals(mLoginUserId) &&
                (chatMessage.getType() == XmppMessage.TYPE_IMAGE || chatMessage.getType() == XmppMessage.TYPE_VIDEO || chatMessage.getType() == XmppMessage.TYPE_FILE)) {
            Log.e("msg_muc", "????????????????????????????????????????????????");
            chatMessage.setUpload(true);
            chatMessage.setUploadSchedule(100);
        }
        if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, roomJid, chatMessage)) {
            Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, roomJid);
            if (friend != null) {// friend == null ?????????????????????????????????
                if (friend.getOfflineNoPushMsg() == 0) {

                    mService.notificationMessage(chatMessage, true);// ??????????????????????????????????????????

                    if (!roomJid.equals(MyApplication.IsRingId)
                            && !chatMessage.getFromUserId().equals(mLoginUserId)) {// ?????????????????????????????????????????????????????? && ???????????????????????????
                        Log.e("msg", "??????????????????");
                        if (!MessageFragment.foreground) {
                            // ????????????????????????
                            NoticeVoicePlayer.getInstance().start();
                        }
                    }
                } else {
                    Log.e("msg", "??????????????????????????????????????????????????????");
                }
            }

            ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, roomJid, chatMessage, true);
        }
    }

    private void chatGroup(String body, ChatMessage chatMessage, Friend friend) {
        int type = chatMessage.getType();
        String fromUserId = chatMessage.getFromUserId();
        String fromUserName = chatMessage.getFromUserName();
        String toUserId = chatMessage.getToUserId();
        JSONObject jsonObject = JSONObject.parseObject(body);
        String toUserName = jsonObject.getString("toUserName");

        if (!TextUtils.isEmpty(toUserId)) {
            if (toUserId.equals(mLoginUserId)) {// ?????????????????????????????????fromUserName??????
                String xF = getName(friend, fromUserId);
                if (!TextUtils.isEmpty(xF)) {
                    fromUserName = xF;
                }
            } else {// ???????????????????????????fromUserName???toUserName???????????????
                String xF = getName(friend, fromUserId);
                if (!TextUtils.isEmpty(xF)) {
                    fromUserName = xF;
                }
                String xT = getName(friend, toUserId);
                if (!TextUtils.isEmpty(xT)) {
                    toUserName = xT;
                }
            }
        }
        chatMessage.setGroup(true);
        chatMessage.setType(XmppMessage.TYPE_TIP);

        /*
        ?????????
         */
        if (type == XmppMessage.TYPE_MUCFILE_DEL || type == XmppMessage.TYPE_MUCFILE_ADD) {
            String str;
            if (type == XmppMessage.TYPE_MUCFILE_DEL) {
                // str = chatMessage.getFromUserName() + " ?????????????????? " + chatMessage.getFilePath();
                str = fromUserName + " " + getString("JXMessage_fileDelete") + ":" + chatMessage.getFilePath();
            } else {
                // str = chatMessage.getFromUserName() + " ?????????????????? " + chatMessage.getFilePath();
                str = fromUserName + " " + getString("JXMessage_fileUpload") + ":" + chatMessage.getFilePath();
            }
            // ???????????????????????????????????????
            chatMessage.setContent(str);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
            return;
        }

        /*
        ?????????
         */
        if (type >= XmppMessage.TYPE_CHANGE_SHOW_READ && type <= XmppMessage.TYPE_GROUP_TRANSFER) {
            if (type == XmppMessage.TYPE_GROUP_VERIFY) {
                // 916??????????????????
                // ????????????????????????????????????????????????????????????????????? ???/??? ???????????????????????????????????????????????????
                // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                if (!TextUtils.isEmpty(chatMessage.getContent()) &&
                        (chatMessage.getContent().equals("0") || chatMessage.getContent().equals("1"))) {// ?????????
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.IS_NEED_OWNER_ALLOW_NORMAL_INVITE_FRIEND + chatMessage.getObjectId(), chatMessage.getContent().equals("1"));
                    if (chatMessage.getContent().equals("1")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_group_enable_verify));
                    } else {
                        chatMessage.setContent(mService.getString(R.string.tip_group_disable_verify));
                    }
                    // chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                    if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                        ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                    }
                } else {//  ???????????????????????? ????????????????????? ?????????????????????????????? ????????????
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(chatMessage.getObjectId());
                        String isInvite = json.getString("isInvite");
                        if (TextUtils.isEmpty(isInvite)) {
                            isInvite = "0";
                        }
                        if (isInvite.equals("0")) {
                            String id = json.getString("userIds");
                            String[] ids = id.split(",");
                            chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_invite_need_verify_place_holder, chatMessage.getFromUserName(), ids.length));
                        } else {
                            chatMessage.setContent(chatMessage.getFromUserName() + MyApplication.getContext().getString(R.string.tip_need_verify_place_holder));
                        }
                        String roomJid = json.getString("roomJid");
                        if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, roomJid, chatMessage)) {
                            ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, roomJid, chatMessage, true);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (type == XmppMessage.TYPE_CHANGE_SHOW_READ) {
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.IS_SHOW_READ + chatMessage.getObjectId(), chatMessage.getContent().equals("1"));
                    if (chatMessage.getContent().equals("1")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_read));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_read));
                    }
                } else if (type == XmppMessage.TYPE_GROUP_LOOK) {
                    if (chatMessage.getContent().equals("1")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_private));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_public));
                    }
                } else if (type == XmppMessage.TYPE_GROUP_SHOW_MEMBER) {
                    if (chatMessage.getContent().equals("1")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_member));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_member));
                    }
                } else if (type == XmppMessage.TYPE_GROUP_SEND_CARD) {
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.IS_SEND_CARD + chatMessage.getObjectId(), chatMessage.getContent().equals("1"));
                    if (chatMessage.getContent().equals("1")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_chat_privately));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_chat_privately));
                    }
                    MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
                } else if (type == XmppMessage.TYPE_GROUP_ALL_SHAT_UP) {
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.GROUP_ALL_SHUP_UP + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                    if (!chatMessage.getContent().equals("0")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_now_ban_all));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_now_disable_ban_all));
                    }
                    MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
                } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_INVITE) {
                    if (!chatMessage.getContent().equals("0")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_invite));
                        MsgBroadcast.broadcastMsgRoomUpdateInvite(MyApplication.getContext(), 1);
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_invite));
                        MsgBroadcast.broadcastMsgRoomUpdateInvite(MyApplication.getContext(), 0);
                    }
                } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_UPLOAD) {
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.IS_ALLOW_NORMAL_SEND_UPLOAD + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                    if (!chatMessage.getContent().equals("0")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_upload));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_upload));
                    }
                } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_CONFERENCE) {
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.IS_ALLOW_NORMAL_CONFERENCE + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                    if (!chatMessage.getContent().equals("0")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_meeting));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_meeting));
                    }
                } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_SEND_COURSE) {
                    PreferenceUtils.putBoolean(MyApplication.getContext(),
                            Constants.IS_ALLOW_NORMAL_SEND_COURSE + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                    if (!chatMessage.getContent().equals("0")) {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_cource));
                    } else {
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_cource));
                    }
                } else if (type == XmppMessage.TYPE_GROUP_TRANSFER) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_new_group_owner_place_holder, toUserName));
                    if (friend != null) {
                        FriendDao.getInstance().updateRoomCreateUserId(mLoginUserId,
                                chatMessage.getObjectId(), chatMessage.getToUserId());
                        RoomMemberDao.getInstance().updateRoomMemberRole(friend.getRoomId(), chatMessage.getToUserId(), 1);
                    }
                }
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                }
            }
            return;
        }

        /*
        ??????????????????
         */
        if (type == XmppMessage.TYPE_CHANGE_NICK_NAME) { // ??????????????????
            String content = chatMessage.getContent();
            if (!TextUtils.isEmpty(toUserId) && toUserId.equals(mLoginUserId)) {// ??????????????????
                if (!TextUtils.isEmpty(content)) {
                    friend.setRoomMyNickName(content);
                    FriendDao.getInstance().updateRoomMyNickName(friend.getUserId(), content);
                    ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), toUserId, content);
                    ChatMessageDao.getInstance().updateNickName(mLoginUserId, friend.getUserId(), toUserId, content);
                }
                // ??????????????????????????????????????????
                chatMessage.setContent(toUserName + " " + getString("JXMessageObject_UpdateNickName") + "???" + content + "???");
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
            } else {  // ????????????????????????????????????????????????
                chatMessage.setContent(toUserName + " " + getString("JXMessageObject_UpdateNickName") + "???" + content + "???");
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), toUserId, content);
                ChatMessageDao.getInstance().updateNickName(mLoginUserId, friend.getUserId(), toUserId, content);
            }
        } else if (type == XmppMessage.TYPE_CHANGE_ROOM_NAME) {
            // ?????????????????????????????????
            String content = chatMessage.getContent();
            FriendDao.getInstance().updateMucFriendRoomName(friend.getUserId(), content);
            ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), "ROOMNAMECHANGE", content);

            chatMessage.setContent(fromUserName + " " + getString("JXMessageObject_UpdateRoomName") + content);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.TYPE_DELETE_ROOM) {// ??????????????????
            if (fromUserId.equals(toUserId)) {
                // ????????????
                FriendDao.getInstance().deleteFriend(mLoginUserId, chatMessage.getObjectId());
                // ??????????????????
                ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, chatMessage.getObjectId());
                RoomMemberDao.getInstance().deleteRoomMemberTable(chatMessage.getObjectId());
                // ??????????????????
                MsgBroadcast.broadcastMsgNumReset(mService);
                MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                MucgroupUpdateUtil.broadcastUpdateUi(mService);
            } else {
                mService.exitMucChat(chatMessage.getObjectId());
                // 2 ????????????????????????  ???????????????
                FriendDao.getInstance().updateFriendGroupStatus(mLoginUserId, friend.getUserId(), 2);
                chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_disbanded));
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                }
            }
            ListenerManager.getInstance().notifyDeleteMucRoom(chatMessage.getObjectId());
        } else if (type == XmppMessage.TYPE_DELETE_MEMBER) {
            // ?????? ?????? || ??????
            if (toUserId.equals(mLoginUserId)) { // ????????????????????????
                // Todo ????????????????????????XChatListener???????????????????????????????????????????????????????????????????????????????????????????????????????????????
/*
                if (fromUserId.equals(toUserId)) {
                    // ?????????????????????
                    mService.exitMucChat(friend.getUserId());
                    // ??????????????????
                    FriendDao.getInstance().deleteFriend(mLoginUserId, friend.getUserId());
                    RoomMemberDao.getInstance().deleteRoomMemberTable(chatMessage.getObjectId());
                    // ??????????????????
                    ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, friend.getUserId());
                    // ??????????????????
                    MsgBroadcast.broadcastMsgNumReset(mService);
                    MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                    MucgroupUpdateUtil.broadcastUpdateUi(mService);
                } else {
                    // ???xx???????????????
                    mService.exitMucChat(friend.getUserId());
                    // / 1 ??????????????????????????? ???????????????
                    FriendDao.getInstance().updateFriendGroupStatus(mLoginUserId, friend.getUserId(), 1);
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_been_kick_place_holder, fromUserName));

                    ListenerManager.getInstance().notifyMyBeDelete(friend.getUserId());// ????????????????????????
                }
*/
            } else {
                // ??????????????? || ?????????
                if (fromUserId.equals(toUserId)) {
                    // message.setContent(toUserName + "???????????????");
                    chatMessage.setContent(toUserName + " " + getString("QUIT_GROUP"));
                } else {
                    // message.setContent(toUserName + "???????????????");
                    chatMessage.setContent(toUserName + " " + getString("KICKED_OUT_GROUP"));
                }
                // ??????RoomMemberDao?????????????????????
                operatingRoomMemberDao(1, friend.getRoomId(), toUserId, null);
                MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
            }

            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.TYPE_NEW_NOTICE) { // ????????????
            EventBus.getDefault().post(new EventNewNotice(chatMessage));
            String content = chatMessage.getContent();
            chatMessage.setContent(fromUserName + " " + getString("JXMessageObject_AddNewAdv") + content);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.TYPE_GAG) {// ??????
            long time = Long.parseLong(chatMessage.getContent());
            if (toUserId != null && toUserId.equals(mLoginUserId)) {
                // Todo ????????????????????????XChatListener???????????????????????????????????????????????????????????????????????????????????????????????????????????????
/*
                // ????????????|| ???????????? ??????RoomTalkTime??????
                FriendDao.getInstance().updateRoomTalkTime(mLoginUserId, friend.getUserId(), (int) time);
                ListenerManager.getInstance().notifyMyVoiceBanned(friend.getUserId(), (int) time);
*/
            }

            // ??????????????????????????????????????????3s?????????
            if (time > (System.currentTimeMillis() / 1000) + 3) {
                String formatTime = XfileUtils.fromatTime((time * 1000), "MM-dd HH:mm");
                // message.setContent("?????????" + toUserName + " ???????????????" + formatTime);
                chatMessage.setContent(fromUserName + " " + getString("JXMessageObject_Yes") + toUserName +
                        getString("JXMessageObject_SetGagWithTime") + formatTime);
            } else {
                // message.setContent("?????????" + toUserName + " ??????????????????");
                /*chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_Yes") + toUserName +
                        getString("JXMessageObject_CancelGag"));*/
                chatMessage.setContent(toUserName + MyApplication.getContext().getString(R.string.tip_been_cancel_ban_place_holder, fromUserName));
            }

            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.NEW_MEMBER) {
            String desc = "";
            if (chatMessage.getFromUserId().equals(toUserId)) {
                // ????????????
                desc = fromUserName + " " + getString("JXMessageObject_GroupChat");
            } else {
                // ???????????????
                desc = fromUserName + " " + getString("JXMessageObject_InterFriend") + toUserName;

                String roomId = jsonObject.getString("fileName");
                if (!toUserId.equals(mLoginUserId)) {// ????????????????????????????????????RoomMemberDao???????????????????????????????????????????????????????????????????????????????????????????????????????????????
                    operatingRoomMemberDao(0, roomId, chatMessage.getToUserId(), toUserName);
                }
            }

            // Todo ????????????????????????XChatListener???????????????????????????????????????????????????????????????????????????????????????????????????????????????
/*
            if (toUserId.equals(mLoginUserId)) {
                // ????????????????????????????????? ?????????????????????
                if (friend != null && friend.getGroupStatus() == 1) {// ???????????????????????????????????????????????? ??????????????????????????????(?????????updateGroupStatus??????????????????????????????????????????????????????????????????????????????)
                    FriendDao.getInstance().deleteFriend(mLoginUserId, friend.getUserId());
                    ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, friend.getUserId());
                }

                String roomId = "";
                // ????????????????????????????????????????????????
                try {
                    // ?????????????????????????????????????????????????????????????????????????????????
                    roomId = jsonObject.getString("fileName");
                    String other = jsonObject.getString("other");
                    JSONObject jsonObject2 = JSONObject.parseObject(other);
                    int showRead = jsonObject2.getInteger("showRead");
                    int allowSecretlyChat = jsonObject2.getInteger("allowSendCard");
                    MyApplication.getInstance().saveGroupPartStatus(chatMessage.getObjectId(), showRead, allowSecretlyChat,
                            1, 1, 0);
                } catch (Exception e) {
                    Log.e("msg", "?????????????????????");
                }

                Friend mCreateFriend = new Friend();
                mCreateFriend.setOwnerId(mLoginUserId);
                mCreateFriend.setUserId(chatMessage.getObjectId());
                mCreateFriend.setNickName(chatMessage.getContent());
                mCreateFriend.setDescription("");
                mCreateFriend.setRoomId(roomId);
                mCreateFriend.setContent(desc);
                mCreateFriend.setDoubleTimeSend(chatMessage.getTimeSend());
                mCreateFriend.setRoomFlag(1);
                mCreateFriend.setStatus(Friend.STATUS_FRIEND);
                mCreateFriend.setGroupStatus(0);
                FriendDao.getInstance().createOrUpdateFriend(mCreateFriend);
                // ??????smack?????????????????????
                // ????????????????????????lastSeconds == ???????????? - ?????????????????????
                mService.joinMucChat(chatMessage.getObjectId(), TimeUtils.sk_time_current_time() - chatMessage.getTimeSend());
            }
*/

            // ???????????????
            chatMessage.setContent(desc);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
            }
        } else if (type == XmppMessage.TYPE_SEND_MANAGER) {
            String content = chatMessage.getContent();
            int role;
            if (content.equals("1")) {
                role = 2;
                chatMessage.setContent(fromUserName + " " + InternationalizationHelper.getString("JXSettingVC_Set") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
            } else {
                role = 3;
                chatMessage.setContent(fromUserName + " " + InternationalizationHelper.getString("JXSip_Canceled") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
            }

            RoomMemberDao.getInstance().updateRoomMemberRole(friend.getRoomId(), toUserId, role);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                Intent intent = new Intent();
                intent.putExtra("roomId", friend.getUserId());
                intent.putExtra("toUserId", chatMessage.getToUserId());
                intent.putExtra("isSet", content.equals("1"));
                intent.setAction(com.ydd.zhichat.broadcast.OtherBroadcast.REFRESH_MANAGER);
                mService.sendBroadcast(intent);
            }
        }
    }

    private String getName(Friend friend, String userId) {
        if (friend == null) {
            return null;
        }
        RoomMember mRoomMember = RoomMemberDao.getInstance().getSingleRoomMember(friend.getRoomId(), mLoginUserId);
        if (mRoomMember != null && mRoomMember.getRole() == 1) {// ???????????? Name?????????????????????
            RoomMember member = RoomMemberDao.getInstance().getSingleRoomMember(friend.getRoomId(), userId);
            if (member != null && !TextUtils.equals(member.getUserName(), member.getCardName())) {
                // ???userName???cardName??????????????????????????????????????????????????????
                return member.getCardName();
            } else {
                Friend mFriend = FriendDao.getInstance().getFriend(mLoginUserId, userId);
                if (mFriend != null && !TextUtils.isEmpty(mFriend.getRemarkName())) {
                    return mFriend.getRemarkName();
                }
            }
        } else {// ????????? ????????????
            Friend mFriend = FriendDao.getInstance().getFriend(mLoginUserId, userId);
            if (mFriend != null && !TextUtils.isEmpty(mFriend.getRemarkName())) {
                return mFriend.getRemarkName();
            }
        }
        return null;
    }

    // ??????????????????
    private void operatingRoomMemberDao(int type, String roomId, String userId, String userName) {
        if (type == 0) {
            RoomMember roomMember = new RoomMember();
            roomMember.setRoomId(roomId);
            roomMember.setUserId(userId);
            roomMember.setUserName(userName);
            roomMember.setCardName(userName);
            roomMember.setRole(3);
            roomMember.setCreateTime(0);
            RoomMemberDao.getInstance().saveSingleRoomMember(roomId, roomMember);
        } else {
            RoomMemberDao.getInstance().deleteRoomMember(roomId, userId);
        }
    }
}
