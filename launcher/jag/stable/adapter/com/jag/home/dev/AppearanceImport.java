package com.jag.home.dev;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;

/** Owner-staged bounded appearance transfer. No access to the development app's private data. */
final class AppearanceImport {
    private AppearanceImport() {}
    static void choose(Activity activity) {
        Ui.dialog(activity).setTitle("Import selected appearance?")
            .setMessage("Imports only approved visual preferences, favorite order and an optional staged wallpaper. Apps, accounts and recent history are preserved.")
            .setPositiveButton("Import", (d, which) -> {
                java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
                worker.execute(() -> {
                    String message;
                    File created = null;
                    try {
                        File external = activity.getExternalFilesDir(null);
                        if (external == null) throw new IOException("Storage unavailable");
                        File folder = new File(external, "migration");
                        File source = new File(folder, "appearance.properties");
                        if (!source.isFile() || source.length() > 16384) throw new IOException("Staged appearance missing");
                        Properties incoming = new Properties();
                        try (FileInputStream in = new FileInputStream(source)) { incoming.load(in); }
                        String wallpaper = null;
                        if ("true".equals(incoming.getProperty("wallpaper.import"))) {
                            Bitmap picture = Wallpapers.decode(new File(folder, "wallpaper.png"));
                            if (picture == null) throw new IOException("Invalid staged wallpaper");
                            File pending = File.createTempFile("appearance-wallpaper-", ".png", activity.getFilesDir());
                            created = pending;
                            try (FileOutputStream out = new FileOutputStream(pending)) {
                                if (!picture.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Cannot save wallpaper");
                                out.getFD().sync();
                            } finally { picture.recycle(); }
                            wallpaper = pending.getPath();
                        }
                        new Configuration(new File(activity.getFilesDir(), "jag.properties")).importAppearance(incoming, wallpaper);
                        message = "Appearance imported. Back returns to JAG Home.";
                    } catch (IOException | RuntimeException | OutOfMemoryError failure) {
                        if (created != null && created.isFile()) created.delete();
                        message = "Import failed. Check the owner-staged appearance files; existing preferences were kept.";
                    }
                    String result = message;
                    boolean imported = message.startsWith("Appearance imported");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!activity.isFinishing()) { Ui.message(activity, result); if (imported) activity.recreate(); }
                    });
                    worker.shutdown();
                });
            }).setNegativeButton("Cancel", null).show();
    }
}
