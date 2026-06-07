package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Miss extends Spider {

    private final String url = "https://missav.ws/";
    private static final long TIMEOUT = 30000L;

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", url);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8");
        return headers;
    }

    private boolean isVideoCategory(String href) {
        return href.startsWith("dm") && !href.contains("VR");
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        Document doc = Jsoup.parse(OkHttp.string(url, null, getHeaders(), TIMEOUT));

        // Parse categories from nav submenu items
        for (Element a : doc.select("nav a.text-nord0")) {
            String href = a.attr("href").replace(url, "");
            if (isVideoCategory(href)) {
                String typeId = href;
                String typeName = a.text();
                classes.add(new Class(typeId, typeName));
                filters.put(typeId, Arrays.asList(
                    new Filter("filters", "過濾", Arrays.asList(
                        new Filter.Value("全部", ""),
                        new Filter.Value("單人作品", "individual"),
                        new Filter.Value("中文字幕", "chinese-subtitle")
                    ))
                ));
            }
        }

        // Parse home page video list
        for (Element card : doc.select("div.thumbnail")) {
            Vod vod = parseVideoCard(card);
            if (vod != null) list.add(vod);
        }

        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String target = url + tid;
        String filters = extend.get("filters");
        if (TextUtils.isEmpty(filters)) {
            target += "?page=" + pg;
        } else {
            target += "?filters=" + filters + "&page=" + pg;
        }

        Document doc = Jsoup.parse(OkHttp.string(target, null, getHeaders(), TIMEOUT));
        for (Element card : doc.select("div.thumbnail")) {
            Vod vod = parseVideoCard(card);
            if (vod != null) list.add(vod);
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(url + ids.get(0), null, getHeaders(), TIMEOUT));
        String name = doc.select("meta[property=og:title]").attr("content");
        String pic = doc.select("meta[property=og:image]").attr("content");

        // Extract m3u8 URL from video element
        String m3u8 = extractM3u8(doc);

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic);
        vod.setVodName(name);
        vod.setVodPlayFrom("MissAV");

        if (!m3u8.isEmpty()) {
            vod.setVodPlayUrl("播放$" + m3u8);
        } else {
            vod.setVodPlayUrl("播放$" + ids.get(0));
        }

        return Result.string(vod);
    }

    private String extractM3u8(Document doc) {
        // Try to find m3u8 URL directly in video.src attribute
        Element video = doc.selectFirst("video.player");
        if (video != null) {
            String src = video.attr("src");
            if (!TextUtils.isEmpty(src) && src.contains(".m3u8")) {
                return src;
            }
        }

        // Try to find m3u8 URL in script tags with obfuscated code
        // Pattern: obfuscated code contains URLs like https://surrit.com/{uuid}/{quality}/video.m3u8
        Pattern m3u8Pattern = Pattern.compile("https://surrit\\.com/[a-f0-9-]+/[a-z0-9]+/video\\.m3u8");
        for (Element script : doc.select("script")) {
            String scriptContent = script.html();
            Matcher m = m3u8Pattern.matcher(scriptContent);
            if (m.find()) {
                return m.group();
            }
        }

        // Try to find any m3u8 URL in page
        Pattern anyM3u8 = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*");
        String pageText = doc.html();
        Matcher mm = anyM3u8.matcher(pageText);
        if (mm.find()) {
            return mm.group();
        }

        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(url + "search/" + key, null, getHeaders(), TIMEOUT));
        for (Element card : doc.select("div.thumbnail")) {
            Vod vod = parseVideoCard(card);
            if (vod != null) list.add(vod);
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // If id contains .m3u8, it's the direct URL
        if (id.contains(".m3u8")) {
            return Result.get().url(id).header(getHeaders()).string();
        }
        // Otherwise id is the page path, extract m3u8 from it
        Document doc = Jsoup.parse(OkHttp.string(url + id, null, getHeaders(), TIMEOUT));
        String m3u8 = extractM3u8(doc);
        if (!m3u8.isEmpty()) {
            return Result.get().url(m3u8).header(getHeaders()).string();
        }
        return Result.get().url(url + id).header(getHeaders()).string();
    }

    private Vod parseVideoCard(Element card) {
        String id = card.select("div.text-xs.truncate a").attr("href").replace(url, "");
        String name = card.select("div.my-2.text-sm.truncate a").text();
        String pic = card.select("img.lozad").attr("data-src");
        if (pic.isEmpty()) pic = card.select("img.lozad").attr("src");
        String remark = card.select("span.rounded-lg").text();
        if (TextUtils.isEmpty(name)) name = card.select("div.text-xs.truncate a").text();
        if (TextUtils.isEmpty(name)) return null;
        return new Vod(id, name, pic, remark);
    }
}