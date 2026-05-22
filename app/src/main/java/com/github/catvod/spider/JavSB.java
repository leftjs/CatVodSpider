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
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavSB extends Spider {

    private static final String siteUrl = "https://jav.sb";
    private static final String siteApi = siteUrl + "/en";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Connection", "keep-alive");
        headers.put("Referer", siteApi + "/");
        if (!currentCookie.isEmpty()) {
            headers.put("Cookie", currentCookie);
        }
        return headers;
    }

    private String currentCookie = "";

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 8 categories
        String[] names = {"有码", "无码", "FC2", "馬賽克去除", "MGS", "成人偶像", "亞洲業餘", "歐美"};
        String[] slugs = {"Censored", "Uncensored", "FC2-PPV", "Mosaic_Removed", "MGS", "Adult_IDOL", "Asian_Amateur", "Western_Porn"};

        for (int i = 0; i < names.length; i++) {
            classes.add(new Class(slugs[i], names[i]));
        }

        // Filter: 4 sort options for each category
        for (String slug : slugs) {
            List<Filter> sortFilters = new ArrayList<>();
            sortFilters.add(new Filter("orderby", "排序", Arrays.asList(
                new Filter.Value("最新更新", "publish_time"),
                new Filter.Value("月度观看", "hits_month"),
                new Filter.Value("周度观看", "hits_week"),
                new Filter.Value("最多点赞", "likes")
            )));
            filters.put(slug, sortFilters);
        }

        // Separate top-level entries for recently updated and monthly view per category
        // These don't need filter - they go directly to the right URL
        for (int i = 0; i < slugs.length; i++) {
            classes.add(new Class("R_" + slugs[i], names[i] + "-最新"));
            classes.add(new Class("M_" + slugs[i], names[i] + "-月度"));
        }

        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        String target;
        int page = Integer.parseInt(pg);

        if (tid.startsWith("R_")) {
            // Recently updated - direct category page
            String category = tid.substring(2);
            target = siteApi + "/javtype/" + category + "-" + page + ".html";
        } else if (tid.startsWith("M_")) {
            // Monthly views
            String category = tid.substring(2);
            target = siteApi + "/vod/show/by/hits_month/id/" + category + "/page/" + page + ".html";
        } else if (tid.startsWith("W_")) {
            // Weekly views
            String category = tid.substring(2);
            target = siteApi + "/vod/show/by/hits_week/id/" + category + "/page/" + page + ".html";
        } else if (tid.startsWith("L_")) {
            // Most liked
            String category = tid.substring(2);
            target = siteApi + "/vod/show/by/likes/id/" + category + "/page/" + page + ".html";
        } else {
            // Plain category slug with filter
            String orderby = "publish_time";
            if (extend != null && extend.containsKey("orderby")) {
                orderby = extend.get("orderby");
            }
            if (orderby.equals("publish_time")) {
                target = siteApi + "/javtype/" + tid + "-" + page + ".html";
            } else {
                target = siteApi + "/vod/show/by/" + orderby + "/id/" + tid + "/page/" + page + ".html";
            }
        }

        String html = OkHttp.string(target, getHeaders());
        Document doc = Jsoup.parse(html);
        parseVideoList(doc, list);

        return Result.string(list);
    }

    private void parseVideoList(Document doc, List<Vod> list) {
        Elements items = doc.select("div.video-item");
        if (items.isEmpty()) {
            items = doc.select("div.col-md-3, div.col-lg-3");
        }

        for (Element item : items) {
            Element a = item.selectFirst("a[href*='/jav/']");
            if (a == null) continue;

            String href = a.attr("href");
            String id = extractVideoId(href);
            if (id.isEmpty()) continue;

            Element img = a.selectFirst("img");
            String pic = img != null ? img.attr("data-src") : "";
            if (pic.isEmpty() && img != null) pic = img.attr("src");
            if (pic.startsWith("//")) pic = "https:" + pic;
            if (pic.startsWith("/")) pic = siteUrl + pic;

            String name = img != null ? img.attr("alt") : "";
            if (name.isEmpty()) name = a.attr("title");
            if (name.isEmpty()) name = a.text().trim();
            name = name.trim();
            if (name.isEmpty() || pic.isEmpty() || pic.contains("data:image")) continue;

            // Duration from img title or nearby span
            String remarks = "";
            Element durationEl = item.selectFirst(".duration");
            if (durationEl != null) {
                remarks = durationEl.text().trim();
            }

            list.add(new Vod(id, name, pic, remarks));
        }

        // Fallback: try generic link parsing
        if (list.isEmpty()) {
            Elements links = doc.select("a[href*='/jav/'][href$='-1.html'], a[href*='/jav/'][href$='-1-1.html']");
            for (Element a : links) {
                String href = a.attr("href");
                String id = extractVideoId(href);
                if (id.isEmpty()) continue;

                Element img = a.selectFirst("img");
                String pic = "";
                if (img != null) {
                    pic = img.attr("data-src");
                    if (pic.isEmpty()) pic = img.attr("src");
                    if (pic.startsWith("//")) pic = "https:" + pic;
                    if (pic.startsWith("/")) pic = siteUrl + pic;
                }

                String name = img != null ? img.attr("alt") : "";
                if (name.isEmpty()) name = a.text().trim();
                name = name.trim();
                if (name.isEmpty() || pic.isEmpty() || pic.contains("data:image")) continue;

                list.add(new Vod(id, name, pic, ""));
            }
        }
    }

    private String extractVideoId(String href) {
        // href like /en/jav/fns-093-1-1.html or /jav/fns-093-1.html
        // result: fns-093
        Pattern p = Pattern.compile("/jav/([a-zA-Z0-9-]+)-1-1\\.html");
        Matcher m = p.matcher(href);
        if (m.find()) return m.group(1);
        p = Pattern.compile("/jav/([a-zA-Z0-9-]+)-1\\.html");
        m = p.matcher(href);
        if (m.find()) return m.group(1);
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        String vid = ids.get(0);
        if (!vid.contains("-1-1")) {
            vid = vid + "-1-1";
        }
        String url = siteApi + "/jav/" + vid + ".html";
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        String name = doc.select("meta[property=og:title]").attr("content");
        if (name.isEmpty()) {
            Element h1 = doc.selectFirst("h1");
            name = h1 != null ? h1.text().trim() : vid;
        }
        String pic = doc.select("meta[property=og:image]").attr("content");
        if (pic.startsWith("//")) pic = "https:" + pic;

        String duration = "";
        Pattern durationPattern = Pattern.compile("Duration\\s*:\\s*(\\d+:\\d+:\\d+)");
        Matcher m = durationPattern.matcher(doc.text());
        if (m.find()) duration = m.group(1);

        String date = "";
        Pattern datePattern = Pattern.compile("Release Date\\s*:\\s*(\\d{4}-\\d{2}-\\d{2})");
        Matcher dm = datePattern.matcher(doc.text());
        if (dm.find()) date = dm.group(1);

        String actress = "";
        Element actressEl = doc.selectFirst("a[href*='/actresses/']");
        if (actressEl != null) actress = actressEl.text().trim();

        // Extract m3u8 from player
        String m3u8 = extractM3u8(doc, url);

        Vod vod = new Vod();
        vod.setVodId(vid.replace("-1-1", ""));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodDuration(duration);
        vod.setVodYear(date);
        vod.setVodActor(actress);

        if (!m3u8.isEmpty()) {
            vod.setVodPlayFrom("JavSB");
            vod.setVodPlayUrl("播放$" + m3u8);
        } else {
            vod.setVodPlayFrom("JavSB");
            vod.setVodPlayUrl("播放$" + url);
        }

        return Result.string(vod);
    }

    private String extractM3u8(Document doc, String refererUrl) {
        // Find player iframe
        Element playerIframe = doc.selectFirst("iframe[src*='videojs.html']");
        if (playerIframe == null) return "";

        String playerSrc = playerIframe.attr("src");
        Pattern tokenPattern = Pattern.compile("[?&]src=([^&]+)");
        Matcher tm = tokenPattern.matcher(playerSrc);
        if (!tm.find()) return "";

        String token = tm.group(1);
        String pic = "";
        Matcher picMatcher = Pattern.compile("[?&]pic=([^&]+)").matcher(playerSrc);
        if (picMatcher.find()) {
            pic = picMatcher.group(1);
        }

        // Call the player API to get m3u8
        String apiUrl = siteApi + "/videojs/out.php?src=" + token + "&pic=" + URLEncoder.encode(pic);
        HashMap<String, String> headers = getHeaders();
        headers.put("Referer", refererUrl);
        headers.put("X-Requested-With", "XMLHttpRequest");

        try {
            String response = OkHttp.string(apiUrl, headers);
            if (response.contains(".m3u8")) {
                Pattern m3u8Pattern = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*");
                Matcher m = m3u8Pattern.matcher(response);
                if (m.find()) return m.group();
            }
            // Try to follow redirect
            String location = OkHttp.getLocation(apiUrl, headers);
            if (location != null && location.contains(".m3u8")) return location;
        } catch (Exception e) {
            // Ignore
        }

        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        List<Vod> list = new ArrayList<>();
        String target = siteApi + "/vod/search.html?wd=" + URLEncoder.encode(key) + "&page=" + pg;
        String html = OkHttp.string(target, getHeaders());
        Document doc = Jsoup.parse(html);
        parseVideoList(doc, list);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (id.contains(".m3u8")) {
            return Result.get().url(id).header(getHeaders()).string();
        }
        return Result.get().url(id).header(getHeaders()).string();
    }
}
