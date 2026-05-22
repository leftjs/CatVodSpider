package com.github.catvod.spider;

import android.util.Base64;

import com.github.catvod.bean.Class;
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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavGuru extends Spider {

    private static final String siteUrl = "https://jav.guru";
    private static final String categoryPath = "/category/jav/";

    // Sort options: key -> display name
    private static final String[][] SORT_OPTIONS = {
        {"date", "最新"},
        {"likes-today", "今日热门"},
        {"views-monthly", "本月观看"},
        {"views", "总观看"},
        {"likes", "总点赞"},
        {"dislikes", "踩"},
        {"comments", "评论数"},
        {"release-year", "发行日期"}
    };

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl + "/");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();

        // Build 8 sort categories
        for (String[] opt : SORT_OPTIONS) {
            classes.add(new Class(opt[0], opt[1]));
        }

        // Fetch default (Recent) page for home video list
        String html = OkHttp.string(siteUrl + categoryPath, getHeaders());
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.inside-article");
        for (Element item : items) {
            Element a = item.selectFirst("div.grid1 h2 a");
            if (a == null) continue;
            String detailUrl = a.attr("href");
            if (detailUrl.contains("/category/") || detailUrl.contains("/tag/") || detailUrl.contains("/page/")) continue;

            String pic = item.selectFirst("div.imgg img").attr("src");
            String title = a.text().trim();
            if (title.isEmpty()) continue;

            list.add(new Vod(detailUrl, title, pic));
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        String url;
        int pageNum = Integer.parseInt(pg);

        if (tid.equals("date") || tid.isEmpty()) {
            if (pageNum == 1) {
                url = siteUrl + categoryPath;
            } else {
                url = siteUrl + categoryPath + "page/" + pageNum + "/";
            }
        } else {
            if (pageNum == 1) {
                url = siteUrl + categoryPath + "?orderby=" + tid + "&order=DESC";
            } else {
                url = siteUrl + categoryPath + "?orderby=" + tid + "&order=DESC&paged=" + pageNum;
            }
        }

        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.inside-article");
        for (Element item : items) {
            Element a = item.selectFirst("div.grid1 h2 a");
            if (a == null) continue;
            String detailUrl = a.attr("href");
            if (detailUrl.contains("/category/") || detailUrl.contains("/tag/") || detailUrl.contains("/page/")) continue;

            String pic = item.selectFirst("div.imgg img").attr("src");
            String title = a.text().trim();
            if (title.isEmpty()) continue;

            list.add(new Vod(detailUrl, title, pic));
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);

        // Title
        String title = doc.select("meta[property=og:title]").attr("content");
        if (title.isEmpty()) {
            Element h1 = doc.selectFirst("h1.entry-title");
            if (h1 != null) title = h1.text().trim();
        }

        // Thumbnail
        String pic = doc.select("meta[property=og:image]").attr("content");
        if (pic.isEmpty()) {
            Element img = doc.selectFirst(".entry-content img");
            if (img != null) pic = img.attr("src");
        }

        // Extract all iframe_url Base64 values
        List<String> playUrls = extractPlayUrls(html);

        Vod vod = new Vod();
        vod.setVodId(url);
        vod.setVodName(title);
        vod.setVodPic(pic);
        vod.setVodPlayFrom("JavGuru");

        if (playUrls.isEmpty()) {
            // No extractable sources - return detail URL for iframe playback
            vod.setVodPlayUrl("播放$" + url);
        } else if (playUrls.size() == 1) {
            vod.setVodPlayUrl("播放$" + playUrls.get(0));
        } else {
            // Multiple sources: 线路1$url1#线路2$url2
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < playUrls.size(); i++) {
                if (i > 0) sb.append("#");
                sb.append("线路").append(i + 1).append("$").append(playUrls.get(i));
            }
            vod.setVodPlayUrl(sb.toString());
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // If already a direct m3u8/mp4 URL, return as-is
        if (id.contains(".m3u8") || id.contains(".mp4")) {
            return Result.get().url(id).header(getHeaders()).string();
        }

        // Otherwise id is a javclan/jav.guru embed URL - load in webview (JS executes)
        return Result.get().parse().url(id).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/?s=" + URLEncoder.encode(key);
        String html = OkHttp.string(url, getHeaders());
        Document doc = Jsoup.parse(html);
        List<Vod> list = new ArrayList<>();

        Elements items = doc.select("div.inside-article");
        for (Element item : items) {
            Element a = item.selectFirst("div.grid1 h2 a");
            if (a == null) continue;
            String detailUrl = a.attr("href");
            if (detailUrl.contains("/category/") || detailUrl.contains("/tag/") || detailUrl.contains("/page/") || detailUrl.contains("/search/")) continue;

            String pic = item.selectFirst("div.imgg img").attr("src");
            String title = a.text().trim();
            if (title.isEmpty()) continue;

            list.add(new Vod(detailUrl, title, pic));
        }

        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    /**
     * Extract all javclan embed URLs from detail page.
     * Each source is stored as a Base64-encoded iframe_url in a JS variable.
     * Decoding gives a searcho redirect URL which 302s to javclan.com embed.
     *
     * We return the javclan embed URL directly so CatVod webview can load it.
     * The JWPlayer JS in the page executes and plays the video.
     */
    private List<String> extractPlayUrls(String html) {
        List<String> urls = new ArrayList<>();
        Pattern p = Pattern.compile("iframe_url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']");
        Matcher m = p.matcher(html);
        while (m.find()) {
            try {
                String b64 = m.group(1);
                String decoded = new String(Base64.decode(b64, Base64.DEFAULT));
                if (decoded.contains("jav.guru/searcho")) {
                    // decoded = https://jav.guru/searcho/?xd=TOKEN&bg=THUMB_URL
                    // We need to follow this redirect to get the javclan URL
                    // For CatVod parse mode, returning the searcho URL works because
                    // the webview follows the redirect to javclan automatically
                    urls.add(decoded);
                }
            } catch (Exception e) {
                // Skip invalid Base64
            }
        }
        return urls;
    }
}
