/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import aces.webctrl.ftp.web.*;
import java.util.*;
import java.util.concurrent.locks.*;
public class Report {
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReentrantLock serialLock = new ReentrantLock();
  private volatile String ID;
  private volatile String folder;
  private volatile byte[] idBytes = null;
  private volatile byte[] folderBytes = null;
  protected volatile ArrayList<ReportFile> files = new ArrayList<ReportFile>();
  public Report(String ID, String folder){
    this.ID = ID;
    this.folder = folder;
  }
  public String getID(){
    return ID;
  }
  public String getFolder(){
    return folder;
  }
  public void setID(String ID){
    lock.writeLock().lock();
    this.ID = ID;
    lock.writeLock().unlock();
  }
  public void setFolder(String folder){
    lock.writeLock().lock();
    this.folder = folder;
    lock.writeLock().unlock();
  }
  public int length(){
    serialLock.lock();
    lock.readLock().lock();
    idBytes = ID.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    folderBytes = folder.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    lock.readLock().unlock();
    return idBytes.length+folderBytes.length+8;
  }
  public boolean serialize(SerializationStream s){
    try{
      s.write(idBytes);
      s.write(folderBytes);
      return true;
    }catch(Throwable t){
      Initializer.logger.println(t);
      return false;
    }finally{
      idBytes = null;
      folderBytes = null;
      serialLock.unlock();
    }
  }
  public static Report deserialize(SerializationStream s){
    try{
      return new Report(s.readString(), s.readString());
    }catch(Throwable t){
      Initializer.logger.println(t);
      return null;
    }
  }
}