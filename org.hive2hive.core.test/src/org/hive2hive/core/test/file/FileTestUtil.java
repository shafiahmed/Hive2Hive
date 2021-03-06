package org.hive2hive.core.test.file;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.test.H2HJUnitTest;
import org.hive2hive.core.test.network.NetworkTestUtil;

public class FileTestUtil {
	
	public static File createFileRandomContent(int numOfChunks, File parent, IFileConfiguration config) throws IOException{
		return createFileRandomContent(NetworkTestUtil.randomString(), numOfChunks, parent, config);
	}

	public static File createFileRandomContent(String fileName, int numOfChunks, File parent, IFileConfiguration config)
			throws IOException {
		// create file of size of multiple numbers of chunks
		String random = "";
		while (Math.ceil(1.0 * random.getBytes().length / config.getChunkSize()) < numOfChunks) {
			random += H2HJUnitTest.generateRandomString((int) config.getChunkSize() - 1);
		}
		File file = new File(parent, fileName);
		FileUtils.write(file, random);

		return file;
	}
}
