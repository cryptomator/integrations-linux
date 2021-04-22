package org.cryptomator.linux.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.freedesktop.secret.simple.SimpleCollection;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecretServiceKeychainAccess implements KeychainAccessProvider {

	private final String LABEL_FOR_SECRET_IN_KEYRING = "Cryptomator";

	@Override
	public boolean isSupported() {
		return SimpleCollection.isAvailable();
	}

	@Override
	public boolean isLocked() {
		try (@SuppressWarnings("unused") SimpleCollection keyring = new SimpleCollection()) {
			// seems like we're able to access the keyring.
			return keyring.isLocked();
		} catch (IOException e) {
			return true;
		}
	}

	@Override
	public void storePassphrase(String key, CharSequence passphrase) throws KeychainAccessException {
		try (SimpleCollection keyring = new SimpleCollection()) {
			List<String> list = keyring.getItems(createAttributes(key));
			if (list == null || list.isEmpty()) {
				keyring.createItem(LABEL_FOR_SECRET_IN_KEYRING, passphrase, createAttributes(key));
			} else {
				changePassphrase(key, passphrase);
			}
		} catch (IOException | AccessControlException e) {
			throw new KeychainAccessException("Storing password failed.", e);
		}
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		try (SimpleCollection keyring = new SimpleCollection()) {
			List<String> list = keyring.getItems(createAttributes(key));
			if (list != null && !list.isEmpty()) {
				return keyring.getSecret(list.get(0));
			} else {
				return null;
			}
		} catch (IOException | AccessControlException e) {
			throw new KeychainAccessException("Loading password failed.", e);
		}
	}

	@Override
	public void deletePassphrase(String key) throws KeychainAccessException {
		try (SimpleCollection keyring = new SimpleCollection()) {
			List<String> list = keyring.getItems(createAttributes(key));
			if (list != null && !list.isEmpty()) {
				keyring.deleteItem(list.get(0));
			}
		} catch (IOException | AccessControlException e) {
			throw new KeychainAccessException("Deleting password failed.", e);
		}
	}

	@Override
	public void changePassphrase(String key, CharSequence passphrase) throws KeychainAccessException {
		try (SimpleCollection keyring = new SimpleCollection()) {
			List<String> list = keyring.getItems(createAttributes(key));
			if (list != null && !list.isEmpty()) {
				keyring.updateItem(list.get(0), LABEL_FOR_SECRET_IN_KEYRING, passphrase, createAttributes(key));
			}
		} catch (IOException | AccessControlException e) {
			throw new KeychainAccessException("Changing password failed.", e);
		}
	}

	private Map<String, String> createAttributes(String key) {
		return Map.of("Vault", key);
	}

}
