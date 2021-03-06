package org.hive2hive.client.menu;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.hive2hive.client.console.H2HConsoleMenu;
import org.hive2hive.client.console.H2HConsoleMenuItem;
import org.hive2hive.client.console.SelectionMenu;
import org.hive2hive.client.util.MenuContainer;
import org.hive2hive.core.exceptions.Hive2HiveException;
import org.hive2hive.core.exceptions.IllegalFileLocation;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.IFileVersion;
import org.hive2hive.core.model.PermissionType;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.processes.framework.interfaces.IProcessComponent;
import org.hive2hive.core.processes.framework.interfaces.IResultProcessComponent;
import org.hive2hive.core.processes.implementations.files.list.FileTaste;
import org.hive2hive.core.processes.implementations.files.recover.IVersionSelector;

public class FileMenu extends H2HConsoleMenu {

	public H2HConsoleMenuItem CreateRootDirectory;

	private File rootDirectory;

	public FileMenu(MenuContainer menus) {
		super(menus);
	}

	@Override
	protected void createItems() {

		CreateRootDirectory = new H2HConsoleMenuItem("Create Root Directory") {
			protected void execute() throws Exception {

				rootDirectory = new File(FileUtils.getUserDirectory(), "H2H_" + System.currentTimeMillis());

				if (isExpertMode) {
					print(String.format(
							"Please specify the root directory path or enter 'ok' if you agree with '%s'.",
							rootDirectory.toPath()));

					String input = awaitStringParameter();

					if (!input.equalsIgnoreCase("ok")) {
						while (!Files.exists(new File(input).toPath(), LinkOption.NOFOLLOW_LINKS)) {

							printError("This directory does not exist. Please retry.");
							input = awaitStringParameter();
						}
					}
				}

				if (!Files.exists(rootDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
					try {
						FileUtils.forceMkdir(rootDirectory);
					} catch (Exception e) {
						printError(String.format("Exception on creating the root directory %s: " + e,
								rootDirectory.toPath()));
					}
				}
			}
		};
	}

	@Override
	protected void addMenuItems() {

		add(new H2HConsoleMenuItem("Add File") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws Hive2HiveException, InterruptedException {

				File file = askForFile(true);
				if (file == null)
					return;

				IProcessComponent addFileProcess = menus.getNodeMenu().getNode().getFileManager().add(file);
				executeBlocking(addFileProcess, displayText);
			}
		});

		add(new H2HConsoleMenuItem("Update File") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws Hive2HiveException, InterruptedException {

				File file = askForFile(true);
				if (file == null)
					return;
				IProcessComponent updateFileProcess = menus.getNodeMenu().getNode().getFileManager()
						.update(file);
				executeBlocking(updateFileProcess, displayText);
			}
		});

