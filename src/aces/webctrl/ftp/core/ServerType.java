/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
public enum ServerType {
  UNKNOWN,
  SFTP,
  FTPS,
  FTP;
  public final static ServerType[] values = ServerType.values();
  public static ServerType fromOrdinal(int i){
    return i>=0 && i<values.length ? values[i] : UNKNOWN;
  }
}