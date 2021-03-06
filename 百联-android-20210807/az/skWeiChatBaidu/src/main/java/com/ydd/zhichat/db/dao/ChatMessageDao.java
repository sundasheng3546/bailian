package com.ydd.zhichat.db.dao;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.j256.ormlite.android.DatabaseTableConfigUtil;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.ydd.zhichat.AppConfig;
import com.ydd.zhichat.MyApplication;
import com.ydd.zhichat.R;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.MsgRoamTask;
import com.ydd.zhichat.bean.message.ChatMessage;
import com.ydd.zhichat.bean.message.XmppMessage;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.SQLiteHelper;
import com.ydd.zhichat.db.SQLiteRawUtil;
import com.ydd.zhichat.db.UnlimitDaoManager;
import com.ydd.zhichat.ui.base.CoreManager;
import com.ydd.zhichat.ui.mucfile.XfileUtils;
import com.ydd.zhichat.util.AsyncUtils;
import com.ydd.zhichat.util.DES;
import com.ydd.zhichat.util.Md5Util;
import com.ydd.zhichat.util.TimeUtils;
import com.ydd.zhichat.util.log.LogUtils;
import com.ydd.zhichat.xmpp.listener.ChatMessageListener;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ydd.zhichat.db.InternationalizationHelper.getString;

public class ChatMessageDao {
    private static ChatMessageDao instance = null;
    private SQLiteHelper mHelper;
    private Map<String, Dao<ChatMessage, Integer>> mDaoMap;

    private ChatMessageDao() {
        mHelper = OpenHelperManager.getHelper(MyApplication.getInstance(), SQLiteHelper.class);
        mDaoMap = new HashMap<String, Dao<ChatMessage, Integer>>();
    }

    public static ChatMessageDao getInstance() {
        if (instance == null) {
            synchronized (ChatMessageDao.class) {
                if (instance == null) {
                    instance = new ChatMessageDao();
                }
            }
        }
        return instance;
    }

    /**
     * ?????????????????????????????????????????????????????????
     */
    public static int fillReCount(int type) {
        int recount = 0;
        if (type < 100) {// ????????????
            recount = 5;
        }
        return recount;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        OpenHelperManager.releaseHelper();
    }

