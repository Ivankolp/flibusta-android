package is.flibusta.client.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import is.flibusta.client.R;

public class ImageLoader {
    private static final int CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 1024) / 8;
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount() / 1024;
        }
    };

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void loadCover(ImageView imageView, String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.ic_book);
            imageView.setTag(null);
            return;
        }

        final String finalUrl = imageUrl.startsWith("http") ? imageUrl : "http://flibusta.is" + imageUrl;
        imageView.setTag(finalUrl);

        // Check memory cache
        Bitmap cached = memoryCache.get(finalUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set default while loading
        imageView.setImageResource(R.drawable.ic_book);

        executor.execute(() -> {
            try {
                URL url = new URL(finalUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) FlibustaReader/1.0");

                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null) {
                        memoryCache.put(finalUrl, bitmap);
                        mainHandler.post(() -> {
                            if (finalUrl.equals(imageView.getTag())) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } else {
                    conn.disconnect();
                }
            } catch (Exception ignored) {
            }
        });
    }
}
