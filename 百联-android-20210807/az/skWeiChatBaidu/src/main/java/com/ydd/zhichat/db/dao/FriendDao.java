package com.ydd.zhichat.db.dao;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.Dao.CreateOrUpdateStatus;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import com.ydd.zhichat.MyApplication;
import com.ydd.zhichat.R;
import com.ydd.zhichat.bean.AttentionUser;
import com.ydd.zhichat.bean.Friend;
import com.ydd.zhichat.bean.User;
import com.ydd.zhichat.bean.message.ChatMessage;
import com.ydd.zhichat.bean.message.MucRoom;
import com.ydd.zhichat.bean.message.MucRoomMember;
import com.ydd.zhichat.bean.message.NewFriendMessage;
import com.ydd.zhichat.bean.message.XmppMessage;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.db.SQLiteHelper;
import com.ydd.zhichat.db.SQLiteRawUtil;
import com.ydd.zhichat.sp.TableVersionSp;
import com.ydd.zhichat.ui.base.CoreManager;
import com.ydd.zhichat.util.Constants;
import com.ydd.zhichat.util.PreferenceUtils;
import com.ydd.zhichat.util.TanX;
import com.ydd.zhichat.util.TimeUtils;
import com.ydd.zhichat.xmpp.listener.ChatMessageListener;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ydd.zhichat.bean.message.XmppMessage.TYPE_CHAT_HISTORY;
import static com.ydd.zhichat.bean.message.XmppMessage.TYPE_IMAGE_TEXT;
import static com.ydd.zhichat.bean.message.XmppMessage.TYPE_IMAGE_TEXT_MANY;


/**
 * 访问朋友数据的Dao
 */
public class FriendDao {
    private static FriendDao instance = null;
    public Dao<Friend, Integer> friendDao;
    private SQLiteHelper mHelper;

