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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeJavBT extends Spider {

    private static final String siteUrl = "https://freejavbt.com";
    private static final String homeUrl = siteUrl + "/zh";
    private static final String detailUrl = homeUrl + "/";
    private static final String searchUrl = homeUrl + "/searchq/";

    // 大类: 有码/无码/欧美/FC2
    private static final String[] TYPE_KEYS  = {"censored", "uncensored", "western", "fc2"};
    private static final String[] TYPE_NAMES  = {"有码", "无码", "欧美", "FC2"};
    // 排行榜: 日/周/月/女优
    private static final String[] RANK_KEYS   = {"day", "week", "month", "actress"};
    private static final String[] RANK_NAMES  = {"日榜", "週榜", "月榜", "女优榜"};

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", homeUrl);
        headers.put("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8");
        return headers;
    }

    private String fixPic(String pic) {
        if (pic.startsWith("/")) return siteUrl + pic;
        return pic;
    }

    @Override
    public String homeContent(boolean filter) {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 每个大类下挂四个排行榜
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            for (int j = 0; j < RANK_KEYS.length; j++) {
                classes.add(new Class(TYPE_KEYS[i] + "/" + RANK_KEYS[j], TYPE_NAMES[i] + RANK_NAMES[j]));
            }
        }

        // 首页最新有码影片
        Document doc = Jsoup.parse(OkHttp.string(homeUrl + "/censored", getHeaders()));
        for (Element item : doc.select("div.category-page.video-list-item")) {
            Element a = item.selectFirst("a[href*=" + homeUrl.replace("/", "\\/") + "]");
            if (a == null) continue;
            String url = a.attr("href");
            String name = item.selectFirst("h5.card-title.text-dark").text();
            String pic = fixPic(item.selectFirst("img[data-src]").attr("data-src"));
            // 过滤广告条目（第一个通常是广告，没有正确的番号标题）
            if (name.isEmpty() || name.length() < 3) continue;
            // 从 URL 取番号作为 ID
            String id = url.substring(homeUrl.length() + 1);
            if (id.contains("/")) id = id.split("/")[0];
            list.add(new Vod(id, name, pic));
        }
        return Result.string(classes, list, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        String target;
        boolean isRank = tid.contains("/");

        if (isRank) {
            String[] parts = tid.split("/");
            target = String.format(homeUrl + "/rank/%s/%s", parts[0], parts[1]);
        } else {
            target = homeUrl + "/" + tid;
        }

        if (pg != null && !pg.equals("1")) {
            target += "?page=" + pg;
        }

        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        for (Element item : doc.select("div.category-page.video-list-item")) {
            Element a = item.selectFirst("a[href*=" + homeUrl.replace("/", "\\/") + "]");
            if (a == null) continue;
            String url = a.attr("href");
            String name = item.selectFirst("h5.card-title.text-dark").text();
            String pic = fixPic(item.selectFirst("img[data-src]").attr("data-src"));
            if (name.isEmpty() || name.length() < 3) continue;
            String id = url.substring(homeUrl.length() + 1);
            if (id.contains("/")) id = id.split("/")[0];
            list.add(new Vod(id, name, pic));
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        String detailHtml = OkHttp.string(detailUrl + ids.get(0), getHeaders());
        Document doc = Jsoup.parse(detailHtml);

        // 标题
        Element h1 = doc.selectFirst("h1");
        String name = h1 != null ? h1.text().replace(" 免费AV在线看", "").trim() : ids.get(0);

        // 封面
        String pic = fixPic(doc.selectFirst("meta[property=og:image]").attr("content"));

        // 从 HTML 中取 m3u8
        String m3u8 = "";
        Pattern p = Pattern.compile("m3u8=([^\"']+)");
        Matcher m = p.matcher(detailHtml);
        if (m.find()) {
            m3u8 = m.group(1);
        }

        // 备选：从按钮 data 属性取
        if (m3u8.isEmpty()) {
            Element btn = doc.selectFirst("[data-m3u8]");
            if (btn != null) m3u8 = btn.attr("data-m3u8");
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
        List<Vod> list = new ArrayList<>();
        String url = searchUrl + URLEncoder.encode(key);
        Document doc = Jsoup.parse(OkHttp.string(url, getHeaders()));
        for (Element item : doc.select("div.category-page.video-list-item")) {
            Element a = item.selectFirst("a[href*=" + homeUrl.replace("/", "\\/") + "]");
            if (a == null) continue;
            String href = a.attr("href");
            String name = item.selectFirst("h5.card-title.text-dark").text();
            String pic = fixPic(item.selectFirst("img[data-src]").attr("data-src"));
            if (name.isEmpty() || name.length() < 3) continue;
            String id = href.substring(homeUrl.length() + 1);
            if (id.contains("/")) id = id.split("/")[0];
            list.add(new Vod(id, name, pic));
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).header(getHeaders()).string();
    }
}