/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import aces.webctrl.ftp.web.*;
import org.apache.commons.net.ftp.*;
import com.jcraft.jsch.*;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.*;
import java.io.*;
public class Protocols {
  /** The timeout in milliseconds for all server I/O operations. */
  private final static int timeout = 6000;
  public static ServerType test(ServerType type, String host, int port, String username, String password){
    if (type==ServerType.UNKNOWN){
      if (port==21 || port==990){
        type = ServerType.FTPS;
      }else{
        type = ServerType.SFTP;
      }
    }
    if (testInternal(type,host,port,username,password)){ return type; }
    if (type==ServerType.SFTP){
      if (testInternal(ServerType.FTPS,host,port,username,password)){ return ServerType.FTPS; }
      if (testInternal(ServerType.FTP,host,port,username,password)){ return ServerType.FTP; }
    }else if (type==ServerType.FTP){
      if (testInternal(ServerType.FTPS,host,port,username,password)){ return ServerType.FTPS; }
      if (testInternal(ServerType.SFTP,host,port,username,password)){ return ServerType.SFTP; }
    }else{
      if (testInternal(ServerType.FTP,host,port,username,password)){ return ServerType.FTP; }
      if (testInternal(ServerType.SFTP,host,port,username,password)){ return ServerType.SFTP; }
    }
    return ServerType.UNKNOWN;
  }
  private static boolean testInternal(ServerType type, String host, int port, String username, String password){
    try{
      switch (type){
        case SFTP:{
          Session s = null;
          ChannelSftp ch = null;
          try{
            s = Initializer.jsch.getSession(username, host, port);
            s.setPassword(password);
            password = null;
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            s.setConfig(config);
            s.setTimeout(timeout);
            s.connect(timeout);
            ch = (ChannelSftp)s.openChannel("sftp");
            ch.connect(timeout);
            return true;
          }finally{
            if (ch!=null && ch.isConnected()){
              ch.exit();
              ch.disconnect();
            }
            if (s!=null && s.isConnected()){
              s.disconnect();
            }
          }
        }
        case FTP: case FTPS: {
          FTPClient ftp = null;
          try{
            ftp = type==ServerType.FTP ? new FTPClient() : new FTPSClient();
            ftp.setDefaultTimeout(timeout);
            ftp.setConnectTimeout(timeout);
            ftp.connect(host, port);
            int i;
            if (!FTPReply.isPositiveCompletion(i=ftp.getReplyCode())){
              Initializer.log("Connection failed with reply code: "+i);
              return false;
            }
            ftp.setSoTimeout(timeout);
            ftp.enterLocalPassiveMode();
            if (!ftp.login(username, password)){
              Initializer.log("Login failed.");
              return false;
            }
            return true;
          }finally{
            if (ftp!=null && ftp.isConnected()){
              try{
                ftp.logout();
                ftp.disconnect();
              }catch(Throwable t){}
            }
          }
        }
        default:{
          return false;
        }
      }
    }catch(Throwable t){
      Initializer.log("ServerType."+type.name());
      Initializer.log(t);
      Throwable[] arr = t.getSuppressed();
      for (int i=0;i<arr.length;++i){
        Initializer.log(arr[i]);
      }
      return false;
    }
  }
  public static boolean sendReports(ServerType type, String host, int port, String username, String password, ArrayList<Report> reports, HashSet<String> sentReports){
    try{
      switch (type){
        case SFTP:{
          Session s = null;
          ChannelSftp ch = null;
          try{
            s = Initializer.jsch.getSession(username, host, port);
            s.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            s.setConfig(config);
            s.setTimeout(timeout);
            s.connect(10000);
            ch = (ChannelSftp)s.openChannel("sftp");
            ch.connect(10000);
            for (Report r:reports){
              try{
                ch.cd(r.getFolder());
                for (ReportFile rf:r.files){
                  try(
                    BufferedInputStream in = new BufferedInputStream(Files.newInputStream(rf.getLocal(), StandardOpenOption.READ));
                  ){
                    ch.put(in, rf.getRemote());
                    sentReports.add(rf.getRemote());
                  }catch(Throwable t){
                    Initializer.logger.println(t);
                  }
                }
              }catch(Throwable t){
                Initializer.logger.println(t);
              }
            }
            return true;
          }finally{
            if (ch!=null && ch.isConnected()){
              ch.exit();
              ch.disconnect();
            }
            if (s!=null && s.isConnected()){
              s.disconnect();
            }
          }
        }
        case FTP: case FTPS: {
          FTPClient ftp = null;
          try{
            ftp = type==ServerType.FTP ? new FTPClient() : new FTPSClient();
            ftp.setDefaultTimeout(timeout);
            if (type==ServerType.FTPS){
              ((FTPSClient)ftp).setTrustManager(new X509TrustManager(){
                @Override public X509Certificate[] getAcceptedIssuers(){
                  return null;
                }
                @Override public void checkClientTrusted(X509Certificate[] certs, String authType){}
                @Override public void checkServerTrusted(X509Certificate[] certs, String authType){}
              });
            }
            ftp.connect(host, port);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())){
              return false;
            }
            ftp.setSoTimeout(timeout);
            ftp.enterLocalPassiveMode();
            if (!ftp.login(username, password)){
              return false;
            }
            for (Report r:reports){
              if (!ftp.changeWorkingDirectory(r.getFolder())){
                Initializer.logger.println("Cannot locate folder: "+r.getFolder());
                continue;
              }
              for (ReportFile rf:r.files){
                try(
                  BufferedInputStream in = new BufferedInputStream(Files.newInputStream(rf.getLocal(), StandardOpenOption.READ));
                ){
                  if (ftp.storeFile(rf.getRemote(), in)){
                    sentReports.add(rf.getRemote());
                  }
                }catch(Throwable t){
                  Initializer.logger.println(t);
                  return false;
                }
              }
            }
            return true;
          }finally{
            if (ftp!=null && ftp.isConnected()){
              try{
                ftp.logout();
                ftp.disconnect();
              }catch(Throwable t){}
            }
          }
        }
        default:{
          return false;
        }
      }
    }catch(Throwable t){
      Initializer.log("ServerType."+type.name());
      Initializer.log(t);
      Throwable[] arr = t.getSuppressed();
      for (int i=0;i<arr.length;++i){
        Initializer.log(arr[i]);
      }
      return false; 
    }finally{
      for (Report r:reports){
        r.files.clear();
      }
    }
  }
}
