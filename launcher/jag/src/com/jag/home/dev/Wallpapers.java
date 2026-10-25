package com.jag.home.dev;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local app-owned pictures only; never downloads or rotates wallpaper. */
final class Wallpapers {
    private Wallpapers() {}

    static List<File> folders(Context c) {
        List<File> result = new ArrayList<>();
        File external = c.getExternalFilesDir(null);
        if (external != null) result.add(new File(external, "wallpapers"));
        for (File media : c.getExternalMediaDirs()) {
            if (media != null) result.add(new File(media, "wallpapers"));
        }
        return result;
    }

    static List<File> list(Context c) {
        List<File> result = new ArrayList<>();
        for (File folder : folders(c)) {
            File[] files = folder.listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (file.isFile() && WallpaperPolicy.supported(file.getName()) && file.length() <= WallpaperPolicy.MAX_BYTES) {
                    result.add(file);
                }
                if (result.size() >= 100) break;
            }
        }
        Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    static File selected(Context c, String path) {
        if (path.isEmpty()) return null;
        File file = new File(path);
        try {
            File canonical = file.getCanonicalFile();
            File imported = new File(c.getFilesDir(), "imported-wallpaper").getCanonicalFile();
            if (canonical.equals(imported)) return valid(file) ? file : null;
            for (File folder : folders(c)) {
                if (folder.getCanonicalFile().equals(canonical.getParentFile())
                        && WallpaperPolicy.supported(canonical.getName())) return valid(file) ? file : null;
            }
        } catch (IOException | SecurityException e) {
            return null;
        }
        return null;
    }

    private static boolean valid(File file) {
        return file.isFile() && file.length() > 0 && file.length() <= WallpaperPolicy.MAX_BYTES;
    }

    static Bitmap decode(File file) {
        if (file == null || !valid(file)) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (!WallpaperPolicy.decodable(bounds.outWidth, bounds.outHeight, bounds.outMimeType)) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = WallpaperPolicy.sample(bounds.outWidth, bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Copy a picker grant into private storage; keep the previous imported file on failure. */
    static File importPicture(Context c, Uri uri) throws IOException {
        if (!"content".equals(uri.getScheme())) throw new IOException("Choose a local document");
        File pending = new File(c.getFilesDir(), "wallpaper-pending");
        try {
            try (InputStream in = c.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(pending)) {
                if (in == null) throw new IOException("Image unavailable");
                byte[] buffer = new byte[16384];
                long size = 0;
                for (int count; (count = in.read(buffer)) != -1;) {
                    size += count;
                    if (size > WallpaperPolicy.MAX_BYTES) throw new IOException("Image exceeds 32 MB");
                    out.write(buffer, 0, count);
                }
                out.getFD().sync();
            }
            Bitmap test = decode(pending);
            if (test == null) throw new IOException("Choose a valid JPG, PNG or WebP image");
            test.recycle();
            File imported = new File(c.getFilesDir(), "imported-wallpaper");
            if (!pending.renameTo(imported)) throw new IOException("Could not import image");
            return imported;
        } finally {
            if (pending.exists() && !pending.delete()) pending.deleteOnExit();
        }
    }
}
