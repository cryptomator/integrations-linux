package org.cryptomator.linux.keychain;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.linux.util.CheckUtil;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusConnectionException;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.purejava.kwallet.KWallet;
import org.purejava.kwallet.KDEWallet;
import org.purejava.kwallet.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Priority(900)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class KDEWalletKeychainAccess implements KeychainAccessProvider {

	private static final Logger LOG = LoggerFactory.getLogger(KDEWalletKeychainAccess.class);
	private static final String FOLDER_NAME = "Cryptomator";
	private static final String APP_NAME = "Cryptomator";

	private final Optional<ConnectedWallet> wallet;

	public KDEWalletKeychainAccess() {
		this.wallet = ConnectedWallet.connect();
	}

	@Override
	public String displayName() {
		return "KDE Wallet";
	}

	@Override
	public boolean isSupported() {
		return wallet.map(ConnectedWallet::isSupported).orElse(false);
	}

	@Override
	public boolean isLocked() {
		return wallet.map(ConnectedWallet::isLocked).orElse(false);
	}

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		CheckUtil.checkState(wallet.isPresent(), "Keychain not supported.");
		wallet.get().storePassphrase(key, passphrase);
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		CheckUtil.checkState(wallet.isPresent(), "Keychain not supported.");
		return wallet.get().loadPassphrase(key);
	}

	@Override
	public void deletePassphrase(String key) throws KeychainAccessException {
		CheckUtil.checkState(wallet.isPresent(), "Keychain not supported.");
		wallet.get().deletePassphrase(key);
	}

	@Override
	public void changePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		CheckUtil.checkState(wallet.isPresent(), "Keychain not supported.");
		wallet.get().changePassphrase(key, passphrase);
	}

	private static class ConnectedWallet {

		private final KDEWallet wallet;
		private int handle = -1;

		public ConnectedWallet(DBusConnection connection) {
			this.wallet = new KDEWallet(connection);
		}

		static Optional<ConnectedWallet> connect() {
			try {
				return Optional.of(new ConnectedWallet(getNewConnection()));
			} catch (DBusException e) {
				LOG.warn("Connecting to D-Bus failed.", e);
				return Optional.empty();
			}
		}

		private static DBusConnection getNewConnection() throws DBusException {
			try {
				return DBusConnectionBuilder.forSessionBus().withShared(false).build();
			} catch (DBusConnectionException | DBusExecutionException de) {
				LOG.warn("Connecting to SESSION bus failed.", de);
				LOG.warn("Falling back to SYSTEM DBus");
				return DBusConnectionBuilder.forSystemBus().build();
			}
		}

		public boolean isSupported() {
			try {
				return wallet.isEnabled();
			} catch (RuntimeException e) {
				LOG.warn("Failed to check if KDE Wallet is available.", e);
				return false;
			}
		}

		public boolean isLocked() {
			try {
				return !wallet.isOpen(Static.DEFAULT_WALLET);
			} catch (RuntimeException e) {
				LOG.warn("Failed to check whether KDE Wallet is open, therefore considering it closed.", e);
				return true;
			}
		}

		public void storePassphrase(String key, CharSequence passphrase) throws KeychainAccessException {
			try {
				if (walletIsOpen() &&
						!(wallet.hasEntry(handle, FOLDER_NAME, key, APP_NAME) && wallet.entryType(handle, FOLDER_NAME, key, APP_NAME) == 1)
						&& wallet.writePassword(handle, FOLDER_NAME, key, passphrase.toString(), APP_NAME) == 0) {
					LOG.debug("Passphrase successfully stored.");
				} else {
					LOG.debug("Passphrase was not stored.");
				}
			} catch (RuntimeException e) {
				throw new KeychainAccessException("Storing the passphrase failed.", e);
			}
		}

		public char[] loadPassphrase(String key) throws KeychainAccessException {
			String password = "";
			try {
				if (walletIsOpen()) {
					password = wallet.readPassword(handle, FOLDER_NAME, key, APP_NAME);
					LOG.debug("loadPassphrase: wallet is open.");
				} else {
					LOG.debug("loadPassphrase: wallet is closed.");
				}
				return (password.isEmpty()) ? null : password.toCharArray();
			} catch (RuntimeException e) {
				throw new KeychainAccessException("Loading the passphrase failed.", e);
			}
		}

		public void deletePassphrase(String key) throws KeychainAccessException {
			try {
				if (walletIsOpen()
						&& wallet.hasEntry(handle, FOLDER_NAME, key, APP_NAME)
						&& wallet.entryType(handle, FOLDER_NAME, key, APP_NAME) == 1
						&& wallet.removeEntry(handle, FOLDER_NAME, key, APP_NAME) == 0) {
					LOG.debug("Passphrase successfully deleted.");
				} else {
					LOG.debug("Passphrase was not deleted.");
				}
			} catch (RuntimeException e) {
				throw new KeychainAccessException("Deleting the passphrase failed.", e);
			}
		}

		public void changePassphrase(String key, CharSequence passphrase) throws KeychainAccessException {
			try {
				if (walletIsOpen()
						&& wallet.hasEntry(handle, FOLDER_NAME, key, APP_NAME)
						&& wallet.entryType(handle, FOLDER_NAME, key, APP_NAME) == 1
						&& wallet.writePassword(handle, FOLDER_NAME, key, passphrase.toString(), APP_NAME) == 0) {
					LOG.debug("Passphrase successfully changed.");
				} else {
					LOG.debug("Passphrase could not be changed.");
				}
			} catch (RuntimeException e) {
				throw new KeychainAccessException("Changing the passphrase failed.", e);
			}
		}

		private boolean walletIsOpen() throws KeychainAccessException {
			try {
				if (wallet.isOpen(Static.DEFAULT_WALLET)) {
					// This is needed due to KeechainManager loading the passphase directly
					if (handle == -1) handle = wallet.open(Static.DEFAULT_WALLET, 0, APP_NAME);
					return true;
				}
				wallet.openAsync(Static.DEFAULT_WALLET, 0, APP_NAME, false);
				wallet.getSignalHandler().await(KWallet.walletAsyncOpened.class, Static.ObjectPaths.KWALLETD5, () -> null);
				handle = wallet.getSignalHandler().getLastHandledSignal(KWallet.walletAsyncOpened.class, Static.ObjectPaths.KWALLETD5).handle;
				LOG.debug("Wallet successfully initialized.");
				return handle != -1;
			} catch (RuntimeException e) {
				throw new KeychainAccessException("Asynchronous opening the wallet failed.", e);
			}
		}


	}
}
