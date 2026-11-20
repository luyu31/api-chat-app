package com.local.apichat;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private static final int REQUEST_FILE_CHOOSER = 1001;
    private static final int REQUEST_CREATE_DOCUMENT = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private byte[] pendingSave;
    private String downloadBridgeScript;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(27, 26, 22));
        getWindow().setNavigationBarColor(Color.rgb(27, 26, 22));
        downloadBridgeScript = readAsset("download-bridge.js");

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.rgb(27, 26, 22));
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.addJavascriptInterface(new DownloadBridge(), "AndroidDownloads");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("file:///android_asset/")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(MainActivity.this, "无法打开该链接", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!downloadBridgeScript.isEmpty()) {
                    view.evaluateJavascript(downloadBridgeScript, null);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQUEST_FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl("file:///android_asset/www/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_CHOOSER) {
            if (filePathCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        if (requestCode == REQUEST_CREATE_DOCUMENT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
                writePendingSave(data.getData());
            } else {
                pendingSave = null;
            }
        }
    }

    private void writePendingSave(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IOException("无法打开目标文件");
            output.write(pendingSave);
            output.flush();
            Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingSave = null;
        }
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    public final class DownloadBridge {
        @JavascriptInterface
        public void saveBase64(final String base64, final String mimeType, final String filename) {
            try {
                final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pendingSave != null) {
                            Toast.makeText(MainActivity.this, "请先完成当前文件保存", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        pendingSave = bytes;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType(mimeType == null || mimeType.isEmpty()
                                ? "application/octet-stream" : mimeType);
                        intent.putExtra(Intent.EXTRA_TITLE, filename);
                        try {
                            startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
                        } catch (ActivityNotFoundException e) {
                            pendingSave = null;
                            Toast.makeText(MainActivity.this, "没有可用的文件保存界面", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (IllegalArgumentException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "导出内容解码失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}
