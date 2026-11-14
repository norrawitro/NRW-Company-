package com.nrw.filefinder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds files on the phone by filename: case-insensitive substring match,
 * results update live while typing. Tap = open, long-press = share.
 */
public class MainActivity extends Activity {

    private static final int REQ_LEGACY_PERMISSION = 1;
    private static final int MAX_SHOWN = 1000;
    private static final long DEBOUNCE_MS = 150;

    /** One indexed file: lower-cased name for matching, original file for display. */
    private static final class Entry {
        final String lowerName;
        final File file;
        Entry(File file) {
            this.file = file;
            this.lowerName = file.getName().toLowerCase(Locale.ROOT);
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private volatile List<Entry> index = new ArrayList<>();
    private volatile boolean indexing = false;
    private int searchGeneration = 0;

    private EditText query;
    private TextView status;
    private final ResultAdapter adapter = new ResultAdapter();
    private boolean askedForPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        query = findViewById(R.id.query);
        status = findViewById(R.id.status);
        ListView list = findViewById(R.id.results);
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> openFile(adapter.getItem(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            shareFile(adapter.getItem(position));
            return true;
        });

        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { scheduleSearch(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            if (index.isEmpty() && !indexing) buildIndex();
        } else if (!askedForPermission) {
            askedForPermission = true;
            requestStoragePermission();
        } else {
            status.setText("ยังไม่ได้รับสิทธิ์เข้าถึงไฟล์ — แตะที่นี่เพื่อให้สิทธิ์");
            status.setOnClickListener(v -> requestStoragePermission());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    // ---------- permission ----------

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new AlertDialog.Builder(this)
                    .setTitle("ขอสิทธิ์เข้าถึงไฟล์ทั้งหมด")
                    .setMessage("แอปต้องใช้สิทธิ์ \"เข้าถึงไฟล์ทั้งหมด\" เพื่อค้นหาไฟล์ทุกโฟลเดอร์ในเครื่อง (ค้นแค่ชื่อไฟล์ ไม่อ่านเนื้อหา)")
                    .setPositiveButton("ไปตั้งค่า", (d, w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (ActivityNotFoundException e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    })
                    .setNegativeButton("ยกเลิก", null)
                    .show();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_LEGACY_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LEGACY_PERMISSION && hasStoragePermission()) buildIndex();
    }

    // ---------- indexing ----------

    private void buildIndex() {
        indexing = true;
        status.setText("กำลังสำรวจไฟล์ในเครื่อง…");
        worker.execute(() -> {
            List<Entry> found = new ArrayList<>();
            ArrayDeque<File> stack = new ArrayDeque<>();
            stack.push(Environment.getExternalStorageDirectory());
            while (!stack.isEmpty()) {
                File dir = stack.pop();
                File[] children = dir.listFiles();
                if (children == null) continue;
                for (File f : children) {
                    if (f.isDirectory()) {
                        stack.push(f);
                    } else {
                        found.add(new Entry(f));
                    }
                }
                if (found.size() % 5000 == 0 && !found.isEmpty()) {
                    final int n = found.size();
                    main.post(() -> status.setText("กำลังสำรวจไฟล์… " + n + " ไฟล์"));
                }
            }
            main.post(() -> {
                index = found;
                indexing = false;
                scheduleSearch();
            });
        });
    }

    // ---------- search ----------

    private void scheduleSearch() {
        final int gen = ++searchGeneration;
        main.removeCallbacks(pendingSearch);
        pendingSearch = () -> runSearch(gen);
        main.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    private Runnable pendingSearch = () -> { };

    private void runSearch(int gen) {
        final String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        final List<Entry> snapshot = index;
        if (indexing) return;
        if (q.isEmpty()) {
            adapter.set(new ArrayList<>());
            status.setText("พร้อมค้นหา: " + snapshot.size() + " ไฟล์ในเครื่อง");
            return;
        }
        worker.execute(() -> {
            List<File> hits = new ArrayList<>();
            int total = 0;
            for (Entry e : snapshot) {
                if (e.lowerName.contains(q)) {
                    total++;
                    if (hits.size() < MAX_SHOWN) hits.add(e.file);
                }
            }
            final int totalHits = total;
            main.post(() -> {
                if (gen != searchGeneration) return; // a newer query is pending
                adapter.set(hits);
                String msg = "พบ " + totalHits + " ไฟล์";
                if (totalHits > MAX_SHOWN) msg += " (แสดง " + MAX_SHOWN + " แรก)";
                status.setText(msg + " — แตะเพื่อเปิด, กดค้างเพื่อแชร์");
            });
        });
    }

    // ---------- open / share ----------

    private Uri uriFor(File f) {
        return FileProvider.getUriForFile(this, getPackageName() + ".files", f);
    }

    private String mimeFor(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "*/*";
    }

    private void openFile(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uriFor(f), mimeFor(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "เปิดด้วย"));
        } catch (Exception e) {
            Toast.makeText(this, "เปิดไฟล์ไม่ได้: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeFor(f));
            i.putExtra(Intent.EXTRA_STREAM, uriFor(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "แชร์ไฟล์"));
        } catch (Exception e) {
            Toast.makeText(this, "แชร์ไฟล์ไม่ได้: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- list adapter ----------

    private final class ResultAdapter extends BaseAdapter {
        private List<File> items = new ArrayList<>();

        void set(List<File> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public File getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result, parent, false);
            File f = items.get(position);
            ((TextView) v.findViewById(R.id.name)).setText(f.getName());
            ((TextView) v.findViewById(R.id.path)).setText(f.getAbsolutePath());
            return v;
        }
    }
}
