package com.temenos.t24pck;

import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.templatehook.InputValue;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.customer.CustomerRecord;

/**
 * TODO: Document me!
 *
 * @author pradeeps
 *
 */
public class DefaultOnHotRtnOne extends RecordLifecycle {

    @Override
    public void defaultFieldValuesOnHotField(String application, String currentRecordId, TStructure currentRecord,
            InputValue currentInputValue, TStructure unauthorisedRecord, TStructure liveRecord,
            TransactionContext transactionContext) {
        // TODO Auto-generated method stub
        CustomerRecord cus = new CustomerRecord(currentRecord);
        String mnc = cus.getMnemonic().getValue();
        
        if(mnc.equals("SAMPLE")){
            cus.getSector().setValue("1001");
            cus.getAccountOfficer().setValue("1");
        } else if(mnc.equals("TESTING")){
            cus.getSector().setValue("1001");
            cus.getAccountOfficer().setValue("2");
        } else {
            cus.getSector().setValue("1001");
            cus.getAccountOfficer().setValue("3");
        }
        
        currentRecord.set(cus.toStructure());  
    }
}
