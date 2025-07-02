package com.temenos.t24.nsb.reports;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.temenos.api.TDate;
import com.temenos.api.TStructure;
import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.aaprddesinterest.AaPrdDesInterestRecord;
import com.temenos.t24.api.records.aaprddesinterest.FixedRateClass;
import com.temenos.t24.api.system.Session;

public class GetInterestrateNsbNbl extends RecordLifecycle {

	Session T24session = new Session(this);
	String T24date = T24session.getCurrentVariable("!TODAY");
	TDate tdate = new TDate(T24date);

	private static final Logger LOGGER = Logger.getLogger(GetInterestrateNsbNbl.class.getName());

	// Debug logging method
	private static void logToFile(String line) {
		String filePath = "/nsbt24/debug/FixAndSubsudyInt01.txt";
		line = line + "\n";
		try {
			Files.write(Paths.get(filePath), line.getBytes(),
					Files.exists(Paths.get(filePath)) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error writing to file", e);
		}
	}

	@Override
	public String formatDealSlip(String data, TStructure currentRecord, TransactionContext transactionContext) {
		String rtnValue = "0.00%";
		DecimalFormat df = new DecimalFormat("0.00");

		Contract cnt = new Contract(this);
		cnt.setContractId(data);

		double baseRate = 0.0;
		double subsidyRate = 0.0;

		try {
			// Always process DEPOSITINT (Mandatory)
			AaPrdDesInterestRecord aaintRecord = new AaPrdDesInterestRecord(
					cnt.getConditionForPropertyEffectiveDate("DEPOSITINT", tdate));
			List<FixedRateClass> fxrateList = aaintRecord.getFixedRate();

			if (fxrateList != null && !fxrateList.isEmpty()) {
				FixedRateClass fx = fxrateList.get(0);
				String baseRateStr = fx.getEffectiveRate().getValue();
				logToFile("Base Rate Raw: " + baseRateStr);
				if (baseRateStr != null && !baseRateStr.isEmpty()) {
					baseRate = Double.parseDouble(baseRateStr);
				}
			} else {
				logToFile("DEPOSITINT has no fixed rate entries");
				throw new Exception("Missing DEPOSITINT fixed rate");
			}

		} catch (Exception e) {
			logToFile("Error in DEPOSITINT: " + e.getMessage());
			return "0.00%"; // Mandatory field failed — exit early
		}

		try {
			// Optionally process DEPSUBSIDYINT
			AaPrdDesInterestRecord aaintRecord2 = new AaPrdDesInterestRecord(
					cnt.getConditionForPropertyEffectiveDate("DEPSUBSIDYINT", tdate));
			List<FixedRateClass> subsidyRateList = aaintRecord2.getFixedRate();

			if (subsidyRateList != null && !subsidyRateList.isEmpty()) {
				FixedRateClass subFx = subsidyRateList.get(0);
				String subsidyRateStr = subFx.getEffectiveRate().getValue();
				logToFile("Subsidy Rate Raw: " + subsidyRateStr);
				if (subsidyRateStr != null && !subsidyRateStr.isEmpty()) {
					subsidyRate = Double.parseDouble(subsidyRateStr);
				}
			} else {
				logToFile("DEPSUBSIDYINT has no fixed rate entries (optional)");
			}

		} catch (Exception e) {
			logToFile("No DEPSUBSIDYINT found or error occurred (optional): " + e.getMessage());
			// Continue — don't stop execution
		}

		double totalRate = baseRate + subsidyRate;
		logToFile("Total Interest Rate: " + totalRate);
		rtnValue = df.format(totalRate) + "%";

		return rtnValue;
	}

}
