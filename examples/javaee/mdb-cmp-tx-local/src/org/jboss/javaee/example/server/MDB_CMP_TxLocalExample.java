/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.javaee.example.server;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
@MessageDriven(name = "MDB_CMP_TxLocalExample",
               activationConfig =
                     {
                           @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                           @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/testQueue"),
                           @ActivationConfigProperty(propertyName = "useLocalTx", propertyValue = "true")
                     })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class MDB_CMP_TxLocalExample implements MessageListener
{
   @Resource(mappedName = "java:/TransactionManager")
   private TransactionManager tm;

   public void onMessage(Message message)
   {
      try
      {
         //Step 9. We know the client is sending a text message so we cast
         TextMessage textMessage = (TextMessage) message;

         //Step 10. get the text from the message.
         String text = textMessage.getText();

         System.out.println("message " + text + " received");

         if (!textMessage.getJMSRedelivered())
         {
            Transaction tx = tm.getTransaction();

            if (tx != null)
            {
               System.out.println("something is wrong, there should be no global transaction: " + tx);
            }
            else
            {
               System.out.println("there is no global transaction, altho the messge delivery is using a local transaction");
               System.out.println("lets throw an exception and see what happens");
               throw new RuntimeException("DOH!");
            }
         }
         else
         {
            System.out.println("The message was redelivered since the message delivery used a local transaction");
         }

      }
      catch (JMSException e)
      {
         e.printStackTrace();
      }
      catch(SystemException e)
      {
         e.printStackTrace();
      }
   }
}