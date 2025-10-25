package org.cryptomator.linux.keychain;

import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.freedesktop.dbus.DBusPath;
import org.purejava.secret.api.Collection;
import org.purejava.secret.api.EncryptedSession;
import org.purejava.secret.api.Item;
import org.purejava.secret.api.Static;

import java.util.Map;

@Priority(900)
@OperatingSystem(OperatingSystem.Value.LINUX)
@DisplayName("Secret Service")
public class SecretServiceKeychainAccess implements KeychainAccessProvider {

	private final EncryptedSession session = new EncryptedSession();
	private final Collection collection = new Collection(new DBusPath(Static.DBusPath.DEFAULT_COLLECTION));

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		try {
			var call = collection.searchItems(createAttributes(key));
			if (call.isSuccess()) {
				if (call.value().isEmpty()) {
					var itemProps = Item.createProperties(displayName, createAttributes(key));
					var secret = session.encrypt(passphrase);
					var created = collection.createItem(itemProps, secret, false);
					if (!created.isSuccess()) {
						throw new KeychainAccessException("Storing password failed", created.error());
					}
				} else {
					changePassphrase(key, displayName, passphrase);
				}
			} else {
				throw new KeychainAccessException("Storing password failed", call.error());
			}
		} catch (Exception e) {
			throw new KeychainAccessException("Storing password failed.", e);
		}
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		try {
			var call = collection.searchItems(createAttributes(key));
			if (call.isSuccess()) {
				if (!call.value().isEmpty()) {
					var path = call.value().getFirst();
					var secret = new Item(path).getSecret(session.getSession());
					return session.decrypt(secret);
				} else {
					return null;
				}
			} else {
				throw new KeychainAccessException("Loading password failed", call.error());
			}
		} catch (Exception e) {
			throw new KeychainAccessException("Loading password failed.", e);
		}
	}

	@Override
	public void deletePassphrase(String key) throws KeychainAccessException {
		try {
			var call = collection.searchItems(createAttributes(key));
			if (call.isSuccess()) {
				if (!call.value().isEmpty()) {
					var path = call.value().getFirst();
					var item = new Item(path);
					var deleted = item.delete();
					if (!deleted.isSuccess()) {
						throw new KeychainAccessException("Deleting password failed", deleted.error());
					}
				} else {
					var msg = "Vault " + key + " not found, deleting failed";
					throw new KeychainAccessException(msg);
				}
			} else {
				throw new KeychainAccessException("Deleting password failed", call.error());
			}
		} catch (Exception e) {
			throw new KeychainAccessException("Deleting password failed", e);
		}
	}

	@Override
	public void changePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		try {
			var call = collection.searchItems(createAttributes(key));
			if (call.isSuccess()) {
				if (!call.value().isEmpty()) {
					var secret = session.encrypt(passphrase);
					var itemProps = Item.createProperties(displayName, createAttributes(key));
					var updated = collection.createItem(itemProps, secret, true);
					if (!updated.isSuccess()) {
						throw new KeychainAccessException("Updating password failed", updated.error());
					}
				} else {
					var msg = "Vault " + key + " not found, updating failed";
					throw new KeychainAccessException(msg);
				}
			} else {
				throw new KeychainAccessException("Updating password failed", call.error());
			}
		} catch (Exception e) {
			throw new KeychainAccessException("Updating password failed", e);
		}
	}

	@Override
	public boolean isSupported() {
		return session.setupEncryptedSession() &&
				session.getService().hasDefaultCollection();
	}

	@Override
	public boolean isLocked() {
		var call = collection.isLocked();
		return call.isSuccess() && call.value();
	}

	private Map<String, String> createAttributes(String key) {
		return Map.of("Vault", key);
	}
}
