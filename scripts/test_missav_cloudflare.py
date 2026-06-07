#!/usr/bin/env python3
"""
Test script demonstrating the Cloudflare JS challenge problem with missav.ws.

PROBLEM:
--------
missav.ws is protected by Cloudflare JS challenge. When you make a direct HTTP
request (using OkHttp in Java, or requests/urllib in Python), Cloudflare returns
HTTP 403 because:
1. The request lacks the Cloudflare clearance cookie (cf_clearance)
2. No JavaScript engine is executing to solve the JS challenge

OKHTTP/requests CANNOT bypass Cloudflare because they are just HTTP clients -
they don't have a JS engine to execute the Cloudflare JS challenge code.

SOLUTION:
---------
TVBox app has a WebView component that CAN execute JavaScript. When we use
Result.parse() mode in the Spider, TVBox will:
1. Load the URL in its WebView
2. WebView executes JS -> solves Cloudflare challenge
3. Cloudflare sets clearance cookie
4. Page content is rendered and accessible
5. TVBox sniffs video URLs from the rendered content

This script demonstrates both the problem and the correct approach.
"""

import requests
import json

MISS_AV_URL = "https://missav.ws/"
TEST_VIDEO_ID = "ja/ipbz-023"  # Example video path


def demonstrate_403_problem():
    """
    Demonstrate that direct HTTP requests to missav.ws get 403.
    This is what happens when OkHttp tries to fetch the page.
    """
    print("=" * 70)
    print("DEMONSTRATING CLOUDFLARE 403 PROBLEM")
    print("=" * 70)
    print()

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.8",
    }

    print(f"Making direct HTTP request to: {MISS_AV_URL}")
    print(f"Headers: {headers}")
    print()

    try:
        response = requests.get(MISS_AV_URL, headers=headers, timeout=30, allow_redirects=True)
        print(f"Status Code: {response.status_code}")
        print(f"Response Headers: {dict(response.headers)}")
        print()

        if response.status_code == 403:
            print("❌ PROBLEM CONFIRMED: HTTP 403 Forbidden")
            print("   Cloudflare is blocking direct HTTP requests.")
            print("   OkHttp (Java HTTP client) would get the same 403.")
            print()
            print("   WHY THIS HAPPENS:")
            print("   1. Cloudflare sees a plain HTTP request without clearance cookie")
            print("   2. No JavaScript engine is executing to solve the JS challenge")
            print("   3. CF blocks the request with 403 Forbidden")
            return True  # Problem confirmed
        else:
            print(f"⚠️  Unexpected status: {response.status_code}")
            return False

    except requests.exceptions.RequestException as e:
        print(f"❌ Request failed: {e}")
        return True  # Problem confirmed (connection failure also shows CF is blocking)


def show_correct_parse_approach():
    """
    Show what the correct Result.parse() approach returns.
    This is what TVBox needs to use to make the webview handle CF.
    """
    print()
    print("=" * 70)
    print("CORRECT APPROACH: Result.parse() mode")
    print("=" * 70)
    print()

    print("When we use Result.parse() mode in Miss.java, TVBox receives:")
    print()

    # Simulate what Result.get().parse().url(url).header(headers).string() returns
    result = {
        "parse": 1,  # This tells TVBox to use webview
        "url": MISS_AV_URL,
        "header": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...",
            "Referer": MISS_AV_URL,
        }
    }

    print("JSON returned to TVBox:")
    print(json.dumps(result, indent=2, ensure_ascii=False))
    print()

    print("KEY INSIGHT:")
    print("-" * 40)
    print("parse=1 signals TVBox to use WebView for this URL")
    print("The WebView has a JS engine and can:")
    print("  1. Execute Cloudflare's JS challenge")
    print("  2. Obtain the cf_clearance cookie")
    print("  3. Render the page content")
    print("  4. Sniff video URLs from the rendered DOM")
    print()

    print("WHAT HAPPENS WITH OkHttp (WRONG):")
    print("-" * 40)
    print("OkHttp.string(url) -> 403 Forbidden")
    print("No JS execution, no CF clearance cookie")
    print()

    print("WHAT HAPPENS WITH WebView (CORRECT):")
    print("-" * 40)
    print("WebView loads URL -> JS executes -> CF challenge solved")
    print("cf_clearance cookie obtained -> Page renders")
    print("Video URLs (m3u8, mp4) become accessible")
    print()


