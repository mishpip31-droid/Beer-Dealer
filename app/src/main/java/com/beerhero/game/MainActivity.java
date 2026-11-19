package com.beerhero.game;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_SAVE = 33;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        // мост для подарка: игра узнаёт, есть ли картинка gift.*, и может сохранить её в Галерею
        webView.addJavascriptInterface(new GiftBridge(), "AndroidGift");
        setContentView(webView);
        hideSystemUi();

        webView.loadUrl("file:///android_asset/index.html");
    }

    /** Подарок: файл gift.jpg / gift.png в app/src/main/assets */
    private String giftName() {
        try {
            String[] list = getAssets().list("");
            if (list == null) return "";
            String[] names = {"gift.jpg", "gift.jpeg", "gift.png", "gift.webp"};
            for (String n : names) for (String f : list) if (f.equalsIgnoreCase(n)) return f;
        } catch (Exception ignored) { }
        return "";
    }

    private boolean saveGiftNow() {
        String name = giftName();
        if (name.isEmpty()) return false;
        String low = name.toLowerCase();
        String mime = low.endsWith(".png") ? "image/png" : low.endsWith(".webp") ? "image/webp" : "image/jpeg";
        String ext = low.substring(low.lastIndexOf('.'));
        String fileName = "Podarok_" + System.currentTimeMillis() + ext;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getAssets().open(name);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                v.put(MediaStore.Images.Media.MIME_TYPE, mime);
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Podarok");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                out = getContentResolver().openOutputStream(uri);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Podarok");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fileName);
                out = new FileOutputStream(f);
            }
            if (out == null) return false;
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            out.flush();
            if (Build.VERSION.SDK_INT < 29) {
                // скан после записи, чтобы картинка сразу появилась в Галерее
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Podarok");
                MediaScannerConnection.scanFile(this, new String[]{new File(dir, fileName).getAbsolutePath()}, new String[]{mime}, null);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
    }

    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show(); }
        });
    }

    private class GiftBridge {
        @JavascriptInterface
        public String name() { return giftName(); }

        @JavascriptInterface
        public String save() {
            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_SAVE);
                    }
                });
                return "perm";
            }
            boolean ok = saveGiftNow();
            toast(ok ? "🎁 Подарок сохранён в Галерею (папка Podarok)" : "Не удалось сохранить подарок");
            return ok ? "ok" : "err";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SAVE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast(saveGiftNow() ? "🎁 Подарок сохранён в Галерею (папка Podarok)" : "Не удалось сохранить подарок");
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
