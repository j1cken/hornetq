/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.server.container;

import org.jboss.aop.advice.Interceptor;
import org.jboss.aop.joinpoint.Invocation;
import org.jboss.aop.joinpoint.MethodInvocation;
import org.jboss.jms.server.BrowserEndpoint;
import org.jboss.jms.server.BrowserEndpointFactory;

/**
 * The server implementation of the browser
 * 
 * @author <a href="mailto:adrian@jboss.org>Adrian Brock</a>
 * @version $Revision$
 */
public class ServerBrowserInterceptor
   implements Interceptor
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   public static ServerBrowserInterceptor singleton = new ServerBrowserInterceptor();

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Interceptor implementation ------------------------------------

   public String getName()
   {
      return "ServerBrowserInterceptor";
   }

   public Object invoke(Invocation invocation) throws Throwable
   {
      MethodInvocation mi = (MethodInvocation) invocation;
      String methodName = mi.method.getName();
      if (methodName.equals("browse"))
      {
         BrowserEndpointFactory factory = (BrowserEndpointFactory) mi.getMetaData("JMS", "BrowserEndpointFactory");
         BrowserEndpoint endpoint = factory.getBrowserEndpoint();
         return endpoint.browse();
      }
      else if (methodName.equals("closing") || methodName.equals("close"))
         return null;
      throw new UnsupportedOperationException(mi.method.toString()); 
   }

   // Protected ------------------------------------------------------

   // Package Private ------------------------------------------------

   // Private --------------------------------------------------------

   // Inner Classes --------------------------------------------------

}
