package org.cryptomator.linux.keychain;

import de.swiesend.secretservice.simple.SimpleCollection;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Priority(900)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class SecretServiceKeychainAccess implements KeychainAccessProvider {

	private static final Logger LOG = LoggerFactory.getLogger(SecretServiceKeychainAccess.class);

	private final String LABEL_FOR_SECRET_IN_KEYRING = "Cryptomator";

	@Override
	public String displayName() {
		return "Gnome Keyring";
	}

	@Override
	public boolean isSupported() {
		try {
			return SimpleCollection.isGnomeKeyringAvailable();
		} catch (RuntimeException e) {
			LOG.warn("Initializing secret service keychain access failed", e);
			return false;
		} catch (ExceptionInInitializerError err) {
			LOG.warn("Initializing secret service keychain access failed", err.getException());
			return false;
		}
	}

	@Override
	public boolean isLocked() {
		try (SimpleCollection keyring = new SimpleCollection()) {
			return keyring.isLocked();
		} catch (IOException e) {
			return true;
		}
	}

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase, boolean ignored) throws KeychainAccessException {
		try (SimpleCollection keyring = new SimpleCollection()) {
			List<String> list = keyring.getItems(createAttributes(key));
			if (list == null || list.isEmpty()) {
				keyring.createItem(LABEL_FOR_SECRET_IN_KEYRING, passphrase, createAttributes(key));
			} else {
				changePassphrase(key, displayName, passphrase);
			}
		} catch (IOException | SecurityException e) {
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
		} catch (IOException | SecurityException e) {
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
		} catch (IOException | SecurityException e) {
			throw new KeychainAccessException("Deleting password failed.", e);
		}
	}

	@Override
	public void changePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		try (SimpleCollection keyring = new SimpleCollection()) {
			List<String> list = keyring.getItems(createAttributes(key));
			if (list != null && !list.isEmpty()) {
				keyring.updateItem(list.get(0), LABEL_FOR_SECRET_IN_KEYRING, passphrase, createAttributes(key));
			}
		} catch (IOException | SecurityException e) {
			throw new KeychainAccessException("Changing password failed.", e);
		}
	}

	private Map<String, String> createAttributes(String key) {
		return Map.of("Vault", key);
	}

}
