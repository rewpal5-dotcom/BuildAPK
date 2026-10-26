package com.buildapk.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;

/**
 * BuildAPK — native shell around https://freewebtoapk.com/generator/website-url
 *
 * Features
 *  - top progress bar while the page loads
 *  - back button walks the page history
 *  - offline / error screen with retry
 *  - zoom lock: device-width layout, no pinch zoom, no left-right panning
 *  - system bar insets (Android 15 edge-to-edge safe)
 *  - downloads handed to Android's DownloadManager
 *  - external links open outside the app
 *  - AD BLOCKER: ad/tracker hosts are blocked at request level, ad frames and
 *    ad containers are hidden, and pop-up windows are disabled. This app shows
 *    no ads of its own.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://freewebtoapk.com/generator/website-url";
    private static final String OWN_HOST = "freewebtoapk.com";

    /** Our own ad tag (quge5 zone 302399) — the only ad this app shows. */
    private static final String OWN_AD_SCRIPT = "https://quge5.com/88/tag.min.js";
    private static final String OWN_AD_ZONE = "302399";

    /** Domains belonging to our own ad provider — always allowed. */
    private static final String[] ALLOWED_AD_HOSTS = { "quge5.com", "5gvci.com" };

    private WebView web;
    private ProgressBar bar;
    private View errorView;

    /** Hosts blocked at request level (suffix match). Ad and tracker networks only. */
    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "fundingchoicesmessages.google.com", "2mdn.net",
            "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
            "mgid.com", "revcontent.com", "bidvertiser.com", "adblade.com", "content.ad",
            "propellerads.com", "propellerads.net", "popads.net", "popcash.net", "popmyads.com",
            "adsterra.com", "hilltopads.net", "exoclick.com", "juicyads.com", "trafficjunky.net",
            "onclckds.com", "clickadu.com", "adcash.com", "adspyglass.com", "adnium.com",
            "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "openx.com",
            "smartadserver.com", "33across.com", "sharethrough.com", "zedo.com",
            "quantserve.com", "scorecardresearch.com", "moatads.com", "adsafeprotected.com",
            "adform.net", "adroll.com", "media.net", "lijit.com", "sovrn.com", "gumgum.com",
            "applovin.com", "unityads.unity3d.com", "chartboost.com", "inmobi.com",
            "startapp.com", "start.io", "vungle.com", "ironsrc.com", "fyber.com",
            "adcolony.com", "mopub.com", "smaato.net", "adpushup.com", "ezoic.net",
            "monetag.com", "profitablecpmgate.com", "displaycontentnetwork.com",
            "googletagmanager.com", "google-analytics.com", "hotjar.com", "clarity.ms"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#07090f"));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        column.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));

        errorView = buildErrorView();
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        /* Android 15+ draws edge-to-edge by default — keep content clear of the bars */
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }
                v.setPadding(left, top, right, bottom);
                return insets;
            }
        });
        root.requestApplyInsets();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        /* zoom lock */
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);

        /* no pop-up windows (a common ad format) */
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);

        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                bar.setProgress(newProgress);
                bar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && isAdHost(host)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                bar.setVisibility(View.GONE);
                if (errorView != null) errorView.setVisibility(View.GONE);
                lockLayout(view);
                hideAdSlots(view);
                injectOwnAd(view);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.startsWith("http")) showError();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                try {
                    String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimeType);
                    req.addRequestHeader("User-Agent", userAgent);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) req.addRequestHeader("Cookie", cookie);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(req);
                    Toast.makeText(MainActivity.this, "Download shuru: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    private boolean isAdHost(String host) {
        String h = host.toLowerCase();
        for (String allowed : ALLOWED_AD_HOSTS) {
            if (h.equals(allowed) || h.endsWith("." + allowed)) return false;   // our own ad
        }
        for (String blocked : AD_HOSTS) {
            if (h.equals(blocked) || h.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (isAdHost(host)) return true;                      // block ad navigations
            if (host.endsWith(OWN_HOST)) return false;            // keep the tool inside
            startActivity(new Intent(Intent.ACTION_VIEW, uri));   // everything else outside
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
        return true;
    }

    /** No pinch zoom, no horizontal panning — fixed app-like viewport. */
    private void lockLayout(WebView view) {
        String js = "(function(){"
                + "var c='width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no';"
                + "var m=document.querySelector('meta[name=viewport]');"
                + "if(m){m.setAttribute('content',c);}else{var x=document.createElement('meta');"
                + "x.setAttribute('name','viewport');x.setAttribute('content',c);"
                + "(document.head||document.documentElement).appendChild(x);}"
                + "var st=document.getElementById('__bp_lock');"
                + "if(!st){st=document.createElement('style');st.id='__bp_lock';"
                + "st.innerHTML='html,body{overflow-x:hidden !important;max-width:100% !important;}'"
                + "(document.head||document.documentElement).appendChild(st);}"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    /** Injects our own ad tag (quge5 zone 302399) in a fixed slot at the bottom of the page. */
    private void injectOwnAd(WebView view) {
        String js = "(function(){"
                + "if(document.getElementById('bp_own_ad'))return;"
                + "var box=document.createElement('div');box.id='bp_own_ad';"
                + "box.style.cssText='position:fixed;left:0;right:0;bottom:0;z-index:2147483000;display:block;"
                + "min-height:64px;background:rgba(7,9,15,.97);border-top:1px solid rgba(255,255,255,.10);"
                + "padding:4px 0 6px;text-align:center;overflow:hidden;';"
                + "(document.body||document.documentElement).appendChild(box);"
                + "var s=document.createElement('script');"
                + "s.src='" + OWN_AD_SCRIPT + "';"
                + "s.async=true;"
                + "s.setAttribute('data-zone','" + OWN_AD_ZONE + "');"
                + "s.setAttribute('data-cfasync','false');"
                + "box.appendChild(s);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    /** Hides other networks' ad containers (never touches our own #bp_own_ad). */
    private void hideAdSlots(WebView view) {
        String js = "(function(){"
                + "if(document.getElementById('__bp_ads'))return;"
                + "var st=document.createElement('style');st.id='__bp_ads';"
                + "st.innerHTML='ins.adsbygoogle,iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"],"
                + "iframe[src*=\"adservice\"],iframe[src*=\"adsterra\"],iframe[src*=\"propellerads\"],"
                + "div[id^=\"google_ads\"],div[id*=\"div-gpt-ad\"],div[class*=\"adsbygoogle\"],"
                + "div[class*=\"ad-slot\"],div[class*=\"ad-banner\"],div[id*=\"banner-ad\"],"
                + ".ad-container:not(#bp_own_ad *),.ad-wrapper:not(#bp_own_ad *),"
                + ".ad-holder:not(#bp_own_ad *),[data-ad-slot]:not(#bp_own_ad *){display:none !important;"
                + "height:0 !important;min-height:0 !important;}"
                + "#bp_own_ad,#bp_own_ad *{display:block !important;visibility:visible !important;}';"
                + "(document.head||document.documentElement).appendChild(st);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private View buildErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#07090f"));
        box.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView title = new TextView(this);
        title.setText("Internet nahi mila");
        title.setTextColor(Color.parseColor("#e8eefc"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Connection check karke dobara try karein.");
        sub.setTextColor(Color.parseColor("#8fa0b8"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(18));
        box.addView(sub);

        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (errorView != null) errorView.setVisibility(View.GONE);
                bar.setVisibility(View.VISIBLE);
                bar.setProgress(0);
                web.loadUrl(START_URL);
            }
        });
        box.addView(retry);

        return box;
    }

    private void showError() {
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
        bar.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }
}
