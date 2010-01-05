/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.jms.client;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

import org.hornetq.api.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.exception.HornetQException;
import org.hornetq.api.jms.HornetQDestination;
import org.hornetq.core.logging.Logger;
import org.hornetq.utils.UUIDGenerator;

/**
 * HornetQ implementation of a JMS MessageProducer.
 * 
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ataylor@redhat.com">Andy Taylor</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class HornetQMessageProducer implements MessageProducer, QueueSender, TopicPublisher
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   private static final Logger log = Logger.getLogger(HornetQMessageProducer.class);

   // Attributes ----------------------------------------------------

   private final HornetQConnection jbossConn;

   private final SimpleString connID;

   private final ClientProducer producer;

   private boolean disableMessageID = false;

   private boolean disableMessageTimestamp = false;

   private int defaultPriority = 4;

   private long defaultTimeToLive = 0;

   private int defaultDeliveryMode = DeliveryMode.PERSISTENT;

   private final HornetQDestination defaultDestination;

   private final SimpleString messageIDPrefix;

   private volatile long sequenceNumber;

   private final ClientSession clientSession;

   // Constructors --------------------------------------------------

   protected HornetQMessageProducer(final HornetQConnection jbossConn,
                                 final ClientProducer producer,
                                 final HornetQDestination defaultDestination,
                                 final ClientSession clientSession) throws JMSException
   {
      this.jbossConn = jbossConn;

      connID = jbossConn.getUID();

      this.producer = producer;

      this.defaultDestination = defaultDestination;

      this.clientSession = clientSession;

      // TODO the UUID should be generated at the JMS Connection level,
      // then session, producers & messages ID could be created using simple sequences
      String uuid = UUIDGenerator.getInstance().generateSimpleStringUUID().toString();

      messageIDPrefix = new SimpleString("ID:" + uuid + ":");
   }

   // MessageProducer implementation --------------------------------

   public void setDisableMessageID(final boolean value) throws JMSException
   {
      checkClosed();

      disableMessageID = value;
   }

   public boolean getDisableMessageID() throws JMSException
   {
      checkClosed();

      return disableMessageID;
   }

   public void setDisableMessageTimestamp(final boolean value) throws JMSException
   {
      checkClosed();

      disableMessageTimestamp = value;
   }

   public boolean getDisableMessageTimestamp() throws JMSException
   {
      checkClosed();

      return disableMessageTimestamp;
   }

   public void setDeliveryMode(final int deliveryMode) throws JMSException
   {
      checkClosed();

      defaultDeliveryMode = deliveryMode;
   }

   public int getDeliveryMode() throws JMSException
   {
      checkClosed();

      return defaultDeliveryMode;
   }

   public void setPriority(final int defaultPriority) throws JMSException
   {
      checkClosed();

      this.defaultPriority = defaultPriority;
   }

   public int getPriority() throws JMSException
   {
      checkClosed();

      return defaultPriority;
   }

   public void setTimeToLive(final long timeToLive) throws JMSException
   {
      checkClosed();

      defaultTimeToLive = timeToLive;
   }

   public long getTimeToLive() throws JMSException
   {
      checkClosed();

      return defaultTimeToLive;
   }

   public Destination getDestination() throws JMSException
   {
      checkClosed();

      return defaultDestination;
   }

   public void close() throws JMSException
   {
      try
      {
         producer.close();
      }
      catch (HornetQException e)
      {
         throw JMSExceptionHelper.convertFromHornetQException(e);
      }
   }

   public void send(final Message message) throws JMSException
   {
      checkClosed();

      message.setJMSDeliveryMode(defaultDeliveryMode);

      message.setJMSPriority(defaultPriority);

      doSend(message, defaultTimeToLive, null);
   }

   public void send(final Message message, final int deliveryMode, final int priority, final long timeToLive) throws JMSException
   {
      checkClosed();

      message.setJMSDeliveryMode(deliveryMode);

      message.setJMSPriority(priority);

      doSend(message, timeToLive, null);
   }

   public void send(final Destination destination, final Message message) throws JMSException
   {
      checkClosed();

      if (destination != null && !(destination instanceof HornetQDestination))
      {
         throw new InvalidDestinationException("Not a JBoss Destination:" + destination);
      }

      message.setJMSDeliveryMode(defaultDeliveryMode);

      message.setJMSPriority(defaultPriority);

      doSend(message, defaultTimeToLive, (HornetQDestination)destination);
   }

   public void send(final Destination destination,
                    final Message message,
                    final int deliveryMode,
                    final int priority,
                    final long timeToLive) throws JMSException
   {
      checkClosed();

      if (destination != null && !(destination instanceof HornetQDestination))
      {
         throw new InvalidDestinationException("Not a JBoss Destination:" + destination);
      }

      message.setJMSDeliveryMode(deliveryMode);

      message.setJMSPriority(priority);

      doSend(message, timeToLive, (HornetQDestination)destination);
   }

   // TopicPublisher Implementation ---------------------------------

   public Topic getTopic() throws JMSException
   {
      return (Topic)getDestination();
   }

   public void publish(final Message message) throws JMSException
   {
      send(message);
   }

   public void publish(final Topic topic, final Message message) throws JMSException
   {
      send(topic, message);
   }

   public void publish(final Message message, final int deliveryMode, final int priority, final long timeToLive) throws JMSException
   {
      send(message, deliveryMode, priority, timeToLive);
   }

   public void publish(final Topic topic,
                       final Message message,
                       final int deliveryMode,
                       final int priority,
                       final long timeToLive) throws JMSException
   {
      send(topic, message, deliveryMode, priority, timeToLive);
   }

   // QueueSender Implementation ------------------------------------

   public void send(final Queue queue, final Message message) throws JMSException
   {
      send((Destination)queue, message);
   }

   public void send(final Queue queue,
                    final Message message,
                    final int deliveryMode,
                    final int priority,
                    final long timeToLive) throws JMSException
   {
      send((Destination)queue, message, deliveryMode, priority, timeToLive);
   }

   public Queue getQueue() throws JMSException
   {
      return (Queue)getDestination();
   }

   // Public --------------------------------------------------------

   @Override
   public String toString()
   {
      return "HornetQRAMessageProducer->" + producer;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private void doSend(final Message message, final long timeToLive, HornetQDestination destination) throws JMSException
   {
      if (timeToLive == 0)
      {
         message.setJMSExpiration(0);
      }
      else
      {
         message.setJMSExpiration(System.currentTimeMillis() + timeToLive);
      }

      if (!disableMessageTimestamp)
      {
         message.setJMSTimestamp(System.currentTimeMillis());
      }
      else
      {
         message.setJMSTimestamp(0);
      }

      SimpleString address = null;

      if (destination == null)
      {
         if (defaultDestination == null)
         {
            throw new InvalidDestinationException("Destination must be specified on send with an anonymous producer");
         }

         destination = defaultDestination;
      }
      else
      {
         if (defaultDestination != null)
         {
            if (!destination.equals(defaultDestination))
            {
               throw new JMSException("Where a default destination is specified " + "for the sender and a destination is "
                                      + "specified in the arguments to the send, "
                                      + "these destinations must be equal");
            }
         }

         address = destination.getSimpleAddress();
      }

      HornetQMessage msg;

      boolean foreign = false;

      // First convert from foreign message if appropriate
      if (!(message instanceof HornetQMessage))
      {
         // JMS 1.1 Sect. 3.11.4: A provider must be prepared to accept, from a client,
         // a message whose implementation is not one of its own.

         if (message instanceof BytesMessage)
         {
            msg = new HornetQBytesMessage((BytesMessage)message, clientSession);
         }
         else if (message instanceof MapMessage)
         {
            msg = new HornetQMapMessage((MapMessage)message, clientSession);
         }
         else if (message instanceof ObjectMessage)
         {
            msg = new HornetQObjectMessage((ObjectMessage)message, clientSession);
         }
         else if (message instanceof StreamMessage)
         {
            msg = new HornetQStreamMessage((StreamMessage)message, clientSession);
         }
         else if (message instanceof TextMessage)
         {
            msg = new HornetQTextMessage((TextMessage)message, clientSession);
         }
         else
         {
            msg = new HornetQMessage(message, clientSession);
         }

         // Set the destination on the original message
         message.setJMSDestination(destination);

         foreign = true;
      }
      else
      {
         msg = (HornetQMessage)message;
      }

      if (!disableMessageID)
      {
         // Generate an id

         SimpleString msgID = generateMessageID();

         msg.getCoreMessage().putStringProperty(HornetQMessage.HORNETQ_MESSAGE_ID, msgID);

         msg.resetMessageID(msgID.toString());
      }

      if (foreign)
      {
         message.setJMSMessageID(msg.getJMSMessageID());
      }

      msg.setJMSDestination(destination);

      try
      {
         msg.doBeforeSend();
      }
      catch (Exception e)
      {
         JMSException je = new JMSException(e.getMessage());

         je.initCause(e);

         throw je;
      }

      ClientMessage coreMessage = msg.getCoreMessage();

      if (jbossConn.hasNoLocal())
      {
         coreMessage.putStringProperty(HornetQConnection.CONNECTION_ID_PROPERTY_NAME, connID);
      }

      try
      {
         producer.send(address, coreMessage);
      }
      catch (HornetQException e)
      {
         throw JMSExceptionHelper.convertFromHornetQException(e);
      }
   }

   // This is faster than doing standard String concatenation and conversions from long to string
   private SimpleString generateMessageID()
   {
      byte[] prefixData = messageIDPrefix.getData();

      int len = prefixData.length + 16 * 2;

      byte[] bytes = new byte[len];

      System.arraycopy(messageIDPrefix.getData(), 0, bytes, 0, prefixData.length);

      int j = prefixData.length;

      long l = sequenceNumber++;

      for (int i = 0; i < 16; i++)
      {
         int ch = (int)(l & 0xF);

         l = l >> 4;

         char chr = (char)(ch + 48);

         bytes[j] = (byte)chr;

         j += 2;
      }

      return new SimpleString(bytes);
   }

   // private SimpleString generateOldMessageID()
   // {
   // SimpleString ss = new SimpleString(messageIDPrefix.getData());
   //      
   // ss.concat(String.valueOf(sequenceNumber++));
   //      
   // return ss;
   // }

   private void checkClosed() throws JMSException
   {
      if (producer.isClosed())
      {
         throw new IllegalStateException("Producer is closed");
      }
   }

   // Inner classes -------------------------------------------------
}
