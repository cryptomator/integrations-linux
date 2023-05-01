import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.integrations.revealpath.RevealPathService;
import org.cryptomator.integrations.tray.TrayMenuController;
import org.cryptomator.linux.keychain.SecretServiceKeychainAccess;
import org.cryptomator.linux.revealpath.DBusSendRevealPathService;
import org.cryptomator.linux.tray.AppindicatorTrayMenuController;

module org.cryptomator.integrations.linux {
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.google.common;
	requires org.apache.commons.lang3;
	requires org.freedesktop.dbus;
	requires org.purejava.appindicator;
	requires org.purejava.kwallet;
	requires secret.service;

	provides KeychainAccessProvider with SecretServiceKeychainAccess;
	provides RevealPathService with DBusSendRevealPathService;
	provides TrayMenuController with AppindicatorTrayMenuController;

	exports org.cryptomator.linux.tray;
}