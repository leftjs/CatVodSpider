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

import java.net.URLEncoder;
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

        // 标题
        String name = "";
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            name = h1.text().replace(" 免费AV在线看", "").replace(" FREE JAV BT", "").trim();
        }

        // 封面
        String pic = "";
        Element ogImg = doc.selectFirst("meta[property=og:image]");
        if (ogImg != null) pic = ogImg.attr("content");

        // 播放源：格式 "源名$URL#源名2$URL2"
        String playUrl = buildPlayUrl(html, ids.get(0));

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodPlayFrom("FreeJavBT");
        vod.setVodPlayUrl(playUrl);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        String html = OkHttp.string(homeUrl + "/searchq/" + URLEncoder.encode(key), getHeaders());
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVideoList(doc);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // id 格式: 原始番号|m3u8_url 或 原始番号|magnet_url
        // flag: "播放" / "备用" 等，和 vodPlayUrl 里的 $ 前面的名字对应
        String[] parts = id.split("\\|", 2);
        String url = parts.length > 1 ? parts[1] : id;
        return Result.get().url(url).header(getHeaders()).string();
    }

    // ============ 核心解析方法 ============

    /**
     * 构建播放URL字符串
     * 优先级: m3u8直链 > 多线路m3u8 > 磁力链
     * 格式: "播放$m3u8_url#备用$magnet_or_iframe_url"
     */
    private String buildPlayUrl(String html, String vid) {
        List<String[]> sources = new ArrayList<>();

        // 1. 找所有 m3u8 直链（来自 <video src> 或 <div data-m3u8>）
        List<String> m3u8s = extractM3u8Sources(html);
        if (!m3u8s.isEmpty()) {
            for (int i = 0; i < m3u8s.size(); i++) {
                String srcName = m3u8s.size() == 1 ? "播放" : ("播放" + (i + 1));
                sources.add(new String[]{srcName, m3u8s.get(i)});
            }
        }

        // 2. 如果没有 m3u8，尝试找磁力链
        if (sources.isEmpty()) {
            List<String> magnets = extractMagnets(html);
            if (!magnets.isEmpty()) {
                // 取第一个磁力链（去掉 &amp; 转义）
                String magnet = magnets.get(0).replace("&amp;", "&");
                sources.add(new String[]{"播放", magnet});
            }
        }

        // 3. 组合成 TVBox 格式: 播放$m3u8_url#备用$magnet_url
        if (sources.isEmpty()) {
            // 完全没有可用源，返回空（会显示无法播放）
            return "播放$" + vid;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) sb.append("#");
            sb.append(sources.get(i)[0]).append("$").append(sources.get(i)[1]);
        }
        return sb.toString();
    }

    /**
     * 从页面提取所有 m3u8 源
     * 优先从 visible 的 <video src> 取，其次从隐藏 tab-content 的 data-m3u8 取
     */
    private List<String> extractM3u8Sources(String html) {
        List<String> m3u8s = new ArrayList<>();

        // 方法1: <video src="xxx.m3u8">
        Pattern vp = Pattern.compile("<video[^>]+src\\s*=\\s*\"([^\"]+\\.m3u8[^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher vm = vp.matcher(html);
        while (vm.find()) {
            String u = vm.group(1).trim();
            if (!u.contains("vod.jpg")) m3u8s.add(u);
        }

        // 方法2: data-m3u8 属性（hidden tab-content 里的备用线路）
        Pattern dp = Pattern.compile("data-m3u8\\s*=\\s*\"([^\"]+\\.m3u8[^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher dm = dp.matcher(html);
        while (dm.find()) {
            String u = dm.group(1).trim();
            if (!u.contains("vod.jpg") && !m3u8s.contains(u)) m3u8s.add(u);
        }

        // 方法3: 正则后备（防止有 m3u8 但上面都没匹配到）
        if (m3u8s.isEmpty()) {
            Pattern fp = Pattern.compile("(https://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)", Pattern.CASE_INSENSITIVE);
            Matcher fm = fp.matcher(html);
            while (fm.find()) {
                String u = fm.group(1).trim();
                if (!u.contains("vod.jpg") && !m3u8s.contains(u)) m3u8s.add(u);
            }
        }

        return m3u8s;
    }

    /**
     * 从页面提取磁力链
     */
    private List<String> extractMagnets(String html) {
        List<String> magnets = new ArrayList<>();
        Pattern p = Pattern.compile("magnet:\\?xt=urn:btih:([a-fA-F0-9]{40})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String btih = m.group(1).toLowerCase();
            magnets.add("magnet:?xt=urn:btih:" + btih);
        }
        return magnets;
    }

    private List<Vod> parseVideoList(Document doc) {
        List<Vod> list = new ArrayList<>();
        for (Element item : doc.select("div.video-list-item")) {
            Element a = item.selectFirst("a[href^='" + homeUrl + "/']");
            if (a == null) continue;
            String url = a.attr("href");
            String id = url.substring(homeUrl.length() + 1);
            if (id.contains("/")) id = id.split("/")[0];

            String name = "";
            Element h5 = item.selectFirst("h5.card-title");
            if (h5 != null) name = h5.text().trim();
            if (name.isEmpty()) continue;

            String pic = "";
            Element img = item.selectFirst("img[data-src]");
            if (img != null) pic = img.attr("data-src");
            if (pic.startsWith("/")) pic = siteUrl + pic;

            // 过滤广告
            if (!id.matches("^[A-Z]+-.+")) continue;
            list.add(new Vod(id, name, pic));
        }
        return list;
    }
}