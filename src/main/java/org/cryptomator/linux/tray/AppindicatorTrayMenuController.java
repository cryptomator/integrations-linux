package org.cryptomator.linux.tray;

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
import org.purejava.appindicator.MemoryAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.util.List;
import java.util.function.Consumer;

import static org.purejava.appindicator.app_indicator_h.*;

@Priority(1000)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class AppindicatorTrayMenuController implements TrayMenuController {

	private static final Logger LOG = LoggerFactory.getLogger(AppindicatorTrayMenuController.class);

	private static final SegmentScope SCOPE = SegmentScope.global();
	private MemorySegment indicator;
	private MemorySegment menu = gtk_menu_new();

	@CheckAvailability
	public static boolean isAvailable() {
		return MemoryAllocator.isLoadedNativeLib();
	}

	@Override
	public void showTrayIcon(Consumer<TrayIconLoader> iconLoader, Runnable runnable, String s) throws TrayMenuException {
		TrayIconLoader.FreedesktopIconName callback = this::showTrayIconWithSVG;
		iconLoader.accept(callback);
		gtk_widget_show_all(menu);
		app_indicator_set_menu(indicator, menu);
		app_indicator_set_status(indicator, APP_INDICATOR_STATUS_ACTIVE());
	}

	private void showTrayIconWithSVG(String s) {
		try (var arena = Arena.openConfined()) {
			indicator = app_indicator_new(arena.allocateUtf8String("org.cryptomator.Cryptomator"),
					arena.allocateUtf8String(s),
					APP_INDICATOR_CATEGORY_APPLICATION_STATUS());
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
			gtk_widget_show_all(menu);
		}
	}
}
