/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.web;
import aces.webctrl.ftp.core.*;
import com.controlj.green.addonsupport.*;
import com.jcraft.jsch.*;
import javax.servlet.*;
import java.nio.file.*;
public class Initializer implements ServletContextListener {
  public volatile static AddOnInfo info;
  public volatile static FileLogger logger;
  public volatile static JSch jsch;
  public volatile static Path dataFolder;
  public volatile static Path configs;
  public volatile static Path reports;
  private volatile static String prefix;
  private volatile Thread th;
  private volatile boolean stop = false;
  /** @return the prefix to use for constructing relative URL paths. */
  public static String getPrefix(){
    return prefix;
  }
  @Override public void contextInitialized(ServletContextEvent e){
    info = AddOnInfo.getAddOnInfo();
    logger = info.getDateStampLogger();
    prefix = '/'+info.getName()+'/';
    jsch = new JSch();
    dataFolder = info.getPrivateDir().toPath();
    Path scheduledReports = dataFolder.getParent().getParent().getParent().resolve("scheduled_reports");
    configs = scheduledReports.resolve("configs");
    reports = scheduledReports.resolve("output");
    Servers.load(dataFolder.resolve("ftp_servers.dat"));
    th = new Thread(){
      public void run(){
        final long reportInterval = 3600000L; // Evaluate reports hourly.
        final long backupInterval = 86400000L; // Backup information daily.
        long nextRun = System.currentTimeMillis();
        long nextBackup = nextRun+backupInterval;
        long x;
        while (!stop){
          try{
            x = nextRun-System.currentTimeMillis();
            if (x<=0){
              Servers.run();
              if (stop){ break; }
              x = System.currentTimeMillis();
              if (nextBackup<=x){
                Servers.save();
                if (stop){ break; }
                x = System.currentTimeMillis();
                nextBackup = x+backupInterval;
              }
              nextRun = x+reportInterval;
              Thread.sleep(reportInterval);
            }else{
              Thread.sleep(x);
            }
          }catch(InterruptedException e){}
        }
      }
    };
    th.start();
  }
  @Override public void contextDestroyed(ServletContextEvent e){
    stop = true;
    Servers.save();
    th.interrupt();
    try{
      th.join();
    }catch(InterruptedException t){}
  }
  /**
   * Logs a message.
   */
  public synchronized static void log(String str){
    logger.println(str);
  }
  /**
   * Logs an error.
   */
  public synchronized static void log(Throwable t){
    logger.println(t);
  }
}