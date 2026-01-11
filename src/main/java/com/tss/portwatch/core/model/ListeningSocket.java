package com.tss.portwatch.core.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListeningSocket {
    public String LocalAddress;
    public Integer LocalPort;
    public Integer ProcessId;
    public String ProcessName;
    public String Path;

    // Jackson necesita constructor vacío
    public ListeningSocket() {
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListeningSocket that = (ListeningSocket) o;
        return Objects.equals(LocalPort, that.LocalPort)
                && Objects.equals(ProcessId, that.ProcessId)
                && Objects.equals(LocalAddress, that.LocalAddress)
                && Objects.equals(ProcessName, that.ProcessName)
                && Objects.equals(Path, that.Path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(LocalAddress, LocalPort, ProcessId, ProcessName, Path);
    }

}
