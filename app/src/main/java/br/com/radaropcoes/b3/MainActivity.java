package br.com.radaropcoes.b3;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    // Preencher com o endpoint HTTPS de um servidor que retorne o JSON esperado
    // pelo arquivo assets/index.html. Deixe vazio até o backend estar ativo.
    private static final String RADAR_API_URL = "";
    private static final String LOCAL_HOST = "appassets.androidplatform.net";

    private WebView webView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 13, 20));
        getWindow().setNavigationBarColor(Color.rgb(8, 13, 20));

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());

        WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (LOCAL_HOST.equals(uri.getHost()) && "/api/radar".equals(uri.getPath())) {
                    return loadRadarApi();
                }
                return assets.shouldInterceptRequest(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && LOCAL_HOST.equals(uri.getHost())) return false;
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                        // Sem navegador externo disponível.
                    }
                }
                return true;
            }
        });

        webView.loadUrl("https://" + LOCAL_HOST + "/assets/index.html");
    }

    private static WebResourceResponse loadRadarApi() {
        if (RADAR_API_URL.isEmpty()) {
            return errorJson(503, "Atualização online ainda não configurada: é necessário hospedar o backend /api/radar. O montador manual permanece disponível.");
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(RADAR_API_URL);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return errorJson(400, "A fonte de dados precisa usar HTTPS.");
            }
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setInstanceFollowRedirects(false);
            int status = conn.getResponseCode();
            InputStream data = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (data == null) return errorJson(502, "Servidor de cotações não retornou dados.");
            // Encaminha o JSON do backend para a aplicação, sem inventar preços.
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-store");
            String reason = (status >= 200 && status < 300) ? "OK" : "Bad Gateway";
            return new WebResourceResponse("application/json", "UTF-8", status, reason, headers, data);
        } catch (Exception e) {
            if (conn != null) conn.disconnect();
            return errorJson(502, "Falha ao buscar cotações: " + e.getClass().getSimpleName());
        }
    }

    private static WebResourceResponse errorJson(int status, String message) {
        try {
            String body = new JSONObject().put("error", message).toString();
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse(
                "application/json", "UTF-8", status,
                "Service Unavailable", headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            return new WebResourceResponse("application/json", "UTF-8",
                new ByteArrayInputStream("{\"error\":\"Falha na API\"}".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
