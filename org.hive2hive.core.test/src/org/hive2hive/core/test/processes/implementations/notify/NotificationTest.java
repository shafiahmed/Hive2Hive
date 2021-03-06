package org.hive2hive.core.test.processes.implementations.notify;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.Locations;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.processes.ProcessFactory;
import org.hive2hive.core.processes.framework.ProcessState;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.processes.framework.exceptions.ProcessExecutionException;
import org.hive2hive.core.processes.framework.interfaces.IProcessComponent;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.core.test.H2HJUnitTest;
import org.hive2hive.core.test.H2HWaiter;
import org.hive2hive.core.test.network.NetworkTestUtil;
import org.hive2hive.core.test.processes.util.DenyingMessageReplyHandler;
import org.hive2hive.core.test.processes.util.TestProcessComponentListener;
import org.hive2hive.core.test.processes.util.UseCaseTestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests the notification procedure
 * 
 * @author Nico
 */
public class NotificationTest extends H2HJUnitTest {

	private static final int networkSize = 10;
	private List<NetworkManager> network;

	private UserCredentials userACredentials;
	private UserCredentials userBCredentials;
	private UserCredentials userCCredentials;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = NotificationTest.class;
		beforeClass();
	}

	@Before
	public void loginNodes() throws NoPeerConnectionException {
		network = NetworkTestUtil.createNetwork(networkSize);

		// create 10 nodes and login 5 of them:
		// node 0-2: user A
		// node 3-4: user B
		// node 5: user C
		userACredentials = new UserCredentials("User A", NetworkTestUtil.randomString(),
				NetworkTestUtil.randomString());
		UseCaseTestUtil.register(userACredentials, network.get(0));

		userBCredentials = new UserCredentials("User B", NetworkTestUtil.randomString(),
				NetworkTestUtil.randomString());
		UseCaseTestUtil.register(userBCredentials, network.get(3));

		userCCredentials = new UserCredentials("User C", NetworkTestUtil.randomString(),
				NetworkTestUtil.randomString());
		UseCaseTestUtil.register(userCCredentials, network.get(5));

		// login all nodes
		UseCaseTestUtil.login(userACredentials, network.get(0), NetworkTestUtil.getTempDirectory());
		UseCaseTestUtil.login(userACredentials, network.get(1), NetworkTestUtil.getTempDirectory());
		UseCaseTestUtil.login(userACredentials, network.get(2), NetworkTestUtil.getTempDirectory());
		UseCaseTestUtil.login(userBCredentials, network.get(3), NetworkTestUtil.getTempDirectory());
		UseCaseTestUtil.login(userBCredentials, network.get(4), NetworkTestUtil.getTempDirectory());
		UseCaseTestUtil.login(userCCredentials, network.get(5), NetworkTestUtil.getTempDirectory());
	}

	/**
	 * Scenario: Call the notification process with an empty list
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws NoSessionException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyNobody() throws ClassNotFoundException, IOException, InvalidProcessStateException,
			IllegalArgumentException, NoPeerConnectionException, NoSessionException {
		NetworkManager notifier = network.get(0);
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory,
				new HashSet<String>(0), notifier);
		TestProcessComponentListener listener = new TestProcessComponentListener();
		process.attachListener(listener);
		process.start();

		// wait until all messages are sent
		UseCaseTestUtil.waitTillSucceded(listener, 10);
	}

	/**
	 * Scenario: User A (peer 0) contacts his own clients (peer 1 and 2)
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws NoSessionException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyOwnUser() throws ClassNotFoundException, IOException, InvalidProcessStateException,
			IllegalArgumentException, NoPeerConnectionException, NoSessionException {
		NetworkManager notifier = network.get(0);

		// send notification to own peers
		Set<String> users = new HashSet<String>(1);
		users.add(userACredentials.getUserId());
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory,
				new HashSet<String>(0), notifier);
		process.start();

		H2HWaiter waiter = new H2HWaiter(20);
		do {
			waiter.tickASecond();
		} while (!msgFactory.allMsgsArrived());

		Assert.assertEquals(ProcessState.SUCCEEDED, process.getState());
	}

	/**
	 * Scenario: User A (peer 0) contacts his own clients (peer 1 and 2). Use the session of the current
	 * user here for performance improvements
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyOwnUserSession() throws ClassNotFoundException, IOException, NoSessionException,
			InvalidProcessStateException, IllegalArgumentException, NoPeerConnectionException {
		NetworkManager notifier = network.get(0);
		// send notification to own peers
		Set<String> users = new HashSet<String>(1);
		users.add(userACredentials.getUserId());
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory, users,
				notifier);
		process.start();

		H2HWaiter waiter = new H2HWaiter(20);
		do {
			waiter.tickASecond();
		} while (!msgFactory.allMsgsArrived());

		Assert.assertEquals(ProcessState.SUCCEEDED, process.getState());
	}

	/**
	 * Scenario: User A (peer 0) contacts his own clients (peer 1 and 2) and also the initial client of user B
	 * (peer 3 or 4) and user C (peer 5)
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws NoSessionException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyOtherUsers() throws ClassNotFoundException, IOException,
			InvalidProcessStateException, IllegalArgumentException, NoPeerConnectionException,
			NoSessionException {
		NetworkManager notifier = network.get(0);
		// send notification to own peers
		Set<String> users = new HashSet<String>(3);
		users.add(userACredentials.getUserId());
		users.add(userBCredentials.getUserId());
		users.add(userCCredentials.getUserId());
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory, users,
				notifier);
		process.start();

		H2HWaiter waiter = new H2HWaiter(20);
		do {
			waiter.tickASecond();
		} while (!msgFactory.allMsgsArrived());

		Assert.assertEquals(4, msgFactory.getSentMessageCount());
		Assert.assertEquals(ProcessState.SUCCEEDED, process.getState());
	}

	/**
	 * Scenario: User A (peer 0) contacts his own clients (peer 1 and 2) and also user B
	 * (peer 3 or 4). Peer 3 (initial) has occurred an unfriendly logout, thus, the message must be sent to
	 * Peer 4.
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws NoSessionException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyUnfriendlyLogoutInitial() throws ClassNotFoundException, IOException,
			InterruptedException, InvalidProcessStateException, IllegalArgumentException,
			NoPeerConnectionException, NoSessionException {
		NetworkManager notifier = network.get(0);

		// send notification to own peers
		Set<String> users = new HashSet<String>(2);
		users.add(userACredentials.getUserId());
		users.add(userBCredentials.getUserId());
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory, users,
				notifier);
		TestProcessComponentListener listener = new TestProcessComponentListener();
		process.attachListener(listener);

		// kick out peer 3 (B)
		network.get(3).getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
		process.start();

		// wait until all messages are sent
		UseCaseTestUtil.waitTillSucceded(listener, 20);

		H2HWaiter waiter = new H2HWaiter(10);
		do {
			waiter.tickASecond();
			// wait until all messages are here except 1
		} while (msgFactory.getArrivedMessageCount() != 3);
	}

	/**
	 * Scenario: User A (peer 0) contacts his own clients (peer 1 and 2) and also user B
	 * (peer 3 or 4). All peers of user B have done an unfriendly logout.
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws NoSessionException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyUnfriendlyLogoutAllPeers() throws ClassNotFoundException, IOException,
			InterruptedException, InvalidProcessStateException, IllegalArgumentException,
			NoPeerConnectionException, NoSessionException {
		NetworkManager notifier = network.get(0);

		// send notification to own peers
		Set<String> users = new HashSet<String>(2);
		users.add(userACredentials.getUserId());
		users.add(userBCredentials.getUserId());
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory, users,
				notifier);
		TestProcessComponentListener listener = new TestProcessComponentListener();
		process.attachListener(listener);

		// kick out peer 3 and 4 (B)
		network.get(3).getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
		network.get(4).getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
		process.start();

		// wait until all messages are sent
		UseCaseTestUtil.waitTillSucceded(listener, 20);

		H2HWaiter waiter = new H2HWaiter(10);
		do {
			waiter.tickASecond();
			// wait until all messages are here except 1
		} while (msgFactory.getArrivedMessageCount() != 2);
	}

	/**
	 * Scenario: User A (peer 0) contacts his own clients (peer 1 and 2), but peer 1 has done an unfriendly
	 * leave. The locations map should be cleaned up
	 * 
	 * @throws InvalidProcessStateException
	 * @throws NoPeerConnectionException
	 * @throws IllegalArgumentException
	 * @throws NoSessionException
	 * @throws ProcessExecutionException
	 */
	@Test
	public void testNotifyUnfriendlyLogoutOwnPeer() throws ClassNotFoundException, IOException,
			InterruptedException, InvalidProcessStateException, IllegalArgumentException,
			NoPeerConnectionException, NoSessionException {
		NetworkManager notifier = network.get(0);

		// send notification to own peers
		Set<String> users = new HashSet<String>(1);
		users.add(userACredentials.getUserId());
		CountingNotificationMessageFactory msgFactory = new CountingNotificationMessageFactory(notifier);
		IProcessComponent process = ProcessFactory.instance().createNotificationProcess(msgFactory, users,
				notifier);
		TestProcessComponentListener listener = new TestProcessComponentListener();
		process.attachListener(listener);

		// kick out Peer 1
		network.get(1).getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
		process.start();

		// wait until all messages are sent
		UseCaseTestUtil.waitTillSucceded(listener, 20);

		// check the locations map; should have 2 entries only
		Locations locations = UseCaseTestUtil.getLocations(network.get(0), userACredentials.getUserId());
		Assert.assertEquals(2, locations.getPeerAddresses().size());
	}

	@After
	public void shutdown() {
		NetworkTestUtil.shutdownNetwork(network);
	}

	@AfterClass
	public static void endTest() {
		afterClass();
	}
}
