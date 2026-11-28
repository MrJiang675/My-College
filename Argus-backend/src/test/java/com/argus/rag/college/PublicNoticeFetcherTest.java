package com.argus.rag.college;
import java.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PublicNoticeFetcherTest {
 @Test void blocksInternalAndReservedAddresses()throws Exception{for(String host:new String[]{"127.0.0.1","10.0.0.1","192.168.1.1","172.16.1.1","169.254.169.254","100.64.1.1","::1","fc00::1","fe80::1","::ffff:127.0.0.1","2001:db8::1"})assertFalse(PublicNoticeFetcher.publicAddress(InetAddress.getByName(host)),host);assertTrue(PublicNoticeFetcher.publicAddress(InetAddress.getByName("8.8.8.8")));}
 @Test void rejectsSchemesCredentialsAndPorts(){for(String s:new String[]{"file:///etc/passwd","ftp://example.com/","http://user:pass@example.com/","http://example.com:10001/","https://example.com/#fragment"})assertThrows(IllegalArgumentException.class,()->PublicNoticeFetcher.checkedUri(s));assertEquals("example.com",PublicNoticeFetcher.checkedUri("https://example.com/notice").getHost());}
 @Test void cleanTextRetainsParagraphsAndDate(){var p=PublicNoticeFetcher.extract("https://example.com","<title>通知 &amp; 申请</title><nav>导航</nav><article><p>发布时间：2026年11月28日</p><p>提交成绩单</p><script>alert(1)</script><p>见附件1</p></article>");assertEquals("通知 & 申请",p.title());assertTrue(p.body().contains("提交成绩单\n"));assertFalse(p.body().contains("alert"));assertFalse(p.body().contains("导航"));assertEquals("2026年11月28",p.publishedAt());}
}
