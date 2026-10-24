package com.wanderingwitch.player;

import android.content.Context;
import java.io.File;

final class Tracks {
    static final String[] TITLES = {"灰色のサーガ", "リテラチュア"};
    static final String[] ARTISTS = {"ChouCho", "上田麗奈"};
    static File file(Context context, int index) {
        return new File(context.getFilesDir(), "track-" + index + ".audio");
    }
    static boolean ready(Context context, int index) { return file(context, index).isFile(); }
    private Tracks() {}
}
