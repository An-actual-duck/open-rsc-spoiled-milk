package orsc;
import java.io.*;
import java.security.*;
import orsc.graphics.three.*;
/** Same fixture executes unmodified on original and candidate complete JARs. */
public final class PreparedCodecWireFixture {
 public static void main(String[] args)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");int checks=0;
  for(boolean polarity:new boolean[]{false,true}){
   RSModel source=WholeRamModelFieldFixture.sentinel(polarity);String expected=WholeRamModelFieldFixture.snapshot(source);
   WholeRamData.Pool pool=new WholeRamData.Pool();int descriptor=RSModel.capturePreparedState(source,pool);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(descriptor);pool.write(out);digest.update(bytes.toByteArray());
   RSModel first=RSModel.instantiatePreparedState(descriptor,pool),second=RSModel.instantiatePreparedState(descriptor,pool);
   if(!expected.equals(WholeRamModelFieldFixture.snapshot(first)))throw new AssertionError("complete first state/alias graph");checks++;
   WholeRamModelFieldFixture.disjoint(source,first);WholeRamModelFieldFixture.disjoint(first,second);
   WholeRamModelFieldFixture.mutateArrays(first);if(!expected.equals(WholeRamModelFieldFixture.snapshot(second)))throw new AssertionError("mutable model leakage");checks++;
   ByteArrayOutputStream encoded=new ByteArrayOutputStream();pool.write(new DataOutputStream(encoded));WholeRamData.Pool restored=WholeRamData.Pool.read(new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())));
   if(!expected.equals(WholeRamModelFieldFixture.snapshot(RSModel.instantiatePreparedState(descriptor,restored))))throw new AssertionError("wire state/alias graph");checks++;
  }
  StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));System.out.println("WIRE "+hex+" checks="+checks+" all93fields/rawfloatbits/fullcapacities/aliases; actual old/default codec");
 }
}
