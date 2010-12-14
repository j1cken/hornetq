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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.hornetq.api.core.management.HornetQServerControl;
import org.hornetq.api.jms.management.JMSQueueControl;
import org.hornetq.common.example.HornetQExample;

/**
 * This example demonstrates a queue with the same name deployed on two nodes of a cluster.
 * Messages are initially round robin'd between both nodes of the cluster.
 * The consumer on one of the nodes is then closed, and we demonstrate that the "stranded" messages
 * are redistributed to the other node which has a consumer so they can be consumed.
 *
 * @author <a href="tim.fox@jboss.com>Tim Fox</a>
 */
public class StarvationExample extends HornetQExample {
    private static final String QUEUE_EXAMPLE_QUEUE = "/queue/exampleQueue";
    private static int totalMsgs = 0;
    private static final String SERVER0_JMX_RMI_PORT = "3000";
    private static final String SERVER1_JMX_RMI_PORT = "3001";

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

            Thread.sleep(1000);

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
            int numMessages = 100;
            Thread t0 = new MySendThread("0", session0, queue0, numMessages);
            Thread t1 = new MySendThread("1", session1, queue1, numMessages);


            System.out.println("waiting for cluster bridge setup ...");
            Thread.sleep(2000);

            // Step 13. We send some messages to server 0

            t0.start();
            t1.start();

            Thread.sleep(100);

            System.out.println("*** server1 address names: ");
            String[] addressNames1 = serverControl1.getAddressNames();
            for (int i = 0; i < addressNames1.length; i++) {
                System.out.println("* " + addressNames1[i]);
            }
            JMSQueueControl jmsQueueControl1 = getJMSQueueControl(SERVER1_JMX_RMI_PORT, queue1);
            System.out.println("*** q count server1 (msgCount/added/delivered/scheduled): " + jmsQueueControl1.getMessageCount() + "/" +
                    jmsQueueControl1.getMessagesAdded() + "/" + jmsQueueControl1.getDeliveringCount() + "/" +
                    jmsQueueControl1.getScheduledCount());


            killServer(1);

            t0.join();
            t1.join();

            System.out.println("*** TOTAL SENT: " + totalMsgs);

            System.out.println("*** server0 address names: ");
            String[] addressNames = serverControl0.getAddressNames();
            for (int i = 0; i < addressNames.length; i++) {
                System.out.println("* " + addressNames[i]);
            }

            JMSQueueControl jmsQueueControl0 = getJMSQueueControl(SERVER0_JMX_RMI_PORT, queue0);
            System.out.println("*** q count server0 (msgCount/added/delivered/scheduled): " + jmsQueueControl0.getMessageCount() + "/" +
                    jmsQueueControl0.getMessagesAdded() + "/" + jmsQueueControl0.getDeliveringCount() + "/" +
                    jmsQueueControl0.getScheduledCount());

            // Step 14. We now consume those messages on *both* server 0 and server 1.
            // We note the messages have been distributed between servers in a round robin fashion
            // JMS Queues implement point-to-point message where each message is only ever consumed by a
            // maximum of one consumer

            TextMessage message0 = null;
            TextMessage message1 = null;

            for (int i = 0; i < numMessages; i += 2) {
                message0 = (TextMessage) consumer0.receive(5000);

                System.out.println("Got message: " + message0.getText() + " from node 0");
            }

            // Step 15. We acknowledge the messages consumed on node 0. The sessions are CLIENT_ACKNOWLEDGE so
            // messages will not get acknowledged until they are explicitly acknowledged.
            // Note that we *do not* acknowledge the message consumed on node 1 yet.
            message0.acknowledge();

            // Step 16. We now close the session and consumer on node 1. (Closing the session automatically closes the
            // consumer)
//            session1.close();

            // Step 17. Since there is no more consumer on node 1, the messages on node 1 are now stranded (no local
            // consumers)
            // so HornetQ will redistribute them to node 0 so they can be consumed.

            for (int i = 0; i < numMessages; i += 2) {
                message0 = (TextMessage) consumer0.receive(5000);

                System.out.println("Got message: " + message0.getText() + " from node 0");
            }

            // Step 18. We ack the messages.
            message0.acknowledge();

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

    private class MySendThread extends Thread {

        private MessageProducer producer;
        private int numMsgs;

        private String serverId;
        private Session session;

        public MySendThread(String s, Session session, Queue queue, int numMsgs) {
            serverId = s;
            this.session = session;
            try {
                producer = session.createProducer(queue);
            } catch (JMSException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            this.numMsgs = numMsgs;
        }

        @Override
        public void run() {
            try {
                for (int i = 1; i <= numMsgs; i++) {
                    TextMessage message = session.createTextMessage("This is text message " + i);
                    producer.send(message);
                    System.out.println("Sent message: " + message.getText() + " with producer " + serverId);
                    increaseTotal();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        private synchronized void increaseTotal() {
            totalMsgs++;
        }
    }
}
