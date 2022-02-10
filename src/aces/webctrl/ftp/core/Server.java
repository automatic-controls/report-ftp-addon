/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import aces.webctrl.ftp.web.*;
import java.util.*;
import java.util.concurrent.locks.*;
/**
 * Thread-safe class which represents an FTP server.
 */
public class Server {
  protected volatile int ID = -1;
  /** Controls access to this server's parameters. */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  /** Specifies the IP address or DNS for this server. */
  private volatile String host = "";
  /** Specifies the port to use for connections to this server. */
  private volatile int port = 0;
  /** Username for accessing the server. */
  private volatile String username = "";
  /** Password for accessing the server. */
  private volatile char[] password = new char[0];
  /** Specifies the connection protocol for this server. */
  private volatile ServerType type = ServerType.UNKNOWN;
  /** Contains scheduled reports to send to this server. */
  private volatile ArrayList<Report> reports = new ArrayList<Report>();
  /** Contains filenames of reports already sent to the server. */
  private volatile HashSet<String> sentReports = new HashSet<String>();
  /**
   * Create a new server with the given {@code host} and {@code port}.
   */
  public Server(String host, int port){
    this.host = host;
    this.port = port;
  }
  /**
   * @return the ID of this {@code Server} which may be used for {@link Servers#get(int)}.
   */
  public int getID(){
    return ID;
  }
  /**
   * @return the IP address of this {@code Server}.
   */
  public String getHost(){
    return host;
  }
  /**
   * @return the port of this {@code Server}.
   */
  public int getPort(){
    return port;
  }
  /**
   * @return the username for this {@code Server}.
   */
  public String getUsername(){
    return username;
  }
  /**
   * Set the host and port for this {@code Server}.
   */
  public void setParameters(String host, int port){
    lock.writeLock().lock();
    this.host = host;
    this.port = port;
    lock.writeLock().unlock();
  }
  /**
   * Sets the credentials used for connecting to this {@code Server}.
   * It is expected that {@code password} has already been obfuscated using {@link #obfuscate(char[])}.
   */
  public void setCredentials(String username, char[] password){
    lock.writeLock().lock();
    this.username = username;
    this.password = password;
    lock.writeLock().unlock();
  }
  /**
   * Sets the username used for connecting to this {@code Server}.
   */
  public void setUsername(String username){
    lock.writeLock().lock();
    this.username = username;
    lock.writeLock().unlock();
  }
  /**
   * Add a report to the list. The report must be uniquely identified by the pair (ID, folder).
   * @return whether the report was successfully appended.
   */
  public boolean addReport(Report r){
    lock.writeLock().lock();
    try{
      final String ID = r.getID();
      final String folder = r.getFolder();
      for (Report rr:reports){
        if (ID.equals(rr.getID()) && folder.equals(rr.getFolder())){
          return false;
        }
      }
      reports.add(r);
      return true;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Removes a report from the list.
   * @return {@code true} if the report was successfully removed; {@code false} if the report was not in the list to begin with.
   */
  public boolean removeReport(Report r){
    lock.writeLock().lock();
    try{
      return reports.remove(r);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Removes a report from the list.
   * @return {@code true} if the report was successfully removed; {@code false} if the report was not in the list to begin with.
   */
  public boolean removeReport(String ID, String folder){
    lock.writeLock().lock();
    try{
      Report r = null;
      for (Report rr:reports){
        if (ID.equals(rr.getID()) && folder.equals(rr.getFolder())){
          r = rr;
          break;
        }
      }
      return r!=null && reports.remove(r);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Applies the given {@code Predicate} to each {@code Report}.
   * The {@code Predicate} should not attempt to acquire a write lock on this {@code Server}, because the program will freeze indefinitely in such a circumstance.
   * @return {@code true} if any {@code Predicate} returns {@code true} (causing list traversal to halt); {@code false} otherwise.
   */
  public boolean forEach(java.util.function.Predicate<Report> tester){
    lock.readLock().lock();
    try{
      for (Report r:reports){
        if (tester.test(r)){
          return true;
        }
      }
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Clears history of reports previously sent to the server.
   */
  public void clearHistory(){
    lock.writeLock().lock();
    sentReports.clear();
    lock.writeLock().unlock();
  }
  /**
   * @return a {@code byte} array containing all data for this {@code Server}.
   */
  public byte[] serialize(){
    lock.readLock().lock();
    try{
      byte[] hostBytes = host.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      byte[] usernameBytes = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      byte[] passwordBytes = java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password)).array();
      int len = hostBytes.length+usernameBytes.length+passwordBytes.length+28;
      ArrayList<byte[]> sent = new ArrayList<byte[]>(sentReports.size());
      byte[] arr;
      for (String str:sentReports){
        arr = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sent.add(arr);
        len+=arr.length+4;
      }
      for (Report r:reports){
        len+=r.length();
      }
      SerializationStream s = new SerializationStream(len);
      s.write(hostBytes);
      s.write(port);
      s.write(type.ordinal());
      s.write(usernameBytes);
      s.write(passwordBytes);
      s.write(reports.size());
      for (Report r:reports){
        r.serialize(s);
      }
      s.write(sent.size());
      for (byte[] data:sent){
        s.write(data);
      }
      return s.data;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Read data from the given {@code SerializationStream}.
   * @return a {@code Server} initialized according to the given data, or {@code null} if a {@code Server} could not be deserialized.
   */
  public static Server deserialize(SerializationStream s){
    try{
      Server server = new Server(s.readString(), s.readInt());
      server.type = ServerType.fromOrdinal(s.readInt());
      server.username = s.readString();
      server.password = java.nio.charset.StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(s.readBytes())).array();
      int len = s.readInt();
      int i;
      server.reports.ensureCapacity(len);
      for (i=0;i<len;++i){
        server.reports.add(Report.deserialize(s));
      }
      len = s.readInt();
      for (i=0;i<len;++i){
        server.sentReports.add(s.readString());
      }
      return server;
    }catch(Throwable t){
      Initializer.logger.println(t);
      return null;
    }
  }
  /**
   * Tests whether it is possible to establish a connection to this server.
   * @return whether the test is successful.
   */
  public boolean test(){
    lock.readLock().lock();
    final String host = this.host;
    final int port = this.port;
    final String username = this.username;
    final String password = new String(Utility.obfuscate(this.password.clone()));
    lock.readLock().unlock();
    ServerType ret = Protocols.test(type, host, port, username, password);
    if (ret!=ServerType.UNKNOWN){
      type = ret;
    }
    return ret!=ServerType.UNKNOWN;
  }
  /**
   * Determines if the given report should be sent to this server, and enqueues the task if necessary.
   */
  public void enqueue(String ID, ReportFile file){
    lock.readLock().lock();
    try{
      if (sentReports.contains(file.getRemote())){
        return;
      }
      for (Report r:reports){
        if (r.getID().equals(ID)){
          r.files.add(file);
        }
      }
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * @return whether or not any reports are queued to be transferred.
   */
  private boolean isQueueEmpty(){
    lock.readLock().lock();
    try{
      for (Report r:reports){
        if (r.files.size()>0){
          return false;
        }
      }
      return true;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Sends all queued reports to the server.
   * @return an indication of success or failure.
   */
  public boolean sendReports(){
    if (isQueueEmpty()){
      return true;
    }
    lock.writeLock().lock();
    try{
      return Protocols.sendReports(type, host, port, username, new String(Utility.obfuscate(this.password.clone())), reports, sentReports);
    }finally{
      lock.writeLock().unlock();
    }
  }
}