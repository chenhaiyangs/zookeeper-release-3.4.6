// File generated by hadoop record compiler. Do not edit.
/**
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

package org.apache.zookeeper.server.quorum;

import org.apache.jute.*;

/**
 * 用于ZooKeeper服务器之间传递的数据包
 */
public class QuorumPacket implements Record {
  private int type;
  private long zxid;
  private byte[] data;
  private java.util.List<org.apache.zookeeper.data.Id> authinfo;
  public QuorumPacket() {
  }
  public QuorumPacket(
        int type,
        long zxid,
        byte[] data,
        java.util.List<org.apache.zookeeper.data.Id> authinfo) {
    this.type=type;
    this.zxid=zxid;
    this.data=data;
    this.authinfo=authinfo;
  }
  public int getType() {
    return type;
  }
  public void setType(int m_) {
    type=m_;
  }
  public long getZxid() {
    return zxid;
  }
  public void setZxid(long m_) {
    zxid=m_;
  }
  public byte[] getData() {
    return data;
  }
  public void setData(byte[] m_) {
    data=m_;
  }
  public java.util.List<org.apache.zookeeper.data.Id> getAuthinfo() {
    return authinfo;
  }
  public void setAuthinfo(java.util.List<org.apache.zookeeper.data.Id> m_) {
    authinfo=m_;
  }

  @Override
  public void serialize(OutputArchive a_, String tag) throws java.io.IOException {
    a_.startRecord(this,tag);
    a_.writeInt(type,"type");
    a_.writeLong(zxid,"zxid");
    a_.writeBuffer(data,"data");
    {
      a_.startVector(authinfo,"authinfo");
      if (authinfo!= null) {          int len1 = authinfo.size();
          for(int vidx1 = 0; vidx1<len1; vidx1++) {
            org.apache.zookeeper.data.Id e1 = (org.apache.zookeeper.data.Id) authinfo.get(vidx1);
    a_.writeRecord(e1,"e1");
          }
      }
      a_.endVector(authinfo,"authinfo");
    }
    a_.endRecord(this,tag);
  }

  @Override
  public void deserialize(InputArchive a_, String tag) throws java.io.IOException {
    a_.startRecord(tag);
    type=a_.readInt("type");
    zxid=a_.readLong("zxid");
    data=a_.readBuffer("data");
    {
      Index vidx1 = a_.startVector("authinfo");
      if (vidx1!= null) {          authinfo=new java.util.ArrayList<org.apache.zookeeper.data.Id>();
          for (; !vidx1.done(); vidx1.incr()) {
    org.apache.zookeeper.data.Id e1;
    e1= new org.apache.zookeeper.data.Id();
    a_.readRecord(e1,"e1");
            authinfo.add(e1);
          }
      }
    a_.endVector("authinfo");
    }
    a_.endRecord(tag);
}
  @Override
  public String toString() {
    try {
      java.io.ByteArrayOutputStream s =
        new java.io.ByteArrayOutputStream();
      CsvOutputArchive a_ = 
        new CsvOutputArchive(s);
      a_.startRecord(this,"");
    a_.writeInt(type,"type");
    a_.writeLong(zxid,"zxid");
    a_.writeBuffer(data,"data");
    {
      a_.startVector(authinfo,"authinfo");
      if (authinfo!= null) {          int len1 = authinfo.size();
          for(int vidx1 = 0; vidx1<len1; vidx1++) {
            org.apache.zookeeper.data.Id e1 = (org.apache.zookeeper.data.Id) authinfo.get(vidx1);
    a_.writeRecord(e1,"e1");
          }
      }
      a_.endVector(authinfo,"authinfo");
    }
      a_.endRecord(this,"");
      return new String(s.toByteArray(), "UTF-8");
    } catch (Throwable ex) {
      ex.printStackTrace();
    }
    return "ERROR";
  }
  public void write(java.io.DataOutput out) throws java.io.IOException {
    BinaryOutputArchive archive = new BinaryOutputArchive(out);
    serialize(archive, "");
  }
  public void readFields(java.io.DataInput in) throws java.io.IOException {
    BinaryInputArchive archive = new BinaryInputArchive(in);
    deserialize(archive, "");
  }
  public int compareTo (Object peer_) throws ClassCastException {
    throw new UnsupportedOperationException("comparing QuorumPacket is unimplemented");
  }

  @Override
  public boolean equals(Object peer_) {
    if (!(peer_ instanceof QuorumPacket)) {
      return false;
    }
    if (peer_ == this) {
      return true;
    }
    QuorumPacket peer = (QuorumPacket) peer_;
    boolean ret = false;
    ret = (type==peer.type);
    if (!ret){
        return ret;
    }
    ret = (zxid==peer.zxid);
    if (!ret){
        return ret;
    }
    ret = org.apache.jute.Utils.bufEquals(data,peer.data);
    if (!ret){
        return ret;
    }
    ret = authinfo.equals(peer.authinfo);
    if (!ret){
        return ret;
    }
     return ret;
  }
  @Override
  public int hashCode() {
    int result = 17;
    int ret;
    ret = (int)type;
    result = 37*result + ret;
    ret = (int) (zxid^(zxid>>>32));
    result = 37*result + ret;
    ret = java.util.Arrays.toString(data).hashCode();
    result = 37*result + ret;
    ret = authinfo.hashCode();
    result = 37*result + ret;
    return result;
  }
  public static String signature() {
    return "LQuorumPacket(ilB[LId(ss)])";
  }
}