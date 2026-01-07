package com.tss.portwatch.core.collector;

public interface ListenerCollector {
    String collectTcpListenersJson() throws Exception;
}
