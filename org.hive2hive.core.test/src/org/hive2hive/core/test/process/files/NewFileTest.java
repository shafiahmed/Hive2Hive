package org.hive2hive.core.test.process.files;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.H2HSession;
import org.hive2hive.core.IFileConfiguration;
import org.hive2hive.core.exceptions.IllegalFileLocation;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.file.FileManager;
import org.hive2hive.core.model.FileTreeNode;
import org.hive2hive.core.model.MetaDocument;
import org.hive2hive.core.model.MetaFile;
import org.hive2hive.core.model.MetaFolder;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.data.UserProfileManager;
import org.hive2hive.core.process.upload.newfile.NewFileProcess;
import org.hive2hive.core.security.EncryptionUtil;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.core.test.H2HJUnitTest;
import org.hive2hive.core.test.file.FileTestUtil;
import org.hive2hive.core.test.integration.TestFileConfiguration;
import org.hive2hive.core.test.network.NetworkTestUtil;
import org.hive2hive.core.test.process.ProcessTestUtil;
import org.hive2hive.core.test.process.TestProcessListener;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests uploading a new file.
 * 
 * @author Nico
 * 
 */
public class NewFileTest extends H2HJUnitTest {

	private final int networkSize = 3;
	private List<NetworkManager> network;
	private FileManager fileManager;
	private IFileConfiguration config = new TestFileConfiguration();
	private UserCredentials userCredentials;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = NewFileTest.class;
		beforeClass();

	}

	@Before
	public void createProfile() {
		network = NetworkTestUtil.createNetwork(networkSize);
		userCredentials = NetworkTestUtil.generateRandomCredentials();

		// register a user
		ProcessTestUtil.register(userCredentials, network.get(0));

		String randomName = NetworkTestUtil.randomString();
		File root = new File(System.getProperty("java.io.tmpdir"), randomName);
		fileManager = new FileManager(root.toPath());
	}

	@Test
	public void testUploadSingleChunk() throws IOException, IllegalFileLocation, NoSessionException {
		File file = FileTestUtil.createFileRandomContent(1, fileManager, config);

		startUploadProcess(file);
		verifyUpload(file, 1);
	}

	@Test
	public void testUploadMultipleChunks() throws IOException, IllegalFileLocation, NoSessionException {
		// creates a file with length of at least 5 chunks
		File file = FileTestUtil.createFileRandomContent(5, fileManager, config);

		startUploadProcess(file);
		verifyUpload(file, 5);
	}

	@Test
	public void testUploadFolder() throws IOException, IllegalFileLocation, NoSessionException {
		File folder = new File(fileManager.getRoot().toFile(), "folder1");
		folder.mkdirs();

		startUploadProcess(folder);
		verifyUpload(folder, 0);
	}

	@Test
	public void testUploadFolderWithFile() throws IOException, IllegalFileLocation, NoSessionException {
		// create a container
		File folder = new File(fileManager.getRoot().toFile(), "folder-with-file");
		folder.mkdirs();
		startUploadProcess(folder);

		File file = new File(folder, "test-file");
		FileUtils.writeStringToFile(file, NetworkTestUtil.randomString());
		startUploadProcess(file);
		verifyUpload(file, 1);
	}

	@Test
	public void testUploadFolderWithFolder() throws IOException, IllegalFileLocation, NoSessionException {
		File folder = new File(fileManager.getRoot().toFile(), "folder-with-folder");
		folder.mkdirs();
		startUploadProcess(folder);

		File innerFolder = new File(fileManager.getRoot().toFile(), "inner-folder");
		innerFolder.mkdir();
		startUploadProcess(innerFolder);

		verifyUpload(innerFolder, 0);
	}

	@Test
	public void testUploadWrongCredentials() throws IOException, IllegalFileLocation, NoSessionException {
		userCredentials = NetworkTestUtil.generateRandomCredentials();
		File file = FileTestUtil.createFileRandomContent(1, fileManager, config);

		NetworkManager client = network.get(new Random().nextInt(networkSize));
		UserProfileManager profileManager = new UserProfileManager(client, userCredentials);

		client.setSession(new H2HSession(EncryptionUtil.generateRSAKeyPair(H2HConstants.KEYLENGTH_USER_KEYS),
				profileManager, config, fileManager));
		NewFileProcess process = new NewFileProcess(file, client);
		TestProcessListener listener = new TestProcessListener();
		process.addListener(listener);
		process.start();
		
		ProcessTestUtil.waitTillFailed(listener, 40);
	}

	private void startUploadProcess(File toUpload) throws IllegalFileLocation, NoSessionException {
		NetworkManager client = network.get(new Random().nextInt(networkSize));
		UserProfileManager profileManager = new UserProfileManager(client, userCredentials);
		client.setSession(new H2HSession(EncryptionUtil.generateRSAKeyPair(H2HConstants.KEYLENGTH_USER_KEYS),
				profileManager, config, fileManager));
		NewFileProcess process = new NewFileProcess(toUpload, client);
		TestProcessListener listener = new TestProcessListener();
		process.addListener(listener);
		process.start();

		// wait maximally 1m because files could be large
		ProcessTestUtil.waitTillSucceded(listener, 60);
	}

	private void verifyUpload(File originalFile, int expectedChunks) throws IOException {
		// pick new client to test
		NetworkManager client = network.get(new Random().nextInt(networkSize));

		// test if there is something in the user profile
		UserProfile gotProfile = ProcessTestUtil.getUserProfile(client, userCredentials);
		Assert.assertNotNull(gotProfile);

		FileTreeNode node = gotProfile.getFileByPath(originalFile, fileManager);
		Assert.assertNotNull(node);

		// verify the meta document
		KeyPair metaFileKeys = node.getKeyPair();
		MetaDocument metaDocument = ProcessTestUtil.getMetaDocument(client, metaFileKeys);
		if (originalFile.isFile()) {
			// get the meta file with the keys (decrypt it)
			MetaFile metaFile = (MetaFile) metaDocument;
			Assert.assertNotNull(metaFile);
			Assert.assertEquals(1, metaFile.getVersions().size());
			Assert.assertEquals(expectedChunks, metaFile.getVersions().get(0).getChunkKeys().size());
		} else {
			// get meta folder
			MetaFolder metaFolder = (MetaFolder) metaDocument;
			Assert.assertNotNull(metaFolder);
			Assert.assertEquals(originalFile.list().length, metaFolder.getChildKeys().size());
		}

		// create new filemanager
		File root = new File(System.getProperty("java.io.tmpdir"), NetworkTestUtil.randomString());
		FileManager fileManager2 = new FileManager(root.toPath());

		// verify the file after downloadig it
		UserProfileManager profileManager = new UserProfileManager(client, userCredentials);
		File file = ProcessTestUtil.downloadFile(client, node, profileManager, fileManager2, config);
		Assert.assertTrue(file.exists());
		if (originalFile.isFile()) {
			Assert.assertEquals(FileUtils.readFileToString(originalFile), FileUtils.readFileToString(file));
		}
	}

	@After
	public void deleteAndShutdown() throws IOException {
		NetworkTestUtil.shutdownNetwork(network);
		FileUtils.deleteDirectory(fileManager.getRoot().toFile());
	}

	@AfterClass
	public static void endTest() throws IOException {
		afterClass();
	}
}
