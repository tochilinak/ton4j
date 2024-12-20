package org.ton.java.tlb.types;

import lombok.Builder;
import lombok.Data;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.cell.CellSlice;

@Builder
@Data
public class ConfigParams3 {
    Address feeCollectorAddr;

    public Cell toCell() {
        return CellBuilder.beginCell()
                .storeAddress(feeCollectorAddr)
                .endCell();
    }

    public static ConfigParams3 deserialize(CellSlice cs) {
        return ConfigParams3.builder()
                .feeCollectorAddr(Address.of(cs.loadBits(256).toByteArray())) // bounceable and workchain -1
                .build();
    }
}
