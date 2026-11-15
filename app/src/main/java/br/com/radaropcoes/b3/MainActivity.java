package br.com.radaropcoes.b3;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    // Usa o Radar web original, que executa as consultas B3 no servidor.
    private static final String ONLINE_URL = "https://radar-opcoes-b3.lovable.app/";
    private static final String ONLINE_HOST = "radar-opcoes-b3.lovable.app";
    private static final String OFFLINE_HOST = "appassets.androidplatform.net";
    private static final String OFFLINE_URL = "https://" + OFFLINE_HOST + "/assets/index.html";
    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;

    private WebView webView;
    private boolean offline = false;
    private long pausedAt = 0L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Atualiza apenas o painel principal em primeiro plano. Não interrompe o montador.
    private final Runnable periodicRefresh = new Runnable() {
        @Override public void run() {
            if (webView != null) {
                String page = webView.getUrl();
                if (!offline && page != null) {
                    Uri uri = Uri.parse(page);
                    if (ONLINE_HOST.equals(uri.getHost()) &&
                        (uri.getPath() == null || "/".equals(uri.getPath()))) {
                        webView.reload();
                    }
                }
                mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 13, 20));
        getWindow().setNavigationBarColor(Color.rgb(8, 13, 20));

        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());

        WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("https".equals(scheme) &&
                    (ONLINE_HOST.equals(uri.getHost()) || OFFLINE_HOST.equals(uri.getHost()))) {
                    return false;
                }
                if ("https".equals(scheme) || "http".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) { /* Sem navegador */ }
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Se o servidor web estiver inacessivel, manter o montador manual local.
                if (request.isForMainFrame() && ONLINE_HOST.equals(request.getUrl().getHost()) && !offline) {
                    offline = true;
                    view.post(() -> {
                        if (webView != null) webView.loadUrl(OFFLINE_URL);
                    });
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && ONLINE_HOST.equals(Uri.parse(url).getHost())) offline = false;
            }
        });

        webView.loadUrl(ONLINE_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            if (pausedAt > 0 && System.currentTimeMillis() - pausedAt >= REFRESH_INTERVAL_MS) {
                if (offline) { offline = false; webView.loadUrl(ONLINE_URL); }
                else webView.reload();
            }
            webView.onResume();
            mainHandler.removeCallbacks(periodicRefresh);
            mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        pausedAt = System.currentTimeMillis();
        mainHandler.removeCallbacks(periodicRefresh);
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(periodicRefresh);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
