package com.temenos.t24pck;

import java.util.List;

import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.restatrepline.ReStatRepLineRecord;
import com.temenos.t24.api.records.teller.TellerRecord;
import com.temenos.t24.api.system.DataAccess;

public class PLLineMapping extends RecordLifecycle {

	@Override
	public String formatDealSlip(String data, TStructure currentRecord, TransactionContext transactionContext) {
		// TODO Auto-generated method stub
		TellerRecord teller = new TellerRecord(currentRecord);
		String acc = teller.getAccount2().getValue();
		
		DataAccess da = new DataAccess();
		System.out.println("Account before processing: " + acc);
		
		ReStatRepLineRecord reStatRepLine = new ReStatRepLineRecord();
		
		if (acc.length() > 2) {
			acc = acc.substring(2); // Remove first two characters
			System.out.println("Account after processing: " + acc);
			List<String> reStatRepLines = da.selectRecords("", "RE.STAT.REP.LINE", "", "WITH PROFIT1 EQ " + acc);
			System.out.println("ReStatRefLine records: " + reStatRepLines);
		}
		
		return acc;
		
	}

	
}
