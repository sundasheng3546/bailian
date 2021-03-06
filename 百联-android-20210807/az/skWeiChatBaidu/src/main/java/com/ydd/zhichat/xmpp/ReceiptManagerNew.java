package com.ydd.zhichat.xmpp;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.ydd.zhichat.xmpp.util.XmppStringUtil;

import org.jivesoftware.smack.iqrequest.IQRequestHandler;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class ReceiptManagerNew {
    private static final String TAG = "ReceiptManagerNew";
    private final MyIqProvider myIqProvider;
    private final EnableIQRequestHandler enableIQRequestHandler;
    private CoreService mService;
    private String mLoginUserId;
    private XMPPTCPConnection mConnection;
    private Set<String> messageQueue = new HashSet<>();
    private Thread sendThread;
    private boolean stop = false;
    private boolean enable = false;
    private Jid from;
    private Jid to;

    public ReceiptManagerNew(CoreService coreService, XMPPTCPConnection connection) {
        mService = coreService;
        mLoginUserId = XmppStringUtil.parseName(connection.getUser().toString());

        this.mConnection = connection;
        from = JidCreate.entityFullFrom(
                Localpart.fromOrThrowUnchecked(mLoginUserId),
                connection.getXMPPServiceDomain(),
                connection.getConfiguration().getResource()
        );
        to = connection.getXMPPServiceDomain();
        myIqProvider = new MyIqProvider();
        ProviderManager.addIQProvider(Enable.ELEMENT, Enable.NAMESPACE, myIqProvider);
        enableIQRequestHandler = new EnableIQRequestHandler();
        connection.registerIQRequestHandler(enableIQRequestHandler);
        sendEnable();
    }

    public void release() {
        stop = true;
        mConnection.unregisterIQRequestHandler(enableIQRequestHandler);
        ProviderManager.removeIQProvider(Enable.ELEMENT, Enable.NAMESPACE);
        if (sendThread != null) {
            sendThread.interrupt();
            sendThread = null;
        }
        messageQueue.clear();
    }

    public void sendReceipt(@NonNull String messageId) {
        if (enable) {
            messageQueue.add(messageId);
        } else {
            Log.w(TAG, "IQ????????????????????????????????????, " + messageId);
        }
    }

    private void sendEnable() {
        Log.d(TAG, "sendEnable() called");
        Enable enable = new Enable();
        try {
            mConnection.sendStanza(enable);
        } catch (Exception e) {
            Log.e(TAG, "send enable failed", e);
        }
    }

    private class SendThread extends Thread {
        private long flushTime;

        @Override
        public void run() {
            try {
                while (!stop) {
                    // ???????????????????????????5??????????????????????????????????????????????????????100???????????????
                    if (!messageQueue.isEmpty()) {
                        if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - flushTime) > 5
                                || messageQueue.size() > 100)
                            flush();
                    }
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception e) {
                Log.e(TAG, "?????????????????????", e);
            }
        }

        private void flush() {
            flushTime = System.currentTimeMillis();
            // ???????????????????????????????????????????????????????????????????????????
            sendReceipt(new ArrayList<>(messageQueue));
            messageQueue.clear();
        }

        private void sendReceipt(List<String> messageIdList) {
            Log.d(TAG, "sendReceipt() called with: messageIdList = [" + messageIdList + "]");
            Receipt receipt = new Receipt(messageIdList);
            try {
                mConnection.sendStanza(receipt);
            } catch (Exception e) {
                Log.e(TAG, "send failed", e);
            }
        }
    }

    private class Enable extends IQ {
        private static final String ELEMENT = "enable";
        private static final String NAMESPACE = "xmpp:shiku:ack";

        private Enable() {
            super(ELEMENT, NAMESPACE);
            setFrom(from);
            setTo(to);
            setType(Type.set);
        }

        @Override
        protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
            xml.rightAngleBracket().optAppend("enable");
            return xml;
        }
    }

    private class Receipt extends IQ {
        private static final String ELEMENT = "body";
        private static final String NAMESPACE = "xmpp:shiku:ack";
        private List<String> messageIdList;

        public Receipt(List<String> messageIdList) {
            super(ELEMENT, NAMESPACE);
            this.messageIdList = messageIdList;
            setFrom(from);
            setTo(to);
            setType(Type.set);
        }

        @Override
        protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
            xml.rightAngleBracket().optAppend(TextUtils.join(",", messageIdList));
            return xml;
        }
    }

    private class MyIqProvider extends IQProvider<Enable> {
        @Override
        public Enable parse(XmlPullParser parser, int initialDepth) throws Exception {
            Log.d(TAG, "parse() called with: parser = [" + parser + "], initialDepth = [" + initialDepth + "]");
            return new Enable();
        }
    }

    private class EnableIQRequestHandler implements IQRequestHandler {
        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            Log.d(TAG, "handleIQRequest() called with: iqRequest = [" + iqRequest + "]");
            enable = true;
            sendThread = new SendThread();
            sendThread.start();
            return null; // ???????????????enable???
        }

        @Override
        public Mode getMode() {
            return Mode.async;
        }

        @Override
        public IQ.Type getType() {
            return IQ.Type.set;
        }

        @Override
        public String getElement() {
            return Enable.ELEMENT;
        }

        @Override
        public String getNamespace() {
            return Enable.NAMESPACE;
        }
    }
}
