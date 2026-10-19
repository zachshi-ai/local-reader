package com.localreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1001;
    private static final String PREF_NAME = "reader";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_BYTES = 3_000_000;

    private WebView web;
    private View home;
    private View readingGroup;
    private TextView titleView;
    private LinearLayout recentList;
    private String currentName = "";
    private int textZoom = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        web = findViewById(R.id.web);
        home = findViewById(R.id.home);
        readingGroup = findViewById(R.id.readingGroup);
        titleView = findViewById(R.id.title);
        recentList = findViewById(R.id.recentList);

        findViewById(R.id.openBtn).setOnClickListener(v -> pickFile());
        findViewById(R.id.openBtn2).setOnClickListener(v -> pickFile());
        findViewById(R.id.zoomIn).setOnClickListener(v -> zoom(10));
        findViewById(R.id.zoomOut).setOnClickListener(v -> zoom(-10));
        findViewById(R.id.closeBtn).setOnClickListener(v -> showHome());

        View about = findViewById(R.id.aboutBtn);
        if (about != null) {
            about.setOnClickListener(v -> showAbout());
        }

        setupWeb();
        showHome();
        renderRecent();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openUri(intent.getData(), intent);
            intent.setAction(null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openUri(intent.getData(), intent);
        }
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setDomStorageEnabled(true);
        web.setBackgroundColor(Color.WHITE);
        web.getSettings().setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                String scheme = Uri.parse(url).getScheme();
                if ("file".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)
                        || "about".equals(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法打开该链接", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/html", "text/markdown", "text/x-markdown", "text/plain", "application/xhtml+xml"
        });
        try {
            startActivityForResult(i, REQ_OPEN);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN && resultCode == RESULT_OK && data != null && data.getData() != null) {
            openUri(data.getData(), data);
        }
    }

    private void openUri(Uri uri, Intent source) {
        try {
            persist(uri);
            String name = queryName(uri);
            String text = readText(uri);
            String lower = name.toLowerCase(Locale.ROOT);
            String mime = null;
            try {
                mime = getContentResolver().getType(uri);
            } catch (Exception ignored) {
            }
            if (mime == null && source != null) mime = source.getType();

            boolean markdown = lower.endsWith(".md") || lower.endsWith(".markdown")
                    || lower.endsWith(".mdown") || lower.endsWith(".mkd")
                    || "text/markdown".equalsIgnoreCase(mime) || "text/x-markdown".equalsIgnoreCase(mime);
            boolean html = lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")
                    || "text/html".equalsIgnoreCase(mime) || "application/xhtml+xml".equalsIgnoreCase(mime);

            String page;
            if (markdown) {
                page = markdownPage(text);
            } else if (html) {
                page = htmlPage(text);
            } else {
                page = plainPage(text, name);
            }

            currentName = name;
            textZoom = 100;
            web.getSettings().setTextZoom(100);
            String base = null;
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                int slash = uri.getPath().lastIndexOf('/');
                if (slash > 0) {
                    base = "file://" + uri.getPath().substring(0, slash + 1);
                }
            }
            web.loadDataWithBaseURL(base, page, "text/html", "utf-8", null);
            showReader(name);
            addRecent(uri, name);
        } catch (Exception e) {
            Toast.makeText(this, "打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String markdownPage(String text) {
        String css = assetText("reader.css");
        String js = assetText("marked.min.js");
        String b64 = Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>" + css + "</style></head><body>"
                + "<article class=\"md\" id=\"md\"></article>"
                + "<script>" + js + "</script>"
                + "<script>"
                + "var b64='" + b64 + "';"
                + "function dec(){var s=atob(b64),a=new Uint8Array(s.length),i;"
                + "for(i=0;i<s.length;i++)a[i]=s.charCodeAt(i);"
                + "return new TextDecoder('utf-8').decode(a);}"
                + "var src=dec();"
                + "try{document.getElementById('md').innerHTML=marked.parse(src,{gfm:true,breaks:false});}"
                + "catch(e){document.getElementById('md').textContent=src;}"
                + "</script></body></html>";
    }

    private String htmlPage(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String meta = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html{-webkit-text-size-adjust:100%;}body{word-wrap:break-word;}</style>";
        if (!lower.contains("name=\"viewport\"") && !lower.contains("name='viewport'")) {
            int headIdx = lower.indexOf("<head");
            if (headIdx >= 0) {
                int end = text.indexOf('>', headIdx);
                if (end > 0) {
                    text = text.substring(0, end + 1) + meta + text.substring(end + 1);
                } else {
                    text = meta + text;
                }
            } else {
                int htmlIdx = lower.indexOf("<html");
                if (htmlIdx >= 0) {
                    int end = text.indexOf('>', htmlIdx);
                    if (end > 0) {
                        text = text.substring(0, end + 1) + "<head>" + meta + "</head>"
                                + text.substring(end + 1);
                    } else {
                        text = "<head>" + meta + "</head>" + text;
                    }
                } else {
                    text = "<!doctype html><html><head><meta charset=\"utf-8\">" + meta
                            + "</head><body>" + text + "</body></html>";
                }
            }
        }
        return text;
    }

    private String plainPage(String text, String name) {
        String css = assetText("reader.css");
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escapeHtml(name) + "</title>"
                + "<style>" + css + "</style></head><body>"
                + "<pre class=\"plain\">" + escapeHtml(text) + "</pre></body></html>";
    }

    private String readText(Uri uri) throws Exception {
        byte[] data;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("无法读取该文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            int total = 0;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > MAX_BYTES) {
                    out.write(buf, 0, Math.max(0, buf.length - (total - MAX_BYTES)));
                    break;
                }
                out.write(buf, 0, r);
            }
            data = out.toByteArray();
        }
        String s = new String(data, StandardCharsets.UTF_8);
        long bad = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\uFFFD') bad++;
        }
        if (bad > data.length * 0.01) {
            try {
                s = new String(data, "GBK");
            } catch (Exception ignored) {
            }
        }
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        return s;
    }

    private String queryName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String v = c.getString(idx);
                    if (v != null && v.length() > 0) return v;
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "未命名文件" : last;
    }

    private void persist(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private String assetText(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private void showReader(String name) {
        home.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        titleView.setText(name);
        readingGroup.setVisibility(View.VISIBLE);
    }

    private void showHome() {
        web.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
        titleView.setText(R.string.app_name);
        readingGroup.setVisibility(View.GONE);
        web.loadUrl("about:blank");
    }

    private void zoom(int delta) {
        textZoom += delta;
        if (textZoom < 60) textZoom = 60;
        if (textZoom > 200) textZoom = 200;
        web.getSettings().setTextZoom(textZoom);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else if (c == '&') sb.append("&amp;");
            else sb.append(c);
        }
        return sb.toString();
    }

    private void addRecent(Uri uri, String name) {
        try {
            SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            JSONArray old = new JSONArray(sp.getString(KEY_RECENT, "[]"));
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("uri", uri.toString())
                    .put("name", name).put("time", System.currentTimeMillis()));
            for (int i = 0; i < old.length() && out.length() < 20; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !uri.toString().equals(o.optString("uri"))) out.put(o);
            }
            sp.edit().putString(KEY_RECENT, out.toString()).apply();
            renderRecent();
        } catch (Exception ignored) {
        }
    }

    private JSONArray readRecent() {
        try {
            return new JSONArray(getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    .getString(KEY_RECENT, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void renderRecent() {
        if (recentList == null) return;
        recentList.removeAllViews();
        JSONArray arr = readRecent();
        if (arr.length() == 0) {
            TextView hint = new TextView(this);
            hint.setText("还没有打开过文件。\n\n点击上面的按钮选择文件，或者在文件管理器里长按文件 →「打开方式」→ 选本应用。");
            hint.setTextSize(13);
            hint.setTextColor(Color.parseColor("#8F959E"));
            hint.setLineSpacing(dp(5), 1f);
            hint.setPadding(dp(2), dp(18), dp(2), dp(8));
            recentList.addView(hint);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            recentList.addView(buildCard(Uri.parse(o.optString("uri")), o.optString("name"),
                    o.optLong("time", 0)));
        }
    }

    private View buildCard(Uri uri, String name, long time) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#E4E6EB"));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        card.setClickable(true);

        TextView nameView = new TextView(this);
        nameView.setText(TextUtils.isEmpty(name) ? "未命名文件" : name);
        nameView.setTextSize(15);
        nameView.setTextColor(Color.parseColor("#1F2329"));
        nameView.setMaxLines(1);
        nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        card.addView(nameView);

        TextView timeView = new TextView(this);
        if (time > 0) {
            timeView.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time)));
        }
        timeView.setTextSize(12);
        timeView.setTextColor(Color.parseColor("#8F959E"));
        timeView.setPadding(0, dp(4), 0, 0);
        card.addView(timeView);

        card.setOnClickListener(v -> openUri(uri, null));
        card.setOnLongClickListener(v -> {
            removeRecent(uri);
            Toast.makeText(this, "已从最近列表移除", Toast.LENGTH_SHORT).show();
            return true;
        });
        return card;
    }

    private void removeRecent(Uri uri) {
        try {
            JSONArray arr = readRecent();
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !uri.toString().equals(o.optString("uri"))) out.put(o);
            }
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_RECENT, out.toString()).apply();
            renderRecent();
        } catch (Exception ignored) {
        }
    }

    private void showAbout() {
        String vn = "1.0";
        try {
            vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle("本地阅读器")
                .setMessage("版本 " + vn + "\n\n"
                        + "完全离线：文件只在本机读取和渲染，不上传、不联网、不留副本。\n\n"
                        + "支持 .html / .htm / .md / .markdown / .txt")
                .setPositiveButton("知道了", (d, w) -> d.dismiss())
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (web.getVisibility() == View.VISIBLE) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
}
