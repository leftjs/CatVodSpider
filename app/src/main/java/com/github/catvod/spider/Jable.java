package com.github.catvod.spider;

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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class Jable extends Spider {

    private static final String siteUrl = "https://jable.tv";
    private static final String cateUrl = siteUrl + "/categories/";
    private static final String hotUrl = siteUrl + "/hot/";
    private static final String detailUrl = siteUrl + "/videos/";
    private static final String searchUrl = siteUrl + "/search/";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    private String getSortBy(String tid) {
        if (tid.startsWith("hot/")) {
            switch (tid) {
                case "hot/today": return "video_viewed_today";
                case "hot/week": return "video_viewed_week";
                case "hot/month": return "video_viewed_month";
                default: return "video_viewed";
            }
        }
        return null;
    }

    @Override
    public String homeContent(boolean filter) {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // Add ranking categories first
        classes.add(new Class("hot/all-time", "🔥 所有時間"));
        classes.add(new Class("hot/today", "🔥 今日熱門"));
        classes.add(new Class("hot/week", "🔥 本週熱門"));
        classes.add(new Class("hot/month", "🔥 本月熱門"));

        // Add regular categories from the site
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeaders()));
        for (Element element : doc.select("div.img-box > a")) {
            String typeId = element.attr("href").split("/")[4];
            String typeName = element.select("div.absolute-center > h4").text();
            classes.add(new Class(typeId, typeName));
        }

        // Parse home page video list
        doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        for (Element element : doc.select("div.video-img-box")) {
            String pic = element.select("img").attr("data-src");
            String url = element.select("a").attr("href");
            String name = element.select("div.detail > h6").text();
            if (pic.endsWith(".gif") || name.isEmpty()) continue;
            String id = url.split("/")[4];
            list.add(new Vod(id, name, pic));
        }
        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        String sortBy = getSortBy(tid);
        String target;

        if (sortBy != null) {
            // Ranking categories (hot/all-time, hot/today, etc.)
            target = hotUrl + "?mode=async&function=get_block&block_id=list_videos_common_videos_list" +
                     "&sort_by=" + sortBy +
                     "&from=" + String.format(Locale.getDefault(), "%02d", Integer.parseInt(pg)) +
                     "&_=" + System.currentTimeMillis();
        } else {
            // Regular categories
            target = cateUrl + tid + "/?mode=async&function=get_block&block_id=list_videos_common_videos_list" +
                     "&sort_by=post_date" +
                     "&from=" + String.format(Locale.getDefault(), "%02d", Integer.parseInt(pg)) +
                     "&_=" + System.currentTimeMillis();
        }

        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        for (Element element : doc.select("div.video-img-box")) {
            String pic = element.select("img").attr("data-src");
            String url = element.select("a").attr("href");
            String name = element.select("div.detail > h6").text();
            String id = url.split("/")[4];
            list.add(new Vod(id, name, pic));
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        Document doc = Jsoup.parse(OkHttp.string(detailUrl.concat(ids.get(0)).concat("/"), getHeaders()));
        String name = doc.select("meta[property=og:title]").attr("content");
        String pic = doc.select("meta[property=og:image]").attr("content");
        String year = doc.select("span.inactive-color").get(0).text();
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(pic);
        vod.setVodYear(year.replace("上市於 ", ""));
        vod.setVodName(name);
        vod.setVodPlayFrom("Jable");
        vod.setVodPlayUrl("播放$" + Util.getVar(doc.html(), "hlsUrl"));
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(searchUrl.concat(URLEncoder.encode(key)).concat("/"), getHeaders()));
        for (Element element : doc.select("div.video-img-box")) {
            String pic = element.select("img").attr("data-src");
            String url = element.select("a").attr("href");
            String name = element.select("div.detail > h6").text();
            String id = url.split("/")[4];
            list.add(new Vod(id, name, pic));
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(getHeaders()).string();
    }
}
