package org.hive2hive.core.test.processes.implementations.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyPair;
import java.util.List;

import net.tomp2p.futures.FutureGet;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.exceptions.GetFailedException;
import org.hive2hive.core.exceptions.IllegalFileLocation;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.FileVersion;
import org.hive2hive.core.model.MetaChunk;
import org.hive2hive.core.model.MetaFile;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.data.parameters.Parameters;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.core.test.H2HJUnitTest;
import org.hive2hive.core.test.file.FileTestUtil;
import org.hive2hive.core.test.integration.TestFileConfiguration;
import org.hive2hive.core.test.network.NetworkTestUtil;
import org.hive2hive.core.test.processes.util.UseCaseTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests deleting a file.
 * 
 * @author Nico
 */
public class DeleteFileTest extends H2HJUnitTest {

	private static final int networkSize = 2;
	private static List<NetworkManager> network;
	private static UserCredentials userCredentials;
	private static final IFileConfiguration config = new TestFileConfiguration();
	private static File root;
	private static NetworkManager client;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = DeleteFileTest.class;
		beforeClass();

		network = NetworkTestUtil.createNetwork(networkSize);
		userCredentials = NetworkTestUtil.generateRandomCredentials();

		root = new File(System.getProperty("java.io.tmpdir"), NetworkTestUtil.randomString());
		client = network.get(0);

		// register a user
		UseCaseTestUtil.registerAndLogin(userCredentials, client, root);
	}

	@Test
	public void testDeleteFile() throws IOException, IllegalFileLocation, GetFailedException,
			InterruptedException, NoPeerConnectionException, NoSessionException, InvalidProcessStateException {
		File file = FileTestUtil.createFileRandomContent(3, root, config);
		UseCaseTestUtil.uploadNewFile(client, file);

		// store the keys of the meta file to verify them later
		UserProfile userProfileBeforeDeletion = UseCaseTestUtil.getUserProfile(client, userCredentials);
		KeyPair metaKeyPair = userProfileBeforeDeletion.getFileByPath(file, root).getFileKeys();
		MetaFile metaDocumentBeforeDeletion = UseCaseTestUtil.getMetaFile(client, metaKeyPair);
		Assert.assertNotNull(metaDocumentBeforeDeletion);

		// delete the file
		UseCaseTestUtil.deleteFile(client, file);

		// check if the file is still in the DHT
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(client, userCredentials);
		Assert.assertNull(userProfile.getFileById(metaKeyPair.getPublic()));

		MetaFile metaDocument = UseCaseTestUtil.getMetaFile(client, metaKeyPair, false);
		Assert.assertNull(metaDocument);

		for (FileVersion version : metaDocumentBeforeDeletion.getVersions()) {
			for (MetaChunk metaChunks : version.getMetaChunks()) {
				FutureGet get = client.getDataManager().getUnblocked(
						new Parameters().setLocationKey(metaChunks.getChunkId()).setContentKey(
								H2HConstants.FILE_CHUNK));
				get.awaitUninterruptibly();
				get.getFutureRequests().awaitUninterruptibly();

				// chunk should not exist
				Assert.assertNull(get.getData());
			}
		}
	}

	@Test
	public void testDeleteFolder() throws FileNotFoundException, IllegalFileLocation, GetFailedException,
			InterruptedException, NoSessionException, NoPeerConnectionException {
		// add a folder to the network
		File folder = new File(root, NetworkTestUtil.randomString());
		folder.mkdir();
		UseCaseTestUtil.uploadNewFile(client, folder);

		// store some keys before deletion
		UserProfile userProfileBeforeDeletion = UseCaseTestUtil.getUserProfile(client, userCredentials);
		KeyPair metaKeyPair = userProfileBeforeDeletion.getFileByPath(folder, root).getFileKeys();

		// delete the folder
		UseCaseTestUtil.deleteFile(client, folder);

		// check if the folder is still in the DHT
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(client, userCredentials);
		Assert.assertNull(userProfile.getFileById(metaKeyPair.getPublic()));
	}

	@Test
	public void testDeleteFileInFolder() throws IOException, IllegalFileLocation, GetFailedException,
			InterruptedException, NoSessionException, NoPeerConnectionException, InvalidProcessStateException {
		// add a folder to the network
		File folder = new File(root, NetworkTestUtil.randomString());
		folder.mkdir();
		UseCaseTestUtil.uploadNewFile(client, folder);

		// add a file to the network
		File file = new File(folder, NetworkTestUtil.randomString());
		FileUtils.writeStringToFile(file, NetworkTestUtil.randomString());
		UseCaseTestUtil.uploadNewFile(client, file);

		// store some things to be able to test later
		UserProfile userProfileBeforeDeletion = UseCaseTestUtil.getUserProfile(client, userCredentials);
		KeyPair metaKeyPairFolder = userProfileBeforeDeletion.getFileByPath(folder, root).getFileKeys();
		KeyPair metaKeyPairFile = userProfileBeforeDeletion.getFileByPath(file, root).getFileKeys();
		MetaFile metaFileBeforeDeletion = (MetaFile) UseCaseTestUtil.getMetaFile(client, metaKeyPairFile);
		Assert.assertNotNull(metaFileBeforeDeletion);

		// delete the file
		UseCaseTestUtil.deleteFile(client, file);

		// check if the file is still in the DHT
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(client, userCredentials);
		Assert.assertNull(userProfile.getFileById(metaKeyPairFile.getPublic()));

		// check if the folder is still in the DHT
		Assert.assertNotNull(userProfile.getFileById(metaKeyPairFolder.getPublic()));

		// check the meta file is still in the DHT
		MetaFile metaFile = UseCaseTestUtil.getMetaFile(client, metaKeyPairFile, false);
		Assert.assertNull(metaFile);
	}

	@Test
	public void testDeleteFolderInFolder() throws IOException, IllegalFileLocation, GetFailedException,
			InterruptedException, NoSessionException, NoPeerConnectionException {
		// add a folder to the network
		File folder = new File(root, NetworkTestUtil.randomString());
		folder.mkdir();
		UseCaseTestUtil.uploadNewFile(client, folder);

		// add a 2nd folder to the network
		File innerFolder = new File(folder, "inner-folder");
		innerFolder.mkdir();
		UseCaseTestUtil.uploadNewFile(client, innerFolder);

		// store some things to be able to test later
		UserProfile userProfileBeforeDeletion = UseCaseTestUtil.getUserProfile(client, userCredentials);
		KeyPair metaKeyPairFolder = userProfileBeforeDeletion.getFileByPath(folder, root).getFileKeys();
		KeyPair metaKeyPairInnerFolder = userProfileBeforeDeletion.getFileByPath(innerFolder, root)
				.getFileKeys();

		// delete the inner folder
		UseCaseTestUtil.deleteFile(client, innerFolder);

		// check if the inner folder is still in the DHT
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(client, userCredentials);
		Assert.assertNull(userProfile.getFileById(metaKeyPairInnerFolder.getPublic()));

		// check if the outer folder is still in the DHT
		Assert.assertNotNull(userProfile.getFileById(metaKeyPairFolder.getPublic()));
	}

	@AfterClass
	public static void endTest() throws IOException {
		NetworkTestUtil.shutdownNetwork(network);
		afterClass();
	}
}
