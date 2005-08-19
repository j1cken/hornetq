/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.client.remoting;

import org.jboss.remoting.callback.HandleCallbackException;

/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class NACKCallbackException extends HandleCallbackException
{
   // Constants -----------------------------------------------------
   
   private static final long serialVersionUID = 5529946441447208850L;

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   // Constructors --------------------------------------------------

   public NACKCallbackException()
   {
      this(null);
   }

   public NACKCallbackException(String message)
   {
       super(message);
   }

   public NACKCallbackException(String message, Throwable cause)
   {
       super(message, cause);
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------   
}
