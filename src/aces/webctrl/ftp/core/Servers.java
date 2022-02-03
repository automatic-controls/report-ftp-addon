/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import aces.webctrl.ftp.web.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
/**
 * Thread-safe class which encapsulates a collection of FTP servers.
 */
public class Servers {
  /** Controls access to properties of this class. */
  private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  /** Indicates whether there is an FTP transfer in progress. */
  private final static AtomicBoolean ftp = new AtomicBoolean();
  /** Indicates whether data is currently being saved. */
  private final static AtomicBoolean saving = new AtomicBoolean();
  /** {@code Path} to the file which stores all server data. */
  private volatile static Path dataFile;
  /** Contains a list of {@code Server} objects. */
  private final static ArrayList<Server> servers = new ArrayList<Server>();
  /**
   * Analyzes current scheduled reports, and sends them to the appropriate servers.
   */
  public static void run(){
    if (servers.size()>0 && ftp.compareAndSet(false,true)){
      try{
        try(
          DirectoryStream<Path> stream = Files.newDirectoryStream(Initializer.configs);
        ){
          for (Path config:stream){
            if (Files.isReadable(config)){
              new ConfigReader(java.nio.charset.StandardCharsets.UTF_8.decode(ByteBuffer.wrap(Files.readAllBytes(config))).array());
            }
          }
        }
        lock.readLock().lock();
        try{
          for (Server s:servers){
            if (s!=null){
              s.sendReports();
            }
          }
        }finally{
          lock.readLock().unlock();
        }
      }catch(Throwable t){
        Initializer.logger.println(t);
      }finally{
        ftp.set(false);
      }
    }
  }
  /**
   * Checks all servers and enqueues the given report to be sent when appropriate.
   */
  protected static void enqueue(String ID, String local, String remote){
    ReportFile rf = ReportFile.create(local, remote);
    if (rf!=null){
      lock.readLock().lock();
      try{
        for (Server s:servers){
          if (s!=null){
            s.enqueue(ID, rf);
          }
        }
      }finally{
        lock.readLock().unlock();
      }
    }
  }
  /**
   * Applies the given {@code Predicate} to each {@code Server}.
   * The {@code Predicate} should not attempt to acquire a write lock on the {@code Server} list, because the program will freeze indefinitely in such a circumstance.
   * @return {@code true} if any {@code Predicate} returns {@code true} (causing list traversal to halt); {@code false} otherwise.
   */
  public static boolean forEach(java.util.function.Predicate<Server> tester){
    lock.readLock().lock();
    try{
      for (Server s:servers){
        if (s!=null && tester.test(s)){
          return true;
        }
      }
      return false;
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Removes a {@code Server} from the list.
   * @return whether the deletion was successful.
   */
  public static boolean delete(int ID){
    if (ID<0){
      return false;
    }
    lock.writeLock().lock();
    try{
      if (ID>=servers.size()){
        return false;
      }
      return servers.set(ID,null)!=null;
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * @return the {@code Server} with the given ID, or {@code null} if no such {@code Server} exists.
   */
  public static Server get(int ID){
    if (ID<0){
      return null;
    }
    lock.readLock().lock();
    try{
      if (ID>=servers.size()){
        return null;
      }
      return servers.get(ID);
    }finally{
      lock.readLock().unlock();
    }
  }
  /**
   * Adds the specified {@code Server} to the list.
   */
  public static void add(Server s){
    lock.writeLock().lock();
    try{
      s.ID = servers.size();
      servers.add(s);
    }finally{
      lock.writeLock().unlock();
    }
  }
  /**
   * Loads all server data from the specified data file.
   */
  public static void load(Path dataFile){
    try{
      Servers.dataFile = dataFile;
      if (Files.isReadable(dataFile)){
        SerializationStream s = new SerializationStream(Files.readAllBytes(dataFile));
        int len = s.readInt();
        lock.writeLock().lock();
        try{
          servers.ensureCapacity(len);
          Server server;
          for (int i=0;i<len;++i){
            server = Server.deserialize(s);
            if (server==null){
              throw new Exception("Data file corrupted.");
            }else{
              server.ID = servers.size();
              servers.add(server);
            }
          }
        }finally{
          lock.writeLock().unlock();
        }
        if (!s.end()){
          throw new Exception("Data file corrupted.");
        }
      }
    }catch(Throwable t){
      Initializer.logger.println(t);
    }
  }
  /**
   * Saves all server data to the preset data file.
   */
  public static void save(){
    if (saving.compareAndSet(false, true)){
      try{
        ArrayList<byte[]> arr;
        int len = 4;
        lock.readLock().lock();
        try{
          arr = new ArrayList<byte[]>(servers.size());
          byte[] bytes;
          for (Server s:servers){
            if (s!=null){
              bytes = s.serialize();
              arr.add(bytes);
              len+=bytes.length;
            }
          }
        }finally{
          lock.readLock().unlock();
        }
        SerializationStream s = new SerializationStream(len);
        s.write(arr.size());
        for (byte[] bytes:arr){
          s.writeRaw(bytes);
        }
        if (!s.end()){
          throw new Exception("Serialization length was computed incorrectly.");
        }
        ByteBuffer buf = ByteBuffer.wrap(s.data);
        try(
          FileChannel ch = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        ){
          while (buf.hasRemaining()){
            ch.write(buf);
          }
        }
      }catch(Throwable t){
        Initializer.logger.println(t);
      }finally{
        saving.set(false);
      }
    }
  }
}