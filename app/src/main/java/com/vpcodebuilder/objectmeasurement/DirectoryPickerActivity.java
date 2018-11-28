package com.vpcodebuilder.objectmeasurement;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DirectoryPickerActivity extends ListActivity {
    public static final String START_DIR = "startDir";
    public static final String ONLY_DIRS = "onlyDirs";
    public static final String SHOW_HIDDEN = "showHidden";
    public static final String FILTER_EXTENSION = "filterExtension";
    public static final String CHOSEN_DIRECTORY = "chosenDir";
    public static final int PICK_DIRECTORY = 43522432;
    private File dir;
    private boolean showHidden = false;
    private boolean onlyDirs = true ;
    private String filterExtension;
    private String chooseDirectoryCaption;
    private String chooseFileCaption = "";
    private File selectedFile;
    private Button btnChoose;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        dir = Environment.getExternalStorageDirectory();

        if (extras != null) {
            String preferredStartDir = extras.getString(START_DIR);
            showHidden = extras.getBoolean(SHOW_HIDDEN, false);
            onlyDirs = extras.getBoolean(ONLY_DIRS, true);
            filterExtension = extras.getString(FILTER_EXTENSION, "");

            if (preferredStartDir != null) {
                File startDir = new File(preferredStartDir);

                if (startDir.isDirectory()) {
                    dir = startDir;
                }
            }
        }

        setContentView(R.layout.activity_directorypicker_list);
        setTitle(dir.getAbsolutePath());
        btnChoose = (Button) findViewById(R.id.btnChoose);
        String name = dir.getName();

        if (name.length() == 0)
            name = "/";

        chooseDirectoryCaption = "choose " + name;

        if (savedInstanceState != null) {
            chooseFileCaption = savedInstanceState.getString("chooseFileCaption");
            String filePath = savedInstanceState.getString("selectedFile", null);

            if (filePath != null)
                selectedFile = new File(filePath);
        }

        btnChoose.setText(chooseDirectoryCaption + chooseFileCaption);
        btnChoose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (onlyDirs) {
                    // Directory selected.
                    returnDir(dir.getAbsolutePath());
                } else {
                    // File selected.
                    if (selectedFile != null) {
                        returnDir(selectedFile.getAbsolutePath());
                    } else {
                        AlertDialog.Builder adlg = new AlertDialog.Builder(v.getContext());
                        adlg.setTitle("choose file");
                        adlg.setMessage("Please choose for import file");
                        adlg.show();
                    }
                }
            }
        });

        ListView lv = getListView();
        lv.setTextFilterEnabled(true);

        if (!dir.canRead()) {
            Context context = getApplicationContext();
            String msg = "Cannot be open directory";
            Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        final ArrayList<File> files = filter(dir.listFiles(), onlyDirs, showHidden, filterExtension);
        setListAdapter(new FileListAdapter(this, files));

        lv.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File chooseFile = files.get(position);

                if (chooseFile.isDirectory()) {
                    // Selected directory.
                    String path = chooseFile.getAbsolutePath();
                    Intent intent = new Intent(DirectoryPickerActivity.this, DirectoryPickerActivity.class);
                    intent.putExtra(DirectoryPickerActivity.START_DIR, path);
                    intent.putExtra(DirectoryPickerActivity.SHOW_HIDDEN, showHidden);
                    intent.putExtra(DirectoryPickerActivity.ONLY_DIRS, onlyDirs);
                    intent.putExtra(DirectoryPickerActivity.FILTER_EXTENSION, filterExtension);
                    startActivityForResult(intent, PICK_DIRECTORY);
                } else {
                    // Selected file.
                    selectedFile = chooseFile;
                    chooseFileCaption = "/" + selectedFile.getName();
                    btnChoose.setText(chooseDirectoryCaption + chooseFileCaption);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString("chooseFileCaption", chooseFileCaption);

        if (selectedFile != null) {
            savedInstanceState.putString("selectedFile", selectedFile.getAbsolutePath());
        } else {
            savedInstanceState.putString("selectedFile", null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            String path = (String) extras.get(DirectoryPickerActivity.CHOSEN_DIRECTORY);
            returnDir(path);
        }
    }

    private void returnDir(String path) {
        Intent result = new Intent();
        result.putExtra(CHOSEN_DIRECTORY, path);
        setResult(RESULT_OK, result);
        finish();
    }

    public ArrayList<File> filter(File[] fileList, boolean onlyDirs, boolean showHidden, String filterExtension) {
        ArrayList<File> files = new ArrayList<File>();

        for (File file: fileList) {
            if (onlyDirs && !file.isDirectory())
                continue;

            if (!showHidden && file.isHidden())
                continue;

            if (file.isFile()) {
                if (!file.getPath().endsWith(filterExtension))
                    continue;
            }

            files.add(file);
        }

        Collections.sort(files);
        return files;
    }

    private class FileListAdapter extends ArrayAdapter<File> {
        private List<File> mObjects;

        public FileListAdapter(Context context, List<File> objects) {
            super(context, R.layout.activity_directorypicker_list_item, android.R.id.text1, objects);
            mObjects = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(R.layout.activity_directorypicker_list_item, parent, false);
            }
            else {
                row = convertView;
            }

            File object = mObjects.get(position);

            ImageView imageView = (ImageView)row.findViewById(R.id.file_list_image);
            TextView textView = (TextView)row.findViewById(R.id.file_list_text);
            textView.setSingleLine(true);
            textView.setText(object.getName());

            if (object.isFile()) {
                imageView.setImageResource(R.mipmap.file);
            }
            else {
                imageView.setImageResource(R.mipmap.folder);
            }

            return row;
        }
    }
}
