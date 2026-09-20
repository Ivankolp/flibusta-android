package is.flibusta.client.update;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import is.flibusta.client.R;

public class AppUpdateManager {

    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/Ivankolp/flibusta-android/releases/latest";
    private static final String PREFS_NAME = "flibusta_prefs";
    private static final String KEY_IGNORE_VERSION = "pref_ignore_update_tag";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void checkAutoUpdate(Activity activity) {
        checkUpdateInternal(activity, false);
    }

    public static void checkManualUpdate(Activity activity) {
        checkUpdateInternal(activity, true);
    }

    private static void checkUpdateInternal(Activity activity, boolean isManual) {
        if (activity == null || activity.isFinishing()) return;

        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_RELEASES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "FlibustaReader-Android");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    if (isManual) {
                        mainHandler.post(() -> Toast.makeText(activity, "Не удалось проверить обновления на GitHub", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String remoteTag = json.optString("tag_name", "").trim();
                String remoteName = json.optString("name", remoteTag).trim();

                String downloadUrl = null;
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase().endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }

                if (remoteTag.isEmpty() || downloadUrl == null || downloadUrl.isEmpty()) {
                    if (isManual) {
                        mainHandler.post(() -> Toast.makeText(activity, "Файл обновления не найден в релизе GitHub", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                String currentVersionName = "1.0.0";
                try {
                    PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    currentVersionName = pInfo.versionName;
                } catch (Exception ignored) {}

                SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String ignoredTag = prefs.getString(KEY_IGNORE_VERSION, "");

                final String finalCurrentVersionName = currentVersionName;
                final String finalDownloadUrl = downloadUrl;

                mainHandler.post(() -> {
                    if (activity.isFinishing()) return;

                    if (isNewerVersion(remoteTag, finalCurrentVersionName)) {
                        if (!isManual && remoteTag.equalsIgnoreCase(ignoredTag)) {
                            return;
                        }
                        showUpdateDialog(activity, remoteTag, remoteName, finalDownloadUrl);
                    } else if (isManual) {
                        Toast.makeText(activity, "У вас установлена последняя версия приложения (" + finalCurrentVersionName + ")", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                if (isManual) {
                    mainHandler.post(() -> Toast.makeText(activity, "Ошибка проверки обновлений: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static boolean isNewerVersion(String remoteTag, String currentVersionName) {
        if (remoteTag == null || currentVersionName == null) return false;
        String r = remoteTag.replaceAll("[^0-9.]", "").trim();
        String c = currentVersionName.replaceAll("[^0-9.]", "").trim();
        if (r.isEmpty() || c.isEmpty()) return false;

        String[] rParts = r.split("\\.");
        String[] cParts = c.split("\\.");
        int maxLen = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < maxLen; i++) {
            int rVal = i < rParts.length ? parseSafeInt(rParts[i]) : 0;
            int cVal = i < cParts.length ? parseSafeInt(cParts[i]) : 0;
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
    }

    private static int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void showUpdateDialog(Activity activity, String newTag, String newVersionTitle, String apkUrl) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null);
        TextView tvMsg = dialogView.findViewById(R.id.tv_update_message);
        CheckBox cbIgnore = dialogView.findViewById(R.id.cb_ignore_version);

        tvMsg.setText("Вышла новая версия приложения: " + newVersionTitle + ".\nХотите обновиться прямо сейчас?");

        new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление")
                .setView(dialogView)
                .setPositiveButton("Обновиться", (dialog, which) -> {
                    if (cbIgnore.isChecked()) {
                        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putString(KEY_IGNORE_VERSION, newTag).apply();
                    }
                    downloadAndInstallApk(activity, apkUrl);
                })
                .setNegativeButton("Позже", (dialog, which) -> {
                    if (cbIgnore.isChecked()) {
                        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putString(KEY_IGNORE_VERSION, newTag).apply();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private static void downloadAndInstallApk(Activity activity, String apkUrl) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Обновление Flibusta Reader");
        progressDialog.setMessage("Загрузка новой версии с GitHub...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        executor.execute(() -> {
            File targetFile = null;
            try {
                File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = activity.getCacheDir();
                targetFile = new File(dir, "Flibusta_update.apk");
                if (targetFile.exists()) targetFile.delete();

                HttpURLConnection conn = openConnectionWithRedirects(apkUrl);
                int fileLength = conn.getContentLength();
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(targetFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) ((total * 100) / fileLength);
                        mainHandler.post(() -> progressDialog.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                File finalFile = targetFile;
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    installApk(activity, finalFile);
                });

            } catch (Exception e) {
                if (targetFile != null && targetFile.exists()) targetFile.delete();
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "Не удалось загрузить обновление: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static HttpURLConnection openConnectionWithRedirects(String initialUrl) throws Exception {
        String currentUrl = initialUrl;
        for (int redirects = 0; redirects < 6; redirects++) {
            URL u = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "FlibustaReader-Android");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc != null && !loc.isEmpty()) {
                    currentUrl = loc;
                    conn.disconnect();
                    continue;
                }
            }
            return conn;
        }
        URL u = new URL(currentUrl);
        return (HttpURLConnection) u.openConnection();
    }

    private static void installApk(Activity activity, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(activity, "Файл обновления не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Ошибка запуска установки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
