package com.github.catvod.spider;

import android.util.Base64;

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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavGuru extends Spider {

    private static final String siteUrl = "https://jav.guru";
    private static final String rankUrl = siteUrl + "/most-watched-rank/";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        classes.add(new Class("today", "TODAY"));
        classes.add(new Class("week", "WEEK"));
        classes.add(new Class("month", "MONTH"));
        classes.add(new Class("alltime", "ALLTIME NONSUB"));

        String html = OkHttp.string(rankUrl, getHeaders());
        list.addAll(parseVideosFromHtml(html, "today"));

        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String html = OkHttp.string(rankUrl, getHeaders());
        list.addAll(parseVideosFromHtml(html, tid));
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = siteUrl + "/" + ids.get(0);
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        String name = doc.select("meta[property=og:title]").attr("content");
        if (name.isEmpty()) {
            Element h1 = doc.select("h1").first();
            name = h1 != null ? h1.text() : "";
        }

        String pic = doc.select("meta[property=og:image]").attr("content");

        Map<String, String> streamUrls = extractStreamUrls(doc);

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodPlayFrom("JavGuru");
        vod.setVodPlayUrl("播放$" + ids.get(0));

        if (!streamUrls.isEmpty()) {
            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();
            int idx = 0;
            for (Map.Entry<String, String> entry : streamUrls.entrySet()) {
                if (idx > 0) {
                    playFrom.append("$");
                    playUrl.append("$$");
                }
                playFrom.append(entry.getKey());
                playUrl.append("播放").append(idx + 1).append("$").append(entry.getValue());
                idx++;
            }
            vod.setVodPlayFrom(playFrom.toString());
            vod.setVodPlayUrl(playUrl.toString());
        }

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        String searchUrl = siteUrl + "/?s=" + java.net.URLEncoder.encode(key, "UTF-8");
        String html = OkHttp.string(searchUrl, getHeaders());
        Document doc = Jsoup.parse(html);

        for (Element article : doc.select("article")) {
            Element a = article.select("a[href*=/" + "\\d+" + "/]").first();
            if (a == null) continue;

            String articleUrl = a.attr("href");
            String id = extractVideoId(articleUrl);
            if (id.isEmpty()) continue;

            String name = article.select("img").attr("alt");
            if (name.isEmpty()) name = article.select("h2, h3").text();
            if (name.isEmpty()) continue;

            String pic = article.select("img").attr("src");
            if (pic.contains("data:")) pic = article.select("img").attr("data-src");
            if (pic.isEmpty()) continue;

            String remarks = "";
            Element viewsEl = article.select(".views, .post-views").first();
            if (viewsEl != null) remarks = viewsEl.text();

            list.add(new Vod(id, name, pic, remarks));
        }

        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String videoUrl = id;

        if (id.matches("\\d+")) {
            String detailUrl = siteUrl + "/" + id;
            String html = OkHttp.string(detailUrl, getHeaders());
            Document doc = Jsoup.parse(html);
            Map<String, String> streams = extractStreamUrls(doc);
            if (!streams.isEmpty()) {
                videoUrl = streams.values().iterator().next();
            }
        }

        if (videoUrl.contains("jav.guru/searcho/") || videoUrl.contains("jav.guru/?")) {
            String redirectUrl = OkHttp.getLocation(videoUrl, getHeaders());
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                videoUrl = redirectUrl;
            }
        }

        if (videoUrl.matches("^[A-Za-z0-9+/=]{20,}$")) {
            try {
                byte[] decoded = Base64.decode(videoUrl, Base64.DEFAULT);
                String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                String redirectUrl = OkHttp.getLocation(decodedStr, getHeaders());
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    videoUrl = redirectUrl;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return Result.get().url(videoUrl).header(getHeaders()).string();
    }

    private List<Vod> parseVideosFromHtml(String html, String tabType) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Set<String> seenIds = new HashSet<>();

        for (Element item : doc.select("article")) {
            Element a = item.select("a[href*=/]").first();
            if (a == null) continue;

            String href = a.attr("href");
            if (!href.contains("/jav.guru/")) continue;

            String id = extractVideoId(href);
            if (id.isEmpty() || seenIds.contains(id)) continue;
            seenIds.add(id);

            String name = a.select("img").attr("alt");
            if (name.isEmpty()) name = a.text();
            if (name.isEmpty()) continue;

            String pic = a.select("img").attr("data-src");
            if (pic.isEmpty()) pic = a.select("img").attr("src");

            String remarks = "";
            Element viewsEl = item.select(".views, .post-views").first();
            if (viewsEl != null) remarks = viewsEl.text().trim();

            list.add(new Vod(id, name, pic, remarks));
        }

        return list;
    }

    private String extractVideoId(String url) {
        try {
            Pattern p = Pattern.compile("/(\\d+)/[^/]+/");
            Matcher m = p.matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    private Map<String, String> extractStreamUrls(Document doc) {
        Map<String, String> streams = new LinkedHashMap<>();
        String html = doc.html();

        Pattern p = Pattern.compile("iframe_url[\"'\\s:]+([^\"']+)");
        Matcher m = p.matcher(html);

        int count = 0;
        while (m.find() && count < 10) {
            String base64Url = m.group(1).replace(":", "").replace(" ", "");
            try {
                byte[] decoded = Base64.decode(base64Url, Base64.DEFAULT);
                String decodedUrl = new String(decoded, StandardCharsets.UTF_8);
                decodedUrl = URLDecoder.decode(decodedUrl, "UTF-8");

                String finalUrl = OkHttp.getLocation(decodedUrl, getHeaders());
                if (finalUrl == null || finalUrl.isEmpty()) {
                    finalUrl = decodedUrl;
                }

                streams.put("Stream" + (count + 1), finalUrl);
                count++;
            } catch (Exception e) {
                // ignore
            }
        }

        return streams;
    }
}
