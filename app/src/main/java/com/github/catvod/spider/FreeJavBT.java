package com.github.catvod.spider;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeJavBT extends Spider {

    private static final String siteUrl = "https://freejavbt.com";
    private static final String homeUrl = siteUrl + "/zh";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", homeUrl + "/");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        // 有码 排行榜：日榜/周榜/月榜
        String[] typeKeys = {"censored/day", "censored/week", "censored/month"};
        String[] typeNames = {"有码日榜", "有码周榜", "有码月榜"};
        for (int i = 0; i < typeKeys.length; i++) {
            classes.add(new Class(typeKeys[i], typeNames[i]));
        }

        // 首页默认拉有码最新
        String html = OkHttp.string(homeUrl + "/censored", getHeaders());
        Document doc = Jsoup.parse(html);
        list.addAll(parseVideoList(doc));

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        String url = homeUrl + "/rank/" + tid;
        if (pg != null && !pg.equals("1")) {
            url += "?page=" + pg;
        }
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        list.addAll(parseVideoList(doc));
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        String html = OkHttp.string(homeUrl + "/" + ids.get(0), getHeaders());
        Document doc = Jsoup.parse(html);

        String name = "";
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            name = h1.text().replace(" 免费AV在线看", "").replace(" FREE JAV BT", "").trim();
        }

        String pic = "";
        Element ogImg = doc.selectFirst("meta[property=og:image]");
        if (ogImg != null) pic = ogImg.attr("content");

        String m3u8 = "";
        // 方法1: <video src="xxx.m3u8">
        Element video = doc.selectFirst("video#player");
        if (video != null) {
            m3u8 = video.attr("src");
        }
        // 方法2: data-m3u8 属性
        if (m3u8.isEmpty()) {
            Element m3u8El = doc.selectFirst("[data-m3u8]");
            if (m3u8El != null) m3u8 = m3u8El.attr("data-m3u8");
        }
        // 方法3: 正则找
        if (m3u8.isEmpty()) {
            Matcher mm = Pattern.compile("(https://[^\\s\"']+\\.m3u8[^\\s\"']*)").matcher(html);
            if (mm.find()) m3u8 = mm.group(1);
        }

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodPlayFrom("FreeJavBT");
        vod.setVodPlayUrl("播放$" + m3u8);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        // 简单搜索：URL = /searchq/{keyword}
        String html = OkHttp.string(homeUrl + "/searchq/" + key, getHeaders());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVideoList(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(getHeaders()).string();
    }

    private List<Vod> parseVideoList(Document doc) {
        List<Vod> list = new ArrayList<>();
        // 实际结构: <div class="video-list-item col-6 col-sm-4 col-md-3">
        for (Element item : doc.select("div.video-list-item")) {
            Element a = item.selectFirst("a[href^='" + homeUrl + "/']");
            if (a == null) continue;
            String url = a.attr("href");
            // 提取番号作为ID: /zh/ADN-764 -> ADN-764
            String id = url.substring(homeUrl.length() + 1);
            // 取标题
            String name = "";
            Element h5 = item.selectFirst("h5.card-title");
            if (h5 != null) name = h5.text().trim();
            if (name.isEmpty()) continue;
            // 取封面
            String pic = "";
            Element img = item.selectFirst("img[data-src]");
            if (img != null) pic = img.attr("data-src");
            if (pic.startsWith("/")) pic = siteUrl + pic;
            // 过滤广告（没有番号的）
            if (!id.matches("^[A-Z]+-.+")) continue;
            list.add(new Vod(id, name, pic));
        }
        return list;
    }
}