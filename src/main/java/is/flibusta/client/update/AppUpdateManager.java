package is.flibusta.client.update;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
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

    private static final String UPDATE_URL = "http://31.76.79.172/static/flibusta_version.json";
    private static final String PREFS_NAME = "flibusta_prefs";
    private static final String KEY_IGNORE_VERSION = "pref_ignore_update_version";

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
                URL url = new URL(UPDATE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() != 200) {
                    if (isManual) {
                        mainHandler.post(() -> Toast.makeText(activity, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show());
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
                int remoteVersionCode = json.optInt("versionCode", 0);
                String remoteVersionName = json.optString("versionName", "");
                String downloadUrl = json.optString("downloadUrl", "");

                int currentVersionCode = 0;
                try {
                    PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    currentVersionCode = pInfo.versionCode;
                } catch (Exception ignored) {}

                SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                int ignoredVersion = prefs.getInt(KEY_IGNORE_VERSION, 0);

                int finalCurrentVersionCode = currentVersionCode;
                mainHandler.post(() -> {
                    if (activity.isFinishing()) return;

                    if (remoteVersionCode > finalCurrentVersionCode) {
                        if (!isManual && remoteVersionCode == ignoredVersion) {
                            return;
                        }
                        showUpdateDialog(activity, remoteVersionCode, remoteVersionName, downloadUrl);
                    } else if (isManual) {
                        Toast.makeText(activity, "У вас установлена последняя версия приложения", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                if (isManual) {
                    mainHandler.post(() -> Toast.makeText(activity, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static void showUpdateDialog(Activity activity, int newVersionCode, String newVersionName, String apkUrl) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null);
        TextView tvMsg = dialogView.findViewById(R.id.tv_update_message);
        CheckBox cbIgnore = dialogView.findViewById(R.id.cb_ignore_version);

        tvMsg.setText("Вышла новая версия приложения: " + newVersionName + ".\nХотите обновиться прямо сейчас?");

        new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление")
                .setView(dialogView)
                .setPositiveButton("Обновиться", (dialog, which) -> {
                    if (cbIgnore.isChecked()) {
                        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putInt(KEY_IGNORE_VERSION, newVersionCode).apply();
                    }
                    downloadAndInstallApk(activity, apkUrl);
                })
                .setNegativeButton("Позже", (dialog, which) -> {
                    if (cbIgnore.isChecked()) {
                        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putInt(KEY_IGNORE_VERSION, newVersionCode).apply();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private static void downloadAndInstallApk(Activity activity, String apkUrl) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Обновление Flibusta Reader");
        progressDialog.setMessage("Загрузка новой версии...");
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

                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

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
