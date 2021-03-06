package org.hive2hive.core.processes.implementations.logout;

import java.io.IOException;
import java.nio.file.Path;

import org.hive2hive.core.file.FileUtil;
import org.hive2hive.core.network.data.PublicKeyManager;
import org.hive2hive.core.processes.framework.abstracts.ProcessStep;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.processes.framework.exceptions.ProcessExecutionException;

public class WritePersistentStep extends ProcessStep {

	private final Path root;
	private final PublicKeyManager keyManager;

	public WritePersistentStep(Path root, PublicKeyManager keyManager) {
		this.root = root;
		this.keyManager = keyManager;
	}

	@Override
	protected void doExecute() throws InvalidProcessStateException, ProcessExecutionException {
		// write the current state to a meta file
		try {
			FileUtil.writePersistentMetaData(root, keyManager);
		} catch (IOException e) {
			throw new ProcessExecutionException("Meta data could not be persisted.", e);
		}
	}

}
