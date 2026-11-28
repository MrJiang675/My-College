package com.argus.rag.college;

import com.argus.rag.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** DNS addresses are checked and pinned to the actual socket, including every redirect. */
@Service
public class PublicNoticeFetcher {
    static final int MAX_BODY=2*1024*1024;
    private final Semaphore slots=new Semaphore(2);
    public record Page(String url,String title,String publishedAt,String body) {}
    public Page fetch(String url) {
        if(!slots.tryAcquire())throw new BusinessException("官网抓取正在处理，请稍后重试或直接粘贴原文。");
        var executor=Executors.newVirtualThreadPerTaskExecutor();
        try {
            var task=executor.submit(()->{try{return request(url);}finally{slots.release();}});
            try{return task.get(8,TimeUnit.SECONDS);}
            catch(Exception failed){task.cancel(true);throw new BusinessException("未能获取公开正文（超时、受限或地址不安全）。请复制通知正文或 Ctrl+V 粘贴截图；内网登录页面不支持抓取。");}
        } finally {executor.shutdownNow();}
    }
    static URI checkedUri(String raw) {
        try {
            var uri=URI.create(raw);
            if(raw.length()>2048||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||
                (uri.getPort()!=-1&&uri.getPort()!=(uri.getScheme().equals("https")?443:80)))throw new IllegalArgumentException();
            return uri;
        }catch(Exception e){throw new IllegalArgumentException("只支持公开 HTTP/HTTPS 网页地址");}
    }
    static boolean publicAddress(InetAddress ip) {
        if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isSiteLocalAddress()||ip.isLinkLocalAddress()||ip.isMulticastAddress())return false;
        byte[] b=ip.getAddress();int a=b[0]&255,c=b[1]&255;
        if(b.length==4)return a!=0&&a!=10&&a!=127&&a<224&&!(a==100&&c>=64&&c<=127)&&!(a==169&&c==254)&&!(a==172&&c>=16&&c<=31)&&!(a==192&&(c==168||c==0||c==2))&&!(a==198&&(c==18||c==19||c==51))&&!(a==203&&c==0);
        // IPv6 global unicast only; excludes ULA/link-local/mapped/unspecified and documentation.
        return (a&0xe0)==0x20&&!(a==0x20&&c==2)&&!(a==0x20&&c==1&&(b[2]&255)==0&&(b[3]&255)==0)&&!(a==0x20&&c==1&&(b[2]&255)==0x0d&&(b[3]&255)==0xb8);
    }
    private Page request(String input)throws Exception {
        URI uri=checkedUri(input);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);
        for(int redirects=0;redirects<=3;redirects++) {
            InetAddress[] addresses=InetAddress.getAllByName(uri.getHost());
            if(addresses.length==0||Arrays.stream(addresses).anyMatch(ip->!publicAddress(ip)))throw new IOException("非公网地址");
            String host=uri.getHost();if(host.startsWith("["))host=host.substring(1,host.length()-1);
            int port=uri.getScheme().equals("https")?443:80;
            try(Socket plain=new Socket()) {
                plain.connect(new InetSocketAddress(addresses[0],port),remaining(end));plain.setSoTimeout(remaining(end));
                Socket connection=plain;
                if(port==443){var ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(plain,host,port,true);var params=ssl.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");if(!host.contains(":"))params.setServerNames(List.of(new SNIHostName(host)));ssl.setSSLParameters(params);ssl.setSoTimeout(remaining(end));ssl.startHandshake();connection=ssl;}
                try(Socket socket=connection){
                    String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
                    if(path.contains("\r")||path.contains("\n"))throw new IOException();
                    socket.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: "+uri.getRawAuthority()+"\r\nUser-Agent: Argus-NoticeReader/1.0\r\nAccept: text/html,text/plain\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    var in=new BufferedInputStream(socket.getInputStream());int[] headerBytes={0};String status=line(in,headerBytes);String[] parts=status.split(" ");if(parts.length<2)throw new IOException();int code=Integer.parseInt(parts[1]);
                    var headers=new HashMap<String,String>();String h;while(!(h=line(in,headerBytes)).isEmpty()){int colon=h.indexOf(':');if(colon>0)headers.put(h.substring(0,colon).toLowerCase(Locale.ROOT),h.substring(colon+1).strip());}
                    if(Set.of(301,302,303,307,308).contains(code)){uri=checkedUri(uri.resolve(headers.getOrDefault("location","")).toString());continue;}
                    if(code!=200)throw new IOException("网页不公开");
                    String type=headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT);if(!type.contains("text/html")&&!type.contains("text/plain"))throw new IOException("不是网页");
                    if(!headers.getOrDefault("content-encoding","identity").equalsIgnoreCase("identity"))throw new IOException("不支持压缩编码，请复制正文");
                    var body=new ByteArrayOutputStream();String transfer=headers.getOrDefault("transfer-encoding","");
                    if(transfer.equalsIgnoreCase("chunked")){
                        while(true){socket.setSoTimeout(remaining(end));String size=line(in,new int[]{0});int n=Integer.parseInt(size.split(";",2)[0].strip(),16);if(n<0||n>MAX_BODY-body.size())throw new IOException("正文超限");if(n==0)break;read(in,body,n,socket,end);if(in.read()!='\r'||in.read()!='\n')throw new IOException();}
                    }else {String length=headers.get("content-length");if(length!=null){long n=Long.parseLong(length);if(n<0||n>MAX_BODY)throw new IOException("正文超限");read(in,body,(int)n,socket,end);}else {byte[] buf=new byte[8192];int n;while(true){socket.setSoTimeout(remaining(end));n=in.read(buf);if(n<0)break;if(n>MAX_BODY-body.size())throw new IOException("正文超限");body.write(buf,0,n);}}}
                    byte[] bytes=body.toByteArray();String head=new String(bytes,0,Math.min(bytes.length,4096),StandardCharsets.ISO_8859_1);var charset=Pattern.compile("charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)",Pattern.CASE_INSENSITIVE).matcher(type+" "+head);Charset encoding=StandardCharsets.UTF_8;if(charset.find()){String name=charset.group(1);encoding=Charset.forName(name.equalsIgnoreCase("gb2312")?"GBK":name);}
                    return extract(uri.toString(),new String(bytes,encoding));
                }
            }
        }
        throw new IOException("跳转次数过多");
    }
    private static int remaining(long end)throws IOException{long ms=TimeUnit.NANOSECONDS.toMillis(end-System.nanoTime());if(ms<=0||Thread.currentThread().isInterrupted())throw new IOException("超时");return (int)Math.min(ms,7000);}
    private static void read(InputStream in,ByteArrayOutputStream out,int count,Socket socket,long end)throws IOException{byte[] b=new byte[8192];while(count>0){socket.setSoTimeout(remaining(end));int n=in.read(b,0,Math.min(count,b.length));if(n<0)throw new EOFException();out.write(b,0,n);count-=n;}}
    private static String line(InputStream in,int[] total)throws IOException{var out=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(++total[0]>32768||out.size()>8192)throw new IOException("头部超限");if(c=='\n')return out.toString(StandardCharsets.ISO_8859_1).stripTrailing();out.write(c);}throw new EOFException();}
    static Page extract(String url,String html) {
        var title=Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);String name=title.find()?HtmlUtils.htmlUnescape(title.group(1).replaceAll("<[^>]+>","")).strip():"公开通知";
        var date=Pattern.compile("(?:发布时间|发布日期|发布于)[^0-9]{0,50}(20[0-9]{2}[-年/][0-9]{1,2}[-月/][0-9]{1,2})").matcher(html);String published=date.find()?date.group(1):"需核实";
        String clean=html.replaceAll("(?is)<!--.*?-->","").replaceAll("(?is)<(script|style|nav|header|footer)\\b[^>]*>.*?</\\1\\s*>","");
        var main=Pattern.compile("(?is)<(?:article|main)\\b[^>]*>(.*?)</(?:article|main)>").matcher(clean);if(main.find())clean=main.group(1);
        String text=HtmlUtils.htmlUnescape(clean.replaceAll("(?i)</(?:p|div|li|h[1-6]|tr)>|<br[^>]*>","\n").replaceAll("<[^>]+>","")).replace('\u00a0',' ').replaceAll("[\\t ]+"," ").replaceAll("\\n[\\t ]*\\n+","\n\n").strip();
        if(text.isBlank())throw new IllegalArgumentException("无公开正文");if(text.length()>30000)text=text.substring(0,30000)+"\n【正文过长，已截取；请检查原网页及附件】";
        return new Page(url,name.substring(0,Math.min(name.length(),200)),published,text);
    }
}
