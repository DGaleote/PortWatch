package com.tss.portwatch.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListeningSocket {
    public String LocalAddress;
    public int LocalPort;
    public int ProcessId;
    public String ProcessName;
    public String Path;

    // Jackson necesita constructor vacío
    public ListeningSocket() {}
}
