package com.dipti.medical;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String OFFLINE_URL = "file:///android_asset/offline.html";

    private WebView webView;
    private ProgressBar progress;
    private SwipeRefreshLayout swipe;
    private String startUrl;
    private String lastOnlineUrl;
    private boolean pageReady = false;
    private long lastBackPress = 0;

    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooser;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> !pageReady);
        setContentView(R.layout.activity_main);

        startUrl = getString(R.string.start_url);
        lastOnlineUrl = startUrl;
        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);
        swipe = findViewById(R.id.swipe);

        // Never hold the splash longer than 4s, even on slow networks
        webView.postDelayed(() -> pageReady = true, 4000);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);       // offline page assets only
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " DiptiMedicalApp/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());
        webView.setDownloadListener(this::handleDownload);

        swipe.setColorSchemeResources(R.color.dipti_blue, R.color.dipti_green);
        swipe.setOnRefreshListener(() -> {
            if (OFFLINE_URL.equals(webView.getUrl())) loadOrOffline(lastOnlineUrl);
            else webView.reload();
        });
        // Only allow pull-to-refresh when the page is scrolled to the very top
        webView.getViewTreeObserver().addOnScrollChangedListener(
                () -> swipe.setEnabled(webView.getScrollY() == 0));

        fileChooser = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (filePathCallback == null) return;
                    filePathCallback.onReceiveValue(
                            WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                    filePathCallback = null;
                });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (webView.canGoBack() && !OFFLINE_URL.equals(webView.getUrl())) {
                    webView.goBack();
                } else if (System.currentTimeMillis() - lastBackPress < 2000) {
                    finish();
                } else {
                    lastBackPress = System.currentTimeMillis();
                    Toast.makeText(MainActivity.this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            pageReady = true;
        } else {
            Uri deepLink = getIntent().getData();
            loadOrOffline(deepLink != null ? deepLink.toString() : startUrl);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) loadOrOffline(intent.getData().toString());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override protected void onPause() { super.onPause(); webView.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); webView.onResume(); }

    private void loadOrOffline(String url) {
        if (isOnline()) webView.loadUrl(url);
        else { lastOnlineUrl = url; webView.loadUrl(OFFLINE_URL); }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void handleDownload(String url, String userAgent, String contentDisposition, String mimeType, long len) {
        try {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "This file can't be downloaded in the app", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);
            req.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
            req.addRequestHeader("User-Agent", userAgent);
            req.setTitle(name);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            openExternal(Uri.parse(url));
        }
    }

    private boolean openExternal(Uri uri) {
        try {
            Intent i;
            if ("intent".equals(uri.getScheme())) {
                i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | java.net.URISyntaxException e) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            // WhatsApp / tel / mailto / upi / maps etc. go to native apps
            if (!scheme.equals("http") && !scheme.equals("https")) { openExternal(uri); return true; }
            if (host.endsWith("wa.me") || host.endsWith("whatsapp.com")
                    || host.endsWith("maps.google.com") || host.endsWith("play.google.com")) {
                openExternal(uri); return true;
            }
            return false; // everything else (site pages, payment gateways, auth) stays in-app
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageReady = true;
            swipe.setRefreshing(false);
            if (!OFFLINE_URL.equals(url)) lastOnlineUrl = url;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                lastOnlineUrl = request.getUrl().toString();
                view.loadUrl(OFFLINE_URL);
            }
        }
    }

    private class AppChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = callback;
            try {
                fileChooser.launch(params.createIntent());
            } catch (ActivityNotFoundException e) {
                filePathCallback = null;
                return false;
            }
            return true;
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> loadOrOffline(lastOnlineUrl));
        }
    }
}
