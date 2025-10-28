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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Priority(1100)
@OperatingSystem(OperatingSystem.Value.LINUX)
@DisplayName("Secret Service")
public class SecretServiceKeychainAccess implements KeychainAccessProvider {

	private static final Logger LOG = LoggerFactory.getLogger(SecretServiceKeychainAccess.class);
	private final EncryptedSession session = new EncryptedSession();
	private final Collection collection = new Collection(new DBusPath(Static.DBusPath.DEFAULT_COLLECTION));

	public SecretServiceKeychainAccess() {
		session.getService().addCollectionChangedHandler(collection -> LOG.debug("Collection {} changed", collection.getPath()));
		session.getService().addCollectionCreatedHandler(collection -> LOG.debug("Collection {} created", collection.getPath()));
		session.getService().addCollectionDeletedHandler(collection -> LOG.debug("Collection {} deleted", collection.getPath()));
		var getAlias = session.getService().readAlias("default");
		if (getAlias.isSuccess() && "/".equals(getAlias.value().getPath())) {
			// default alias is not set; set it to the login keyring
			session.getService().setAlias("default", new DBusPath(Static.DBusPath.LOGIN_COLLECTION));
		}
		collection.addItemChangedHandler(item -> LOG.debug("Item {} changed", item.getPath()));
		collection.addItemCreatedHandler(item -> LOG.debug("Item {} created", item.getPath()));
		collection.addItemDeletedHandler(item -> LOG.debug("Item {} deleted", item.getPath()));

		migrateKDEWalletEntries();
	}

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		try {
			var call = collection.searchItems(createAttributes(key));
			if (call.isSuccess()) {
				if (call.value().isEmpty()) {
					List<DBusPath> lockable = new ArrayList<>();
					lockable.add(new DBusPath(collection.getDBusPath()));
					session.getService().unlock(lockable);
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
					session.getService().ensureUnlocked(path);
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
					session.getService().ensureUnlocked(path);
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
					session.getService().ensureUnlocked(call.value().getFirst());
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

	private void migrateKDEWalletEntries() {
		session.setupEncryptedSession();
		var getItems = collection.getItems();
		if (getItems.isSuccess() && !getItems.value().isEmpty()) {
			for (DBusPath i : getItems.value()) {
				session.getService().ensureUnlocked(i);
				var attribs = new Item(i).getAttributes();
				if (attribs.isSuccess() &&
						attribs.value().containsKey("server") &&
						attribs.value().containsKey("user") &&
						attribs.value().get("server").equals("Cryptomator")) {

					session.getService().ensureUnlocked(i);
					var item = new Item(i);
					var secret = item.getSecret(session.getSession());
					Map<String, String> newAttribs = new HashMap<>(attribs.value());
					newAttribs.put("server", "Cryptomator - already migrated");
					var label = item.getLabel().value();
					var itemProps = Item.createProperties(label, newAttribs);
					var replace = collection.createItem(itemProps, secret, true);
					assert replace.isSuccess() : "Replacing migrated item failed";
					item.delete();
 					try {
						storePassphrase(attribs.value().get("user"), "Cryptomator", new String(session.decrypt(secret)));
						LOG.info("Successfully migrated password for vault {}", attribs.value().get("user"));
					} catch (KeychainAccessException | NoSuchPaddingException | NoSuchAlgorithmException |
							 InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException |
							 IllegalBlockSizeException e) {
						LOG.error("Migrating entry {} for vault {} failed", i.getPath(), attribs.value().get("user"));
					 }
				}
			}
		}
	}
}
