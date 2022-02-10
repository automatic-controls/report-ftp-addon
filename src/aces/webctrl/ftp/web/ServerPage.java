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
public class ServerPage extends HttpServlet {
  private volatile static String html = null;
  @Override public void init() throws ServletException {
    try{
      html = Utility.loadResourceAsString("aces/webctrl/ftp/web/ServerPage.html").replaceAll(
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
      String type = req.getParameter("type");
      if (type==null){
        final StringBuilder sb = new StringBuilder(256);
        Servers.forEach(new java.util.function.Predicate<Server>(){
          public boolean test(Server s){
            sb.append("addRow(\"").append(s.getID()).append("\",\"");
            sb.append(Utility.escapeJS(s.getHost())).append("\",\"");
            sb.append(s.getPort()).append("\",\"");
            sb.append(Utility.escapeJS(s.getUsername())).append("\",\"\");\n");
            return false;
          }
        });
        res.setContentType("text/html");
        res.getWriter().print(html.replace("//__INIT_SCRIPT__", sb.toString()));
      }else{
        switch (type){
          case "delete":{
            String ID = req.getParameter("ID");
            if (ID==null){
              res.setStatus(400);
            }else{
              try{
                if (!Servers.delete(Integer.parseInt(ID))){
                  res.setStatus(404);
                }
              }catch(NumberFormatException e){
                res.setStatus(400);
              }
            }
            break;
          }
          case "test":{
            String ID_ = req.getParameter("ID");
            if (ID_==null){
              res.setStatus(400);
            }else{
              try{
                int ID = Integer.parseInt(ID_);
                Server s = Servers.get(ID);
                if (s==null){
                  res.setStatus(404);
                }else if (!s.test()){
                  res.setStatus(504);
                }
              }catch(NumberFormatException e){
                res.setStatus(400);
              }
            }
            break;
          }
          case "run":{
            Servers.run();
            break;
          }
          case "save":{
            String ID_ = req.getParameter("ID");
            String IP = req.getParameter("IP");
            String port_ = req.getParameter("port");
            String user = req.getParameter("user");
            String pass = req.getParameter("pass");
            if (ID_==null || IP==null || port_==null || user==null){
              res.setStatus(400);
            }else{
              try{
                int port = Integer.parseInt(port_);
                int ID = Integer.parseInt(ID_);
                Server s = Servers.get(ID);
                if (s==null){
                  res.setStatus(404);
                }else{
                  s.setParameters(IP, port);
                  if (pass==null){
                    s.setUsername(user);
                  }else{
                    s.setCredentials(user, pass.toCharArray());
                  }
                }
              }catch(NumberFormatException e){
                res.setStatus(400);
              }
            }
            break;
          }
          case "create":{
            String IP = req.getParameter("IP");
            String port = req.getParameter("port");
            String user = req.getParameter("user");
            String pass = req.getParameter("pass");
            if (IP==null || port==null || user==null || pass==null){
              res.setStatus(400);
            }else{
              try{
                Server s = new Server(IP, Integer.parseInt(port));
                s.setCredentials(user, pass.toCharArray());
                Servers.add(s);
                res.setContentType("text/plain");
                res.getWriter().print(s.getID());
              }catch(NumberFormatException e){
                res.setStatus(400);
              }
            }
            break;
          }
          default:{
            res.sendError(400);
          }
        }
      }
    }catch(Throwable t){
      Initializer.logger.println(t);
      res.sendError(500);
    }
  }
}