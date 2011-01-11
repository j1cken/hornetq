/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.jms.example;

import org.hornetq.api.core.management.HornetQServerControl;
import org.hornetq.api.core.management.QueueControl;
import org.hornetq.common.example.HornetQExample;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

/**
 * This example demonstrates a queue with the same name deployed on two nodes of a cluster.
 * Messages are initially round robin'd between both nodes of the cluster.
 * The consumer on one of the nodes is then closed, and we demonstrate that the "stranded" messages
 * are redistributed to the other node which has a consumer so they can be consumed.
 *
 * @author <a href="torben@redhat.com>Torben Jaeger</a>
 */
public class StarvationExample extends HornetQExample {
    private static final String QUEUE_EXAMPLE_QUEUE = "/queue/exampleQueue";
    private static int totalMsgs = 0;
    private static final String SERVER0_JMX_RMI_PORT = "3000";
    private static final String SERVER1_JMX_RMI_PORT = "3001";

    private static final Object lock = new Object();

    public static void main(final String[] args) {
        new StarvationExample().run(args);
    }

    @Override
    public boolean runExample() throws Exception {
        clearServer(0, 1);

        Connection connection0 = null;
        Connection connection1 = null;

        InitialContext ic0 = null;
        InitialContext ic1 = null;

        try {
            // Step 1. Get an initial context for looking up JNDI from server 0
            ic0 = getContext(0);
            // Step 2. Look-up the JMS Queue object from JNDI
            Queue queue0 = (Queue) ic0.lookup(QUEUE_EXAMPLE_QUEUE);
            System.out.println("*** found Queue " + queue0.getQueueName());

            // Step 3. Look-up a JMS Connection Factory object from JNDI on server 0
            ConnectionFactory cf0 = (ConnectionFactory) ic0.lookup("/ConnectionFactory");

            // Step 4. Get an initial context for looking up JNDI from server 1
            ic1 = getContext(1);
            // Step 2. Look-up the JMS Queue object from JNDI
            Queue queue1 = (Queue) ic1.lookup(QUEUE_EXAMPLE_QUEUE);
            // Step 5. Look-up a JMS Connection Factory object from JNDI on server 1
            ConnectionFactory cf1 = (ConnectionFactory) ic1.lookup("/ConnectionFactory");

            // Step 6. We create a JMS Connection connection0 which is a connection to server 0
            connection0 = cf0.createConnection();
            // Step 7. We create a JMS Connection connection1 which is a connection to server 1
            connection1 = cf1.createConnection();

            // Step 8. We create a JMS Session on server 0, note the session is CLIENT_ACKNOWLEDGE
            Session session0 = connection0.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            // Step 9. We create a JMS Session on server 1, note the session is CLIENT_ACKNOWLEDGE
            Session session1 = connection1.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            // Step 10. We start the connections to ensure delivery occurs on them
            connection0.start();
            connection1.start();

            // Step 11. We create JMS MessageConsumer objects on server 0 and server 1
            MessageConsumer consumer0 = session0.createConsumer(queue0);
            MessageConsumer consumer1 = session1.createConsumer(queue1);

//            Thread.sleep(1000);

            System.out.println("*** getting server controls ...");

            HornetQServerControl serverControl0 = getServerControl(SERVER0_JMX_RMI_PORT);
            serverControl0.enableMessageCounters();
            serverControl0.resetAllMessageCounters();
            serverControl0.resetAllMessageCounterHistories();

            HornetQServerControl serverControl1 = getServerControl(SERVER1_JMX_RMI_PORT);
            serverControl1.enableMessageCounters();
            serverControl1.resetAllMessageCounters();
            serverControl1.resetAllMessageCounterHistories();

            assert serverControl0.isStarted() == true : "Server0 is not started!";
            assert serverControl1.isStarted() == true : "Server1 is not started!";
            assert serverControl0.isClustered() == true : "Server0 is not clustered!";
            assert serverControl1.isClustered() == true : "Server1 is not clustered!";

            // Step 12. We create a JMS MessageProducer object on server 0
            int numMessages = 1000;

            Thread t0 = new MySendThread("0", session0, queue0, numMessages);
            Thread t1 = new MySendThread("1", session1, queue1, numMessages);

            // Step 13. We send some messages to server 0

            t0.start();
            t1.start();

            printStatistics(serverControl1, SERVER1_JMX_RMI_PORT);

            Thread.sleep(1500);
            killServer(1);

            t0.join();
            t1.join();

            System.out.println("*** TOTAL SENT: " + totalMsgs);

            printStatistics(serverControl0, SERVER0_JMX_RMI_PORT);

            TextMessage message = null;

            long msgOnServer = getNumberOfMessages(SERVER0_JMX_RMI_PORT, "jms.queue.exampleQueue");
            System.out.println("*** consuming " + msgOnServer + " messages on server 0:");
            for (int i = 0; i < msgOnServer; i++) {
                message = (TextMessage) consumer0.receive(5000);

                System.out.println("Got message: " + message.getText() + " from node 0");
            }
            message.acknowledge();

            printStatistics(serverControl0, SERVER0_JMX_RMI_PORT);

            System.out.println("*** check for redistribution of cluster bridge ...");
            for (int i = 0; i < 5; i++) {
                printQueueStats(SERVER0_JMX_RMI_PORT, getClusterBridgeAddress(serverControl0));
                Thread.sleep(5000);
            }
            return true;
        } finally

        {
            // Step 18. Be sure to close our resources!

            if (connection0 != null) {
                connection0.close();
            }

            if (connection1 != null) {
                connection1.close();
            }

            if (ic0 != null) {
                ic0.close();
            }

            if (ic1 != null) {
                ic1.close();
            }
        }
    }

