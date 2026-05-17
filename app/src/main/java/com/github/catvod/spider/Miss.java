package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Miss extends Spider {

    private final String url = "https://missav.ws/";
    private static final long TIMEOUT = 30000L;

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36");
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
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic);
        vod.setVodName(name);
        vod.setVodPlayFrom("MissAV");
        vod.setVodPlayUrl("播放$" + ids.get(0));
        return Result.string(vod);
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
        return Result.get().parse().url(url + id).string();
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
