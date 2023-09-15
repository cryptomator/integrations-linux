package org.cryptomator.linux.tray;

import org.apache.commons.lang3.StringUtils;
import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.tray.ActionItem;
import org.cryptomator.integrations.tray.SeparatorItem;
import org.cryptomator.integrations.tray.SubMenuItem;
import org.cryptomator.integrations.tray.TrayIconLoader;
import org.cryptomator.integrations.tray.TrayMenuController;
import org.cryptomator.integrations.tray.TrayMenuException;
import org.cryptomator.integrations.tray.TrayMenuItem;
import org.purejava.appindicator.GCallback;
import org.purejava.appindicator.NativeLibUtilities;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.purejava.appindicator.app_indicator_h.*;

@Priority(1000)
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
public class AppindicatorTrayMenuController implements TrayMenuController {
	private static final String APP_INDICATOR_ID = "org.cryptomator.Cryptomator";
	private static final String SVG_SOURCE_PROPERTY = "cryptomator.integrationsLinux.trayIconsDir";

	private static final SegmentScope SCOPE = SegmentScope.global();
	private MemorySegment indicator;
	private MemorySegment menu = gtk_menu_new();
	private Optional<String> svgSourcePath;

	@CheckAvailability
	public static boolean isAvailable() {
		return NativeLibUtilities.isLoadedNativeLib();
	}

	@Override
	public void showTrayIcon(Consumer<TrayIconLoader> iconLoader, Runnable runnable, String s) throws TrayMenuException {
		TrayIconLoader.FreedesktopIconName callback = this::showTrayIconWithSVG;
		iconLoader.accept(callback);
		gtk_widget_show_all(menu);
		app_indicator_set_status(indicator, APP_INDICATOR_STATUS_ACTIVE());
	}

	private void showTrayIconWithSVG(String s) {
		try (var arena = Arena.openConfined()) {
			svgSourcePath = Optional.ofNullable(System.getProperty(SVG_SOURCE_PROPERTY));
			// flatpak
			if (svgSourcePath.isEmpty()) {
				indicator = app_indicator_new(arena.allocateUtf8String(APP_INDICATOR_ID),
						arena.allocateUtf8String(s),
						APP_INDICATOR_CATEGORY_APPLICATION_STATUS());
			// AppImage and ppa
			} else {
				indicator = app_indicator_new_with_path(arena.allocateUtf8String(APP_INDICATOR_ID),
						arena.allocateUtf8String(s),
						APP_INDICATOR_CATEGORY_APPLICATION_STATUS(),
						// find tray icons theme in mounted AppImage / installed on system by ppa
						arena.allocateUtf8String(svgSourcePath.get()));
			}
		}
	}

	@Override
	public void updateTrayIcon(Consumer<TrayIconLoader> iconLoader) {
		TrayIconLoader.FreedesktopIconName callback = this::updateTrayIconWithSVG;
		iconLoader.accept(callback);
	}

	private void updateTrayIconWithSVG(String s) {
		try (var arena = Arena.openConfined()) {
			app_indicator_set_icon(indicator, arena.allocateUtf8String(s));
		}
	}

	@Override
	public void updateTrayMenu(List<TrayMenuItem> items) throws TrayMenuException {
		menu = gtk_menu_new();
		addChildren(menu, items);
		gtk_widget_show_all(menu);
		app_indicator_set_menu(indicator, menu);
	}

	@Override
	public void onBeforeOpenMenu(Runnable runnable) {

	}

	private void addChildren(MemorySegment menu, List<TrayMenuItem> items) {
		for (var item : items) {
			switch (item) {
				case ActionItem a -> {
					var gtkMenuItem = gtk_menu_item_new();
					try (var arena = Arena.openConfined()) {
						gtk_menu_item_set_label(gtkMenuItem, arena.allocateUtf8String(a.title()));
						g_signal_connect_object(gtkMenuItem,
								arena.allocateUtf8String("activate"),
								GCallback.allocate(new ActionItemCallback(a), SCOPE),
								menu,
								0);
					}
					gtk_menu_shell_append(menu, gtkMenuItem);
				}
				case SeparatorItem separatorItem -> {
					var gtkSeparator = gtk_menu_item_new();
					gtk_menu_shell_append(menu, gtkSeparator);
				}
				case SubMenuItem s -> {
					var gtkMenuItem = gtk_menu_item_new();
					var gtkSubmenu = gtk_menu_new();
					try (var arena = Arena.openConfined()) {
						gtk_menu_item_set_label(gtkMenuItem, arena.allocateUtf8String(s.title()));
					}
					addChildren(gtkSubmenu, s.items());
					gtk_menu_item_set_submenu(gtkMenuItem, gtkSubmenu);
					gtk_menu_shell_append(menu, gtkMenuItem);
				}
			}
		}
	}
}
