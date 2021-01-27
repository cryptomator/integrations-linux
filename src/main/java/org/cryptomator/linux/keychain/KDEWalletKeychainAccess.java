package org.cryptomator.linux.keychain;

import com.google.common.base.Preconditions;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.kde.KWallet;
import org.kde.Static;
import org.purejava.KDEWallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class KDEWalletKeychainAccess implements KeychainAccessProvider {

	private static final Logger LOG = LoggerFactory.getLogger(KDEWalletKeychainAccess.class);
	private static final String FOLDER_NAME = "Cryptomator";
	private static final String APP_NAME = "Cryptomator";

	private final Optional<ConnectedWallet> wallet;

	public KDEWalletKeychainAccess() {
		ConnectedWallet wallet = null;
		try {
			DBusConnection conn = null;
			try {
				conn = DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION);
			} catch (RuntimeException e) {
				if (e.getMessage() == "Cannot Resolve Session Bus Address") {
					LOG.warn("SESSION DBus not found.");
				}
			}
			if (conn == null) {
				conn = DBusConnection.getConnection(DBusConnection.DBusBusType.SYSTEM);
			}
			wallet = new ConnectedWallet(conn);
		} catch (DBusException e) {
			LOG.warn("Connecting to D-Bus failed.", e);
		}
		this.wallet = Optional.ofNullable(wallet);
	}

	@Override
	public boolean isSupported() {
		return wallet.map(ConnectedWallet::isSupported).orElse(false);
	}

	@Override
	public void storePassphrase(String key, CharSequence passphrase) throws KeychainAccessException {
		Preconditions.checkState(wallet.isPresent(), "Keychain not supported.");
		wallet.get().storePassphrase(key, passphrase);
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		Preconditions.checkState(wallet.isPresent(), "Keychain not supported.");
		return wallet.get().loadPassphrase(key);
	}

	@Override
	public void deletePassphrase(String key) throws KeychainAccessException {
		Preconditions.checkState(wallet.isPresent(), "Keychain not supported.");
		wallet.get().deletePassphrase(key);
	}

	@Override
	public void changePassphrase(String key, CharSequence passphrase) throws KeychainAccessException {
		Preconditions.checkState(wallet.isPresent(), "Keychain not supported.");
		wallet.get().changePassphrase(key, passphrase);
	}

	private static class ConnectedWallet {

		private final KDEWallet wallet;
		private int handle = -1;

		public ConnectedWallet(DBusConnection connection) {
			this.wallet = new KDEWallet(connection);
		}

		public boolean isSupported() {
			return wallet.isEnabled();
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
				return (password.equals("")) ? null : password.toCharArray();
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