		add(new H2HConsoleMenuItem("Move File") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws Hive2HiveException, InterruptedException {
				File source = askForFile(
						"Specify the relative path of the source file to the root directory '%s'.", true);
				if (source == null)
					return;
				File destination = askForFile(
						"Specify the relative path of the destination file to the root directory '%s'.",
						false);
				if (destination == null)
					return;

				IProcessComponent moveFileProcess = menus.getNodeMenu().getNode().getFileManager()
						.move(source, destination);
				executeBlocking(moveFileProcess, displayText);
			}
		});

		add(new H2HConsoleMenuItem("Delete File") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws Hive2HiveException, InterruptedException {

				File file = askForFile(true);
				if (file == null)
					return;

				IProcessComponent deleteFileProcess = menus.getNodeMenu().getNode().getFileManager()
						.delete(file);
				executeBlocking(deleteFileProcess, displayText);
			}
		});

		add(new H2HConsoleMenuItem("Recover File") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws Hive2HiveException, FileNotFoundException,
					IllegalArgumentException, InterruptedException {

				File file = askForFile(true);
				if (file == null)
					return;

				IVersionSelector versionSelector = new IVersionSelector() {
					public IFileVersion selectVersion(List<IFileVersion> availableVersions) {
						return new SelectionMenu<IFileVersion>(availableVersions,
								"Choose the version you want to recover.").openAndSelect();
					}

					public String getRecoveredFileName(String fullName, String name, String extension) {
						print(String
								.format("Specify the new name for the recovered file '%s' or enter 'default' to take the default values:",
										fullName));
						String input = awaitStringParameter();
						if (input.equalsIgnoreCase("default"))
							return null;
						else
							return input;
					}
				};

				IProcessComponent recoverFileProcess = menus.getNodeMenu().getNode().getFileManager()
						.recover(file, versionSelector);
				executeBlocking(recoverFileProcess, displayText);
			}
		});

		add(new H2HConsoleMenuItem("Share File") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws NoSessionException, NoPeerConnectionException,
					InvalidProcessStateException, InterruptedException {

				File folderToShare = askForFolder(
						"Specify the relative path of the folder you want to share to the root directory '%s'.",
						true);
				if (folderToShare == null)
					return;

				print("Specify the user ID of the user you want to share with.");
				String friendID = awaitStringParameter();

				PermissionType permission = askForPermission(folderToShare.getAbsolutePath(), friendID);
				if (permission == null)
					return;

				IProcessComponent shareProcess;
				try {
					shareProcess = menus.getNodeMenu().getNode().getFileManager()
							.share(folderToShare, friendID, permission);
				} catch (IllegalFileLocation | IllegalArgumentException e) {
					printError(e.getMessage());
					return;
				}
				executeBlocking(shareProcess, displayText);
			}
		});

		add(new H2HConsoleMenuItem("Print File List") {
			@Override
			protected void execute() throws Exception {

				IResultProcessComponent<List<FileTaste>> fileListProcess = menus.getNodeMenu().getNode()
						.getFileManager().getFileList();
				executeBlocking(fileListProcess, displayText);

				if (!fileListProcess.getResult().isEmpty()) {
					for (FileTaste fileTaste : fileListProcess.getResult()) {
						print("* " + fileTaste);
					}
				} else {
					print("The file list is empty.");
				}
			}
		});

		add(new H2HConsoleMenuItem("File Observer") {
			protected void checkPreconditions() {
				forceRootDirectory();
			}

			protected void execute() throws Exception {
				menus.getFileObserverMenu().open(isExpertMode);
			}
		});
	}

	private File askForFile(boolean expectExistence) {
		return askForFile("Specify the relative path to the root directory '%s'.", expectExistence);
	}

	private File askForFile(String msg, boolean expectExistence) {
		return askFor(msg, expectExistence, false);
	}

	private File askForFolder(String msg, boolean expectExistence) {
		return askFor(msg, expectExistence, true);
	}

	private File askFor(String msg, boolean expectExistence, boolean requireDirectory) {

		// TODO allow drag&drop or another kind of easy navigation
		// TODO find better way to exit this menu
		// TODO be more flexible with inputs, e.g. files with whitespaces in name

		File file = null;
		do {
			print(String.format(msg.concat(expectExistence ? String.format(
					" The %s at this path must exist.", requireDirectory ? "folder" : "file") : ""),
					rootDirectory.getAbsolutePath()));
			print("Or enter 'cancel' in order to go back.");

			String input = awaitStringParameter();

			if (input.equalsIgnoreCase("cancel"))
				return null;

			file = new File(rootDirectory, input);
			if (expectExistence && !file.exists()) {
				printError(String.format("The specified %s '%s' does not exist. Try again.",
						requireDirectory ? "folder" : "file", file.getAbsolutePath()));
				continue;
			}
			if (expectExistence && requireDirectory && !file.isDirectory()) {
				printError(String.format("The specified file '%s' is not a folder. Try again.",
						file.getAbsolutePath()));
			}
		} while (expectExistence
				&& (file == null || !file.exists() || (requireDirectory && !file.isDirectory())));
		return file;
	}

	private PermissionType askForPermission(String folder, String userID) {

		List<PermissionType> permissionTypes = new ArrayList<PermissionType>();
		permissionTypes.add(PermissionType.WRITE);
		permissionTypes.add(PermissionType.READ);

		List<String> displayTexts = new ArrayList<String>();
		displayTexts.add("Read and Write");
		displayTexts.add("Read Only");

		return new SelectionMenu<PermissionType>(permissionTypes, displayTexts, String.format(
				"Specify the permissions of folder '%s' for the user '%s'.", folder, userID)).openAndSelect();
	}

	@Override
	protected String getInstruction() {
		return "Select a file operation:";
	}

	public File getRootDirectory() {
		return rootDirectory;
	}

	public void forceRootDirectory() {
		while (getRootDirectory() == null) {
			CreateRootDirectory.invoke();
		}
	}
}
