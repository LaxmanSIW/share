package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import javafx.stage.Stage;

/**
 * Recolors the OS window title bar (the Windows strip holding the app icon,
 * window title, and min/max/close buttons) so it matches the app's dark
 * "Obsidian & Gold" theme instead of the default white caption.
 *
 * <p><b>Why a native helper:</b> the title bar is drawn by the operating
 * system, not JavaFX — no CSS rule can reach it. Windows exposes documented
 * Desktop Window Manager attributes for exactly this purpose, set through
 * {@code DwmSetWindowAttribute} (dwmapi.dll):</p>
 * <ul>
 *   <li>{@code DWMWA_USE_IMMERSIVE_DARK_MODE} (20/19) — dark caption (Win10 1809+)</li>
 *   <li>{@code DWMWA_BORDER_COLOR} (34) — window border (Windows 11)</li>
 *   <li>{@code DWMWA_CAPTION_COLOR} (35) — exact caption background (Windows 11)</li>
 *   <li>{@code DWMWA_TEXT_COLOR} (36) — exact caption text (Windows 11)</li>
 * </ul>
 * Attributes a given Windows build does not support return an error code (no
 * exception), so Windows 11 gets the exact brand colors, Windows 10 the dark
 * caption, and other OSes a silent no-op.
 *
 * <p><b>HWND lookup — no reflection:</b> reaching the handle through JavaFX
 * internals ({@code getPeer()} → {@code getPlatformWindow()} →
 * {@code getNativeWindow()}) fails on JDK 17+ because {@code com.sun.javafx.*}
 * and {@code com.sun.glass.*} are strongly encapsulated modules — reflective
 * access throws {@code InaccessibleObjectException} and the helper no-ops
 * (the exact bug this class shipped with). Instead the HWND is found the way
 * other apps do it, through plain Win32:</p>
 * <ol>
 *   <li>{@code FindWindowW} by the stage's exact title (fast path),</li>
 *   <li>fallback {@code EnumWindows} + {@code GetWindowThreadProcessId}:
 *       the first visible top-level window of <em>this</em> process (works for
 *       identical titles, e.g. several "Confirmation" alerts).</li>
 * </ol>
 * Both run on Windows only and never throw; any failure degrades to the
 * default title bar.
 *
 * <p>Colors mirror the {@code .root} token block in {@code globalfile.css}:
 * caption {@code #0B0E13} ({@code -color-bg}), text {@code #F4F4F5}
 * ({@code -color-text}), border {@code #232B38} ({@code -color-border}).
 * Change them in both places together.</p>
 */
public final class TitleBarTheme {

    // App palette (from globalfile.css .root — keep in sync; a comment there
    // points back to this class).
    static final String CAPTION_BG = "#0B0E13";   // -color-bg
    static final String TEXT       = "#F4F4F5";   // -color-text
    static final String BORDER     = "#232B38";   // -color-border

    /** Windows 11 SDK id for the dark-caption flag. */
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    /** Pre-20H1 Windows 10 builds accept the same flag under id 19; the
     *  renumbered id returns an error there, so both are set harmlessly. */
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_PRE20H1 = 19;
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;

    /** Read-back attribute for tests: DWMWA_CAPTION_COLOR via DwmGetWindowAttribute. */
    static final int DWMWA_CAPTION_COLOR_READBACK = DWMWA_CAPTION_COLOR;

    static {
        initUxThemeDarkMode();
    }

    /**
     * Initializes process-wide dark mode support via uxtheme.dll so that
     * Windows 10 honors dark captions even if the OS is in Light Theme.
     */
    public static void init() {
        initUxThemeDarkMode();
    }

