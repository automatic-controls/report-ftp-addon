/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.web;
import aces.webctrl.ftp.core.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.nio.*;
import java.nio.file.*;
public class ReportPage extends HttpServlet {
  private volatile static String html = null;
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/ftp/web/ReportPage.html").replaceAll(
        "(?m)^[ \\t]++",
        ""
      ).replace(
        "__PREFIX__",
        Initializer.getPrefix()
      );
    }catch(Throwable e){
      if (e instanceof ServletException){
        throw (ServletException)e;
      }else{
        throw new ServletException(e);
      }
    }
  }
  @Override public void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override public void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    try{
      req.setCharacterEncoding("UTF-8");
      res.setCharacterEncoding("UTF-8");
      String ID = req.getParameter("ID");
      if (ID==null){
        res.sendError(400);
      }else{
        try{
          Server s = Servers.get(Integer.parseInt(ID));
          if (s==null){
            res.sendError(404);
          }else{
            String type = req.getParameter("type");
            if (type==null){
              final StringBuilder sb = new StringBuilder(256);
              s.forEach(new java.util.function.Predicate<Report>(){
                public boolean test(Report r){
                  sb.append("addRow(\"").append(Utility.escapeJS(r.getID())).append("\",\"");
                  sb.append(Utility.escapeJS(r.getFolder())).append("\");\n");
                  return false;
                }
              });
              String str = html.replace("__ID__", ID).replace("__SERVER__", s.getHost()).replace("//__INIT_SCRIPT__", sb.toString());
              sb.setLength(0);
              synchronized (Servers.configReadLock){
                try(
                  DirectoryStream<Path> stream = Files.newDirectoryStream(Initializer.configs);
                ){
                  for (Path config:stream){
                    if (Files.isReadable(config)){
                      new ConfigReader(sb, java.nio.charset.StandardCharsets.UTF_8.decode(ByteBuffer.wrap(Files.readAllBytes(config))).array());
                    }
                  }
                }catch(Throwable t){}
              }
              str = str.replace("<!--__REPORTS__-->", sb.toString());
              res.setContentType("text/html");
              res.getWriter().print(str);
            }else{
              switch (type){
                case "clear":{
                  s.clearHistory();
                  break;
                }
                case "delete":{
                  String label = req.getParameter("label");
                  String folder = req.getParameter("folder");
                  if (label==null || folder==null){
                    res.setStatus(400);
                  }else if (!s.removeReport(label, folder)){
                    res.setStatus(404);
                  }
                  break;
                }
                case "create":{
                  String label = req.getParameter("label");
                  String folder = req.getParameter("folder");
                  if (label==null || folder==null){
                    res.setStatus(400);
                  }else if (!s.addReport(new Report(label, folder))){
                    res.setStatus(409);
                  }
                  break;
                }
                default:{
                  res.sendError(400);
                }
              }
            }
          }
        }catch(NumberFormatException e){
          res.sendError(400);
        }
      }
    }catch(Throwable t){
      Initializer.logger.println(t);
      res.sendError(500);
    }
  }
}