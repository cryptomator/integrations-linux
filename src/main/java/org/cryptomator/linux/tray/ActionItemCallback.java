package org.cryptomator.linux.tray;

import org.cryptomator.integrations.tray.ActionItem;
import org.purejava.appindicator.GCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record ActionItemCallback (ActionItem actionItem) implements GCallback {
	private static final Logger LOG = LoggerFactory.getLogger(ActionItemCallback.class);

	@Override
	public void apply() {
		LOG.trace("Hit tray menu action '{}'", actionItem.title());
		actionItem.action().run();
	}
}
