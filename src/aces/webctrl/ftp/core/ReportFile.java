/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import aces.webctrl.ftp.web.*;
import java.nio.file.*;
/**
 * Immutable class to represent a local report file and the designated name on the remote server.
 */
public class ReportFile {
  private volatile Path local;
  private volatile String remote;
  private ReportFile(){}
  public Path getLocal(){
    return local;
  }
  public String getRemote(){
    return remote;
  }
  /**
   * @return a new {@code ReportFile} or {@code null} if one could not be created with the given parameters.
   */
  public static ReportFile create(String local, String remote){
    try{
      ReportFile r = new ReportFile();
      r.local = Initializer.reports.resolve(local);
      if (!Files.isReadable(r.local)){
        return null;
      }
      r.remote = remote;
      return r;
    }catch(Throwable t){
      Initializer.logger.println(t);
      return null;
    }
  }
}