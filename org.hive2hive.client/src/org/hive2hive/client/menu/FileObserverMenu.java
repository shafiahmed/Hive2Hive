package org.hive2hive.client.menu;

import org.hive2hive.client.console.H2HConsoleMenu;
import org.hive2hive.client.console.H2HConsoleMenuItem;
import org.hive2hive.client.util.MenuContainer;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.api.H2HFileObserver;
import org.hive2hive.core.api.H2HFileObserverListener;
import org.hive2hive.core.api.interfaces.IFileObserver;
import org.hive2hive.core.api.interfaces.IFileObserverListener;

public class FileObserverMenu extends H2HConsoleMenu {

	private IFileObserver fileObserver;
	private long interval = H2HConstants.DEFAULT_FILE_OBSERVER_INTERVAL;

	public FileObserverMenu(MenuContainer menus) {
		super(menus);
	}

	@Override
	protected void addMenuItems() {

		if (isExpertMode) {
			add(new H2HConsoleMenuItem("Set Interval") {
				// TODO restart observer
				protected void execute() {
					System.out.println("Specify the observation interval (ms):");
					interval = awaitIntParameter();
				}
			});
		}

		add(new H2HConsoleMenuItem("Start File Observer") {
			protected void checkPreconditions() {
				menus.getNodeMenu().forceNetwork();
				menus.getFileMenu().forceRootDirectory();
			}

			protected void execute() throws Exception {

				fileObserver = new H2HFileObserver(menus.getFileMenu().getRootDirectory(), interval);

				IFileObserverListener listener = new H2HFileObserverListener(menus.getNodeMenu().getNode()
						.getFileManager());

				fileObserver.addFileObserverListener(listener);

				fileObserver.start();
			}
		});

		add(new H2HConsoleMenuItem("Stop File Observer") {
			protected void execute() throws Exception {
				if (fileObserver != null) {
					fileObserver.stop();
				}
			}
		});
	}

	@Override
	protected String getInstruction() {

		if (isExpertMode)
			return "Configure and start/stop the file observer:";
		else
			return "Start/stop the file observer:";
	}
	
	public IFileObserver getFileObserver() {
		return fileObserver;
	}

}
