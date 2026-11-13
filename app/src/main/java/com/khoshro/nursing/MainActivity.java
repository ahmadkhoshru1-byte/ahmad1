package com.khoshro.nursing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILE = 1;
    private static final int PAGE_BG = 0xFFF1F5F9;

    private WebView web;
    private ValueCallback<Uri[]> filePathCb;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(PAGE_BG);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMinimumFontSize(1);
        s.setMinimumLogicalFontSize(1);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(false);
        }

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("باشه", (d, i) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("بله", (d, i) -> r.confirm())
                        .setNegativeButton("خیر", (d, i) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "انتخاب فایل"), REQ_FILE);
                } catch (Exception e) {
                    filePathCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("https://" + HOST + "/assets/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (filePathCb != null) {
                filePathCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
                filePathCb = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidApp");
            web.destroy();
        }
        super.onDestroy();
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    private void shareBytes(String name, byte[] data, String mime, String text) throws Exception {
        File dir = new File(getCacheDir(), "shared");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
        Uri uri = FileProvider.getUriForFile(this, "com.khoshro.nursing.fileprovider", f);
        final Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        runOnUiThread(() -> startActivity(Intent.createChooser(i, "ارسال")));
    }

    private class Bridge {

        @JavascriptInterface
        public void saveFile(String name, String b64, String mime) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u == null) throw new Exception("insert failed");
                    try (OutputStream o = getContentResolver().openOutputStream(u)) {
                        o.write(data);
                    }
                    toast("ذخیره شد در پوشه Downloads: " + name);
                } else {
                    shareBytes(name, data, mime, null);
                }
            } catch (Exception e) {
                toast("خطا در ذخیره فایل");
            }
        }

        @JavascriptInterface
        public void shareFile(String name, String b64, String mime, String text) {
            try {
                shareBytes(name, Base64.decode(b64, Base64.DEFAULT), mime, text);
            } catch (Exception e) {
                toast("خطا در ارسال فایل");
            }
        }

        @JavascriptInterface
        public void shareText(String text) {
            final Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, "ارسال")));
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    android.print.PrintManager pm = (android.print.PrintManager) getSystemService(PRINT_SERVICE);
                    android.print.PrintAttributes attrs = new android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A3.asLandscape())
                            .build();
                    pm.print("roster", web.createPrintDocumentAdapter("roster"), attrs);
                } catch (Exception e) {
                    toast("چاپ در دسترس نیست");
                }
            });
        }
    }
}
