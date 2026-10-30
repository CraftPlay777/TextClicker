package com.craftplay.textclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

public class ClickerService extends AccessibilityService {
    public static ClickerService instance;
    public String lastInfo = "";

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    // Junta las raíces de todas las ventanas (app, teclado, menús flotantes)
    private List<AccessibilityNodeInfo> allRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        List<AccessibilityWindowInfo> wins = getWindows();
        if (wins != null) {
            for (AccessibilityWindowInfo w : wins) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) roots.add(r);
            }
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) roots.add(r);
        }
        return roots;
    }

    // Busca el texto en todas las ventanas y lo toca
    public boolean clickText(String text) {
        List<AccessibilityNodeInfo> roots = allRoots();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int matches = 0;

        for (AccessibilityNodeInfo root : roots) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo node : nodes) {
                matches++;
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                boolean onScreen = !r.isEmpty()
                        && r.centerX() >= 0 && r.centerX() <= dm.widthPixels
                        && r.centerY() >= 0 && r.centerY() <= dm.heightPixels;

                if (onScreen && tap(r.centerX(), r.centerY())) {
                    Store.log(this, "toque en " + r.centerX() + "," + r.centerY()
                            + " | ventanas=" + roots.size());
                    return true;
                }
                if (nodeClick(node)) {
                    Store.log(this, "click por accesibilidad | ventanas=" + roots.size());
                    return true;
                }
            }
        }

        List<String> seen = new ArrayList<>();
        for (AccessibilityNodeInfo root : roots) collect(root, seen, 0);
        lastInfo = "ventanas=" + roots.size() + " coincidencias=" + matches + " textos=" + seen;
        return false;
    }

    // Toque real con gesto (Android 7+)
    private boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) return false;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 50))
                .build();
        return dispatchGesture(g, null, null);
    }

    private boolean nodeClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        while (n != null && !n.isClickable()) {
            n = n.getParent();
        }
        return n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // Junta algunos textos visibles para el log de errores
    private void collect(AccessibilityNodeInfo n, List<String> out, int depth) {
        if (n == null || out.size() >= 15 || depth > 40) return;
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t != null && t.length() > 0) {
            String s = t.toString();
            out.add(s.length() > 20 ? s.substring(0, 20) : s);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChild(i), out, depth + 1);
        }
    }
}
