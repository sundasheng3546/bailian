package com.ydd.zhichat.call;


import com.ydd.zhichat.bean.message.ChatMessage;

/**
 * Created by Administrator on 2017/6/26 0026.
 */
public class MessageHangUpPhone {
    public final ChatMessage chatMessage;

    public MessageHangUpPhone(ChatMessage message) {
        this.chatMessage = message;
    }
}