    private Dao<ChatMessage, Integer> getDao(String ownerId, String friendId) {
        if (TextUtils.isEmpty(ownerId) || TextUtils.isEmpty(friendId)) {
            return null;
        }
        String tableName = SQLiteRawUtil.CHAT_MESSAGE_TABLE_PREFIX + ownerId + friendId;
        if (mDaoMap.containsKey(tableName)) {
            return mDaoMap.get(tableName);
        }
        Dao<ChatMessage, Integer> dao = null;
        try {
            DatabaseTableConfig<ChatMessage> config = DatabaseTableConfigUtil.fromClass(mHelper.getConnectionSource(), ChatMessage.class);
            config.setTableName(tableName);
            SQLiteRawUtil.createTableIfNotExist(mHelper.getWritableDatabase(), tableName, SQLiteRawUtil.getCreateChatMessageTableSql(tableName));
            dao = UnlimitDaoManager.createDao(mHelper.getConnectionSource(), config);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (dao != null)
            mDaoMap.put(tableName, dao);
        return dao;
    }

    /**
     * ??????????????????????????????
     */
    public boolean saveNewSingleChatMessage(String ownerId, String friendId, ChatMessage message) {
        Log.e("ormlite", "???????????????");
        if (XfileUtils.isNotChatVisibility(message.getType())) {
            Log.e("ormlite", "isNotChatVisibility");
            return false;
        }
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            Log.e("ormlite", "dao == nul");
            return false;
        }
        try {
            // ??????????????????
            List<ChatMessage> chatMessages = dao.queryForEq("packetId", message.getPacketId());
            Log.e("ormlite", message.getPacketId());
            if (chatMessages != null && chatMessages.size() > 0) {
                Log.e("ormlite", "????????????");
                return false;// ????????????
            }

            // ???????????????????????????????????????????????????????????????fromUserName
            // ???????????????????????????Content??????
            if (message.getType() != XmppMessage.TYPE_READ
                    && message.getType() != XmppMessage.TYPE_TIP
                    && message.isGroup()) {
                String groupNameForGroupOwner = RoomMemberDao.getInstance().getRoomRemarkName(friendId, message.getFromUserId());
                if (!TextUtils.isEmpty(groupNameForGroupOwner)) {
                    message.setFromUserName(groupNameForGroupOwner);
                } else {
                    Friend friend = FriendDao.getInstance().getFriend(CoreManager.requireSelf(MyApplication.getContext()).getUserId(), message.getFromUserId());
                    if (friend != null && !TextUtils.isEmpty(friend.getRemarkName())) {
                        message.setFromUserName(friend.getRemarkName());
                    }
                }
            }

            // ??????????????????
            dao.create(message);

            Log.e("ormlite", "????????????");
            Log.e("ormlite", "???????????????????????????");
            // ???????????????????????????????????????
            if (message.getType() != XmppMessage.TYPE_READ) {// ?????????????????????
                if (message.isGroup()) {// ??????
                    if (message.getType() == XmppMessage.TYPE_TIP || TextUtils.isEmpty(message.getFromUserName())) {// ?????????????????? || FromUserName??????
                        FriendDao.getInstance().updateFriendContent(ownerId, friendId, message.getContent(), message.getType(), message.getTimeSend());
                    } else {
                        FriendDao.getInstance().updateFriendContent(ownerId, friendId, message.getFromUserName() + " : " + message.getContent(), message.getType(), message.getTimeSend());
                    }
                } else {
                    String str;
                    if (message.getType() == XmppMessage.TYPE_TEXT && message.getIsReadDel()) {
                        str = MyApplication.getContext().getString(R.string.tip_click_to_read);
                    } else {
                        str = message.getContent();
                    }
                    FriendDao.getInstance().updateFriendContent(ownerId, friendId, str, message.getType(), message.getTimeSend());
                }
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            // Todo ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            // Todo ????????????????????????????????????SQLException ?????????????????????????????????????????????????????????dao????????????????????????????????????
            // Todo ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            String tableName = SQLiteRawUtil.CHAT_MESSAGE_TABLE_PREFIX + ownerId + friendId;
            if (!SQLiteRawUtil.isTableExist(mHelper.getWritableDatabase(), tableName)) {
                Log.e("ormlite", tableName + "????????????????????????");
                SQLiteRawUtil.createTableIfNotExist(mHelper.getWritableDatabase(), tableName, SQLiteRawUtil.getCreateChatMessageTableSql(tableName));
                saveNewSingleChatMessage(ownerId, friendId, message);// ???????????????????????????????????????
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean saveRoamingChatMessage(String ownerId, String friendId, ChatMessage message) {
        decryptDES(message);
        handlerRoamingSpecialMessage(message);

        Log.e("ormlite", "???????????????");
        if (XfileUtils.isNotChatVisibility(message.getType())) {
            Log.e("ormlite", "isNotChatVisibility");
            return false;
        }
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            Log.e("ormlite", "dao == nul");
            return false;
        }
        try {
            // ??????????????????
            List<ChatMessage> chatMessages = dao.queryForEq("packetId", message.getPacketId());
            Log.e("ormlite", message.getPacketId());
            if (chatMessages != null && chatMessages.size() > 0) {
                Log.e("ormlite", "????????????");
                return false;
            }
            // ?????????????????????
            dao.create(message);

            Log.e("ormlite", "????????????");
            Log.e("ormlite", "???????????????????????????");
            // ????????????content ???????????????content
            /*if (message.getType() != XmppMessage.TYPE_READ) {// ?????????????????????
                if (message.isGroup()) {// ??????
                    if (message.getType() == XmppMessage.TYPE_TIP || TextUtils.isEmpty(message.getFromUserName())) {// ?????????????????? || FromUserName??????
                        FriendDao.getInstance().updateFriendContent(ownerId, friendId, message.getContent(), message.getType(), message.getTimeSend());
                    } else {
                        FriendDao.getInstance().updateFriendContent(ownerId, friendId, message.getFromUserName() + " : " + message.getContent(), message.getType(), message.getTimeSend());
                    }
                } else {
                    String str;
                    if (message.getType() == XmppMessage.TYPE_TEXT && message.getIsReadDel() == 1) {
                        str = "???????????? T";
                    } else {
                        str = message.getContent();
                    }
                    FriendDao.getInstance().updateFriendContent(ownerId, friendId, str, message.getType(), message.getTimeSend());
                }
            }*/
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ?????? ???????????? ??????
     */
    public boolean saveNewSingleAnswerMessage(String ownerId, String friendId, ChatMessage message) {
        if (XfileUtils.isNotChatVisibility(message.getType())) {
            return false;
        }

        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return false;
        }
        try {
            // ??????????????????
            List<ChatMessage> chatMessages = dao.queryForEq("packetId", message.getPacketId());
            if (chatMessages != null && chatMessages.size() > 0) {
                return false;// ????????????
            }
            FriendDao.getInstance().updateFriendContent(ownerId, friendId,
                    message.getContent(), message.getType(), message.getTimeSend());
            // ?????????????????????
            dao.create(message);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ??????????????????
     */
    public boolean deleteSingleChatMessage(String ownerId, String friendId, ChatMessage message) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return false;
        }
        try {
            List<ChatMessage> chatMessages = dao.queryForEq("packetId", message.getPacketId());
            if (chatMessages != null && chatMessages.size() > 0) {
                dao.delete(chatMessages);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteSingleChatMessage(String ownerId, String friendId, String packet) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return false;
        }
        try {
            List<ChatMessage> chatMessages = dao.queryForEq("packetId", packet);
            if (chatMessages != null && chatMessages.size() > 0) {
                dao.delete(chatMessages);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ??????????????????
    public boolean deleteOutTimeChatMessage(String ownerId, String friendId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return false;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages;
        try {
            builder.where().ne("deleteTime", -1)
                    .and().ne("deleteTime", 0)
                    .and().lt("deleteTime", TimeUtils.sk_time_current_time());// deleteTime????????? -1 || 0???-1???0????????????????????????deleteTime?????????????????? ????????????
            messages = dao.query(builder.prepare());
            Log.e("deleteTime", TimeUtils.sk_time_current_time() + "");
            if (messages != null && messages.size() > 0) {
                Log.e("deleteTime", messages.size() + "");
                dao.delete(messages);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void updateExpiredStatus(String ownerId, String friendId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages;
        try {
            builder.where().ne("isExpired", 1)
                    .and().ne("deleteTime", -1)
                    .and().ne("deleteTime", 0)
                    .and().lt("deleteTime", TimeUtils.sk_time_current_time());// deleteTime????????? -1 || 0???-1???0????????????????????????deleteTime?????????????????? ????????????
            messages = dao.query(builder.prepare());
            Log.e("deleteTime", TimeUtils.sk_time_current_time() + "");
            if (messages != null && messages.size() > 0) {
                Log.e("deleteTime", messages.size() + "");
                Object[] msgIds = new Object[messages.size()];
                for (int i = 0; i < messages.size(); i++) {
                    msgIds[i] = messages.get(i).getPacketId();
                }
                UpdateBuilder<ChatMessage, Integer> builder2 = dao.updateBuilder();
                builder2.updateColumnValue("isExpired", 1);
                builder2.where().in("packetId", msgIds);
                dao.update(builder2.prepare());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return;
    }

    /**
     * ??????????????????
     *
     * @param ownerId
     * @param friendId
     * @param state
     */
    public void updateMessageRead(String ownerId, String friendId, String packetId, boolean state) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            Log.e("xuan", "??????????????????:" + packetId);
            return;
        }

        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("sendRead", state);
            if (state) { // ?????????????????????????????? ?????????????????????
                builder.updateColumnValue("messageState", ChatMessageListener.MESSAGE_SEND_SUCCESS);
            }
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            Log.e("xuan", "??????????????????:" + packetId);
            e.printStackTrace();
        }
    }

    public void updateMessageRead(String ownerId, String friendId, ChatMessage chat) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);

        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            // builder.updateColumnValue("sendRead", true);
            builder.updateColumnValue("readPersons", chat.getReadPersons());
            builder.updateColumnValue("readTime", chat.getReadTime());
            builder.where().eq("packetId", chat.getPacketId());

            dao.update(builder.prepare());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????????????????
     */
    public boolean updateReadMessage(String ownerId, String friendId, String packetId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return false;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            ChatMessage message = findMsgById(ownerId, friendId, packetId);
            if (message != null && message.getIsReadDel()) {
                builder.updateColumnValue("content", MyApplication.getInstance().getString(R.string.tip_burn_message));
                builder.updateColumnValue("type", XmppMessage.TYPE_TIP);
                builder.where().eq("packetId", packetId);
                dao.update(builder.prepare());
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    /**
     * ?????????????????????????????????????????????
     */
    public void updateMessageReadTime(String ownerId, String friendId, String packetId, long time) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }

        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("readTime", time);
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????
     */
    public void updateMessageShakeState(String ownerId, String friendId, String packetId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("isDownload", true);
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ???????????????????????????
     */
    public void updateMessageBack(String ownerId, String friendId, String packetId, String name) {
        // ??????message?????????
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            ChatMessage message = findMsgById(ownerId, friendId, packetId);
            if (message != null) {
                builder.updateColumnValue("content", name + " " + InternationalizationHelper.getString("JX_OtherWithdraw"));
                builder.updateColumnValue("type", XmppMessage.TYPE_TIP);
                builder.where().eq("packetId", packetId);
                dao.update(builder.prepare());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
     * ??????????????????????????????
     * */
    public boolean updateChatMessageReceiptStatus(String ownerId, String friendId, String packetId) {
        try {
            Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
            UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
            ChatMessage message = findMsgById(ownerId, friendId, packetId);
            if (message != null) {
                builder.updateColumnValue("fileSize", 2);
                builder.where().eq("packetId", packetId);
                dao.update(builder.prepare());
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ????????????????????????OK
     */
    public void updateMessageSendState(String ownerId, String friendId, int msg_id, int messageState) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            Log.e("msg", "updateMessageSendState Failed");
            return;
        }

        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("messageState", messageState);
            builder.updateColumnValue("timeReceive", TimeUtils.sk_time_current_time());
            builder.where().idEq(msg_id);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            Log.e("msg", "updateMessageSendState SQLException");
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????OK
     */
    public void updateMessageUploadSchedule(String ownerId, String friendId, int msg_id, int uploadSchedule) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("uploadSchedule", uploadSchedule);
            builder.where().idEq(msg_id);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????OK
     */
    public void updateMessageUploadState(String ownerId, String friendId, int msg_id, boolean isUpload, String url) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("isUpload", isUpload);
            builder.updateColumnValue("content", url);
            builder.where().idEq(msg_id);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????OK
     */
    public void updateMessageDownloadState(String ownerId, String friendId, int msg_id, boolean isDownload, String filePath) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("isDownload", isDownload);
            builder.updateColumnValue("filePath", filePath);
            builder.where().idEq(msg_id);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ?????? ?????????????????? ?????????(???????????????????????????isDownload??????????????????true ??????????????? false ?????????)
    public void updateGroupVerifyMessageStatus(String ownerId, String friendId, String packetId, boolean isDownload) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("isDownload", isDownload);
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateMessageContent(String ownerId, String friendId, String packetId, String content) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("content", content);
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * OK ???????????????????????????
     *
     * @param time     ?????????time
     * @param pageSize ??????????????????
     * @return
     */
    public List<ChatMessage> getSingleChatMessages(String ownerId, String friendId, long time, int pageSize) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return null;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages = new ArrayList<>();
        try {
            // builder.where().gt("_id", mMinId);
            builder.where().ne("type", XmppMessage.TYPE_READ).and().lt("timeSend", time);
            builder.orderBy("timeSend", false);
            builder.orderBy("_id", false);
            builder.limit((long) pageSize);
            builder.offset(0L);
            messages = dao.query(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    /**
     * @param ownerId
     * @param friendId
     * @param time     Search >= timeSend 's Messages
     * @return
     */
    public List<ChatMessage> searchMessagesByTime(String ownerId, String friendId, double time) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return null;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages = new ArrayList<>();
        try {
            // builder.where().gt("_id", mMinId);
            builder.where().ne("type", XmppMessage.TYPE_READ).and()
                    .ge("timeSend", time);
            builder.orderBy("timeSend", false);
            messages = dao.query(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public List<ChatMessage> getOneGroupChatMessages(String ownerId, String friendId, double time, int pageSize) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return null;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();

        // ???????????????????????????????????????
        MsgRoamTask mLastMsgRoamTask = MsgRoamTaskDao.getInstance().getFriendLastMsgRoamTask(ownerId, friendId);

        List<ChatMessage> messages = new ArrayList<>();
        try {
            if (mLastMsgRoamTask == null) {
                builder.where().ne("type", XmppMessage.TYPE_READ)
                        .and().ne("isExpired", 1)
                        .and().lt("timeSend", time);
                builder.orderBy("timeSend", false);
                builder.orderBy("_id", false);
                builder.limit((long) pageSize);
                builder.offset(0L);
            } else {
                builder.where().ne("type", XmppMessage.TYPE_READ)
                        .and().ne("isExpired", 1)
                        .and().ge("timeSend", mLastMsgRoamTask.getEndTime())
                        .and().lt("timeSend", time);
                builder.orderBy("timeSend", false);
                builder.orderBy("_id", false);
                builder.limit((long) pageSize);
                builder.offset(0L);
            }
            messages = dao.query(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    // ???????????? ??????????????? ???????????? startTime(???????????????????????????) ???????????? endTime
    public List<ChatMessage> getCourseChatMessage(String ownerId, String friendId, double startTime, double endTime, int pageSize) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return null;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages = new ArrayList<>();
        try {
            // builder.where().gt("_id", mMinId);
            builder.where().ne("type", XmppMessage.TYPE_READ)
                    .and().ge("timeSend", startTime)
                    .and().le("timeSend", endTime);
            builder.orderBy("timeSend", false);
            builder.orderBy("_id", false);
            builder.limit((long) pageSize);
            builder.offset(0L);
            messages = dao.query(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    // ??????????????????userId???????????????????????????
    public List<ChatMessage> getAllVerifyMessage(String ownerId, String friendId, String userId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return null;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages = null;
        try {
            builder.where().eq("type", XmppMessage.TYPE_TIP)
                    .and().eq("fromUserId", userId);
            messages = dao.query(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public void updateMessageState(String ownerId, String friendId, String packetId, int messageState) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("messageState", messageState);
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
            LogUtils.e("msg", "??????????????????????????????-->packetId???" + packetId + "???messageState" + messageState);
        } catch (SQLException e) {
            e.printStackTrace();
            LogUtils.e("msg", "??????????????????????????????-->packetId???" + packetId + "???messageState" + messageState);
        }
    }

    public void updateMessageLocationXY(String ownerId, String friendId, String packetId, String location_x, String location_y) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return;
        }
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.updateColumnValue("location_x", location_x);
            builder.updateColumnValue("location_y", location_y);
            builder.where().eq("packetId", packetId);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateMessageLocationXY(ChatMessage newmsg, String userid) {
        if (newmsg.isMySend()) {
            updateMessageLocationXY(userid, newmsg.getToUserId(), newmsg.getPacketId(), newmsg.getLocation_x(), newmsg.getLocation_y());
        } else {
            updateMessageLocationXY(userid, newmsg.getFromUserId(), newmsg.getPacketId(), newmsg.getLocation_x(), newmsg.getLocation_y());
        }
    }

    public boolean hasSameMessage(String ownerId, String friendId, String packetId) {
        boolean exist;
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return false;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        List<ChatMessage> messages = null;
        try {
            builder.where().eq("packetId", packetId);
            messages = dao.query(builder.prepare());
            if (messages != null && messages.size() > 0) {
                exist = true;
            } else {
                exist = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            exist = false;
        }
        return exist;
    }

    /**
     * ??????????????????
     * ???????????????????????????????????????????????????????????????????????????packetId?????????
     */
    public ChatMessage findMsgById(String ownerId, String friendId, String packetId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        if (dao == null) {
            return null;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        ChatMessage messages = null;
        try {
            if (!TextUtils.isEmpty(packetId)) {
                builder.where().eq("packetId", packetId);
            }
            messages = dao.queryForFirst(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    /**
     * ?????????????????????????????????
     */
    public void deleteMessageTable(String ownerId, String friendId) {
        String tableName = SQLiteRawUtil.CHAT_MESSAGE_TABLE_PREFIX + ownerId + friendId;
        if (mDaoMap.containsKey(tableName)) {
            mDaoMap.remove(tableName);
        }
        if (SQLiteRawUtil.isTableExist(mHelper.getWritableDatabase(), tableName)) {
            SQLiteRawUtil.dropTable(mHelper.getWritableDatabase(), tableName);
        }
    }

    public void updateNickName(String ownerId, String friendId, String fromUserId, String newNickName) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        UpdateBuilder<ChatMessage, Integer> builder = dao.updateBuilder();
        try {
            builder.where().eq("fromUserId", fromUserId);
            builder.updateColumnValue("fromUserName", newNickName);
            dao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ??????????????????????????????
    public ChatMessage getLastChatMessage(String ownerId, String friendId) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);

        ChatMessage chatMessage;
        if (dao == null) {
            return null;
        }

        try {
            QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
            builder.orderBy("timeSend", false);
            chatMessage = dao.queryForFirst(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return chatMessage;
    }

    /**
     * ??????????????????????????????
     *
     * @param content ??????????????????????????????packetid;
     * @return true ????????? false ?????????
     */
    public boolean checkRepeatRead(String ownerId, String friendId, String userId, String content) {
        boolean b = false;
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();

        List<ChatMessage> messages = null;
        try {
            builder.where().eq("type", XmppMessage.TYPE_READ)
                    .and().eq("content", content)
                    .and().eq("fromUserId", userId);
            messages = builder.query();
            if (messages != null && messages.size() > 0) {
                b = true;
            } else {
                b = false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return b;
    }

    /**
     * ???????????????????????????????????????
     *
     * @param loginUserId
     * @param roomId
     * @param packetId
     */
    public List<ChatMessage> queryFriendsByReadList(String loginUserId, String roomId, String packetId, int pager) {
        Dao<ChatMessage, Integer> dao = getDao(loginUserId, roomId);
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        try {
            builder.where().eq("type", XmppMessage.TYPE_READ).and().eq("content", packetId);
            builder.orderBy("timeSend", false);
            long k = (pager + 1) * 10;
            builder.limit(k);
            List<ChatMessage> list = builder.query();
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ??????????????????????????????????????????
     * ?????????????????????????????????????????????
     */
    public List<Friend> queryChatMessageByContent(Friend friend, String content) {
        String loginUserId = CoreManager.requireSelf(MyApplication.getInstance()).getUserId();

        Dao<ChatMessage, Integer> dao = getDao(loginUserId, friend.getUserId());
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();

        try {
            builder.where().eq("type", "1").and().like("content", "%" + content + "%");
            builder.orderBy("timeSend", true);

            List<ChatMessage> query = builder.query();
            if (query == null) {
                return null;
            }

            List<Friend> friends = new ArrayList<>();
            for (int i = 0; i < query.size(); i++) {
                ChatMessage chatMessage = query.get(i);
                Friend temp = new Friend();
                temp.setUserId(friend.getUserId());
                // ???????????????????????????
                temp.setRoomId(friend.getRoomId());
                temp.setNickName(friend.getNickName());
                temp.setRoomFlag(friend.getRoomFlag());
                temp.setContent(chatMessage.getContent());
                temp.setTimeSend(chatMessage.getTimeSend());
                // Todo 2019.2.18  ?????????double?????????????????????????????????????????????????????????ChatRecordTimeOut?????????
                temp.setChatRecordTimeOut(chatMessage.getDoubleTimeSend());
                friends.add(temp);
            }
            return friends;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ????????????Type??????????????? ????????????????????????
    public List<ChatMessage> queryChatMessageByType(String ownerId, String friendId, int type) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        List<ChatMessage> messages = new ArrayList<>();
        if (dao == null) {
            return messages;
        }
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        try {
            builder.where().eq("type", type).and().ne("isReadDel", 1);
            messages = dao.query(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    // ???????????????????????????
    public List<ChatMessage> queryChatMessageByContent(String ownerId, String friendId, String content) {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        QueryBuilder<ChatMessage, Integer> builder = dao.queryBuilder();
        try {
            builder.where().eq("type", "1").and().like("content", "%" + content + "%");
            builder.orderBy("timeSend", true);
            return builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 1.?????????????????????????????????????????????
     * 2.???????????????????????????
     *
     * @param chatMessage
     */
    public void decryptDES(ChatMessage chatMessage) {
        int isEncrypt = chatMessage.getIsEncrypt();
        if (isEncrypt == 1) {
            try {
                String decryptKey = Md5Util.toMD5(AppConfig.apiKey + chatMessage.getTimeSend() + chatMessage.getPacketId());
                String decryptContent = DES.decryptDES(chatMessage.getContent(), decryptKey);
                // ???chatMessage????????????
                chatMessage.setContent(decryptContent);
                chatMessage.setIsEncrypt(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ????????????tigase/getLastChatList ????????????????????????
     */
    public String handlerGetLastSpecialMessage(int isRoom, int type, String loginUserId, String from, String fromUserName, String toUserName) {
        String text = "";
        if (type == XmppMessage.TYPE_BACK) {
            if (TextUtils.equals(from, loginUserId)) {
                text = MyApplication.getContext().getString(R.string.you) + " " + getString("JX_OtherWithdraw");
            } else {
                text = fromUserName + " " + getString("JX_OtherWithdraw");
            }
        } else if (type == XmppMessage.TYPE_83) {
            // ??????????????????????????????
            if (TextUtils.equals(from, loginUserId)) {
                // ??????????????????????????? ???????????????????????????????????????????????????????????????????????????????????????????????????
                text = MyApplication.getContext().getString(R.string.red_received_self, toUserName);
            } else {
                // ???????????????????????????
                text = MyApplication.getContext().getString(R.string.tip_receive_red_packet_place_holder, fromUserName, MyApplication.getContext().getString(R.string.you));
            }
        } else if (type == XmppMessage.TYPE_RED_BACK) {
            text = MyApplication.getContext().getString(R.string.tip_red_back);
        } else if (type == XmppMessage.TYPE_TRANSFER_RECEIVE) {
            if (TextUtils.equals(from, loginUserId)) {
                // ??????????????????????????? ???????????????????????????????????????????????????????????????????????????????????????????????????
                text = MyApplication.getContext().getString(R.string.transfer_received_self);
            } else {
                // ???????????????????????????
                text = MyApplication.getContext().getString(R.string.transfer_received);
            }
        }
        return text;
    }

    /**
     * ??????tigase/shiku_msgs ????????????????????????
     *
     * @param chatMessage
     * @return
     */
    public void handlerRoamingSpecialMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == XmppMessage.TYPE_83) {
            // ???????????????
        } else if (chatMessage.getType() == XmppMessage.TYPE_RED_BACK) {
            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_red_back));
        } else if (chatMessage.getType() == XmppMessage.TYPE_TRANSFER_RECEIVE) {
            chatMessage.setType(XmppMessage.TYPE_TIP);
            if (TextUtils.equals(chatMessage.getFromUserId(), CoreManager.requireSelf(MyApplication.getInstance()).getUserId())) {
                // ??????????????????????????? ???????????????????????????????????????????????????????????????????????????????????????????????????
                chatMessage.setContent(MyApplication.getContext().getString(R.string.transfer_received_self));
            } else {
                // ???????????????????????????
                chatMessage.setContent(MyApplication.getContext().getString(R.string.transfer_received));
            }
        }
    }

    // ????????????????????????????????????
    // ???????????????????????????null,
    @Nullable
    public List<ChatMessage> searchFromMessage(Context ctx, String ownerId, String friendId, ChatMessage fromMessage) throws Exception {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        Objects.requireNonNull(dao);
        ChatMessage localFromMessage = dao.queryBuilder().where().ne("type", XmppMessage.TYPE_READ)
                .and().ne("isExpired", 1)
                .and().eq("packetId", fromMessage.getPacketId())
                .queryForFirst();
        if (localFromMessage == null) {
            return null;
        }
        return dao.queryBuilder()
                .orderBy("timeSend", true)
                .orderBy("_id", true)
                .where().ne("type", XmppMessage.TYPE_READ)
                .and().ne("isExpired", 1)
                // ?????????????????????????????????localFromMessage,
                .and().ge("timeSend", localFromMessage.getTimeSend())
                .query();
    }

    /**
     * @return ??????true?????????????????????????????????????????????false?????????????????????
     */
    public boolean roamingMessageFilter(int type) {
        return type < 100
                // ??????????????????????????????????????????
                && type != XmppMessage.TYPE_83;
    }

    /**
     * ???????????????????????????
     * ???????????????????????????????????????????????????????????????iterable?????????list,
     */
    public void exportChatHistory(
            String ownerId, String friendId,
            AsyncUtils.Function<Iterator<ChatMessage>> callback
    ) throws Exception {
        Dao<ChatMessage, Integer> dao = getDao(ownerId, friendId);
        Objects.requireNonNull(dao);
        CloseableIterator<ChatMessage> results = dao.iterator(dao.queryBuilder()
                .where().ne("type", XmppMessage.TYPE_READ)
                .and().ne("isExpired", 1)
                .and().le("deleteTime", 0).or().gt("deleteTime", TimeUtils.sk_time_current_time())
                .prepare());
        callback.apply(results);
        results.close();
    }
}
