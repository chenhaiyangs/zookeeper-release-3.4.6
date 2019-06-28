/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jute;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 二进制的输入
 */
public class BinaryInputArchive implements InputArchive {
    
    private DataInput in;
    
    static public BinaryInputArchive getArchive(InputStream strm) {
        return new BinaryInputArchive(new DataInputStream(strm));
    }
    
    static private class BinaryIndex implements Index {
        private int nelems;
        BinaryIndex(int nelems) {
            this.nelems = nelems;
        }
        @Override
        public boolean done() {
            return (nelems <= 0);
        }
        @Override
        public void incr() {
            nelems--;
        }
    }
    /** Creates a new instance of BinaryInputArchive */
    public BinaryInputArchive(DataInput in) {
        this.in = in;
    }

    @Override
    public byte readByte(String tag) throws IOException {
        return in.readByte();
    }
    @Override
    public boolean readBool(String tag) throws IOException {
        return in.readBoolean();
    }
    @Override
    public int readInt(String tag) throws IOException {
        return in.readInt();
    }
    @Override
    public long readLong(String tag) throws IOException {
        return in.readLong();
    }
    @Override
    public float readFloat(String tag) throws IOException {
        return in.readFloat();
    }
    @Override
    public double readDouble(String tag) throws IOException {
        return in.readDouble();
    }
    @Override
    public String readString(String tag) throws IOException {
    	int len = in.readInt();
    	if (len == -1){
    	    return null;
        }
    	byte b[] = new byte[len];
    	in.readFully(b);
    	return new String(b, "UTF8");
    }

    /**
     * 最大长度
     */
    static public final int maxBuffer = Integer.getInteger("jute.maxbuffer", 0xfffff);

    @Override
    public byte[] readBuffer(String tag) throws IOException {
        int len = readInt(tag);
        if (len == -1){
            return null;
        }
        // Since this is a rough sanity check, add some padding to maxBuffer to
        // make up for extra fields, etc. (otherwise e.g. clients may be able to
        // write buffers larger than we can read from disk!)
        if (len < 0 || len > maxBuffer + 1024) {
            throw new IOException("Unreasonable length = " + len);
        }
        byte[] arr = new byte[len];
        in.readFully(arr);
        return arr;
    }
    @Override
    public void readRecord(Record r, String tag) throws IOException {
        r.deserialize(this, tag);
    }
    @Override
    public void startRecord(String tag) throws IOException {

    }
    @Override
    public void endRecord(String tag) throws IOException {

    }
    @Override
    public Index startVector(String tag) throws IOException {
        int len = readInt(tag);
        if (len == -1) {
        	return null;
        }
		return new BinaryIndex(len);
    }
    @Override
    public void endVector(String tag) throws IOException {}
    @Override
    public Index startMap(String tag) throws IOException {
        return new BinaryIndex(readInt(tag));
    }
    @Override
    public void endMap(String tag) throws IOException {}
    
}
