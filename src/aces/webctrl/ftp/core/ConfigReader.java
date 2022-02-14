/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import java.util.regex.*;
public class ConfigReader {
  private final static Pattern reg = Pattern.compile("\\W");
  private char[] arr;
  private int i = 0;
  private int len;
  public ConfigReader(StringBuilder sb, char[] arr){
    this.arr = arr;
    len = arr.length;
    skipUntil("label");
    String ID = nextToken();
    if (ID.length()>0){
      sb.append("<option value=\"").append(Utility.escapeHTML(ID)).append("\">\n");
    }
  }
  public ConfigReader(char[] arr){
    String ID, local, remote;
    this.arr = arr;
    len = arr.length;
    skipUntil("label");
    ID = nextToken();
    skipUntilBlock("artifacts");
    while (true){
      skipUntil("displayName");
      if (i>=len){ return; }
      remote = reg.matcher(nextToken()).replaceAll("_");
      skipUntil("mimeType");
      remote+='.'+nextToken();
      skipUntil("path");
      local = nextToken();
      if (i>=len){ return; }
      Servers.enqueue(ID, local, remote);
    }
  }
  private void skipUntilBlock(String str){
    while (i<len){
      if (nextToken().equals(str)){
        break;
      }else{
        skipUntil('}');
      }
    }
  }
  private void skipUntil(String str){
    while (i<len){
      if (nextToken().equals(str)){
        break;
      }else{
        nextLine();
      }
    }
  }
  private void skipUntil(char c){
    while (i<len){
      if (arr[i]==c){
        break;
      }else if (arr[i]=='"'){
        ++i;
        boolean esc = false;
        while (i<len){
          if (esc){
            esc = false;
          }else if (arr[i]=='\\'){
            esc = true;
          }else if (arr[i]=='"'){
            break;
          }
          ++i;
        }
      }
      ++i;
    }
    ++i;
  }
  private String nextToken(){
    StringBuilder sb = new StringBuilder();
    while (i<len && arr[i]!='"'){
      ++i;
    }
    ++i;
    boolean esc = false;
    while (i<len){
      if (esc){
        esc = false;
        sb.append(arr[i]);
      }else if (arr[i]=='\\'){
        esc = true;
      }else if (arr[i]=='"'){
        break;
      }else{
        sb.append(arr[i]);
      }
      ++i;
    }
    ++i;
    return sb.toString();
  }
  private void nextLine(){
    while (i<len && arr[i]!='\n'){
      ++i;
    }
    ++i;
  }
}