    private FriendDao() {
        try {
            mHelper = OpenHelperManager.getHelper(MyApplication.getInstance(), SQLiteHelper.class);
            friendDao = DaoManager.createDao(mHelper.getConnectionSource(), Friend.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static final FriendDao getInstance() {
        if (instance == null) {
            synchronized (FriendDao.class) {
                if (instance == null) {
                    instance = new FriendDao();
                }
            }
        }
        return instance;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        OpenHelperManager.releaseHelper();
    }

    /**
     * 生成两个系统号
     */
    public void checkSystemFriend(String ownerId) {
        try {
            Friend friend = getFriend(ownerId, Friend.ID_SYSTEM_MESSAGE);
            if (friend == null) {// 公众号
                friend = new Friend();
                friend.setOwnerId(ownerId);
                friend.setUserId(Friend.ID_SYSTEM_MESSAGE);
                friend.setNickName(MyApplication.getInstance().getString(R.string.system_public_number));
                friend.setRemarkName(MyApplication.getInstance().getString(R.string.system_public_number));
                friend.setStatus(Friend.STATUS_SYSTEM);
                friend.setContent(MyApplication.getInstance().getString(R.string.system_public_number_welcome));

                friendDao.create(friend);
                // 添加一条系统提示
/*
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setType(XmppMessage.TYPE_TIP);
                chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));// 随机产生一个PacketId
                chatMessage.setFromUserId(Friend.ID_SYSTEM_MESSAGE);
                chatMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
                // 为了使得初始生成的系统消息排在新朋友前面，所以在时间节点上延迟一点 1s
                chatMessage.setDoubleTimeSend(TimeUtils.sk_time_current_time() + 1);
                chatMessage.setContent(MyApplication.getInstance().getString(R.string.system_public_number_welcome));
                chatMessage.setMySend(false);// 表示不是自己发的
                // 往消息表里插入一条记录
                ChatMessageDao.getInstance().saveNewSingleChatMessage(ownerId, Friend.ID_SYSTEM_MESSAGE, chatMessage);
                // 往朋友表里面插入一条未读记录
                markUserMessageUnRead(ownerId, Friend.ID_SYSTEM_MESSAGE);
*/
            }

            friend = getFriend(ownerId, Friend.ID_NEW_FRIEND_MESSAGE);
            if (friend == null) {// 新的朋友
                friend = new Friend();
                friend.setOwnerId(ownerId);
                friend.setUserId(Friend.ID_NEW_FRIEND_MESSAGE);
                friend.setNickName(InternationalizationHelper.getString("JXNewFriendVC_NewFirend"));
                friend.setRemarkName(InternationalizationHelper.getString("JXNewFriendVC_NewFirend"));
                friend.setStatus(Friend.STATUS_SYSTEM);
                friendDao.create(friend);

                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setType(XmppMessage.TYPE_TIP);
                chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));// 随机产生一个PacketId
                chatMessage.setFromUserId(Friend.ID_NEW_FRIEND_MESSAGE);
                chatMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
                chatMessage.setDoubleTimeSend(TimeUtils.sk_time_current_time_double());
                chatMessage.setContent("");
                chatMessage.setMySend(false);// 表示不是自己发的
                // 更新消息记录
                updateLastChatMessage(ownerId, Friend.ID_NEW_FRIEND_MESSAGE, chatMessage);
            }

            friend = getFriend(ownerId, Friend.ID_SK_PAY);
            if (friend == null) {// 支付公众号，
                friend = new Friend();
                friend.setOwnerId(ownerId);
                friend.setUserId(Friend.ID_SK_PAY);
                friend.setNickName(MyApplication.getInstance().getString(R.string.sk_pay));
                friend.setRemarkName(MyApplication.getInstance().getString(R.string.sk_pay));
                friend.setStatus(Friend.STATUS_SYSTEM);
                friendDao.create(friend);
            }

            checkDevice(ownerId);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 我的设备
    public void checkDevice(String ownerId) {
        // TODO 我的设备
        if (!MyApplication.IS_SUPPORT_MULTI_LOGIN) {
            List<Friend> friendList = getDevice(ownerId);
            for (Friend f : friendList) {
                deleteFriend(ownerId, f.getUserId());
                ChatMessageDao.getInstance().deleteMessageTable(ownerId, f.getUserId());
            }
        } else {
            for (String s : MyApplication.machine) {
                Friend friend = getFriend(ownerId, s);
                if (friend == null) {
                    friend = new Friend();
                    friend.setOwnerId(ownerId);
                    friend.setUserId(s);
                    if (s.equals("ios")) {
                        friend.setNickName(MyApplication.getInstance().getString(R.string.my_iphone));
                        friend.setRemarkName(MyApplication.getInstance().getString(R.string.my_iphone));
                    } else if (s.equals("pc")) {
                        friend.setNickName(MyApplication.getInstance().getString(R.string.my_windows));
                        friend.setRemarkName(MyApplication.getInstance().getString(R.string.my_windows));
                    } else if (s.equals("mac")) {
                        friend.setNickName(MyApplication.getInstance().getString(R.string.my_mac));
                        friend.setRemarkName(MyApplication.getInstance().getString(R.string.my_mac));
                    } else {
                        friend.setNickName(MyApplication.getInstance().getString(R.string.my_web));
                        friend.setRemarkName(MyApplication.getInstance().getString(R.string.my_web));
                    }
                    friend.setIsDevice(1);// 标志该朋友为其它设备(userId本质为自己)
                    // friend.setStatus(Friend.STATUS_FRIEND);
                    friend.setStatus(Friend.STATUS_SYSTEM);// 将状态改为系统号，否则在更新朋友表的时候，因为服务器attentionList内 未存自己，在清除旧数据的时候会清除掉自己
                    try {
                        friendDao.create(friend);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 更新与某个好友的阅读状态为已读
     */
    public void markUserMessageRead(String ownerId, String friendId) {
        TanX.Log("markUserMessageRead----" + friendId + "设置为已读");
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("unReadNum", 0);
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新某个好友的阅读状态，+1条未读信息
     */
    public boolean markUserMessageUnRead(String ownerId, String friendId) {
        Log.e("markUserMessageUnRead", "+1条未读消息");
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId).and().eq("userId", friendId)
                    .prepare();
            List<Friend> friendsList = friendDao.query(preparedQuery);
            if (friendsList != null && friendsList.size() > 0) {
                // Todo 之前发现群组收到消息但角标一直不更新，调试发现本地存在两个一模一样的Friend，而下面只是取出第一个Friend来更新，但消息界面显示的是第二个Friend(两个Friend可能由频繁切换账号引起)
                Friend friend = friendsList.get(0);
                int unReadCount = friend.getUnReadNum();
                friend.setUnReadNum(++unReadCount);
                friendDao.update(friend);
                // Todo 调用获取群组的接口，更新本地群组
                if (friendsList.size() > 1) {
                    return true;
                }

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean addNewFriendInMsgTable(String loginUserId, String friendId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setType(XmppMessage.TYPE_TIP);
        chatMessage.setFromUserId(friendId);
        chatMessage.setContent(InternationalizationHelper.getString("JXMsgViewController_StartChat"));
        chatMessage.setMySend(false);// 表示不是自己发的
        chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));// 随机产生一个PacketId
        chatMessage.setDoubleTimeSend(TimeUtils.sk_time_current_time_double());
        chatMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
        // 往消息表里插入一条记录
        ChatMessageDao.getInstance().saveNewSingleChatMessage(loginUserId, friendId, chatMessage);
        // 往朋友表里面插入一条未读记录
        markUserMessageUnRead(loginUserId, friendId);
        return true;
    }

    /* 获取消息模块未读数量总和 */
    public int getMsgUnReadNumTotal(String ownerId) {
        try {
            Where<Friend, Integer> builder = friendDao.queryBuilder()
                    .selectRaw("ifnull(sum(unReadNum), 0)")
                    // 过滤条件参照MessageFragment页面加载数据的方法，com.client.yanchat.fragment.MessageFragment.loadDatas
                    .where().eq("ownerId", ownerId)
                    .and().in("offlineNoPushMsg",0)//免打扰的不加
                    .and().in("status", Friend.STATUS_FRIEND, Friend.STATUS_SYSTEM, Friend.STATUS_UNKNOW)
                    .and().ne("userId", Friend.ID_NEW_FRIEND_MESSAGE)
                    .and().ne("userId", ownerId)
                    .and().isNotNull("content");
            return Integer.valueOf(builder.queryRawFirst()[0]);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 查询所有最近聊天的好友 全部
     */
    public List<Friend> getNearlyFriendMsg(String ownerId) {
        List<Friend> friends = new ArrayList<>();
        try {
            // 过滤条件，content不为空，status == 0 ||status ==2 || status==8（陌生人||好友||系统号）
            QueryBuilder<Friend, Integer> builder = friendDao.queryBuilder();
            /*
            TODO: 过滤不应该放在查询后，但是这个方法用在多个地方，过滤条件不一样，应该改成多个查询方法，
                    .where().eq("ownerId", ownerId)
                    .and().in("status", Friend.STATUS_FRIEND, Friend.STATUS_SYSTEM, Friend.STATUS_UNKNOW)
                    .and().ne("userId", Friend.ID_NEW_FRIEND_MESSAGE)
                    .and().ne("userId", ownerId)
                    .and().isNotNull("content");
             */
            builder.where()
                    .eq("status", Friend.STATUS_UNKNOW).or()
                    .eq("status", Friend.STATUS_FRIEND).or()
                    .eq("status", Friend.STATUS_SYSTEM).and()
                    .eq("ownerId", ownerId).and()
                    .isNotNull("content");
            builder.orderBy("topTime", false);
            builder.orderBy("timeSend", false);
            friends = builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Map<String, Friend> mFriendMap = new LinkedHashMap<>();
        if (friends != null && friends.size() > 0) {// 偶然发现该方法有时会查询出多条重复数据，去重
            for (int i = 0; i < friends.size(); i++) {
                mFriendMap.put(friends.get(i).getUserId(), friends.get(i));
            }
            Collection<Friend> values = mFriendMap.values();
            friends = new ArrayList<>(values);
        }

        // 置顶的Friend也根据timeSend排序
        if (friends != null) {
            mFriendMap.clear();
            for (int i = 0; i < friends.size(); i++) {
                if (friends.get(i).getTopTime() != 0) {
                    mFriendMap.put(friends.get(i).getUserId(), friends.get(i));
                }
            }
            Collection<Friend> values = mFriendMap.values();
            List<Friend> topFriends = new ArrayList<>(values);
            Comparator<Friend> comparator = (o1, o2) -> (int) (o1.getTimeSend() - o2.getTimeSend());
            Collections.sort(topFriends, comparator);

            for (int i = 0; i < topFriends.size(); i++) {
                friends.remove(topFriends.get(i));
                friends.add(0, topFriends.get(i));
            }
        }

        return friends;
    }

    /**
     * 获取备注名
     */
    public String getRemarkName(String ownerId, String userId) {
        QueryBuilder<Friend, Integer> builder = friendDao.queryBuilder();
        builder.selectRaw("remarkName");
        try {
            builder.where().eq("ownerId", ownerId).and().eq("userId", userId);
            GenericRawResults<String[]> results = friendDao.queryRaw(builder.prepareStatementString());
            if (results != null) {
                String[] first = results.getFirstResult();
                if (first != null && first.length > 0) {
                    return first[0];
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void resetFriendMessage(String loginUserId, String userId) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("unReadNum", 0);
            builder.updateColumnValue("content", null);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteFriend(String ownerId, String friendId) {
        try {
            DeleteBuilder<Friend, Integer> builder = friendDao.deleteBuilder();
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.delete(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建群组
     */
    public boolean createOrUpdateFriend(Friend friend) {
        try {
            CreateOrUpdateStatus status = friendDao.createOrUpdate(friend);
            return status.isCreated() || status.isUpdated();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 创建好友
     */
    public boolean createOrUpdateFriendByNewFriend(NewFriendMessage newFriend, int friendStatus) {
        try {
            Friend existFriend = getFriend(newFriend.getOwnerId(), newFriend.getUserId());
            if (existFriend == null) {
                existFriend = new Friend();
                existFriend.setOwnerId(newFriend.getOwnerId());
                existFriend.setUserId(newFriend.getUserId());
                existFriend.setNickName(newFriend.getNickName());
                existFriend.setTimeCreate(TimeUtils.sk_time_current_time());
                existFriend.setCompanyId(newFriend.getCompanyId());
                existFriend.setVersion(TableVersionSp.getInstance(MyApplication.getInstance()).getFriendTableVersion(newFriend.getOwnerId()));
            }
            existFriend.setStatus(friendStatus);
            CreateOrUpdateStatus status = friendDao.createOrUpdate(existFriend);
            return status.isCreated() || status.isUpdated();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 将陌生人加入朋友表
     */
    public void createNewFriend(ChatMessage chatMessage) {
        Friend friend = new Friend();
        friend.setOwnerId(CoreManager.requireSelf(MyApplication.getInstance()).getUserId());
        friend.setUserId(chatMessage.getFromUserId());
        friend.setNickName(chatMessage.getFromUserName());
        friend.setRemarkName(chatMessage.getFromUserName());
        friend.setTimeCreate(TimeUtils.sk_time_current_time());
        friend.setContent(chatMessage.getContent());
        friend.setCompanyId(0);// 公司
        friend.setTimeSend(chatMessage.getTimeSend());
        friend.setRoomFlag(0);// 0朋友 1群组
        friend.setStatus(Friend.STATUS_UNKNOW);
        friend.setVersion(TableVersionSp.getInstance(MyApplication.getInstance()).getFriendTableVersion(CoreManager.requireSelf(MyApplication.getInstance()).getUserId()));// 更新版本
        try {
            friendDao.create(friend);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Friend> getDevice(String ownerId) {
        List<Friend> query = new ArrayList<>();
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId)
                    .and().eq("isDevice", 1)
                    .prepare();

            query = friendDao.query(preparedQuery);
            return query;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return query;
    }

    public List<Friend> getAllFriends(String ownerId) {
        List<Friend> query = new ArrayList<>();
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId)
                    // .and().in("status", new Object[]{Friend.STATUS_FRIEND, Friend.STATUS_SYSTEM})
                    .and().in("status", Friend.STATUS_FRIEND)// 仅限我的好友
                    .and().eq("isDevice", 0)// 移除我的设备
                    .and().eq("roomFlag", 0)// 移除房间
                    .and().eq("companyId", 0)
                    .prepare();

            query = friendDao.query(preparedQuery);
            return query;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return query;
    }

    /**
     * 获取好友用于建群，
     * <p>
     * 支持群聊的除了好友还有公众号，
     * 但是要排除系统号10000,
     * 还要新的朋友系统号10001,
     * 还要支付系统号1100,
     */
    public List<Friend> getFriendsGroupChat(String ownerId) throws SQLException {
        PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                .eq("ownerId", ownerId)
                .and().in("status", new Object[]{Friend.STATUS_FRIEND, Friend.STATUS_SYSTEM})
                .and().not().eq("userId", Friend.ID_SYSTEM_MESSAGE)
                .and().not().eq("userId", Friend.ID_NEW_FRIEND_MESSAGE)
                .and().not().eq("userId", Friend.ID_SK_PAY)
                .and().eq("isDevice", 0)// 移除我的设备
                .and().eq("roomFlag", 0)// 移除房间
                .and().eq("companyId", 0)
                .prepare();

        return friendDao.query(preparedQuery);
    }

    /**
     * 查询好友的数量，
     * 仅限好友，
     */
    public long getFriendsCount(String ownerId) throws SQLException {
        return friendDao.queryBuilder().where()
                .eq("ownerId", ownerId)
                // 仅限好友，
                .and().eq("status", Friend.STATUS_FRIEND)
                // 排除群组，
                .and().eq("roomFlag", 0)
                .countOf();
    }

    /**
     * 查询好友的数量，
     * 仅限好友，
     */
    public long getGroupsCount(String ownerId) throws SQLException {
        return friendDao.queryBuilder().where()
                .eq("ownerId", ownerId)
                // 仅限好友，
                .and().eq("status", Friend.STATUS_FRIEND)
                // 仅限群组，
                .and().ne("roomFlag", 0)
                .countOf();
    }

    public List<Friend> getAllSystems(String ownerId) {
        List<Friend> query = new ArrayList<>();
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId)
                    // .and().in("status", new Object[]{Friend.STATUS_FRIEND, Friend.STATUS_SYSTEM})
                    .and().in("status", Friend.STATUS_SYSTEM)// 仅限公众号
                    .and().eq("isDevice", 0)// 移除我的设备
                    .and().eq("roomFlag", 0)// 移除房间
                    .and().eq("companyId", 0)
                    .and().ne("userId", Friend.ID_NEW_FRIEND_MESSAGE)
                    .prepare();

            query = friendDao.query(preparedQuery);
            return query;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return query;
    }

    public List<Friend> getAllRooms(String ownerId) {
        List<Friend> query = new ArrayList<>();
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId).and()
                    .eq("groupStatus", 0).and()
                    .in("roomFlag", 1, 510)
                    .prepare();
            query = friendDao.query(preparedQuery);
            return query;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return query;
    }

    public List<Friend> getAllBlacklists(String ownerId) {
        List<Friend> query = new ArrayList<>();
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where().eq("ownerId", ownerId).and()
                    .eq("status", Friend.STATUS_BLACKLIST).and()
                    .eq("roomFlag", 0)
                    .prepare();
            query = friendDao.query(preparedQuery);
            return query;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return query;
    }

    /**
     * 获取单个好友 陌生人 || 好友 || 公众号 || 群组
     */
    public Friend getFriend(String ownerId, String friendId) {
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId).and().eq("userId", friendId)
                    .prepare();
            return friendDao.queryForFirst(preparedQuery);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取单个好友 仅限好友
     */
    public Friend getFriendAndFriendStatus(String ownerId, String friendId) {
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId).and().eq("userId", friendId).and()
                    .eq("status", Friend.STATUS_FRIEND)
                    .prepare();
            Friend existFriend = friendDao.queryForFirst(preparedQuery);
            return existFriend;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取单个好友 仅限公众号
     */
    public Friend getFriendAndSystemStatus(String ownerId, String friendId) {
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where()
                    .eq("ownerId", ownerId).and().eq("userId", friendId).and()
                    .eq("status", Friend.STATUS_SYSTEM)
                    .prepare();
            Friend existFriend = friendDao.queryForFirst(preparedQuery);
            return existFriend;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 通过roomId获取群组Friend
     */
    public Friend getMucFriendByRoomId(String ownerId, String roomId) {
        try {
            PreparedQuery<Friend> preparedQuery = friendDao.queryBuilder().where().eq("ownerId", ownerId).and().eq("roomId", roomId).prepare();
            return friendDao.queryForFirst(preparedQuery);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 更新好友的状态
    public void updateFriendStatus(String loginUserId, String userId, int status) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("status", status);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新昵称
    public void updateNickName(String loginUserId, String userId, String nickName) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("nickName", nickName);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新备注名
    public void updateRemarkName(String loginUserId, String userId, String remarkName) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("remarkName", remarkName);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新描述
    public void updateDescribe(String loginUserId, String userId, String remarkName) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("describe", remarkName);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新备注名与描述
    public void updateRemarkNameAndDescribe(String loginUserId, String userId, String remarkName,
                                            String describe) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("remarkName", remarkName);
            builder.updateColumnValue("describe", describe);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新群组昵称
    public void updateMucFriendRoomName(String roomId, String roomName) {
        try {
            UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
            builder.updateColumnValue("nickName", roomName).where().eq("userId", roomId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新群内昵称
    public void updateRoomMyNickName(String roomId, String roomMyNickName) {
        try {
            UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
            builder.updateColumnValue("roomMyNickName", roomMyNickName).where().eq("userId", roomId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新群组状态
    public void updateFriendGroupStatus(String loginUserId, String userId, int groupStatus) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("groupStatus", groupStatus);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新群内昵称
    public void updateRoomName(String ownerId, String friendId, String myNickName) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            builder.updateColumnValue("roomMyNickName", myNickName);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新群创建者id
    public void updateRoomCreateUserId(String ownerId, String friendId, String roomCreateUserId) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            builder.updateColumnValue("roomCreateUserId", roomCreateUserId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新禁言时间
    public void updateRoomTalkTime(String ownerId, String friendId, int roomTalkTime) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            builder.updateColumnValue("roomTalkTime", roomTalkTime);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 针对某个好友的部分设置统一更新
    public void updateFriendPartStatus(String friendId, User user) {
        FriendDao.getInstance().updateOfflineNoPushMsgStatus(friendId,
                user.getFriends().getOfflineNoPushMsg());
        if (user.getFriends().getOpenTopChatTime() > 0) {
            FriendDao.getInstance().updateTopFriend(friendId, user.getFriends().getOpenTopChatTime());
        } else {
            FriendDao.getInstance().resetTopFriend(friendId);
        }
        PreferenceUtils.putInt(MyApplication.getContext(), Constants.MESSAGE_READ_FIRE + friendId + CoreManager.requireSelf(MyApplication.getContext()).getUserId(),
                user.getFriends().getIsOpenSnapchat());
        FriendDao.getInstance().updateChatRecordTimeOut(friendId,
                user.getFriends().getChatRecordTimeOut());
    }

    // 更新为置顶
    public void updateTopFriend(String friendId, long time) {
        if (time == 0) {
            time = TimeUtils.sk_time_current_time();
        }
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            String ownerId = CoreManager.requireSelf(MyApplication.getInstance()).getUserId();
            builder.updateColumnValue("topTime", time);
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 取消置顶
    public void resetTopFriend(String friendId) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            String ownerId = CoreManager.requireSelf(MyApplication.getInstance()).getUserId();
            builder.updateColumnValue("topTime", 0);
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新消息免打扰状态
    public void updateOfflineNoPushMsgStatus(String friendId, int offlineNoPushMsg) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            String ownerId = CoreManager.requireSelf(MyApplication.getInstance()).getUserId();
            builder.updateColumnValue("offlineNoPushMsg", offlineNoPushMsg);
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新消息保存天数
    public void updateChatRecordTimeOut(String friendId, double chatRecordTimeOut) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            String ownerId = CoreManager.requireSelf(MyApplication.getInstance()).getUserId();
            builder.updateColumnValue("chatRecordTimeOut", chatRecordTimeOut);
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新@我的状态
     */
    public void updateAtMeStatus(String friendId, int status) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            String ownerId = CoreManager.requireSelf(MyApplication.getContext()).getUserId();
            builder.updateColumnValue("isAtMe", status);
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 单聊 synchronizeChatHistory 调用成功后，将downloadTime 与 timeSend保持一致
     *
     * @param loginUserId
     * @param userId
     * @param time
     */
    public void updateDownloadTime(String loginUserId, String userId, long time) {
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("downloadTime", time);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新content字段
     */
    public void updateFriendContent(String loginUserId, String userId, String text, int type, long time) {
        if (type == XmppMessage.TYPE_IMAGE) {
            text = "[" + InternationalizationHelper.getString("JX_Image") + "]";
        } else if (type == XmppMessage.TYPE_CARD) {
            text = "[" + InternationalizationHelper.getString("JX_Card") + "]";
        } else if (type == XmppMessage.TYPE_VOICE) {
            text = "[" + InternationalizationHelper.getString("JX_Voice") + "]";
        } else if (type == XmppMessage.TYPE_LOCATION) {
            text = "[" + InternationalizationHelper.getString("JX_Location") + "]";
        } else if (type == XmppMessage.TYPE_GIF) {
            text = "[" + InternationalizationHelper.getString("emojiVC_Anma") + "]";
        } else if (type == XmppMessage.TYPE_VIDEO) {
            text = "[" + InternationalizationHelper.getString("JX_Video") + "]";
        } else if (type == XmppMessage.TYPE_FILE) {
            text = "[" + InternationalizationHelper.getString("JX_File") + "]";
        } else if (type == XmppMessage.TYPE_RED) {
            text = "[" + InternationalizationHelper.getString("JX_RED") + "]";
        } else if (type == XmppMessage.TYPE_LINK || type == XmppMessage.TYPE_SHARE_LINK) {
            text = "[" + InternationalizationHelper.getString("JXLink") + "]";
        } else if (type == TYPE_IMAGE_TEXT || type == TYPE_IMAGE_TEXT_MANY) {
            text = "[" + InternationalizationHelper.getString("JXGraphic") + InternationalizationHelper.getString("JXMainViewController_Message") + "]";
        } else if (type == XmppMessage.TYPE_SHAKE) {
            text = MyApplication.getInstance().getString(R.string.msg_shake);
        } else if (type == XmppMessage.TYPE_CHAT_HISTORY) {
            text = MyApplication.getInstance().getString(R.string.msg_chat_history);
        }
        // 通话结束与通话取消的聊天记录在消息界面不做特殊处理
        else if (type == XmppMessage.TYPE_END_CONNECT_VOICE || type == XmppMessage.TYPE_END_CONNECT_VIDEO) {
            text = !TextUtils.isEmpty(text) ? text : MyApplication.getInstance().getString(R.string.msg_call_end);
        } else if (type == XmppMessage.TYPE_NO_CONNECT_VIDEO || type == XmppMessage.TYPE_NO_CONNECT_VOICE) {
            text = !TextUtils.isEmpty(text) ? text : MyApplication.getInstance().getString(R.string.msg_call_cancel);
        } else if (type == XmppMessage.TYPE_OK_MU_CONNECT_VOICE || type == XmppMessage.TYPE_EXIT_VOICE) {
            text = MyApplication.getInstance().getString(R.string.msg_voice_meeting);
        } else if (type == XmppMessage.TYPE_VIDEO_IN || type == XmppMessage.TYPE_VIDEO_OUT) {
            text = MyApplication.getInstance().getString(R.string.msg_video_meeting);
        } else if (type == XmppMessage.TYPE_TRANSFER) {
            text = MyApplication.getContext().getString(R.string.tip_transfer_money);
        } else if (type == XmppMessage.TYPE_TRANSFER_RECEIVE) {
            text = MyApplication.getContext().getString(R.string.tip_transfer_money) + MyApplication.getContext().getString(R.string.transfer_friend_sure_save);
        } else if (type == XmppMessage.TYPE_TRANSFER_BACK) {
            text = MyApplication.getContext().getString(R.string.transfer_back);
        } else if (type == XmppMessage.TYPE_PAYMENT_OUT || type == XmppMessage.TYPE_RECEIPT_OUT) {
            text = MyApplication.getContext().getString(R.string.payment_get_notify);
        } else if (type == XmppMessage.TYPE_PAYMENT_GET || type == XmppMessage.TYPE_RECEIPT_GET) {
            text = MyApplication.getContext().getString(R.string.receipt_get_notify);
        } else if (type == XmppMessage.TYPE_PAY_CERTIFICATE) {
            text = MyApplication.getContext().getString(R.string.pay_certificate);
        }
        Friend friend = FriendDao.getInstance().getFriend(loginUserId, userId);
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("type", type);
            builder.updateColumnValue("content", text);
            builder.updateColumnValue("timeSend", time);
            if (friend != null && friend.getTimeSend() == friend.getDownloadTime()) {
                builder.updateColumnValue("downloadTime", time);
            }
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Todo 以下两个updateLastChatMessage 貌似都和NewFriend 有关 不知为啥要这么写

    public void updateApartDownloadTime(String loginUserId, String userId, String text, int type, long time,
                                        int isRoom, String from, String fromUserName, String toUserName) {
        if (type == XmppMessage.TYPE_IMAGE) {
            text = "[" + InternationalizationHelper.getString("JX_Image") + "]";
        } else if (type == XmppMessage.TYPE_CARD) {
            text = "[" + InternationalizationHelper.getString("JX_Card") + "]";
        } else if (type == XmppMessage.TYPE_VOICE) {
            text = "[" + InternationalizationHelper.getString("JX_Voice") + "]";
        } else if (type == XmppMessage.TYPE_LOCATION) {
            text = "[" + InternationalizationHelper.getString("JX_Location") + "]";
        } else if (type == XmppMessage.TYPE_GIF) {
            text = "[" + InternationalizationHelper.getString("emojiVC_Anma") + "]";
        } else if (type == XmppMessage.TYPE_VIDEO) {
            text = "[" + InternationalizationHelper.getString("JX_Video") + "]";
        } else if (type == XmppMessage.TYPE_FILE) {
            text = "[" + InternationalizationHelper.getString("JX_File") + "]";
        } else if (type == XmppMessage.TYPE_RED) {
            text = "[" + InternationalizationHelper.getString("JX_RED") + "]";
        } else if (type == XmppMessage.TYPE_LINK || type == XmppMessage.TYPE_SHARE_LINK) {
            text = "[" + InternationalizationHelper.getString("JXLink") + "]";
        } else if (type == TYPE_IMAGE_TEXT || type == TYPE_IMAGE_TEXT_MANY) {
            text = "[" + InternationalizationHelper.getString("JXGraphic") + InternationalizationHelper.getString("JXMainViewController_Message") + "]";
        } else if (type == XmppMessage.TYPE_SHAKE) {
            text = MyApplication.getInstance().getString(R.string.msg_shake);
        } else if (type == XmppMessage.TYPE_CHAT_HISTORY) {
            text = MyApplication.getInstance().getString(R.string.msg_chat_history);
        }
        // 通话结束与通话取消的聊天记录在消息界面不做特殊处理
        else if (type == XmppMessage.TYPE_END_CONNECT_VOICE || type == XmppMessage.TYPE_END_CONNECT_VIDEO) {
            text = !TextUtils.isEmpty(text) ? text : MyApplication.getInstance().getString(R.string.msg_call_end);
        } else if (type == XmppMessage.TYPE_NO_CONNECT_VIDEO || type == XmppMessage.TYPE_NO_CONNECT_VOICE) {
            text = !TextUtils.isEmpty(text) ? text : MyApplication.getInstance().getString(R.string.msg_call_cancel);
        } else if (type == XmppMessage.TYPE_OK_MU_CONNECT_VOICE || type == XmppMessage.TYPE_EXIT_VOICE) {
            text = MyApplication.getInstance().getString(R.string.msg_voice_meeting);
        } else if (type == XmppMessage.TYPE_VIDEO_IN || type == XmppMessage.TYPE_VIDEO_OUT) {
            text = MyApplication.getInstance().getString(R.string.msg_video_meeting);
        } else if (type == XmppMessage.TYPE_TRANSFER) {
            text = MyApplication.getContext().getString(R.string.tip_transfer_money);
        } else if (type == XmppMessage.TYPE_TRANSFER_RECEIVE) {
            text = MyApplication.getContext().getString(R.string.tip_transfer_money) + MyApplication.getContext().getString(R.string.transfer_friend_sure_save);
        } else if (type == XmppMessage.TYPE_TRANSFER_BACK) {
            text = MyApplication.getContext().getString(R.string.transfer_back);
        } else if (type == XmppMessage.TYPE_PAYMENT_OUT || type == XmppMessage.TYPE_RECEIPT_OUT) {
            text = MyApplication.getContext().getString(R.string.payment_get_notify);
        } else if (type == XmppMessage.TYPE_PAYMENT_GET || type == XmppMessage.TYPE_RECEIPT_GET) {
            text = MyApplication.getContext().getString(R.string.receipt_get_notify);
        } else if (type == XmppMessage.TYPE_PAY_CERTIFICATE) {
            text = MyApplication.getContext().getString(R.string.pay_certificate);
        } else if (type == XmppMessage.TYPE_BACK || type == XmppMessage.TYPE_83
                || type == XmppMessage.TYPE_RED_BACK || type == XmppMessage.TYPE_TRANSFER_RECEIVE) {
            text = ChatMessageDao.getInstance().handlerGetLastSpecialMessage(isRoom, type, loginUserId, from, fromUserName, toUserName);
        } else if (type == XmppMessage.TYPE_SEND_MANAGER) {
            if (text.equals("1")) {
                text = (fromUserName + " " + InternationalizationHelper.getString("JXSettingVC_Set") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
            } else if (text.equals("0")) {
                text = (fromUserName + " " + InternationalizationHelper.getString("JXSip_Canceled") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
            } // 以防万一，1和0以外情况认为已经处理过了，
        }
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("type", type);
            builder.updateColumnValue("content", text);
            builder.updateColumnValue("timeSend", time);
            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新朋友表里面的最后一条未读信息
     */
    public void updateLastChatMessage(String ownerId, String friendId, ChatMessage message) {
        Context context = MyApplication.getInstance();
        String content = "";
        int type = message.getType();
        if (type == XmppMessage.TYPE_TEXT) {
            content = message.getContent();
        } else if (type == XmppMessage.TYPE_IMAGE) {
            content = "[" + InternationalizationHelper.getString("JX_Image") + "]";
        } else if (type == XmppMessage.TYPE_CARD) {
            content = "[" + InternationalizationHelper.getString("JX_Card") + "]";
        } else if (type == XmppMessage.TYPE_VOICE) {
            content = "[" + InternationalizationHelper.getString("JX_Voice") + "]";
        } else if (type == XmppMessage.TYPE_LOCATION) {
            content = "[" + InternationalizationHelper.getString("JX_Location") + "]";
        } else if (type == XmppMessage.TYPE_GIF) {
            content = "[" + InternationalizationHelper.getString("emojiVC_Anma") + "]";
        } else if (type == XmppMessage.TYPE_VIDEO) {
            content = "[" + InternationalizationHelper.getString("JX_Video") + "]";
        } else if (type == XmppMessage.TYPE_FILE) {
            content = "[" + InternationalizationHelper.getString("JX_File") + "]";
        } else if (type == XmppMessage.TYPE_RED) {
            content = "[" + InternationalizationHelper.getString("JX_RED") + "]";
        } else if (type == XmppMessage.TYPE_TIP) {
            //content = message.getContent();
        } else if (type == XmppMessage.TYPE_NEWSEE) {// 新关注提示
            if (!message.isMySend()) {
                content = InternationalizationHelper.getString("JXFriendObject_FollowYour");
            }
        } else if (type == XmppMessage.TYPE_SAYHELLO) {// 打招呼提示
            if (!message.isMySend()) {
                if (TextUtils.isEmpty(message.getContent())) {
                    content = context.getString(R.string.msg_be_say_hello);
                } else {
                    content = message.getContent();
                }
            }
        } else if (type == XmppMessage.TYPE_PASS) {// 验证通过提示
            if (!message.isMySend()) {
                content = InternationalizationHelper.getString("JXFriendObject_PassGo");
                NewFriendDao.getInstance().changeNewFriendState(message.getFromUserId(), Friend.STATUS_13);
            }
        } else if (type == XmppMessage.TYPE_FRIEND) { // 新朋友提示
            if (!message.isMySend()) {
                content = message.getFromUserName() + context.getString(R.string.add_me_as_friend);
            }
        } else if (type == XmppMessage.TYPE_FEEDBACK) {// 回话
            if (!message.isMySend()) {
                if (!TextUtils.isEmpty(message.getContent())) {
                    content = message.getContent();
                }
            }
        } else if (type == XmppMessage.TYPE_BLACK) {
            if (!message.isMySend()) {
                content = context.getString(R.string.be_pull_black_place_holder, message.getFromUserId());
            } else {
                content = context.getString(R.string.pull_black_place_holder, message.getFromUserId());
            }
        } else if (type == XmppMessage.TYPE_DELALL || type == XmppMessage.TYPE_BACK_DELETE) {
            if (!message.isMySend()) {
                content = context.getString(R.string.be_delete_place_holder, message.getFromUserId());
            } else {
                content = context.getString(R.string.delete_place_holder, message.getFromUserId());
            }
        } else if (type == XmppMessage.TYPE_RECOMMEND) {
            content = context.getString(R.string.msg_has_new_recommend_friend);
        } else if (type == XmppMessage.TYPE_LINK || type == XmppMessage.TYPE_SHARE_LINK) {
            content = "[" + InternationalizationHelper.getString("JXLink") + "]";
        } else if (type == TYPE_IMAGE_TEXT || type == TYPE_IMAGE_TEXT_MANY) {
            content = "[" + InternationalizationHelper.getString("JXGraphic") + InternationalizationHelper.getString("JXMainViewController_Message") + "]";
        } else if (type == TYPE_CHAT_HISTORY) {
            content = context.getString(R.string.msg_chat_history);
        } else {
            content = message.getContent();
        }

        if (TextUtils.isEmpty(content)) {
            content = "";
        }
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            builder.updateColumnValue("type", type);
            builder.updateColumnValue("content", content);
            builder.updateColumnValue("timeSend", message.getTimeSend());
            builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            friendDao.update(builder.prepare());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateLastChatMessage(String ownerId, String friendId, String content) {
        QueryBuilder<Friend, Integer> queryBuilder = friendDao.queryBuilder();
        UpdateBuilder<Friend, Integer> builder = friendDao.updateBuilder();
        try {
            queryBuilder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
            Friend friend = queryBuilder.queryForFirst();
            if (friend != null) {
                builder.updateColumnValue("type", 1);
                builder.updateColumnValue("content", content);
                builder.updateColumnValue("timeSend", TimeUtils.sk_time_current_time());
                builder.where().eq("ownerId", ownerId).and().eq("userId", friendId);
                friendDao.update(builder.prepare());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addAttentionUsers(final String loginUserId, final List<AttentionUser> attentionUsers,
                                  final OnCompleteListener2 listener) throws SQLException {
        new TransactionManager(friendDao.getConnectionSource()).callInTransaction(() -> {
            checkSystemFriend(loginUserId);
            int tableVersion = TableVersionSp.getInstance(MyApplication.getInstance()).getFriendTableVersion(loginUserId);
            int newVersion = tableVersion + 1;
            if (attentionUsers != null && attentionUsers.size() > 0) {
                for (int i = 0; i < attentionUsers.size(); i++) {
                    AttentionUser attentionUser = attentionUsers.get(i);
                    if (attentionUser == null) {
                        continue;
                    }
                    String userId = attentionUser.getToUserId();// 好友的Id
                    QueryBuilder<Friend, Integer> builder = friendDao.queryBuilder();
                    Friend friend = null;
                    try {
                        builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
                        friend = friendDao.queryForFirst(builder.prepare());
                    } catch (SQLException e1) {
                        e1.printStackTrace();
                    }
                    if (friend == null) {
                        friend = new Friend();
                    }
                    friend.setOwnerId(attentionUser.getUserId());
                    friend.setUserId(attentionUser.getToUserId());
                    if (!userId.equals(Friend.ID_SYSTEM_MESSAGE)) {
                        friend.setNickName(attentionUser.getToNickName());
                        friend.setRemarkName(attentionUser.getRemarkName());
                        friend.setTimeCreate(attentionUser.getCreateTime());
                        // 公众号的status为8，服务端返回的为2，不修改
                        int status = (attentionUser.getBlacklist() == 0) ? attentionUser.getStatus() : -1;
                        friend.setStatus(status);
                    }
                    if (attentionUser.getToUserType() == 2) {// 公众号
                        friend.setStatus(Friend.STATUS_SYSTEM);
                    }
                    if (!TextUtils.isEmpty(attentionUser.getDescribe())) {
                        friend.setDescribe(attentionUser.getDescribe());
                    }
                    // Todo 注意 IsBeenBlack==1表示 为对方拉黑了我，不能将其状态置为STATUS_BLACKLIST
                    // Todo 注意 blacklist==1才表示我将对方拉入黑名单，但是我将对方拉入黑名单之后，该接口就不在返回此人了，所以在通讯录的黑名单内需要单独调用获取黑名单列表的接口
                    if (attentionUser.getBlacklist() == 1) {
                        friend.setStatus(Friend.STATUS_BLACKLIST);
                    }
                    if (attentionUser.getIsBeenBlack() == 1) {
                        // friend.setStatus(Friend.STATUS_BLACKLIST);
                        friend.setStatus(Friend.STATUS_19);
                    }

                    friend.setOfflineNoPushMsg(attentionUser.getOfflineNoPushMsg());
                    friend.setTopTime(attentionUser.getOpenTopChatTime());
                    PreferenceUtils.putInt(MyApplication.getContext(), Constants.MESSAGE_READ_FIRE + attentionUser.getUserId() + CoreManager.requireSelf(MyApplication.getContext()).getUserId(),
                            attentionUser.getIsOpenSnapchat());
                    friend.setChatRecordTimeOut(attentionUser.getChatRecordTimeOut());// 消息保存天数 -1/0 永久

                    friend.setCompanyId(attentionUser.getCompanyId());
                    friend.setRoomFlag(0);
                    friend.setVersion(newVersion);// 更新版本
                    try {
                        friendDao.createOrUpdate(friend);
                        if (listener != null) {
                            listener.onLoading(i + 1, attentionUsers.size());
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
            // 本地Sp中保存的版本号更新（+1）
            TableVersionSp.getInstance(MyApplication.getInstance()).setFriendTableVersion(loginUserId, newVersion);
            // 更新完成，把过期的好友数据删除
            try {
                DeleteBuilder<Friend, Integer> builder = friendDao.deleteBuilder();

                builder.where()
                        .eq("ownerId", loginUserId).and().eq("roomFlag", 0).and()
                        .in("status", new Object[]{Friend.STATUS_FRIEND, Friend.STATUS_ATTENTION}).and()
                        .ne("version", newVersion);
                friendDao.delete(builder.prepare());
            } catch (SQLException e) {
                e.printStackTrace();
            }
            // 朋友数据更新了，在去删除不存在的消息表
            List<String> tables = SQLiteRawUtil.getUserChatMessageTables(mHelper.getReadableDatabase(), loginUserId);
            if (tables != null && tables.size() > 0) {
                for (int i = 0; i < tables.size(); i++) {
                    String tableName = tables.get(i);
                    String tablePrefix = SQLiteRawUtil.CHAT_MESSAGE_TABLE_PREFIX + loginUserId;
                    int index = tableName.indexOf(tablePrefix);
                    if (index == -1) {
                        continue;
                    }
                    String toUserId = tableName.substring(index + tablePrefix.length(), tableName.length());
                    if (toUserId.equals(Friend.ID_BLOG_MESSAGE) || toUserId.equals(Friend.ID_INTERVIEW_MESSAGE)
                            || toUserId.equals(Friend.ID_NEW_FRIEND_MESSAGE) || toUserId.equals(Friend.ID_SYSTEM_MESSAGE)) {
                        continue;
                    }
                    Friend friend = getFriend(loginUserId, toUserId);
                    if (friend == null) {// 删除这张消息表

                        if (SQLiteRawUtil.isTableExist(mHelper.getWritableDatabase(), tableName)) {
                            SQLiteRawUtil.dropTable(mHelper.getReadableDatabase(), tableName);
                        }

                    }
                }
            }

            if (listener != null) {
                listener.onCompleted();
            }

            // 这里没用到返回值，但是这个方法必须返回，
            return Void.class;
        });
    }

    /**
     * 用户数据更新，下载进入的房间
     */
    public void addRooms(final Handler handler, final String loginUserId, final List<MucRoom> rooms, final OnCompleteListener2 listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int tableVersion = TableVersionSp.getInstance(MyApplication.getInstance()).getFriendTableVersion(loginUserId);
                int newVersion = tableVersion + 1;
                if (rooms != null && rooms.size() > 0) {
                    for (int i = 0; i < rooms.size(); i++) {
                        MucRoom mucRoom = rooms.get(i);
                        if (mucRoom == null) {
                            continue;
                        }
                        String userId = mucRoom.getJid();// 群组的jid
                        MyApplication.getInstance().saveGroupPartStatus(userId, mucRoom.getShowRead(), mucRoom.getAllowSendCard(),
                                mucRoom.getAllowConference(), mucRoom.getAllowSpeakCourse(), mucRoom.getTalkTime());

                        QueryBuilder<Friend, Integer> builder = friendDao.queryBuilder();
                        Friend friend = null;
                        try {
                            builder.where().eq("ownerId", loginUserId).and().eq("userId", userId);
                            friend = friendDao.queryForFirst(builder.prepare());
                        } catch (SQLException e1) {
                            e1.printStackTrace();
                        }
                        if (friend == null) {
                            friend = new Friend();
                            friend.setOwnerId(loginUserId);
                            friend.setUserId(mucRoom.getJid());

                            friend.setTimeSend((int) mucRoom.getCreateTime());
                        }
                        friend.setNickName(mucRoom.getName());
                        friend.setDescription(mucRoom.getDesc());
                        friend.setRoomId(mucRoom.getId());
                        friend.setRoomCreateUserId(mucRoom.getUserId());
                        // Todo getMember可能为空，需要做非空判断，放到下面去
                        // friend.setOfflineNoPushMsg(mucRoom.getMember().getOfflineNoPushMsg());
                        friend.setChatRecordTimeOut(mucRoom.getChatRecordTimeOut());// 消息保存天数 -1/0 永久
                        if (mucRoom.getCategory() == 510 &&
                                mucRoom.getUserId().equals(CoreManager.requireSelf(MyApplication.getInstance()).getUserId())) {
                            friend.setRoomFlag(510);// 我的手机联系人群组
                        } else {
                            friend.setRoomFlag(1);
                        }
                        friend.setStatus(Friend.STATUS_FRIEND);
                        friend.setVersion(newVersion);// 更新版本
                        MucRoomMember memberMy = mucRoom.getMember();
                        if (memberMy != null) {
                            friend.setRoomMyNickName(memberMy.getNickName());
                            friend.setRoomTalkTime(memberMy.getTalkTime());
                            friend.setOfflineNoPushMsg(memberMy.getOfflineNoPushMsg());
                            friend.setTopTime(memberMy.getOpenTopChatTime());
                        }
                        try {
                            friendDao.createOrUpdate(friend);
                            if (listener != null) {
                                listener.onLoading(i + 1, rooms.size());
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // 本地Sp中保存的版本号更新（+1）
                TableVersionSp.getInstance(MyApplication.getInstance()).setFriendTableVersion(loginUserId, newVersion);
                // 更新完成，把过期的房间数据删除
                try {
                    DeleteBuilder<Friend, Integer> builder = friendDao.deleteBuilder();
                    builder.where().eq("ownerId", loginUserId).and().eq("roomFlag", 1).and().eq("status", Friend.STATUS_FRIEND).and()
                            .ne("version", newVersion);
                    friendDao.delete(builder.prepare());
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // 朋友数据更新了，在去删除不存在的消息表
                List<String> tables = SQLiteRawUtil.getUserChatMessageTables(mHelper.getReadableDatabase(), loginUserId);
                if (tables != null && tables.size() > 0) {
                    for (int i = 0; i < tables.size(); i++) {
                        String tableName = tables.get(i);
                        String tablePrefix = SQLiteRawUtil.CHAT_MESSAGE_TABLE_PREFIX + loginUserId;
                        int index = tableName.indexOf(tablePrefix);
                        if (index == -1) {
                            continue;
                        }
                        String toUserId = tableName.substring(index + tablePrefix.length(), tableName.length());
                        if (toUserId.equals(Friend.ID_BLOG_MESSAGE) || toUserId.equals(Friend.ID_INTERVIEW_MESSAGE)
                                || toUserId.equals(Friend.ID_NEW_FRIEND_MESSAGE) || toUserId.equals(Friend.ID_SYSTEM_MESSAGE)) {
                            continue;
                        }
                        Friend friend = getFriend(loginUserId, toUserId);
                        if (friend == null) {// 删除这张消息表
                            if (SQLiteRawUtil.isTableExist(mHelper.getWritableDatabase(), tableName)) {
                                SQLiteRawUtil.dropTable(mHelper.getReadableDatabase(), tableName);
                            }
                        }
                    }
                }

                if (handler != null && listener != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onCompleted();
                        }
                    });
                }
            }
        }
        ).start();
    }

    public List<Friend> getChatFriendList(String ownerId) throws SQLException {
        return friendDao.queryBuilder()
                .orderBy("topTime", false)
                .orderBy("timeSend", false)
                .where().eq("ownerId", ownerId)
                .and().eq("status", Friend.STATUS_FRIEND)
                .and().isNotNull("content")
                .query();
    }
}