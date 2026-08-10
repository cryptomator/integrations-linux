package org.cryptomator.linux.keychain;

import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.freedesktop.dbus.DBusPath;
import org.purejava.secret.api.Collection;
import org.purejava.secret.api.DBusMessageHandler;
import org.purejava.secret.api.EncryptedSession;
import org.purejava.secret.api.Item;
import org.purejava.secret.api.Pair;
import org.purejava.secret.api.Static;
import org.purejava.secret.api.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.purejava.secret.api.DBusMessageHandler.DBusResult.*;

@Priority(1100)
@OperatingSystem(OperatingSystem.Value.LINUX)
@DisplayName("Secret Service")
public class SecretServiceKeychainAccess implements KeychainAccessProvider {

	private static final Logger LOG = LoggerFactory.getLogger(SecretServiceKeychainAccess.class);
	private static final String LABEL_FOR_SECRET_IN_KEYRING = "Cryptomator";
	private static final String ID_KEY = "Vault";
	private static final String NAME_KEY = "Name";
	private final EncryptedSession session = new EncryptedSession();
	private final Collection collection = new Collection(new DBusPath(Static.DBusPath.DEFAULT_COLLECTION));

	public SecretServiceKeychainAccess() {
		session.getService().addCollectionChangedHandler(collection -> LOG.debug("Collection {} changed", collection.getPath()));
		session.getService().addCollectionCreatedHandler(collection -> LOG.debug("Collection {} created", collection.getPath()));
		session.getService().addCollectionDeletedHandler(collection -> LOG.debug("Collection {} deleted", collection.getPath()));
		var getAlias = session.getService().readAlias("default");
		switch (getAlias) {
			case Success<DBusPath> success-> {
				if ("/".equals(success.value().getPath())) {
					// default alias is not set; set it to the login keyring
					session.getService().setAlias("default", new DBusPath(Static.DBusPath.LOGIN_COLLECTION));
				}
			}
			case Failure<DBusPath> failure
					-> LOG.warn("Getting the collection with the \"default\" alias failed with: {}", failure.error().getMessage());
		}
		collection.addItemChangedHandler(item -> LOG.debug("Item {} changed", item.getPath()));
		collection.addItemCreatedHandler(item -> LOG.debug("Item {} created", item.getPath()));
		collection.addItemDeletedHandler(item -> LOG.debug("Item {} deleted", item.getPath()));

	}

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		try {
			var call = collection.searchItems(withKey(key));
			switch (call) {
				case DBusMessageHandler.DBusResult.Success<List<DBusPath>> success
						when success.value().isEmpty() -> {
					List<DBusPath> lockable = List.of(new DBusPath(collection.getDBusPath()));
					var unlockResult = session.getService().unlock(lockable);

					switch (unlockResult) {
						case Success<Pair<List<DBusPath>, DBusPath>> unlockSuccess -> {
							var prompt = unlockSuccess.value().b;
							if (!"/".equals(prompt.getPath())) {
								Util.promptAndGetResultAsArrayList(prompt);
							}
						}
						case Failure<Pair<List<DBusPath>, DBusPath>> unlockFailure ->
								LOG.warn("Failed to unlock collection {}",
										collection.getDBusPath(),
										unlockFailure.error());
					}

					var itemProps = Item.createProperties(
							LABEL_FOR_SECRET_IN_KEYRING,
							withKeyAndName(key, displayName)
					);
					var secret = session.encrypt(passphrase);
					var created = collection.createItem(itemProps, secret, false);

					switch (created) {
						case Success<Pair<DBusPath, DBusPath>> successful ->
								LOG.debug("Created item {} on collection {}",
										successful.value().a.getPath(),
										collection.getDBusPath());
						case Failure<Pair<DBusPath, DBusPath>> failure ->
								throw new KeychainAccessException(
										"Storing password failed for collection "
												+ collection.getDBusPath(),
										failure.error()
								);
					}
				}
				case DBusMessageHandler.DBusResult.Success<List<DBusPath>> _ ->
						changePassphrase(key, displayName, passphrase);

				case DBusMessageHandler.DBusResult.Failure<List<DBusPath>> failure ->
						throw new KeychainAccessException(
								"Storing password failed for collection "
										+ collection.getDBusPath(),
								failure.error()
						);
			}
		} catch (KeychainAccessException e) {
			throw e;
		} catch (Exception e) {
			throw new KeychainAccessException(
					"Storing password failed for collection "
							+ collection.getDBusPath(),
					e
			);
		}
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		try {
			var call = collection.searchItems(withKey(key));

			switch (call) {
				case Success<List<DBusPath>> success
						when success.value().isEmpty() -> {
					return null;
				}

				case Success<List<DBusPath>> success -> {
					assertOnlyOneItem(success);
					var path = success.value().getFirst();

					session.getService().ensureUnlocked(path);

					var secret = new Item(path).getSecret(session.getSession());
					return session.decrypt(secret);
				}

				case Failure<List<DBusPath>> failure ->
						throw new KeychainAccessException(
								"Loading password failed for collection "
										+ collection.getDBusPath(),
								failure.error()
						);
			}
		} catch (KeychainAccessException e) {
			throw e;
		} catch (Exception e) {
			throw new KeychainAccessException(
					"Loading password failed for collection "
							+ collection.getDBusPath(),
					e
			);
		}
	}

	@Override
	public void deletePassphrase(String key) throws KeychainAccessException {
		try {
			var call = collection.searchItems(withKey(key));

			switch (call) {
				case Success<List<DBusPath>> success
						when success.value().isEmpty() ->
					LOG.debug("Deleting entry with {}={} failed: No such item found",
							ID_KEY,
							key);

				case Success<List<DBusPath>> success -> {
					assertOnlyOneItem(success);
					var path = success.value().getFirst();
					session.getService().ensureUnlocked(path);
					var item = new Item(path);

					switch (item.delete()) {
						case Success<DBusPath> _ ->
								LOG.debug("Deleted item {} from collection {}",
										path.getPath(),
										collection.getDBusPath());

						case Failure<DBusPath> failure -> {
							LOG.warn("Failed to delete item {} from collection {}",
									path.getPath(),
									collection.getDBusPath(),
									failure.error());

							throw new KeychainAccessException(
									"Deleting password failed for collection "
											+ collection.getDBusPath(),
									failure.error()
							);
						}
					}
				}

				case Failure<List<DBusPath>> failure ->
						throw new KeychainAccessException(
								"Deleting password failed for collection "
										+ collection.getDBusPath(),
								failure.error()
						);
			}
		} catch (KeychainAccessException e) {
			throw e;
		} catch (Exception e) {
			throw new KeychainAccessException(
					"Deleting password failed for collection "
							+ collection.getDBusPath(),
					e
			);
		}
	}

	@Override
	public void changePassphrase(String key, String displayName, CharSequence passphrase)
			throws KeychainAccessException {
		try {
			var call = collection.searchItems(withKey(key));

			switch (call) {
				case Success<List<DBusPath>> success
						when success.value().isEmpty() -> {
					var message = "Vault " + key + " not found, updating failed";
					throw new KeychainAccessException(message);
				}

				case Success<List<DBusPath>> success -> {
					assertOnlyOneItem(success);
					var path = success.value().getFirst();
					session.getService().ensureUnlocked(path);
					var secret = session.encrypt(passphrase);
					var itemProps = Item.createProperties(
							LABEL_FOR_SECRET_IN_KEYRING,
							withKeyAndName(key, displayName)
					);
					var updated = collection.createItem(itemProps, secret, true);

					switch (updated) {
						case Success<Pair<DBusPath, DBusPath>> _ ->
								LOG.debug("Updated item {} in collection {}",
										path.getPath(),
										collection.getDBusPath());

						case Failure<Pair<DBusPath, DBusPath>> failure -> {
							LOG.warn("Failed to update item {} in collection {}",
									path.getPath(),
									collection.getDBusPath(),
									failure.error());

							throw new KeychainAccessException(
									"Updating password failed for collection "
											+ collection.getDBusPath(),
									failure.error()
							);
						}
					}
				}

				case Failure<List<DBusPath>> failure ->
						throw new KeychainAccessException(
								"Updating password failed for collection "
										+ collection.getDBusPath(),
								failure.error()
						);
			}
		} catch (KeychainAccessException e) {
			throw e;
		} catch (Exception e) {
			throw new KeychainAccessException(
					"Updating password failed for collection "
							+ collection.getDBusPath(),
					e
			);
		}
	}

	private static void assertOnlyOneItem(Success<List<DBusPath>> success) throws KeychainAccessException {
		if (success.value().size() != 1) {
			throw new KeychainAccessException(
					"Expected exactly one item, but found " + success.value().size()
			);
		}
	}

	@Override
	public boolean isSupported() {
		try {
			return session.setupEncryptedSession() &&
					session.getService().hasDefaultCollection();
		} catch (RuntimeException e) {
			LOG.debug("Not supported due to exception in isSupported method", e);
			return false;
		}
	}

	@Override
	public boolean isLocked() {
		return switch (collection.isLocked()) {
			case Success<Boolean> success ->
					success.value(); // yields the value

			case Failure<Boolean> failure -> {
				LOG.warn(
						"Failed to determine lock state of collection {}",
						collection.getDBusPath(),
						failure.error()
				);
				yield true;
			}
		};
	}

	private Map<String, String> withKey(String key) {
		if (key == null) {
			throw new IllegalArgumentException("Arguments must not be null");
		}
		return Map.of(ID_KEY, key);
	}

	private Map<String, String> withKeyAndName(String key, String name) {
		if (key == null) {
			throw new IllegalArgumentException("Arguments must not be null");
		}
		return Map.of(ID_KEY, key, NAME_KEY, Objects.requireNonNullElse(name, ""));
	}
}
