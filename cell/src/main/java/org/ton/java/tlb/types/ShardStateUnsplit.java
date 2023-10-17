package org.ton.java.tlb.types;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.ton.java.cell.Cell;
import org.ton.java.cell.TonHashMapE;

import java.math.BigInteger;

@Builder
@Getter
@Setter
@ToString
/**
 * shard_state#9023afe2
 *   global_id:int32
 *   shard_id:ShardIdent
 *   seq_no:uint32
 *   vert_seq_no:#
 *   gen_utime:uint32
 *   gen_lt:uint64
 *   min_ref_mc_seqno:uint32
 *   out_msg_queue_info:^OutMsgQueueInfo
 *   before_split:(## 1)
 *   accounts:^ShardAccounts
 *   ^[ overload_history:uint64
 *     underload_history:uint64
 *     total_balance:CurrencyCollection
 *     total_validator_fees:CurrencyCollection
 *     libraries:(HashmapE 256 LibDescr)
 *     master_ref:(Maybe BlkMasterInfo) ]
 *   custom:(Maybe ^McStateExtra)
 *   = ShardStateUnsplit;
 */
public class ShardStateUnsplit {
    long magic;
    int globalId;
    ShardIdent shardIdent;
    long seqno;
    long vertSeqno;
    long genUTime;
    BigInteger genLT;
    long minRefMCSeqno;
    Cell outMsgQueueInfo;
    boolean beforeSplit;
    TonHashMapE accounts;
    Cell stats;
    McStateExtra custom;

    private String getMagic() {
        return Long.toHexString(magic);
    }
}