    private String getClusterBridgeAddress(HornetQServerControl serverControl) {
        String[] addressNames = serverControl.getAddressNames();
        for (int i = 0; i < addressNames.length; i++) {
            if (addressNames[i].startsWith("sf.my-cluster")) {
                return addressNames[i];
            }
        }
        return null;
    }

    private long getNumberOfMessages(String rmiPort, String queue) throws Exception {
        return getQueueControl(rmiPort, queue).getMessageCount();
    }

    private void printStatistics(HornetQServerControl serverControl, String rmiPort) throws Exception {
        String[] addressNames = serverControl.getAddressNames();

//        listAddressNames(addressNames);

        for (int i = 0; i < addressNames.length; i++) {
            printQueueStats(rmiPort, addressNames[i]);
        }
    }

    void printQueueStats(String rmiPort, String addressName) throws Exception {
        if (isInteresting(addressName)) {
            QueueControl queueControl = getQueueControl(rmiPort, addressName);
            System.out.println("*** q count (msgCount/added/delivered/scheduled) of " + addressName + ": " +
                    queueControl.getMessageCount() + "/" +
                    queueControl.getMessagesAdded() + "/" +
                    queueControl.getDeliveringCount() + "/" +
                    queueControl.getScheduledCount());
        }
    }

    private void listAddressNames(String[] addressNames) {
        System.out.println("*** address names: ");
        for (int i = 0; i < addressNames.length; i++) {
            System.out.println(addressNames[i]);
        }
    }

    boolean isInteresting(String addressName) {
        return addressName.startsWith("jms.queue") ||
                addressName.startsWith("sf.my-cluster");
    }

    String getQName(String addressName) {
        return addressName.startsWith("jms.queue")
                ? addressName.substring(10, addressName.length())
                : addressName.substring(14, addressName.length());
    }

    private class MySendThread extends Thread {

        private MessageProducer producer;
        private int numMsgs;

        private String serverId;
        private Session session;


        public MySendThread(String s, Session session, Queue queue, int numMsgs) {
            serverId = s;
            this.session = session;
            this.numMsgs = numMsgs;
            try {
                producer = session.createProducer(queue);
            } catch (JMSException e) {
            }
        }

        @Override
        public void run() {
            try {
                for (int i = 1; i <= numMsgs; i++) {

                    TextMessage message = session.createTextMessage("This is text message " + i + "/" + serverId);
                    producer.send(message);
                    System.out.println("Sent message: " + message.getText());

                    new Thread() {
                        @Override
                        public void run() {
                            System.out.println("Server " + serverId + " increasing total of " + totalMsgs + " by 1.");
                            increaseTotal();
                        }
                    }.start();
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
//                throw new RuntimeException(e);
                e.printStackTrace();
            }

        }

    }

    private static void increaseTotal() {
        synchronized (lock) {
            totalMsgs++;
        }
    }
}
