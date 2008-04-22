/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.tests.integration.core.remoting.mina;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.jboss.messaging.tests.util.RandomUtil.randomLong;
import junit.framework.TestCase;

import org.apache.mina.common.IoSession;
import org.jboss.messaging.core.exception.MessagingException;
import org.jboss.messaging.core.remoting.KeepAliveFactory;
import org.jboss.messaging.core.remoting.impl.mina.CleanUpNotifier;
import org.jboss.messaging.core.remoting.impl.mina.MinaKeepAliveFactory;
import org.jboss.messaging.core.remoting.impl.wireformat.Pong;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * 
 * @version <tt>$Revision$</tt>
 * 
 */
public class MinaKeepAliveFactoryTest extends TestCase
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testResponseIsNotAPong()
   {
      IoSession session = createMock(IoSession.class);
      KeepAliveFactory factory = createMock(KeepAliveFactory.class);
      CleanUpNotifier notifier = createMock(CleanUpNotifier.class);
      replay(session, factory, notifier);

      MinaKeepAliveFactory minaFactory = new MinaKeepAliveFactory(factory, notifier);

      assertFalse(minaFactory.isResponse(session, new Object()));
      
      verify(session, factory, notifier);
   }

   public void testResponseIsAPongWithSessionNotFailed()
   {
      IoSession session = createMock(IoSession.class);
      long sessionID = randomLong();
      Pong pong = new Pong(sessionID, false);
      KeepAliveFactory factory = createMock(KeepAliveFactory.class);
      CleanUpNotifier notifier = createMock(CleanUpNotifier.class);
      replay(session, factory, notifier);

      MinaKeepAliveFactory minaFactory = new MinaKeepAliveFactory(factory, notifier);

      assertTrue(minaFactory.isResponse(session, pong));

      verify(session, factory, notifier);
   }

   public void testResponseIsAPongWithSessionFailed()
   {
      IoSession session = createMock(IoSession.class);
      long sessionID = randomLong();
      expect(session.getId()).andStubReturn(sessionID);
      Pong pong = new Pong(sessionID, true);
      KeepAliveFactory factory = createMock(KeepAliveFactory.class);
      CleanUpNotifier notifier = createMock(CleanUpNotifier.class);
      notifier.fireCleanup(anyLong(), isA(MessagingException.class));
      expectLastCall().once();
      replay(session, factory, notifier);
      
      MinaKeepAliveFactory minaFactory = new MinaKeepAliveFactory(factory, notifier);

      assertTrue(minaFactory.isResponse(session, pong));

      verify(session, factory, notifier);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
