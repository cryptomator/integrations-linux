import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.integrations.revealpath.RevealPathService;
import org.cryptomator.integrations.tray.TrayMenuController;
import org.cryptomator.linux.keychain.KDEWalletKeychainAccess;
import org.cryptomator.linux.keychain.SecretServiceKeychainAccess;
import org.cryptomator.linux.revealpath.DBusSendRevealPathService;
import org.cryptomator.linux.tray.AppindicatorTrayMenuController;

module org.cryptomator.integrations.linux {
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires org.freedesktop.dbus;
	requires org.purejava.appindicator;
	requires org.purejava.kwallet;
	requires de.swiesend.secretservice;

	provides KeychainAccessProvider with SecretServiceKeychainAccess, KDEWalletKeychainAccess;
	provides RevealPathService with DBusSendRevealPathService;
	provides TrayMenuController with AppindicatorTrayMenuController;

	opens org.cryptomator.linux.tray to org.cryptomator.integrations.api;
}