    private static com.sun.jna.Function getUxThemeProc(int ordinal) {
        try {
            Pointer hModule = Kernel32Ext.INSTANCE.LoadLibraryW(new com.sun.jna.WString("uxtheme.dll"));
            if (hModule == null) return null;
            Pointer p = Kernel32Ext.INSTANCE.GetProcAddress(hModule, new Pointer(ordinal));
            if (p != null) {
                return com.sun.jna.Function.getFunction(p);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void initUxThemeDarkMode() {
        if (!isWindows()) return;
        try {
            // Ordinal 135: SetPreferredAppMode (Win10 1903+)
            // 2 = ForceDark
            com.sun.jna.Function fn135 = getUxThemeProc(135);
            if (fn135 != null) {
                fn135.invoke(Integer.class, new Object[]{2});
            } else {
                // Ordinal 132: AllowDarkModeForApp (Win10 1809)
                com.sun.jna.Function fn132 = getUxThemeProc(132);
                if (fn132 != null) {
                    fn132.invoke(Boolean.class, new Object[]{true});
                }
            }
        } catch (Throwable ignored) {}
    }

    private TitleBarTheme() {}

    /**
     * Themes the OS title bar of the given stage (no-op on non-Windows or on
     * any failure). Safe before or after {@code stage.show()} — the lookup
     * simply returns 0 (no-op) until the native window exists.
     */
    public static void apply(Stage stage) {
        if (stage == null) return;
        try {
            if (!isWindows()) return;
            long hwnd = findWindowHandle(stage);
            if (hwnd == 0) return;
            applyToHwnd(hwnd);
        } catch (Throwable t) {
            // Throwable on purpose: a missing native library or an unexpected
            // OS response must degrade to the default title bar, never crash.
            AppLog.debug(t);
        }
    }

    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int FRAME_REFRESH_FLAGS = SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED;
    private static final int REDRAW_FRAME_FLAGS = 0x0001 /* RDW_INVALIDATE */ | 0x0400 /* RDW_FRAME */ | 0x0100 /* RDW_UPDATENOW */ | 0x0080 /* RDW_ALLCHILDREN */;

    /**
     * Themes EVERY top-level window of this process in one pass —
     * a catch-all that also covers dialogs whose title is not known yet
     * (JavaFX Alerts are created blank, then titled). Idempotent; call
     * freely. No-throw by contract.
     */
    public static void applyToAllProcessWindows() {
        try {
            if (!isWindows()) return;
            int pid = Kernel32Ext.INSTANCE.GetCurrentProcessId();
            User32Ext.INSTANCE.EnumWindows((hwnd, data) -> {
                IntByReference pidRef = new IntByReference();
                User32Ext.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
                if (pidRef.getValue() == pid) {
                    try {
                        applyToHwnd(Pointer.nativeValue(hwnd));
                    } catch (Throwable ignored) {
                        AppLog.debug(ignored); // one bad window must not stop the sweep
                    }
                }
                return true; // continue enumeration
            }, null);
        } catch (Throwable t) {
            AppLog.debug(t);
        }
    }

    /** Applies the four DWM attributes to a raw HWND and forces a frame redraw. Package-visible for tests. */
    static void applyToHwnd(long hwnd) {
        if (hwnd == 0) return;
        try {
            // Ordinal 133: AllowDarkModeForWindow (Win10 1809+)
            com.sun.jna.Function fn133 = getUxThemeProc(133);
            if (fn133 != null) {
                fn133.invoke(Boolean.class, new Object[]{new Pointer(hwnd), true});
            }
            try {
                com.sun.jna.Function setWindowTheme = com.sun.jna.NativeLibrary.getInstance("uxtheme").getFunction("SetWindowTheme");
                setWindowTheme.invoke(Integer.class, new Object[]{new Pointer(hwnd), new com.sun.jna.WString("DarkMode_Explorer"), null});
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        Dwm.enableDarkCaption(hwnd);
        Dwm.setColorAttribute(hwnd, DWMWA_CAPTION_COLOR, CAPTION_BG);
        Dwm.setColorAttribute(hwnd, DWMWA_TEXT_COLOR, TEXT);
        Dwm.setColorAttribute(hwnd, DWMWA_BORDER_COLOR, BORDER);
        try {
            User32Ext.INSTANCE.SetWindowPos(new Pointer(hwnd), Pointer.NULL, 0, 0, 0, 0, FRAME_REFRESH_FLAGS);
            User32Ext.INSTANCE.RedrawWindow(new Pointer(hwnd), Pointer.NULL, Pointer.NULL, REDRAW_FRAME_FLAGS);
        } catch (Throwable ignored) {
            AppLog.debug(ignored);
        }
    }

    /** True on Windows (case-tolerant; os.name is "Windows 10", "Windows 11", …). */
    static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os != null && os.toLowerCase().contains("win");
    }

    /**
     * Finds the native HWND for a JavaFX stage without touching JavaFX
     * internals: exact-title {@code FindWindowW} first, transient probe
     * if showing, and own-process visible-window sweep as fallback.
     * Returns 0 when the window does not exist yet (stage not shown).
     */
    static long findWindowHandle(Stage stage) {
        if (stage == null) return 0L;
        String title = stage.getTitle();
        if (title != null && !title.isBlank()) {
            Pointer found = User32Ext.INSTANCE.FindWindowW(null, new com.sun.jna.WString(title));
            if (found != null && ownsWindow(found)) {
                return Pointer.nativeValue(found);
            }
        }
        // If the stage is showing, probe with a transient unique title to find its exact HWND:
        if (stage.isShowing()) {
            String originalTitle = stage.getTitle();
            String probeTitle = "__IS_THEME_" + System.nanoTime();
            stage.setTitle(probeTitle);
            Pointer found = User32Ext.INSTANCE.FindWindowW(null, new com.sun.jna.WString(probeTitle));
            stage.setTitle(originalTitle);
            if (found != null && ownsWindow(found)) {
                return Pointer.nativeValue(found);
            }
            return firstOwnedVisibleWindow();
        }
        return 0L;
    }

    /** True when the HWND belongs to this process. */
    private static boolean ownsWindow(Pointer hwnd) {
        if (hwnd == null) return false;
        IntByReference pidRef = new IntByReference();
        User32Ext.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        return pidRef.getValue() == Kernel32Ext.INSTANCE.GetCurrentProcessId();
    }

    /** First visible top-level window of this process, or 0. */
    private static long firstOwnedVisibleWindow() {
        int pid = Kernel32Ext.INSTANCE.GetCurrentProcessId();
        long[] first = {0L};
        User32Ext.INSTANCE.EnumWindows((hwnd, data) -> {
            IntByReference pidRef = new IntByReference();
            User32Ext.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            if (pidRef.getValue() == pid && User32Ext.INSTANCE.IsWindowVisible(hwnd)) {
                first[0] = Pointer.nativeValue(hwnd);
                return false; // stop at the first match
            }
            return true;
        }, null);
        return first[0];
    }

    /**
     * Packs {@code #RRGGBB} into the {@code COLORREF} layout the Win32 DWM
     * attributes expect: {@code 0x00BBGGRR}. Malformed input yields black
     * rather than an exception — a wrong title bar color must never crash.
     */
    static int packColorRef(String hex) {
        if (hex == null || !hex.matches("(?i)#?[0-9a-f]{6}")) return 0x000000;
        int v = Integer.parseInt(hex.replace("#", ""), 16);
        int r = (v >> 16) & 0xFF;
        int g = (v >> 8) & 0xFF;
        int b = v & 0xFF;
        return (b << 16) | (g << 8) | r;
    }

    /** The only code that touches the native libraries. */
    private static final class Dwm {
        /** Minimal dwmapi binding — set + get of window attributes. */
        private interface DwmApi extends com.sun.jna.win32.StdCallLibrary {
            DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

            int DwmSetWindowAttribute(Pointer hwnd, int attribute, Pointer pvAttribute, int cbAttribute);
            int DwmGetWindowAttribute(Pointer hwnd, int attribute, Pointer pvAttribute, int cbAttribute);
        }

        /** Enables the OS dark caption (respected from Windows 10 1809 on). */
        static void enableDarkCaption(long hwnd) {
            setInt(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1);
            setInt(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_PRE20H1, 1);
        }

        /** Sets a COLORREF-valued attribute; unsupported attributes no-op. */
        static void setColorAttribute(long hwnd, int attribute, String hex) {
            setInt(hwnd, attribute, packColorRef(hex));
        }

        /** Read-back for the OS round-trip test; -1 when unsupported. */
        static int getInt(long hwnd, int attribute) {
            com.sun.jna.Memory pv = new com.sun.jna.Memory(4);
            pv.clear();
            int hr = DwmApi.INSTANCE.DwmGetWindowAttribute(new Pointer(hwnd), attribute, pv, 4);
            return (hr == 0) ? pv.getInt(0) : -1;
        }

        private static void setInt(long hwnd, int attribute, int value) {
            com.sun.jna.Memory pv = new com.sun.jna.Memory(4);
            pv.setInt(0, value);
            // Return code intentionally ignored: E_INVALIDARG simply means the
            // running Windows build does not support this attribute.
            DwmApi.INSTANCE.DwmSetWindowAttribute(new Pointer(hwnd), attribute, pv, 4);
        }
    }

    /** Read-back of the caption color for tests (-1 = unsupported, e.g. Win10). */
    static int readCaptionColor(long hwnd) {
        try {
            return Dwm.getInt(hwnd, DWMWA_CAPTION_COLOR_READBACK);
        } catch (Throwable t) {
            AppLog.debug(t);
            return -1;
        }
    }

    /** Read-back of the dark-caption flag for tests (-1 = unsupported, pre-19041). */
    static int readDarkFlag(long hwnd) {
        try {
            return Dwm.getInt(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE);
        } catch (Throwable t) {
            AppLog.debug(t);
            return -1;
        }
    }

    /** Thin JNA surface — kept minimal and package-local on purpose. */
    private interface User32Ext extends com.sun.jna.win32.StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);

        Pointer FindWindowW(com.sun.jna.WString lpClassName, com.sun.jna.WString lpWindowName);
        void GetWindowThreadProcessId(Pointer hwnd, IntByReference lpdwProcessId);
        boolean IsWindowVisible(Pointer hwnd);
        boolean EnumWindows(WndEnumProc lpEnumFunc, Pointer lParam);
        boolean SetWindowPos(Pointer hWnd, Pointer hWndInsertAfter, int X, int Y, int cx, int cy, int uFlags);
        boolean RedrawWindow(Pointer hWnd, Pointer lprcUpdate, Pointer hrgnUpdate, int flags);
    }

    /** Own-process window enumeration callback (core-JNA, no platform jar). */
    private interface WndEnumProc extends com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        boolean callback(Pointer hwnd, Pointer data);
    }

    /** kernel32 surface for the current process id. */
    private interface Kernel32Ext extends com.sun.jna.win32.StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);
        int GetCurrentProcessId();
        Pointer LoadLibraryW(com.sun.jna.WString lpLibFileName);
        Pointer GetProcAddress(Pointer hModule, Pointer lpProcName);
    }
}
