package com.temenos.t24pck;

import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.account.AccountRecord;

public class CheckRecordRtnUSD extends RecordLifecycle {

    @Override
    public void defaultFieldValues(String application, String currentRecordId, TStructure currentRecord,
            TStructure unauthorisedRecord, TStructure liveRecord, TransactionContext transactionContext) {
        // TODO Auto-generated method stub
        AccountRecord acc = new AccountRecord(currentRecord);
        acc.getCurrency().setValue("USD");
        currentRecord.set(acc.toStructure());
    }
}