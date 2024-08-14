import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.revealpath.RevealPathService;
import org.cryptomator.integrations.tray.TrayMenuController;
import org.cryptomator.linux.autostart.FreedesktopAutoStartService;
import org.cryptomator.linux.keychain.KDEWalletKeychainAccess;
import org.cryptomator.linux.keychain.SecretServiceKeychainAccess;
import org.cryptomator.linux.quickaccess.DolphinPlaces;
import org.cryptomator.linux.quickaccess.NautilusBookmarks;
import org.cryptomator.linux.revealpath.DBusSendRevealPathService;
import org.cryptomator.linux.tray.AppindicatorTrayMenuController;

module org.cryptomator.integrations.linux {
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires org.freedesktop.dbus;
	requires org.purejava.appindicator;
	requires org.purejava.kwallet;
	requires de.swiesend.secretservice;

	provides AutoStartProvider with FreedesktopAutoStartService;
	provides KeychainAccessProvider with SecretServiceKeychainAccess, KDEWalletKeychainAccess;
	provides RevealPathService with DBusSendRevealPathService;
	provides TrayMenuController with AppindicatorTrayMenuController;
	provides QuickAccessService with NautilusBookmarks, DolphinPlaces;

	opens org.cryptomator.linux.tray to org.cryptomator.integrations.api;
	opens org.cryptomator.linux.quickaccess to org.cryptomator.integrations.api;
}