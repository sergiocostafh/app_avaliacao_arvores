package br.vistoria.arvores;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Vistoria Arbórea: interface em HTML (assets/www) dentro de um WebView,
 * com uma ponte "Android" para ditado, câmera, armazenamento e exportação.
 * Tudo funciona sem internet; os dados ficam na memória interna do app.
 */
public class MainActivity extends Activity {

    private static final int REQ_SPEECH = 11;
    private static final int REQ_CAMERA = 12;
    private static final int REQ_GALLERY = 13;
    private static final int MAX_PX = 1600;

    private WebView web;
    private String pendingId;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1F4E79"));
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back?window.__back():false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    private File photosDir() {
        File d = new File(getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void callJs(String fn, String... args) {
        StringBuilder sb = new StringBuilder("window.").append(fn).append("&&window.").append(fn).append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(JSONObject.quote(args[i] == null ? "" : args[i]));
        }
        sb.append(")");
        final String js = sb.toString();
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final String id = pendingId;
        pendingId = null;
        if (id == null) return;
        if (requestCode == REQ_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                callJs("__speech", id, (r != null && !r.isEmpty()) ? r.get(0) : "");
            } else {
                callJs("__speechError", id, "");
            }
        } else if (requestCode == REQ_CAMERA) {
            final Uri uri = cameraUri;
            cameraUri = null;
            if (resultCode == RESULT_OK && uri != null) {
                processPhoto(id, uri);
            } else {
                if (uri != null) {
                    try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
                }
                callJs("__photoError", id, "");
            }
        } else if (requestCode == REQ_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                processPhoto(id, data.getData());
            } else {
                callJs("__photoError", id, "");
            }
        }
    }

    /** Reduz para no máximo 1600 px, corrige a rotação e grava em JPEG na pasta interna. */
    private void processPhoto(final String id, final Uri uri) {
        new Thread(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_PX) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap bmp;
                try (InputStream in = getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
                if (bmp == null) throw new Exception("imagem inválida");
                int rot = 0;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    ExifInterface exif = new ExifInterface(in);
                    int or = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (or == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                    else if (or == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                    else if (or == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
                } catch (Exception ignored) { }
                float scale = Math.min(1f, (float) MAX_PX / Math.max(bmp.getWidth(), bmp.getHeight()));
                Matrix m = new Matrix();
                if (scale < 1f) m.postScale(scale, scale);
                if (rot != 0) m.postRotate(rot);
                Bitmap out = (scale < 1f || rot != 0)
                        ? Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true) : bmp;
                String name = "p" + System.currentTimeMillis() + ".jpg";
                try (FileOutputStream fo = new FileOutputStream(new File(photosDir(), name))) {
                    out.compress(Bitmap.CompressFormat.JPEG, 85, fo);
                }
                callJs("__photo", id, name);
            } catch (Exception e) {
                callJs("__photoError", id, "Não foi possível processar a foto: " + e.getMessage());
            }
        }).start();
    }

    /** Métodos chamados pelo JavaScript da interface. */
    public class Bridge {

        @JavascriptInterface
        public String loadDb() {
            File f = new File(getFilesDir(), "db.json");
            if (!f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveDb(String json) {
            try {
                File tmp = new File(getFilesDir(), "db.json.tmp");
                try (FileOutputStream fo = new FileOutputStream(tmp)) {
                    fo.write(json.getBytes(StandardCharsets.UTF_8));
                    fo.getFD().sync();
                }
                File dst = new File(getFilesDir(), "db.json");
                File bak = new File(getFilesDir(), "db.json.bak");
                if (dst.exists()) { bak.delete(); dst.renameTo(bak); }
                return tmp.renameTo(dst);
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String photoBase() {
            return "file://" + photosDir().getAbsolutePath() + "/";
        }

        @JavascriptInterface
        public String readPhoto(String name) {
            File f = new File(photosDir(), new File(name).getName());
            if (!f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                return Base64.encodeToString(b, Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void deletePhoto(String name) {
            new File(photosDir(), new File(name).getName()).delete();
        }

        @JavascriptInterface
        public void startSpeech(final String id) {
            runOnUiThread(() -> {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
                i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o texto do campo");
                try {
                    pendingId = id;
                    startActivityForResult(i, REQ_SPEECH);
                } catch (ActivityNotFoundException e) {
                    pendingId = null;
                    callJs("__speechError", id, "Reconhecimento de voz não encontrado. Instale ou ative o app Google.");
                }
            });
        }

        @JavascriptInterface
        public void takePhoto(final String id) {
            runOnUiThread(() -> {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, "vistoria_" + System.currentTimeMillis() + ".jpg");
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vistoria Arvores");
                    cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    pendingId = id;
                    startActivityForResult(i, REQ_CAMERA);
                } catch (Exception e) {
                    pendingId = null;
                    callJs("__photoError", id, "Não foi possível abrir a câmera.");
                }
            });
        }

        @JavascriptInterface
        public void pickPhoto(final String id) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    pendingId = id;
                    startActivityForResult(Intent.createChooser(i, "Escolher foto"), REQ_GALLERY);
                } catch (Exception e) {
                    pendingId = null;
                    callJs("__photoError", id, "Não foi possível abrir a galeria.");
                }
            });
        }

        /** Grava o arquivo em Download/Vistoria Arvores e devolve a URI (ou "ERR:..."). */
        @JavascriptInterface
        public String saveExport(String filename, String b64, String mime) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vistoria Arvores");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "ERR:sem acesso à pasta Download";
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) return "ERR:sem acesso à pasta Download";
                    os.write(data);
                }
                return uri.toString();
            } catch (Exception e) {
                return "ERR:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public void share(final String uri, final String mime) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_STREAM, Uri.parse(uri));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(Intent.createChooser(i, "Compartilhar")); } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void openFile(final String uri, final String mime) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.parse(uri), mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    callJs("__speechError", "", "Nenhum app instalado abre este arquivo (instale o Word ou o Google Docs/Planilhas).");
                }
            });
        }
    }
}
