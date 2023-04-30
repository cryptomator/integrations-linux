package org.cryptomator.ui.traymenu;

import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.integrations.common.CheckAvailability;
import org.cryptomator.integrations.tray.ActionItem;
import org.cryptomator.integrations.tray.SeparatorItem;
import org.cryptomator.integrations.tray.SubMenuItem;
import org.cryptomator.integrations.tray.TrayMenuController;
import org.cryptomator.integrations.tray.TrayMenuException;
import org.cryptomator.integrations.tray.TrayMenuItem;
import org.purejava.linux.MemoryAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;

import static org.purejava.linux.app_indicator_h.*;

@CheckAvailability
public class AppindicatorTrayMenuController implements TrayMenuController {

	private static final Logger LOG = LoggerFactory.getLogger(AppindicatorTrayMenuController.class);

	private final SegmentScope scope = SegmentScope.auto();
	private MemorySegment indicator;
	private MemorySegment menu = gtk_menu_new();

	@CheckAvailability
	public static boolean isAvailable() {
		return SystemUtils.IS_OS_LINUX && MemoryAllocator.isLoadedNativeLib();
	}

	@Override
	public void showTrayIcon(URI uri, Runnable runnable, String s) throws TrayMenuException {
		indicator = app_indicator_new(MemoryAllocator.ALLOCATE_FOR("org.cryptomator.Cryptomator"),
				MemoryAllocator.ALLOCATE_FOR(getAbsolutePath(getPathString(uri))),
				APP_INDICATOR_CATEGORY_APPLICATION_STATUS());
		gtk_widget_show_all(menu);
		app_indicator_set_menu(indicator, menu);
		app_indicator_set_status(indicator, APP_INDICATOR_STATUS_ACTIVE());
	}

	@Override
	public void updateTrayIcon(URI uri) {
		app_indicator_set_icon(indicator, MemoryAllocator.ALLOCATE_FOR(getAbsolutePath(getPathString(uri))));
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
			// TODO: use Pattern Matching for switch, once available
			if (item instanceof ActionItem a) {
				var gtkMenuItem = gtk_menu_item_new();
				gtk_menu_item_set_label(gtkMenuItem, MemoryAllocator.ALLOCATE_FOR(a.title()));
				g_signal_connect_object(gtkMenuItem,
						MemoryAllocator.ALLOCATE_FOR("activate"),
						MemoryAllocator.ALLOCATE_CALLBACK_FOR(new ActionItemCallback(a), scope),
						menu,
						0);
				gtk_menu_shell_append(menu, gtkMenuItem);
			} else if (item instanceof SeparatorItem) {
				var gtkSeparator = gtk_menu_item_new();
				gtk_menu_shell_append(menu, gtkSeparator);
			} else if (item instanceof SubMenuItem s) {
				var gtkMenuItem = gtk_menu_item_new();
				var gtkSubmenu = gtk_menu_new();
				gtk_menu_item_set_label(gtkMenuItem, MemoryAllocator.ALLOCATE_FOR(s.title()));
				addChildren(gtkSubmenu, s.items());
				gtk_menu_item_set_submenu(gtkMenuItem, gtkSubmenu);
				gtk_menu_shell_append(menu, gtkMenuItem);
			}
			gtk_widget_show_all(menu);
		}
	}
	private String getAbsolutePath(String iconName) {
		var res = getClass().getClassLoader().getResource(iconName);
		if (null == res) {
			throw new IllegalArgumentException("Icon '" + iconName + "' cannot be found in resource folder");
		}
		File file = null;
		try {
			file = Paths.get(res.toURI()).toFile();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Icon '" + iconName + "' cannot be converted to file", e);
		}
		return file.getAbsolutePath();
	}

	private String getPathString(URI uri) {
		return uri.getPath().substring(1);
	}
}