def show_homecontent_example():
    """
    Show what homeContent returns in parse() mode.
    """
    print()
    print("=" * 70)
    print("homeContent() in parse() mode")
    print("=" * 70)
    print()

    # Categories are hardcoded since we can't fetch them (CF blocks)
    categories = [
        {"type_id": "dm1", "type_name": "步兵"},
        {"type_id": "dm2", "type_name": "騎兵"},
        {"type_id": "dm3", "type_name": "中文字幕"},
        {"type_id": "dm4", "type_name": "無碼"},
        {"type_id": "dm5", "type_name": "VR"},
        {"type_id": "dm6", "type_name": "成人"},
        {"type_id": "dm7", "type_name": "新婚"},
        {"type_id": "dm8", "type_name": "人妻"},
    ]

    result = {
        "class": categories,
        "filters": {cat["type_id"]: [{"key": "filters", "name": "過濾", "values": [
            {"n": "全部", "v": ""},
            {"n": "單人作品", "v": "individual"},
            {"n": "中文字幕", "v": "chinese-subtitle"}
        ]}] for cat in categories},
        "parse": 1,  # KEY: Webview mode
        "url": MISS_AV_URL,
        "header": {
            "User-Agent": "Mozilla/5.0...",
            "Referer": MISS_AV_URL,
        }
    }

    print("TVBox receives this JSON for homeContent:")
    print(json.dumps(result, indent=2, ensure_ascii=False)[:800] + "...")
    print()
    print("TVBox will use WebView to fetch the home page and render the video list.")


def show_playercontent_example():
    """
    Show what playerContent returns in parse() mode.
    """
    print()
    print("=" * 70)
    print("playerContent() in parse() mode")
    print("=" * 70)
    print()

    video_id = TEST_VIDEO_ID

    result_direct = {
        "url": f"https://some-cdn.com/video/{video_id}/1080p.mp4",
        "header": {"Referer": "https://missav.ws/"},
    }

    result_parse = {
        "parse": 1,  # KEY: Webview mode for non-m3u8 IDs
        "url": f"{MISS_AV_URL}{video_id}",
        "header": {
            "User-Agent": "Mozilla/5.0...",
            "Referer": MISS_AV_URL,
        }
    }

    print("When ID contains .m3u8 (direct stream):")
    print(json.dumps(result_direct, indent=2))
    print()

    print("When ID is a page path (needs JS rendering):")
    print(json.dumps(result_parse, indent=2))
    print()

    print("In the second case, TVBox WebView will:")
    print("1. Load the page URL")
    print("2. Execute JS to solve Cloudflare challenge")
    print("3. Find video element or m3u8 URL in rendered DOM")
    print("4. Extract and play the video")


if __name__ == "__main__":
    print()
    print("╔" + "═" * 68 + "╗")
    print("║" + " " * 20 + "MISS.AV CLOUDFLARE TEST" + " " * 23 + "║")
    print("╚" + "═" * 68 + "╝")
    print()

    # Demonstrate the problem
    problem_found = demonstrate_403_problem()

    # Show correct approach
    show_correct_parse_approach()

    # Show examples
    show_homecontent_example()
    show_playercontent_example()

    print()
    print("=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print()
    print("PROBLEM: OkHttp cannot bypass Cloudflare JS challenge -> HTTP 403")
    print()
    print("SOLUTION: Use Result.parse() mode so TVBox uses WebView")
    print("  - WebView has JS engine to solve CF challenge")
    print("  - After challenge solved, page content is accessible")
    print("  - Video URLs can be extracted from rendered DOM")
    print()
    print("KEY CODE CHANGE in Miss.java:")
    print("  Before: OkHttp.string(url) -> 403")
    print("  After:  Result.get().parse().url(url).header(headers).string()")
    print()