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
import org.cryptomator.linux.util.CheckUtil;
import org.purejava.appindicator.AppIndicator;
import org.purejava.appindicator.GCallback;
import org.purejava.appindicator.GObject;
import org.purejava.appindicator.Gtk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.Consumer;

import static org.purejava.appindicator.app_indicator_h.APP_INDICATOR_CATEGORY_APPLICATION_STATUS;
import static org.purejava.appindicator.app_indicator_h.APP_INDICATOR_STATUS_ACTIVE;

@Priority(1000)
@CheckAvailability
@OperatingSystem(OperatingSystem.Value.LINUX)
public class AppindicatorTrayMenuController implements TrayMenuController {
	private static final String APP_INDICATOR_ID = "org.cryptomator.Cryptomator";
	private static final String SVG_SOURCE_PROPERTY = "cryptomator.integrationsLinux.trayIconsDir";

	private static final Arena ARENA = Arena.global();
	private MemorySegment indicator;
	private MemorySegment menu;

	@CheckAvailability
	public static boolean isAvailable() {
		return AppIndicator.isLoaded();
	}

	@Override
	public void showTrayIcon(Consumer<TrayIconLoader> iconLoader, Runnable runnable, String s) throws TrayMenuException {
		menu = Gtk.newMenu();
		TrayIconLoader.FreedesktopIconName callback = this::showTrayIconWithSVG;
		iconLoader.accept(callback);
		Gtk.widgetShowAll(menu);
		AppIndicator.setStatus(indicator, APP_INDICATOR_STATUS_ACTIVE());
	}

	private void showTrayIconWithSVG(String iconName) {
		var svgSourcePath = System.getProperty(SVG_SOURCE_PROPERTY);
		// flatpak
		if (svgSourcePath == null) {
			indicator = AppIndicator.newIndicator(APP_INDICATOR_ID,
					iconName,
					APP_INDICATOR_CATEGORY_APPLICATION_STATUS());
		// AppImage and ppa
		} else {
			indicator = AppIndicator.newIndicatorWithPath(APP_INDICATOR_ID,
					iconName,
					APP_INDICATOR_CATEGORY_APPLICATION_STATUS(),
					// find tray icons theme in mounted AppImage / installed on system by ppa
					svgSourcePath);
		}
	}

	@Override
	public void updateTrayIcon(Consumer<TrayIconLoader> iconLoader) {
		TrayIconLoader.FreedesktopIconName callback = this::updateTrayIconWithSVG;
		iconLoader.accept(callback);
	}

	private void updateTrayIconWithSVG(String iconName) {
		CheckUtil.checkState(indicator != null, "Appindicator is not setup. Call showTrayIcon(...) first.");
		AppIndicator.setIcon(indicator, iconName);
	}

	@Override
	public void updateTrayMenu(List<TrayMenuItem> items) throws TrayMenuException {
		CheckUtil.checkState(indicator != null, "Appindicator is not setup. Call showTrayIcon(...) first.");
		menu = Gtk.newMenu();
		addChildren(menu, items);
		Gtk.widgetShowAll(menu);
		AppIndicator.setMenu(indicator, menu);
	}

	@Override
	public void onBeforeOpenMenu(Runnable runnable) {

	}

	private void addChildren(MemorySegment menu, List<TrayMenuItem> items) {
		for (var item : items) {
			switch (item) {
				case ActionItem a -> {
					var gtkMenuItem = Gtk.newMenuItem();
					Gtk.menuItemSetLabel(gtkMenuItem, a.title());
					GObject.signalConnectObject(gtkMenuItem,
							"activate",
							GCallback.allocate(new ActionItemCallback(a), ARENA),
							menu,
							0);
					Gtk.widgetSetSensitive(gtkMenuItem, a.enabled());
					Gtk.menuShellAppend(menu, gtkMenuItem);
				}
				case SeparatorItem _ -> {
					var gtkSeparator = Gtk.newMenuItem();
					Gtk.menuShellAppend(menu, gtkSeparator);
				}
				case SubMenuItem s -> {
					var gtkMenuItem = Gtk.newMenuItem();
					var gtkSubmenu = Gtk.newMenu();
					Gtk.menuItemSetLabel(gtkMenuItem, s.title());
					addChildren(gtkSubmenu, s.items());
					Gtk.menuItemSetSubmenu(gtkMenuItem, gtkSubmenu);
					Gtk.menuShellAppend(menu, gtkMenuItem);
				}
			}
		}
	}
}
