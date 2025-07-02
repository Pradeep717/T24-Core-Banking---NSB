package com.temenos.t24.nsb.reports;

import java.text.DecimalFormat;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.temenos.api.TDate;
import com.temenos.api.TStructure;
import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.aaprddesinterest.AaPrdDesInterestRecord;
import com.temenos.t24.api.records.aaprddesinterest.FixedRateClass;
import com.temenos.t24.api.system.Session;

public class GetFixAndSubsidyInt extends RecordLifecycle {

	Session T24session = new Session(this);
	String T24date = T24session.getCurrentVariable("!TODAY");
	TDate tdate = new TDate(T24date);

	@Override
	public String formatDealSlip(String data, TStructure currentRecord, TransactionContext transactionContext) {
		String rtnValue = "0.00%";
		DecimalFormat df = new DecimalFormat("0.00");
		Contract cnt = new Contract(this);
		cnt.setContractId(data);

		try {
			// Process DEPOSITINT (mandatory)
			AaPrdDesInterestRecord aaintRecord = new AaPrdDesInterestRecord(
					cnt.getConditionForPropertyEffectiveDate("DEPOSITINT", tdate));
			List<FixedRateClass> Fxrate = aaintRecord.getFixedRate();

			double baseRate = 0.0;
			double subsidyRate = 0.0;

			// Process base interest rate
			if (Fxrate != null && !Fxrate.isEmpty()) {
				JSONArray fxrateArray = new JSONArray(Fxrate);
				if (fxrateArray.length() > 0) {
					JSONObject fxrateObject = fxrateArray.getJSONObject(0);
					if (fxrateObject.has("effectiveRate")) {
						Object effectiveRate = fxrateObject.get("effectiveRate");
						if (effectiveRate instanceof JSONObject) {
							baseRate = ((JSONObject) effectiveRate).optDouble("value", 0.0);
						} else {
							baseRate = Double.parseDouble(effectiveRate.toString());
						}
						rtnValue = df.format(baseRate) + "%";
					}
				}
			}

			// Process DEPSUBSIDYINT (optional - only if exists)
			try {
				AaPrdDesInterestRecord aaintRecord2 = new AaPrdDesInterestRecord(
						cnt.getConditionForPropertyEffectiveDate("DEPSUBSIDYINT", tdate));
				List<FixedRateClass> SubsidyFxrate = aaintRecord2.getFixedRate();

				if (SubsidyFxrate != null && !SubsidyFxrate.isEmpty()) {
					JSONArray subsidyFxrateArray = new JSONArray(SubsidyFxrate);
					if (subsidyFxrateArray.length() > 0) {
						JSONObject subsidyFxrateObject = subsidyFxrateArray.getJSONObject(0);

						if (subsidyFxrateObject.has("effectiveRate")) {
							Object subsidyEffectiveRate = subsidyFxrateObject.get("effectiveRate");
							if (subsidyEffectiveRate instanceof JSONObject) {
								subsidyRate = ((JSONObject) subsidyEffectiveRate).optDouble("value", 0.0);
							} else {
								subsidyRate = Double.parseDouble(subsidyEffectiveRate.toString());
							}
						}
					}
				}
			} catch (Exception e) {

				// Continue with subsidyRate = 0.0
			}

			// Calculate total rate
			double totalInterestRate = baseRate + subsidyRate;

			rtnValue = df.format(totalInterestRate) + "%";

		} catch (Exception e) {

			rtnValue = "0.00%";
		}

		return rtnValue;
	}
}