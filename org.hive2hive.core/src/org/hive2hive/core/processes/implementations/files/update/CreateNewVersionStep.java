package org.hive2hive.core.processes.implementations.files.update;

import java.util.ArrayList;
import java.util.List;

import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.file.FileUtil;
import org.hive2hive.core.model.FileVersion;
import org.hive2hive.core.model.MetaChunk;
import org.hive2hive.core.model.MetaFile;
import org.hive2hive.core.processes.framework.RollbackReason;
import org.hive2hive.core.processes.framework.abstracts.ProcessStep;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.processes.framework.exceptions.ProcessExecutionException;
import org.hive2hive.core.processes.implementations.context.UpdateFileProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a new file version.
 * 
 * @author Seppi, Nico
 */
public class CreateNewVersionStep extends ProcessStep {

	private final static Logger logger = LoggerFactory.getLogger(CreateNewVersionStep.class);

	private final UpdateFileProcessContext context;
	private final IFileConfiguration config;

	// used for rollback
	private FileVersion newVersion;
	private final List<FileVersion> deletedFileVersions;

	public CreateNewVersionStep(UpdateFileProcessContext context, IFileConfiguration config) {
		this.context = context;
		this.config = config;
		this.deletedFileVersions = new ArrayList<FileVersion>();
	}

	@Override
	protected void doExecute() throws InvalidProcessStateException, ProcessExecutionException {
		if (context.consumeMetaFile() == null) {
			throw new ProcessExecutionException("Meta document is null.");
		}

		logger.debug("Adding a new version to the meta file.");

		// create a new version and add it to the meta file
		MetaFile metaFile = context.consumeMetaFile();
		newVersion = new FileVersion(metaFile.getVersions().size(), FileUtil.getFileSize(context.getFile()),
				System.currentTimeMillis(), context.getMetaChunks());
		metaFile.getVersions().add(newVersion);

		initiateCleanup();
	}

	private void initiateCleanup() {
		MetaFile metaFile = (MetaFile) context.consumeMetaFile();

		// remove files when the number of allowed versions is exceeded or when the total file size (sum
		// of all versions) exceeds the allowed file size
		while (metaFile.getVersions().size() > config.getMaxNumOfVersions()
				|| metaFile.getTotalSize().compareTo(config.getMaxSizeAllVersions()) == 1) {
			// more versions than allowed or size is larger

			// keep at least one version
			if (metaFile.getVersions().size() == 1)
				break;

			// remove the version of the meta file
			deletedFileVersions.add(metaFile.getVersions().remove(0));
		}

		logger.debug("Need to remove {} old versions.", deletedFileVersions.size());
		List<MetaChunk> chunksToDelete = new ArrayList<MetaChunk>();
		for (FileVersion fileVersion : deletedFileVersions) {
			chunksToDelete.addAll(fileVersion.getMetaChunks());
		}
		context.setChunksToDelete(chunksToDelete);
	}

	@Override
	protected void doRollback(RollbackReason reason) throws InvalidProcessStateException {
		if (context.consumeMetaFile() != null) {
			MetaFile metaFile = (MetaFile) context.consumeMetaFile();
			// remove the new version
			metaFile.getVersions().remove(newVersion);

			// add the cleaned up versions
			metaFile.getVersions().addAll(deletedFileVersions);
		}
	}
}
