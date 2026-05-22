# CatVodSpider 开发指南

> 本项目是一个 Android TV 视频聚合爬虫，支持对接多种视频源（成人网站、AList、Bili等）。本文档介绍如何编写新的网站爬虫并将其添加到 `adult.json` 配置中。

---

## 目录

1. [项目架构](#项目架构)
2. [爬虫基类 Spider 详解](#爬虫基类-spider-详解)
3. [核心数据结构](#核心数据结构)
4. [编写新爬虫的完整流程](#编写新爬虫的完整流程)
5. [添加到 adult.json](#添加到-adultjson)
6. [构建与发布](#构建与发布)
7. [常用工具类](#常用工具类)
8. [示例：参考现有爬虫](#示例参考现有爬虫)

---

## 项目架构

```
app/src/main/java/com/github/catvod/
├── spider/          # 所有爬虫实现类（继承 Spider）
├── crawler/         # Spider 基类（抽象类）
├── bean/            # 数据模型（Vod, Class, Result, Filter 等）
├── net/             # 网络请求封装（OkHttp）
└── utils/           # 工具类
```

### json 配置文件

| 文件 | 用途 |
|------|------|
| `adult.json` | 成人内容源配置 |
| `config.json` | 普通内容源配置 |
| `bili.json` | Bili 专用配置 |

### adult.json 结构概览

```json
{
  "spider": "https://raw.githubusercontent.com/leftjs/CatVodSpider/main/jar/custom_spider.jar;md5;xxxx",
  "wallpaper": "https://...",
  "sites": [
    {
      "key": "Jable",          // 站点唯一标识（对应 Spider 类名）
      "name": "Jable",         // 显示名称
      "type": 3,               // 类型：0=XML API, 1=JSON API, 3=自定义爬虫
      "api": "csp_Jable",      // 动态类名 → com.github.catvod.spider.Jable
      "searchable": 1,         // 是否支持搜索
      "changeable": 0,         // 是否可修改
      "ext": "..."             // 扩展参数（可选）
    }
  ]
}
```

---

## 爬虫基类 Spider 详解

所有爬虫必须继承 `com.github.catvod.crawler.Spider`，重写以下核心方法：

### 方法对照表

| 方法 | 必须重写 | 说明 | 返回格式 |
|------|---------|------|---------|
| `homeContent(boolean filter)` | ✅ | 首页：分类 + 视频列表 | `Result.string(classes, list, filters)` |
| `categoryContent(tid, pg, filter, extend)` | ✅ | 分类页/列表页 | `Result.string(list)` |
| `detailContent(List<String> ids)` | ✅ | 详情页：视频信息 | `Result.string(vod)` |
| `searchContent(key, quick)` | ✅ | 搜索 | `Result.string(list)` |
| `playerContent(flag, id, vipFlags)` | ✅ | 播放解析 | `Result.get().url(id).header(headers).string()` |
| `homeVideoContent()` | ❌ | 首页推荐（部分网站需要） | - |
| `liveContent(url)` | ❌ | 直播（一般不需要） | - |
| `manualVideoCheck()` | ❌ | 手动检测视频URL | - |
| `isVideoFormat(url)` | ❌ | 检测URL是否为视频 | - |
| `proxy(params)` | ❌ | 代理（高级用法） | - |

### Result 返回方式

```java
// 返回首页（分类 + 列表）
return Result.string(classes, list, filters);

// 返回视频列表
return Result.string(list);

// 返回单个视频详情
return Result.string(vod);

// 返回播放地址（直接播放URL）
return Result.get().url(videoUrl).string();

// 返回播放地址（带header，如UA/Referer）
return Result.get().url(videoUrl).header(headers).string();

// 需要解析时（如m3u8需要二次解析）
return Result.get().parse().url(videoUrl).header(headers).string();

// 搜索结果
return Result.string(list);

// 分类列表
return Result.string(classes, list, filters);

// 错误提示
return Result.error("获取失败");
```

---

## 核心数据结构

### Vod（视频条目）

```java
Vod vod = new Vod();
vod.setVodId("视频唯一ID");          // 重要：用于 detailContent 定位
vod.setVodName("视频标题");
vod.setVodPic("封面图URL");
vod.setVodRemarks("时长/更新信息");  // 可选，如 "01:30:00" 或 "更新至第3集"
vod.setVodYear("2024");              // 可选，年份
vod.setVodActor("演员");             // 可选
vod.setVodDirector("导演");          // 可选
vod.setVodContent("简介");           // 可选
vod.setVodPlayFrom("Jable");        // 必须：播放源名称
vod.setVodPlayUrl("播放$视频页面URL或直链");
// 或者组合写法：
// new Vod(id, name, pic)                   // 最简
// new Vod(id, name, pic, remarks)           // 带备注
// new Vod(id, name, pic, remarks, style)   // 带样式
```

**Vod 构造函数速查：**
```java
new Vod(String vodId, String vodName, String vodPic)
new Vod(String vodId, String vodName, String vodPic, String vodRemarks)
new Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style)
```

### Class（分类）

```java
// 简单分类
new Class("typeId", "分类名称")

// 带标识的分类（用于特殊标识如排序）
new Class("hot/week", "🔥 本周热播", "hot")
```

### Filter（筛选条件）

```java
// 筛选条件，配合 categoryContent 的 extend 参数使用
LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
filters.put("typeId", Arrays.asList(
    new Filter("key", "显示名", Arrays.asList(
        new Filter.Value("全部", ""),
        new Filter.Value("单人中字", "chinese"),
        new Filter.Value("VR", "vr")
    ))
));
```

### playerContent 返回格式

```java
// 方式1：直链视频（直接返回URL，框架播放）
return Result.get().url("https://example.com/video.m3u8").string();

// 方式2：直链+请求头
HashMap<String, String> headers = new HashMap<>();
headers.put("User-Agent", Util.CHROME);
headers.put("Referer", "https://referer.com/");
return Result.get().url("https://example.com/video.m3u8").header(headers).string();

// 方式3：需要解析的页面（播放器会尝试解析）
return Result.get().parse().url("https://player.example.com/play?id=xxx").string();

// 方式4：指定格式（m3u8/dash/rtsp）
return Result.get().url(url).m3u8().string();
return Result.get().url(url).dash().string();
```

---

## 编写新爬虫的完整流程

### 第一步：创建爬虫类文件

在 `app/src/main/java/com/github/catvod/spider/` 下新建文件，例如 `MySite.java`：

```java
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

public class MySite extends Spider {

    // 站点基础URL
    private static final String siteUrl = "https://www.mysite.com";
    private static final String searchUrl = siteUrl + "/search/";

    // HTTP 请求头（大多数网站需要 UA 和 Referer）
    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", siteUrl);
        return headers;
    }

    // ===== 首页：分类 + 视频列表 =====
    @Override
    public String homeContent(boolean filter) {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();

        // 1. 解析网站分类，添加到 classes
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        for (Element item : doc.select("div.category-item")) {
            String typeId = item.select("a").attr("href").split("/")[4];
            String typeName = item.select("span.title").text();
            classes.add(new Class(typeId, typeName));
        }

        // 2. 解析首页视频列表
        for (Element card : doc.select("div.video-card")) {
            String id = card.select("a").attr("href").split("/")[4];
            String name = card.select("h3.title").text();
            String pic = card.select("img.cover").attr("data-src");
            String remarks = card.select("span.duration").text();
            list.add(new Vod(id, name, pic, remarks));
        }

        // 3. 如果有筛选条件，添加到 filters
        filters.put("categoryId", Arrays.asList(
            new Filter("sort", "排序", Arrays.asList(
                new Filter.Value("最新", ""),
                new Filter.Value("最热", "hot"),
                new Filter.Value("评分", "rating")
            ))
        ));

        return Result.string(classes, list, filters);
    }

    // ===== 分类/列表页 =====
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        String target = siteUrl + "/category/" + tid + "?page=" + pg;

        // 支持筛选参数
        if (extend != null && extend.containsKey("sort")) {
            target += "&sort=" + extend.get("sort");
        }

        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        for (Element card : doc.select("div.video-card")) {
            String id = card.select("a").attr("href").split("/")[4];
            String name = card.select("h3.title").text();
            String pic = card.select("img.cover").attr("data-src");
            String remarks = card.select("span.duration").text();
            list.add(new Vod(id, name, pic, remarks));
        }
        return Result.string(list);
    }

    // ===== 详情页 =====
    @Override
    public String detailContent(List<String> ids) {
        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "/video/" + ids.get(0), getHeaders()));

        String name = doc.select("meta[property=og:title]").attr("content");
        String pic = doc.select("meta[property=og:image]").attr("content");
        String actor = doc.select("span.actor").text();
        String year = doc.select("span.year").text();

        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(name);
        vod.setVodPic(pic);
        vod.setVodActor(actor);
        vod.setVodYear(year);
        vod.setVodPlayFrom("MySite");
        vod.setVodPlayUrl("播放$" + getVideoUrl(ids.get(0)));  // "播放$" 是固定格式
        return Result.string(vod);
    }

    // ===== 搜索 =====
    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(searchUrl + URLEncoder.encode(key), getHeaders()));
        for (Element card : doc.select("div.video-card")) {
            String id = card.select("a").attr("href").split("/")[4];
            String name = card.select("h3.title").text();
            String pic = card.select("img.cover").attr("data-src");
            list.add(new Vod(id, name, pic));
        }
        return Result.string(list);
    }

    // ===== 播放（重要！）=====
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // 方式A：直接返回直链
        // return Result.get().url(id).string();

        // 方式B：直链+headers
        return Result.get().url(getVideoUrl(id)).header(getHeaders()).string();

        // 方式C：需要解析
        // return Result.get().parse().url(getVideoUrl(id)).header(getHeaders()).string();
    }

    // ===== 辅助方法：获取真实视频URL =====
    private String getVideoUrl(String videoId) {
        // 有些网站详情页不直接给视频URL，需要再请求一次或从页面提取
        return videoId;
    }
}
```

### 第二步：添加到 adult.json

在 `adult.json` 的 `sites` 数组中添加一条：

```json
{
  "key": "MySite",
  "name": "我的网站",
  "type": 3,
  "api": "csp_MySite",
  "searchable": 1,
  "changeable": 0
}
```

**type 类型说明：**
- `0` = XML 格式视频API（如 `https://xxx/api.php/provide/vod/?ac=list`）
- `1` = JSON 格式视频API
- `3` = 自定义爬虫（对应 Java 类，通过 `api` 字段的 `csp_` 前缀映射）

### 第三步：构建 jar 并发布

```bash
# 完整流程
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/root/android-sdk
cd /root/CatVodSpider

# 1. 构建 APK
./gradlew assembleRelease

# 2. 生成 custom_spider.jar（从 APK 提取爬虫代码）
bash jar/genJar.sh

# 3. 更新 adult.json MD5（脚本自动完成）
bash scripts/build_and_push.sh "feat: 添加 MySite 爬虫"

# 或者手动更新 MD5 并提交
NEW_MD5=$(cat jar/custom_spider.jar.md5)
sed -i "s/md5;[a-f0-9]\{32\}/md5;$NEW_MD5/g" json/adult.json
git add jar/custom_spider.jar jar/custom_spider.jar.md5 json/adult.json
git commit -m "feat: 添加 MySite 爬虫"
git push origin main
```

---

## 常用工具类

### OkHttp（网络请求）

```java
// GET 请求，返回 HTML/JSON 字符串
String html = OkHttp.string("https://example.com/page");
String json = OkHttp.string("https://api.example.com/data", headers);

// GET 带超时（毫秒）
String html = OkHttp.string("https://example.com", null, headers, 30000L);

// POST 请求
String result = OkHttp.post("https://api.example.com/post", params);
OkResult okResult = OkHttp.post("https://api.example.com/post", params, headers);

// 获取重定向后的真实 URL
String realUrl = OkHttp.getLocation(url, headers);
```

### Util（工具常量）

```java
Util.CHROME   // "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ..."
Util.MOBILE   // 移动端 UA
Util.getVar(String html, String key)  // 从 HTML 中提取 JS 变量，如 getVar(html, "hlsUrl")
```

### Result（返回构造）

```java
// 分页信息
return Result.get().vod(list).page(page, pageCount, limit, total).string();

// 弹幕
return Result.get().vod(vod).danmaku(danmakuList).string();

// 多种播放源
vod.setVodPlayFrom("SourceA$SourceB");           // 多源用 $ 分隔
vod.setVodPlayUrl("播放1$url1$$播放2$url2");       // 格式：名称$URL$名称$URL
```

---

## 示例：参考现有爬虫

推荐按复杂度从低到高参考以下爬虫：

| 爬虫 | 文件 | 特点 |
|------|------|------|
| **Jable** | `spider/Jable.java` | 分类+列表+详情+搜索完整流程，有 Async AJAX 请求处理 |
| **Miss** | `spider/Miss.java` | 有 Filter 筛选条件，详情页返回 ID 由播放器解析 |
| **FreeJavBT** | `spider/FreeJavBT.java` | 较简单，适合入门 |

### Jable 爬虫工作流解析

```
1. homeContent()
   ├─ 获取 siteUrl 首页 HTML
   ├─ 解析分类 div.img-box → Class
   ├─ 解析视频 div.video-img-box → Vod
   └─ 返回 Result.string(classes, list, filters)

2. categoryContent()
   ├─ 判断是否为 hot/ 排序分类
   ├─ 拼接 AJAX 请求 URL（含分页参数）
   └─ 解析视频列表返回

3. detailContent()
   └─ 获取视频页 HTML → 提取 og:title, og:image, hlsUrl
       └─ 返回 Result.string(vod)  // vodPlayUrl = "播放$" + hlsUrl

4. searchContent()
   └─ 搜索页 HTML → 解析视频卡片 → Vod 列表

5. playerContent()
   └─ 直接返回 m3u8 直链 + headers
       └─ Result.get().url(id).header(headers).string()
```

---

## 常见问题排查

**Q: 视频无法播放？**
- 检查 `playerContent` 返回的 URL 是否为直链（m3u8/mp4）
- 检查是否需要 `parse()` 模式
- 检查 `header` 是否包含必要的 Referer/User-Agent

**Q: 分类/列表为空？**
- 网站可能改版，DOM 结构变化，需要更新 CSS Selector
- 网站可能有反爬，需要添加/修改请求头

**Q: MD5 不匹配？**
- 每次 `genJar.sh` 后 jar MD5 会变
- 必须同步更新 `adult.json` 和 `custom_spider.jar.md5`

**Q: 找不到 `csp_XXX` 类？**
- 类名必须精确匹配：`api` 字段 `csp_XXX` → `com.github.catvod.spider.XXX`
- 检查类是否放在正确的包路径下
