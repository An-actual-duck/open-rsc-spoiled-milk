package com.openrsc.server.login;

import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import java.security.MessageDigest;

/** Explicit custom-WebSocket-only pre-world protocol, isolated from gameplay254 equipment. */
public final class StagedLoginProtocol {
    public static final int OPCODE=254,MAGIC=0x42535031,VERSION=1,PENDING_RESPONSE=26;
    public static final int SUPPORT=0,PREPARE=1,COMMIT=2,ERROR=3,ACK=4,COMPLETE=5;
    public static final int TOKEN_BYTES=16,DIGEST_BYTES=32,MAX_INPUT_BYTES=6+TOKEN_BYTES+DIGEST_BYTES+1+222;
    private StagedLoginProtocol(){}
    public static PacketBuilder packet(int kind){return new PacketBuilder(OPCODE).writeInt(MAGIC).writeByte(VERSION).writeByte(kind);}
    public static int header(Packet packet){
        if(packet.getID()!=OPCODE||packet.getReadableBytes()<6||packet.getReadableBytes()>MAX_INPUT_BYTES)throw new IllegalArgumentException("Invalid staged-login envelope");
        if(packet.readInt()!=MAGIC||packet.readUnsignedByte()!=VERSION)throw new IllegalArgumentException("Unsupported staged-login envelope");return packet.readUnsignedByte();
    }
    public static boolean matches(byte[] expected,byte[] actual){return MessageDigest.isEqual(expected,actual);}

    /** Parse only the bounded pending control grammar, before considering ownership. */
    public static Control control(Packet packet) {
        int kind=header(packet);
        if(kind<1||kind>4||packet.getReadableBytes()<TOKEN_BYTES+DIGEST_BYTES)
            throw new IllegalArgumentException("Invalid preparation action");
        byte[] token=packet.readBytes(TOKEN_BYTES),digest=packet.readBytes(DIGEST_BYTES);
        int receiptOpcode=-1;byte[] receipt=null;
        if(kind==ACK){
            if(packet.getReadableBytes()<1)throw new IllegalArgumentException("Missing preparation receipt");
            receiptOpcode=packet.readUnsignedByte();int n=packet.getReadableBytes();
            if((receiptOpcode!=154&&receiptOpcode!=155)||n<(receiptOpcode==155?28:24)||n>222)
                throw new IllegalArgumentException("Invalid preparation receipt");
            receipt=packet.readBytes(n);
        }else if(packet.getReadableBytes()!=0)throw new IllegalArgumentException("Trailing preparation action");
        return new Control(kind,token,digest,receiptOpcode,receipt);
    }
    public static final class Control {
        public final int kind,receiptOpcode;
        public final byte[] token,digest,receipt;
        private Control(int kind,byte[] token,byte[] digest,int receiptOpcode,byte[] receipt){
            this.kind=kind;this.token=token;this.digest=digest;this.receiptOpcode=receiptOpcode;this.receipt=receipt;
        }
    }
}
