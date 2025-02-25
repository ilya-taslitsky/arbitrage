package com.crypto.arbitrage.providers.orc.util;

import com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;

@Component
public class OrcSwapInstructionBuilder {

  public static final String ORCA_WHIRLPOOL_PROGRAM_ID =
      "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc";

  public byte[] createSwapInstructionData(BigInteger inputAmount, BigInteger minOutput) {
    ByteBuffer buffer = ByteBuffer.allocate(1 + 8 + 8);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.put((byte) 1); // swap instruction ID
    buffer.putLong(inputAmount.longValue());
    buffer.putLong(minOutput.longValue());
    return buffer.array();
  }

  public List<AccountMeta> buildSwapAccounts(
      OrcWhirlpool pool,
      String userWallet,
      String userTokenAccountA,
      String userTokenAccountB,
      String tokenVaultA,
      String tokenVaultB,
      String tickArray0,
      String tickArray1,
      String tickArray2,
      String extraTickArray1,
      String extraTickArray2) {
    List<AccountMeta> accounts = new ArrayList<>();
    accounts.add(AccountMeta.createWritableSigner(PublicKey.fromBase58Encoded(userWallet)));
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(pool.tokenVaultA)));
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(pool.tokenVaultB)));
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(tickArray0)));
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(tickArray1)));
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(tickArray2)));
    accounts.add(AccountMeta.createWrite(PublicKey.fromBase58Encoded(extraTickArray1)));
    accounts.add(AccountMeta.createWrite(PublicKey.fromBase58Encoded(extraTickArray2)));
    return accounts;
  }

  public Instruction buildSwapInstruction(byte[] swapData, List<AccountMeta> accounts) {
    PublicKey programId = PublicKey.fromBase58Encoded(ORCA_WHIRLPOOL_PROGRAM_ID);
    return Instruction.createInstruction(programId, accounts, swapData);
  }
}
