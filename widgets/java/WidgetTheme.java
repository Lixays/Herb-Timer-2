package com.example.herbtimer;

import android.content.Context;

/** Colours for the widgets, matching the app's themes (Old School, the four Ironman modes, and Dark Mode). */
final class WidgetTheme {
    final int bgRes;
    final int accent;   // top line
    final int text;     // countdown
    final int dim;      // small grey line
    final int ready;    // "Ready!" / "Harvest"
    final int idle;     // "Empty" / "Start" / warnings

    private WidgetTheme(int bgRes, int accent, int text, int dim, int ready, int idle) {
        this.bgRes = bgRes;
        this.accent = accent;
        this.text = text;
        this.dim = dim;
        this.ready = ready;
        this.idle = idle;
    }

    static WidgetTheme current(Context c) {
        return of(HerbWidgetStore.theme(c), HerbWidgetStore.dark(c));
    }

    static WidgetTheme of(String id, boolean dark) {
        if (id == null) id = "oldschool";
        switch (id) {
            case "iron":
                return new WidgetTheme(dark ? R.drawable.widget_bg_iron_amoled : R.drawable.widget_bg_iron,
                        0xFFC3CAD3, 0xFFD5DAE0, 0xFF9AA1AB, 0xFFD9D94A, 0xFFD05656);
            case "uim":   // black with white trim
                return new WidgetTheme(dark ? R.drawable.widget_bg_uim_amoled : R.drawable.widget_bg_uim,
                        0xFFF2F2F2, 0xFFDADADA, 0xFF9A9A9A, 0xFFE6DC5A, 0xFFE05A58);
            case "hcim":
                return new WidgetTheme(dark ? R.drawable.widget_bg_hcim_amoled : R.drawable.widget_bg_hcim,
                        0xFFE65A4D, 0xFFF2C8C0, 0xFFC9928A, 0xFFE6D84A, 0xFFFF8C7C);
            case "gim":
                return new WidgetTheme(dark ? R.drawable.widget_bg_gim_amoled : R.drawable.widget_bg_gim,
                        0xFF6AA0F2, 0xFFD0E0F8, 0xFF8FADDC, 0xFFE3DE4C, 0xFFE0675F);
            case "hcworld":
                return new WidgetTheme(dark ? R.drawable.widget_bg_hcworld_amoled : R.drawable.widget_bg_hcworld,
                        0xFFD6CBA8, 0xFFECE4CC, 0xFF9A9482, 0xFFE6D84A, 0xFFD95A4E);
            default:   // Old School
                return new WidgetTheme(dark ? R.drawable.widget_bg_oldschool_amoled : R.drawable.herb_widget_bg,
                        0xFFC9A227, 0xFFE8D9A8, 0xFFB8AB8C, 0xFFE8D83A, 0xFFC25A5A);
        }
    }
}
