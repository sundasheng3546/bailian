package com.ydd.zhichat.bean.redpacket;

public class EventRedReceived {
    private OpenRedpacket openRedpacket;

    public EventRedReceived(OpenRedpacket openRedpacket) {
        this.openRedpacket = openRedpacket;
    }

    public OpenRedpacket getOpenRedpacket() {
        return openRedpacket;
    }

    public void setOpenRedpacket(OpenRedpacket openRedpacket) {
        this.openRedpacket = openRedpacket;
    }
}
