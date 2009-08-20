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

package org.hornetq.core.asyncio;

import java.nio.ByteBuffer;

import org.hornetq.core.exception.MessagingException;

/**
 * 
 * @author clebert.suconic@jboss.com
 *
 */
public interface AsynchronousFile
{
   void close() throws Exception;

   /**
    * 
    * Note: If you are using a native Linux implementation, maxIO can't be higher than what's defined on /proc/sys/fs/aio-max-nr, or you would get an error 
    * @param fileName
    * @param maxIO The number of max concurrent asynchrnous IO operations. It has to be balanced between the size of your writes and the capacity of your disk.
    * @throws MessagingException 
    */
   void open(String fileName, int maxIO) throws MessagingException;

   /** 
    * Warning: This function will perform a synchronous IO, probably translating to a fstat call
    * @throws MessagingException 
    * */
   long size() throws MessagingException;

   /** Any error will be reported on the callback interface */ 
   void write(long position, long size, ByteBuffer directByteBuffer, AIOCallback aioCallback);

   void read(long position, long size, ByteBuffer directByteBuffer, AIOCallback aioCallback) throws MessagingException;

   void fill(long position, int blocks, long size, byte fillChar) throws MessagingException;

   void setBufferCallback(BufferCallback callback);

   int getBlockSize();

   String getFileName();

}
