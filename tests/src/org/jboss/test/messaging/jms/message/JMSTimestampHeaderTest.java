/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.jms.message;

import javax.jms.Message;

/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class JMSTimestampHeaderTest extends MessageTest
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public JMSTimestampHeaderTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void setUp() throws Exception
   {
      super.setUp();
   }

   public void tearDown() throws Exception
   {
      super.tearDown();
   }

   public void testJMSTimestamp() throws Exception
   {
      Message m = producerSession.createMessage();

      long t1 = System.currentTimeMillis();
      producer.send(m);
      long t2 = System.currentTimeMillis();
      long timestamp = consumer.receive().getJMSTimestamp();

      assertTrue(timestamp >= t1);
      assertTrue(timestamp <= t2);
   }

   public void testDisabledTimestamp() throws Exception
   {
      Message m = producerSession.createMessage();

      producer.setDisableMessageTimestamp(true);
      producer.send(m);
      assertEquals(0l, consumer.receive().getJMSTimestamp());
   }


   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------

}
