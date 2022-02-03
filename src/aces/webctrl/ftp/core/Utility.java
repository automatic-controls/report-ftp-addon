/*
  BSD 3-Clause License
  Copyright (c) 2022, Automatic Controls Equipment Systems, Inc.
  Contributors: Cameron Vogt (@cvogt729)
*/
package aces.webctrl.ftp.core;
import java.io.*;
public class Utility {
  /**
   * Obfuscates a character array.
   * Reverses the order and XORs each character with 4.
   * The array is modified in-place, so no copies are made.
   * For convenience, the given array is returned.
   */
  public static char[] obfuscate(char[] arr){
    char c;
    for (int i=0,j=arr.length-1;i<=j;++i,--j){
      if (i==j){
        arr[i]^=4;
      }else{
        c = (char)(arr[j]^4);
        arr[j] = (char)(arr[i]^4);
        arr[i] = c;
      }
    }
    return arr;
  }
  /**
   * Loads all bytes from the given resource and convert to a {@code UTF-8} string.
   * @return the {@code UTF-8} string representing the given resource.
   */
  public static String loadResourceAsString(String name) throws Throwable {
    java.util.ArrayList<byte[]> list = new java.util.ArrayList<byte[]>();
    int len = 0;
    byte[] buf;
    int read;
    try(
      InputStream s = Utility.class.getClassLoader().getResourceAsStream(name);
    ){
      while (true){
        buf = new byte[8192];
        read = s.read(buf);
        if (read==-1){
          break;
        }
        len+=read;
        list.add(buf);
        if (read!=buf.length){
          break;
        }
      }
    }
    byte[] arr = new byte[len];
    int i = 0;
    for (byte[] bytes:list){
      read = Math.min(bytes.length,len);
      len-=read;
      System.arraycopy(bytes, 0, arr, i, read);
      i+=read;
    }
    return new String(arr, java.nio.charset.StandardCharsets.UTF_8);
  }
}