package com.github.catvod.spider;

import android.content.Context;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Miss extends Spider {

    private final String url = "https://missav.ws/";
    private static final long TIMEOUT = 30000L;

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
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
    public void init(Context context) throws Exception {
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        // missav.ws is protected by Cloudflare JS challenge
        // OkHttp cannot bypass it - must use parse() mode so TVBox webview handles CF
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // Define categories - these are static, no need to fetch from page
        String[] categoryIds = {"dm1", "dm2", "dm3", "dm4", "dm5", "dm6", "dm7", "dm8"};
        String[] categoryNames = {"步兵", "騎兵", "中文字幕", "無碼", "VR", "成人", "新婚", "人妻"};

        for (int i = 0; i < categoryIds.length; i++) {
            classes.add(new Class(categoryIds[i], categoryNames[i]));
            filters.put(categoryIds[i], Arrays.asList(
                new Filter("filters", "過濾", Arrays.asList(
                    new Filter.Value("全部", ""),
                    new Filter.Value("單人作品", "individual"),
                    new Filter.Value("中文字幕", "chinese-subtitle")
                ))
            ));
        }

        // Return with parse=1 so TVBox uses webview to fetch home page
        // The webview will handle Cloudflare JS challenge
        return Result.get().classes(classes).filters(filters).parse().url(url).header(getHeaders()).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // Build category URL with filters
        String target = url + tid;
        String filters = extend.get("filters");
        if (TextUtils.isEmpty(filters)) {
            target += "?page=" + pg;
        } else {
            target += "?filters=" + filters + "&page=" + pg;
        }

        // Use parse() mode so TVBox webview handles Cloudflare
        return Result.get().parse().url(target).header(getHeaders()).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        // Use parse() mode - TVBox webview will fetch the page and extract video
        // This is REQUIRED for Cloudflare-protected sites because:
        // 1. OkHttp cannot execute JS -> Cloudflare returns 403
        // 2. TVBox webview CAN execute JS -> Cloudflare challenge passes
        return Result.get().parse().url(url + ids.get(0)).header(getHeaders()).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // Use parse() mode so TVBox webview handles Cloudflare
        String searchUrl = url + "search/" + key;
        return Result.get().parse().url(searchUrl).header(getHeaders()).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // If id contains .m3u8, it's a direct URL - return as-is
        if (id.contains(".m3u8")) {
            return Result.get().url(id).header(getHeaders()).string();
        }

        // For page paths, use parse() mode so webview handles CF
        // The webview will load the page, execute JS, and find the video URL
        return Result.get().parse().url(url + id).header(getHeaders()).string();
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        // Return true to enable video format detection via webview sniffing
        return true;
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        // Allow TVBox to sniff for video URLs (m3u8, mp4, etc.) in webview content
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains("surrit.com");
    }
}